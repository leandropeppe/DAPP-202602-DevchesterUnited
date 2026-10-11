# Research: Token Market

## Repository evidence

- The repository uses a Java 21 Spring Boot backend with Gradle, PostgreSQL, Flyway, Spring Data JPA, JWT resource-server security, validation, and springdoc OpenAPI.
- The backend follows the verified package convention under `com.devchesterunited.devchesterunitedv2`: `adapter`, `configuration`, `controller`, `modelo`, `persistence`, and `service`, with the subpackages documented in `plan.md`. The frontend is React/Vite with `api`, `components`, `session`, `styles`, and `views`.
- Existing authentication and player catalog responsibilities remain outside Token Market. Token Market references stable player identity and active state; it does not alter player statistics or scrape WhoScored.
- The current branch has Flyway history through V14. The locally available remote-tracking statistics branch contains a V15 migration, but it is not integrated into this branch. V16 is therefore a provisional candidate only.
- The approved feature specification is the functional source of truth. This document records technical decisions needed to plan implementation without changing it.

## Decision 1: Use existing application conventions

Use Spring Boot application services, repositories, DTOs, controllers, PostgreSQL transactions, Flyway, JWT principal resolution, and existing error/response conventions. Avoid a new module or framework because the repository already has a consistent layered application structure.

## Decision 2: Protected internal superuser

Represent the market counterparty as exactly one protected internal identity, separate from publicly authenticatable `Usuario` accounts. It has a credit reserve and the initial player-token inventory, but no password, JWT login, public registration path, or client-selectable user identifier.

Alternatives rejected:

- A normal user with a special role would couple the reserve to public registration/login and risk exposing it through ordinary user flows.
- A hard-coded account identifier would not provide persistent uniqueness or a durable balance/inventory owner.
- An implicit balance without an identity would make counterparty references and transaction audit less explicit.

## Decision 3: Deterministic, resource-scoped locking

All buy and sell executions acquire lockable resources in this order:

1. The idempotency request row for `(authenticated user, idempotency key)`.
2. The operational market-state row in a shared-lock read for a normal trade, or an exclusive lock for initialization/state transition.
3. The selected catalog player's row in a shared-lock read to coordinate active/inactive status.
4. The selected player's current quote/head row.
5. The authenticated user's credit-account row.
6. The protected superuser credit-account row.
7. The selected player's issuance/inventory row.
8. The authenticated user's holding row for that player.

Both buy and sell use the same order, including when a holding row is absent. A missing holding is represented by an initialization-safe path that creates or locks a canonical per-user/player row under a uniqueness constraint, then retries the ordered operation; it never relies on an unprotected application-only check. A transaction that must create the row takes the uniqueness conflict into account and retries after a concurrent insert.

There is no exclusive global market lock for normal operations. A normal operation takes a PostgreSQL shared row lock on the singleton market state and keeps it until its short transaction commits; shared locks are compatible with other trades but block an exclusive state transition. Initialization or a transition takes the exclusive state-row lock, waits for in-flight trades, changes state/epoch, and prevents new shared-lock readers until the transition commits. Thus a trade never commits against a state that was changed concurrently, without serializing ordinary trades with one another.

The catalog player owner changes active/inactive status under an exclusive lock on that player's row. A trade takes a shared lock on the same row before validating active status and holds it through commit. If deactivation wins first, the trade observes inactive and rejects; if the trade wins first, deactivation waits and applies afterward. The market does not update catalog state. Quote publication/invalidation locks the same player's quote-head resource and updates the immutable quote version/head atomically. A market operation validates the exact quote version after acquiring that lock and never substitutes a newer version. The player-row and quote-head positions are fixed in the order above to prevent cycles.

## Decision 4: Persistent idempotency and recovery

The idempotency key is unique per authenticated user. The stored request includes a normalized parameter hash and a status such as `PROCESSING`, `CONFIRMED`, `REJECTED`, or `CONFLICT`.

The flow is:

1. A short claim transaction inserts the request as `PROCESSING` with a unique execution token. An identical `CONFIRMED` or `REJECTED` request returns its stored result. A different hash for the same key returns a conflict. The claim is durable so a crashed process leaves a recoverable request.
2. The executor immediately starts the business transaction and obtains a PostgreSQL transaction-scoped advisory lock derived from the persisted request identity, then locks the request row. A concurrent executor uses a non-blocking advisory-lock attempt: if the first executor still owns the transaction, it returns `PROCESSING` and cannot execute. If the first process crashed, PostgreSQL releases the transaction-scoped lock; the retry acquires it, locks the request row, and takes over the still-`PROCESSING` request. The owner token is conditionally checked/updated while holding the row lock.
3. The single executor runs the ordered business transaction. The accepted quote, balance/token movements, holdings/cost update, immutable confirmed transaction, and final idempotency result are committed together. No executor can perform movements without holding the request's transaction-scoped lock.
4. A functional validation failure is committed as `REJECTED` with a non-sensitive reason and no movement. It is not a confirmed trade.
5. A technical rollback leaves no movements or confirmed transaction. The durable request remains `PROCESSING`; after the transaction-scoped advisory lock is released, a retry can safely take it over. A timeout while the first database transaction is still active cannot be taken over because the advisory lock is held; the retry reports pending or waits according to the API policy. After connection/process failure, the lock is released and the next retry recovers the result or re-executes atomically.
6. A lost response is recovered by repeating the same key. A response is not declared successful until the commit has completed. The advisory lock is transaction-scoped and therefore cannot survive a crashed database session; there is no lease-expiry window in which two active executors can both move balances or tokens.
 
