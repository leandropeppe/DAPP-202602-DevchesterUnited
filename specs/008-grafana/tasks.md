# Tasks: Prometheus y Grafana OSS

## Phase 1: Setup

- [x] T001 Crear SDD en `specs/008-grafana/`.

## Phase 2: Foundational

- [x] T002 Crear overlay en `backend/docker/grafana-compose.yml`.
- [x] T003 [P] Crear scrape en `backend/docker/prometheus/prometheus.yml`.
- [x] T004 [P] Crear provisioning y dashboard en `backend/docker/grafana/`.

## Phase 3: User Story 1

- [x] T005 [US1] Verificar scrape, datasource y dashboard en ejecución.

## Phase 4: User Story 2

- [x] T006 [US2] Validar estáticamente redes, puertos y configuración.
- [x] T007 [US2] Ejecutar aislamiento dinámico completo en G03.

## Phase 5: Polish

- [x] T008 Validar JSON, estructura YAML, Compose estático, puerto y ausencia de secretos.
- [x] T009 Ejecutar persistencia y regresión completas en G03.

## Phase 6: Corrección de acceso local G02-B

- [x] T010 Añadir `grafana-access` exclusiva de Grafana en `backend/docker/grafana-compose.yml`.
- [x] T011 Recrear Grafana y verificar loopback, autenticación, aislamiento y provisioning.
