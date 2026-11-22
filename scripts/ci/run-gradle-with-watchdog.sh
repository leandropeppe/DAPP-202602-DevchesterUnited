#!/usr/bin/env bash

# Run a Gradle command under a bounded watchdog. Command arguments are never
# written to diagnostics because they can contain credentials.
set -o pipefail

TIMEOUT_SECONDS="${CI_WATCHDOG_TIMEOUT_SECONDS:-2520}"
GRACE_SECONDS="${CI_WATCHDOG_GRACE_SECONDS:-30}"
MAX_JAVA_DUMPS="${CI_WATCHDOG_MAX_JAVA_DUMPS:-4}"
CLEANUP_TIMEOUT_SECONDS="${CI_WATCHDOG_CLEANUP_TIMEOUT_SECONDS:-300}"
CLEANUP_PUBLICATION_RESERVE_SECONDS=30

SCRIPT_DIRECTORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPOSITORY_ROOT="$(cd -- "${SCRIPT_DIRECTORY}/../.." && pwd -P)"
DIAGNOSTICS_DIRECTORY="${REPOSITORY_ROOT}/backend/build/diagnostics"

if (( $# == 0 )); then
	printf 'Usage: %s <gradle-command> [arguments...]\n' "$0" >&2
	exit 2
fi

for setting in TIMEOUT_SECONDS GRACE_SECONDS MAX_JAVA_DUMPS CLEANUP_TIMEOUT_SECONDS; do
	value="${!setting}"
	if ! [[ "$value" =~ ^[1-9][0-9]*$ ]]; then
		printf '%s must be a positive integer, got %q\n' "$setting" "$value" >&2
		exit 2
	fi
done

SUPERVISED_PID=''
SUPERVISED_PROCESS_GROUP=''
USES_ISOLATED_PROCESS_GROUP=false
STAGING_DIRECTORY=''
WATCHDOG_PID="$$"
CLEANUP_DEADLINE=0
OPERATION_DEADLINE=0

run_with_deadline() {
	local timeout_seconds="$1"
	shift
	local effective_timeout="$timeout_seconds"
	local configured_deadline
	local remaining_seconds
	for configured_deadline in "$OPERATION_DEADLINE" "$CLEANUP_DEADLINE"; do
		((configured_deadline > 0)) || continue
		remaining_seconds=$((configured_deadline - SECONDS))
		((remaining_seconds > 0)) || return 124
		((remaining_seconds < effective_timeout)) && effective_timeout=$remaining_seconds
	done

	"$@" &
	local command_pid=$!
	local deadline=$((SECONDS + effective_timeout))
	while process_is_running "$command_pid"; do
		if ((SECONDS >= deadline)); then
			kill -TERM "$command_pid" 2>/dev/null || true
			kill -KILL "$command_pid" 2>/dev/null || true
			return 124
		fi
		sleep 1
	done
	wait "$command_pid"
}

process_is_running() {
	local process_pid="$1"
	kill -0 "$process_pid" 2>/dev/null
}

deadline_has_expired() {
	local configured_deadline
	for configured_deadline in "$OPERATION_DEADLINE" "$CLEANUP_DEADLINE"; do
		((configured_deadline > 0 && SECONDS >= configured_deadline)) && return 0
	done
	return 1
}

supervised_command_is_running() {
	process_is_running "$SUPERVISED_PID"
}

reap_supervised_command_if_finished() {
	if ! supervised_command_is_running; then
		wait "$SUPERVISED_PID"
		return $?
	fi
	return 125
}

associated_processes() {
	deadline_has_expired && return 124
	if [[ "$USES_ISOLATED_PROCESS_GROUP" == true ]]; then
		run_with_deadline 5 ps -eo pid=,pgid= 2>/dev/null |
			awk -v group="$SUPERVISED_PROCESS_GROUP" -v watchdog="$WATCHDOG_PID" \
				'$1 != watchdog && $2 == group { print $1 }'
		return
	fi

	local root_pid="$1"
	[[ "$root_pid" == "$WATCHDOG_PID" ]] && return
	printf '%s\n' "$root_pid"
	while IFS= read -r child_pid; do
		[[ -n "$child_pid" ]] && associated_processes "$child_pid"
	done < <(
		run_with_deadline 5 ps -eo pid=,ppid= 2>/dev/null |
			awk -v parent="$root_pid" -v watchdog="$WATCHDOG_PID" \
				'$1 != watchdog && $2 == parent { print $1 }'
	)
}

associated_processes_are_running() {
	local process_pid
	if [[ "$USES_ISOLATED_PROCESS_GROUP" == true ]]; then
		kill -0 -- "-${SUPERVISED_PROCESS_GROUP}" 2>/dev/null
		return
	fi
	while IFS= read -r process_pid; do
		[[ "$process_pid" == "$WATCHDOG_PID" ]] && continue
		kill -0 "$process_pid" 2>/dev/null && return 0
	done < <(associated_processes "$SUPERVISED_PID")
	return 1
}

signal_associated_processes() {
	local signal_name="$1"
	if [[ "$USES_ISOLATED_PROCESS_GROUP" == true ]]; then
		kill -s "$signal_name" -- "-${SUPERVISED_PROCESS_GROUP}" 2>/dev/null || true
		return
	fi
	associated_processes "$SUPERVISED_PID" |
		awk '{ processes[NR] = $1 } END { for (i = NR; i >= 1; i--) print processes[i] }' |
		while IFS= read -r process_pid; do
			[[ "$process_pid" == "$WATCHDOG_PID" ]] && continue
			kill -s "$signal_name" "$process_pid" 2>/dev/null || true
		done
}

capture_process_snapshot() {
	{
		printf 'PID PPID PGID COMMAND\n'
		while IFS= read -r process_pid; do
			deadline_has_expired && break
			run_with_deadline 5 ps -o pid=,ppid=,pgid=,comm= -p "$process_pid" 2>/dev/null || true
		done < <(associated_processes "$SUPERVISED_PID")
	} > "${STAGING_DIRECTORY}/associated-processes.txt"

	if ! deadline_has_expired && command -v ps >/dev/null 2>&1; then
		# Keep only the first command token: ps -ef arguments may contain secrets.
		run_with_deadline 5 ps -ef 2>&1 |
			awk 'NR == 1 { print "UID PID PPID C STIME TTY TIME COMMAND"; next } { print $1, $2, $3, $4, $5, $6, $7, $8 }' \
			> "${STAGING_DIRECTORY}/ps-ef.txt" || true
	fi
}

capture_java_diagnostics() {
	local java_pid
	deadline_has_expired && return 124
	printf 'Java PIDs associated with the supervised command:\n' \
		> "${STAGING_DIRECTORY}/jps-associated.txt"

	if ! command -v jps >/dev/null 2>&1; then
		printf 'jps is not available.\n' >> "${STAGING_DIRECTORY}/jps-associated.txt"
		return
	fi

	run_with_deadline 10 jps -l > "${STAGING_DIRECTORY}/jps-output.txt" \
		2> "${STAGING_DIRECTORY}/jps-errors.txt" || true
	while IFS= read -r jps_line; do
		deadline_has_expired && break
		java_pid="${jps_line%% *}"
		[[ -n "$java_pid" ]] || continue
		if ! associated_processes "$SUPERVISED_PID" | grep -Fxq "$java_pid"; then
			continue
		fi
		printf '%s\n' "$jps_line" >> "${STAGING_DIRECTORY}/jps-associated.txt"
	done < "${STAGING_DIRECTORY}/jps-output.txt"

	local dump_count=0
	capture_thread_dump() {
		deadline_has_expired && return 124
		java_pid="${1%% *}"
		[[ "$java_pid" =~ ^[0-9]+$ ]] || return
		((dump_count < MAX_JAVA_DUMPS)) || return
		if grep -Fxq "$java_pid" "${STAGING_DIRECTORY}/thread-dump-pids.txt" 2>/dev/null; then
			return
		fi
		run_with_deadline 10 jcmd "$java_pid" Thread.print \
			> "${STAGING_DIRECTORY}/jcmd-${java_pid}-thread-dump.txt" 2>&1 || true
		printf '%s\n' "$java_pid" >> "${STAGING_DIRECTORY}/thread-dump-pids.txt"
		((dump_count += 1))
	}

	if ! command -v jcmd >/dev/null 2>&1; then
		return
	fi

	while IFS= read -r jps_line; do
		deadline_has_expired && break
		[[ "$jps_line" =~ [Gg]radle|[Ww]orker ]] && capture_thread_dump "$jps_line"
	done < "${STAGING_DIRECTORY}/jps-associated.txt"
	while IFS= read -r jps_line; do
		deadline_has_expired && break
		capture_thread_dump "$jps_line"
	done < "${STAGING_DIRECTORY}/jps-associated.txt"
}

capture_docker_diagnostics() {
	deadline_has_expired && return 124
	if ! command -v docker >/dev/null 2>&1; then
		printf 'docker is not available.\n' > "${STAGING_DIRECTORY}/docker-ps.txt"
		return
	fi
	run_with_deadline 15 docker ps -a --no-trunc \
		> "${STAGING_DIRECTORY}/docker-ps.txt" 2>&1 || true
	# A Docker log tail can contain request bodies or credentials, so the safe
	# limit is zero lines. Container metadata above is enough to correlate tests.
	printf 'Docker logs intentionally not collected (limit: 0 lines).\n' \
		> "${STAGING_DIRECTORY}/docker-logs-policy.txt"
}

publish_diagnostics() {
	if ! run_with_deadline 10 mkdir -p "$DIAGNOSTICS_DIRECTORY"; then
		printf 'ERROR: could not create diagnostics directory: %s\n' "$DIAGNOSTICS_DIRECTORY" >&2
		printf 'ERROR: could not create diagnostics directory: %s\n' "$DIAGNOSTICS_DIRECTORY" \
			>> "${STAGING_DIRECTORY}/publish-failed.txt"
		return 1
	fi
	if ! run_with_deadline 30 cp -Rf "${STAGING_DIRECTORY}/." "$DIAGNOSTICS_DIRECTORY/"; then
		printf 'ERROR: could not publish watchdog diagnostics to: %s\n' "$DIAGNOSTICS_DIRECTORY" >&2
		printf 'ERROR: could not publish watchdog diagnostics to: %s\n' "$DIAGNOSTICS_DIRECTORY" \
			>> "${STAGING_DIRECTORY}/publish-failed.txt"
		return 1
	fi
}

terminate_associated_processes() {
	signal_associated_processes TERM
	local grace_deadline=$((SECONDS + GRACE_SECONDS))
	local configured_deadline
	for configured_deadline in "$OPERATION_DEADLINE" "$CLEANUP_DEADLINE"; do
		((configured_deadline > 0 && configured_deadline < grace_deadline)) && \
			grace_deadline=$configured_deadline
	done
	while associated_processes_are_running; do
		if ((SECONDS >= grace_deadline)); then
			signal_associated_processes KILL
			local force_deadline=$((SECONDS + 5))
			for configured_deadline in "$OPERATION_DEADLINE" "$CLEANUP_DEADLINE"; do
				((configured_deadline > 0 && configured_deadline < force_deadline)) && \
					force_deadline=$configured_deadline
			done
			while associated_processes_are_running && ((SECONDS < force_deadline)); do
				sleep 1
			done
			break
		fi
		sleep 1
	done
	reap_supervised_command_if_finished 2>/dev/null || true
}

watchdog_expired() {
	CLEANUP_DEADLINE=$((SECONDS + CLEANUP_TIMEOUT_SECONDS))
	OPERATION_DEADLINE=0
	STAGING_DIRECTORY="$(run_with_deadline 10 mktemp -d "${TMPDIR:-/tmp}/devchester-watchdog.XXXXXX" 2>/dev/null || true)"
	if [[ -z "$STAGING_DIRECTORY" ]]; then
		printf 'Watchdog expired, but no diagnostic staging directory could be created.\n' >&2
		OPERATION_DEADLINE=$((CLEANUP_DEADLINE - CLEANUP_PUBLICATION_RESERVE_SECONDS))
		terminate_associated_processes
		return
	fi

	{
		printf 'WATCHDOG_FIRED_AT=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
		printf 'TIMEOUT_SECONDS=%s\n' "$TIMEOUT_SECONDS"
		printf 'GRACE_SECONDS=%s\n' "$GRACE_SECONDS"
		printf 'CLEANUP_TIMEOUT_SECONDS=%s\n' "$CLEANUP_TIMEOUT_SECONDS"
		printf 'SUPERVISED_PID=%s\n' "$SUPERVISED_PID"
		printf 'SUPERVISED_PROCESS_GROUP=%s\n' "${SUPERVISED_PROCESS_GROUP:-not-isolated}"
	} > "${STAGING_DIRECTORY}/watchdog-fired.txt"

	local termination_reserve_seconds=$((GRACE_SECONDS + 10))
	local capture_deadline=$((CLEANUP_DEADLINE - termination_reserve_seconds - CLEANUP_PUBLICATION_RESERVE_SECONDS))
	if ((capture_deadline > SECONDS)); then
		OPERATION_DEADLINE=$capture_deadline
		capture_process_snapshot || true
		capture_java_diagnostics || true
		capture_docker_diagnostics || true
	fi

	OPERATION_DEADLINE=$((CLEANUP_DEADLINE - CLEANUP_PUBLICATION_RESERVE_SECONDS))
	terminate_associated_processes || \
		printf 'ERROR: watchdog termination routine reported a failure.\n' >&2
	OPERATION_DEADLINE=$CLEANUP_DEADLINE
	if ! publish_diagnostics; then
		printf 'ERROR: watchdog diagnostics remain staged at: %s\n' "$STAGING_DIRECTORY" >&2
	fi
	OPERATION_DEADLINE=0
	CLEANUP_DEADLINE=0
}

if command -v setsid >/dev/null 2>&1; then
	setsid -- "$@" &
	SUPERVISED_PID=$!
	SUPERVISED_PROCESS_GROUP=$SUPERVISED_PID
	USES_ISOLATED_PROCESS_GROUP=true
else
	"$@" &
	SUPERVISED_PID=$!
fi

started_at=$SECONDS
while supervised_command_is_running; do
	if ((SECONDS - started_at >= TIMEOUT_SECONDS)); then
		watchdog_expired
		exit 124
	fi
	sleep 1
done

reap_supervised_command_if_finished
exit $?