Alternatives rejected:

- In-memory idempotency cannot survive restart or coordinate multiple instances.
- Marking a request confirmed before committing movements can expose a false success.
- Holding one long database transaction across the client request would increase lock duration and deadlock risk; the database transaction is limited to the claim executor and does not wait on the client.

## Decision 5: Transactional market-state barrier

The singleton market-state row is a shared-lock barrier, not a global exclusive lock for every trade. Every normal trade reads it with `FOR SHARE` and verifies `READY` plus the current state epoch. Initialization and explicit state changes use `FOR UPDATE`, transition through `INITIALIZING`/`FAILED`/`READY`, and commit the epoch change only after their idempotent work completes. Because shared readers and the exclusive transition lock overlap for the full short transaction, no trade can commit while a state transition changes the operational state. A failed initialization leaves the market non-operational while unrelated application features continue.

## Decision 6: One quote contract and source of truth

The initial 1.00 credit quote is published through the same versioned quote store/contract consumed by Token Market and later published by Player Valuation. It is an immutable quote version with a valid state and no automatic seven-day expiry. A later Player Valuation quote replaces the current head by publication; it does not migrate data from a parallel initial-price table.

Player Valuation owns calculation, strategy selection, publication semantics, validity policy for dynamic quotes, and recalc-failure records. Token Market consumes and validates exact quote versions, stores the quote reference in confirmed history, and never calculates or substitutes prices. The concrete initial publisher is the internal `MarketInitializationService`, invoked by an automatic application-startup coordinator; it calls the shared `QuotePublicationPort.publishInitialQuote(playerId, 1.00, nonExpiring)` for each eligible player. The port is implemented by the single quote persistence adapter and is also the boundary used later by Player Valuation for dynamic publication. This is seeding, not valuation: the initializer contains no statistics, scraping, or strategy logic.

## Decision 7: Automatic initialization and recovery

An internal startup coordinator listens for application readiness and invokes the initialization service; there is no public endpoint or manual user action. The service locks the singleton market-state row, transitions to `INITIALIZING`, and performs idempotent steps: create/verify the one protected superuser and its configured initial reserve; grant exactly 1,000 credits to pre-existing users using a unique grant marker; ensure the one-time 100-token issuance and superuser inventory for currently active players; invoke the shared initial quote publication for eligible players. A player initially inactive receives its issuance and initial quote when it first becomes eligible; reactivation finds the existing issuance and never emits again.

New registration uses an internal post-registration credit-grant hook with a unique `(user, initial-grant)` marker and retry. The user record can be committed independently, while market operations remain unavailable to that user until the grant is present. This preserves the requirement that registration receive exactly 1,000 credits without making unrelated authentication unavailable during a market initialization failure. Repeated startup, retries, and concurrent activation events use unique constraints and row locks, so each grant, issuance, and initial quote version is created once.

If initialization fails, the state is `FAILED` and trading rejects. A later startup/retry reacquires the state lock, resumes only missing idempotent steps, and returns to `READY` only after reserve, grants, issuance, and required initial quotes are complete. The initializer never resets existing balances, reissues tokens, or replaces an already-valid quote. Dynamic Player Valuation publication waits for the same quote-head coordination and can replace the initial quote only through the shared publication boundary.

## Decision 8: Accounting representation

Credit balances and prices use two-decimal external semantics. Holding acquisition cost uses sufficient internal precision for weighted-average calculations. On a partial sale, the cost removed is proportional to the sold quantity; on the final sale, quantity and residual cost are both zero. Realized gain/loss is retained in confirmed history and an account-level aggregate so it remains visible after a position closes.

## Decision 9: Flyway coordination

Do not assume V15 is available on this branch. Before implementation, compare the final statistics migration with this branch and coordinate an ordered merge/integration decision. V16 is a provisional candidate for the first Token Market migration, not a reserved version. No SQL or migration is generated by this Plan.

## Decision 10: Verification strategy

Use unit tests for domain calculations and state transitions, MockMvc for authenticated API behavior, PostgreSQL/Testcontainers for constraints/locking/rollback/concurrency, and the existing frontend test conventions for UI states. The four-user/five-player scenario is controlled demonstration data. Integration with real statistics and dynamic Player Valuation is a later cross-feature verification, not part of Token Market's calculation logic.

## Coordination still required

- Confirm the final V15 contents and the migration ordering before selecting a real migration number.
- Agree with Player Valuation on the shared quote publication boundary, identifiers, validity-state vocabulary, and adapter placement; the initial trigger is fixed here as the automatic `MarketInitializationService` coordinator.
- Confirm the stable player identifier and active-state contract exposed by the catalog/statistics work without changing that feature here.
