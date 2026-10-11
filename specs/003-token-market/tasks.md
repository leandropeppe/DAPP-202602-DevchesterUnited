---

description: "Task list for Token Market implementation"
---

# Tasks: Token Market

**Input**: Design documents from `specs/003-token-market/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `quickstart.md`, `contracts/market-api.md`, and `contracts/quote-contract.md`.

**Scope**: Implement only Token Market. Do not modify `specs/001-user-auth-player-catalog`, `specs/002-player-statistics-sync`, or files belonging to the remote statistics branch. Player Statistics Sync and Player Valuation are integration dependencies, not implementation targets of this feature.

**Path convention**: Existing backend package root is `backend/src/main/java/com/devchesterunited/devchesterunitedv2/` with `configuration`, `modelo`, `persistence`, `service`, `controller`, and `adapter`. Existing frontend directories are `frontend/src/api`, `components`, `session`, `styles`, and `views`. Application configuration is `backend/src/main/resources/application.properties`.

**Tests**: Tests are included because Specify and the approved Plan require unit, MockMvc, PostgreSQL/Testcontainers, frontend, concurrency, idempotency, and demonstration verification.

## Phase 1: Setup

**Purpose**: Establish the Token Market implementation surface without changing approved features or configuration conventions.

- [ ] T001 Verify that the active branch is `feature/token-market` and read/confirm the approved inputs in `specs/003-token-market/spec.md`, `plan.md`, `research.md`, `data-model.md`, `quickstart.md`, and `contracts/`; do not write, register, or modify any approved document.
- [ ] T002 [P] Inventory existing authentication, player, repository, service, controller, frontend API, and test conventions in `backend/src/main/java/com/devchesterunited/devchesterunitedv2/` and `frontend/src/` without modifying their feature ownership.
- [ ] T003 [P] Define the Token Market package/file map under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/`, `persistence/`, `service/`, `controller/`, `adapter/`, and `configuration/`, preserving the verified repository conventions.
- [ ] T004 [P] Define the frontend Token Market file map under `frontend/src/api/`, `frontend/src/views/`, and `frontend/src/components/`, reusing existing session and HTTP conventions.

## Phase 2: Foundational Persistence and Contracts

**Purpose**: Create the database and domain foundations required by every market story.

**Migration coordination gate**: V16 is provisional. Do not create the migration until the final V15 from Player Statistics Sync has been reconciled with this branch. Do not modify the statistics branch.

- [ ] T005 Confirm the definitive integration/order of Player Statistics Sync V15 against the current sequence under `backend/src/main/resources/db/migration/`; if V15 is absent, conflicting, or unresolved, stop as an explicit blocker and request coordination approval without fetch, checkout, merge, rebase, or modification of that feature.
- [ ] T006 Create the provisional Token Market migration at `backend/src/main/resources/db/migration/V16__create_token_market.sql` only after T005 confirms that V15 is definitively integrated and V16 is the next available version; if that confirmation is unavailable, do not create V16, and do not merge, checkout, rebase, or modify the statistics feature.
- [ ] T007 [P] Add persistence entities for market state, internal superuser/counterparty, credit accounts, initial grants, token issuance/inventory, and user holdings under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/dto/`.
- [ ] T008 [P] Add persistence entities for quote versions/heads, idempotency requests, confirmed transactions, credit/token movements, and rejection audit under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/dto/`.
- [ ] T009 [P] Add repositories with uniqueness, non-negative, one-time, and player/user ownership queries under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/`.
- [ ] T010 Add persistence mappers/DTOs for Token Market records under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/mapper/` and `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/dto/`, without duplicating `Jugador` or statistics persistence.
- [ ] T011 Add domain value validation and market exceptions under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/exception/` and `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/exception/` for invalid quantities, inactive players, unavailable quotes, insufficient balances, market-not-ready, idempotency conflict, and stale requests.
- [ ] T012 [P] Add unit tests for database/domain invariants and monetary/token validation under `backend/src/test/java/com/devchesterunited/devchesterunitedv2/modelo/` and `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/`.
- [ ] T013 Configure only the existing PostgreSQL/Flyway test conventions needed by Token Market in `backend/src/test/resources/` and verify that the application continues to use `backend/src/main/resources/application.properties`; do not introduce YAML configuration.
- [ ] T014 Add the shared internal quote publication boundary and DTOs under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/interfaces/`, `service/dto/`, and `adapter/`, including immutable quote identity/version, validity, publication time, price, player identity, and optional strategy reference.
- [ ] T015 Add contract-level tests for the quote publication/consumption boundary under `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/`, proving that initial and future quote versions use one source of truth and that quote versions are immutable.

**Checkpoint**: Persistence constraints, shared quote boundary, and test infrastructure are ready; no user story may bypass these invariants.

## Phase 3: User Story 1 - Inicializar el mercado de forma segura (P1)

**Goal**: Automatically initialize the protected superuser, finite reserve, one-time user credits, eligible-player emissions, and initial quotes without public intervention.

**Independent test**: Repeated startup and activation scenarios produce one superuser, one 1,000-credit grant per user, exactly 100 issued tokens per eligible player, and no trading while initialization is incomplete.

### Tests for User Story 1

- [ ] T016 [P] [US1] Add unit tests for initialization state transitions, configured reserve, one-time grants, one-time emissions, inactive-player activation, and retry recovery in `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/`.
- [ ] T017 [P] [US1] Add PostgreSQL/Testcontainers tests for singleton superuser, grant uniqueness, issuance uniqueness, exactly 100 tokens, non-negative balances, and restart idempotency in `backend/src/test/java/com/devchesterunited/devchesterunitedv2/persistence/`.

### Implementation for User Story 1

- [ ] T018 Create initialization/state persistence repositories and lock queries in `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/` using the singleton market-state row and deterministic transaction semantics.
- [ ] T019 Implement `MarketInitializationService` under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/` to create/verify the protected superuser, apply the configured 1,000,000-credit default before first initialization, and never reset an initialized reserve.
- [ ] T020 Implement the automatic application-readiness coordinator under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/` or `service/config/`, with retryable `INITIALIZING`, `FAILED`, and `READY` transitions and no public endpoint.
- [ ] T021 Implement the idempotent initial-credit grant for existing users and the post-registration grant hook under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/`, using the unique grant marker and preventing authentication from depending on successful market startup.
- [ ] T022 Implement one-time issuance/allocation for active players and first activation under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/`, referencing the existing stable player identity and active-state contract without modifying Player Statistics Sync.
- [ ] T023 Implement initial quote seeding through the real shared publication adapter exposed by `QuotePublicationPort.publishInitialQuote` at 1.00 credit with non-expiring validity under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/` and `service/interfaces/`, only after T014–T015 define/verify the contract and T039–T040 implement/verify its persistence and publication service; do not create a parallel price table.
- [ ] T024 Implement initialization recovery so a failed or interrupted run resumes missing grants, emissions, and initial quotes, leaves trading disabled until complete, and preserves existing successful steps under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/`.
- [ ] T025 Add initialization and activation integration tests covering four users, active/inactive players, restart, failure recovery, and independent operation of authentication in `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/` and `persistence/`.

**Checkpoint**: Market initialization is automatic, idempotent, recoverable, and cannot duplicate identities, credits, quotes, or token emissions.

## Phase 4: User Story 2 - Consultar el estado operativo del mercado (P1)

**Goal**: Expose market readiness and operable-player information without exposing internal operations or another user's data.

**Independent test**: A user can see only active players with current quote/token availability; incomplete initialization and missing/stale quotes are represented correctly.

- [ ] T026 [P] [US2] Add unit tests for shared/exclusive market-state barriers, state epochs, inactive-player handling, missing quotes, and historical-price labeling in `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/`.
- [ ] T027 [P] [US2] Add MockMvc security tests for unauthenticated access, authenticated market listing, and exclusion of internal superuser operations in `backend/src/test/java/com/devchesterunited/devchesterunitedv2/controller/`.
- [ ] T028 Implement market-state and player-availability services under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/` using shared state locks for normal reads and the stable player active-state contract.
- [ ] T029 Implement balance and market-player DTOs under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/` and service DTOs under `service/dto/`, reusing existing pagination and response conventions.
- [ ] T030 Implement the balance and market-player read endpoints in `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/`, including current quote, historical marker, tokens available, and market readiness semantics from `contracts/market-api.md`.
- [ ] T031 Add OpenAPI/error-handler documentation for market-not-ready, missing quote, inactive player, and authorization errors through `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/exceptionhandler/` and the market controller.

**Checkpoint**: Read-only market information is authenticated, scoped, and consistent with initialization and player state.

## Phase 5: User Story 7 - Recuperar solicitudes idempotentes y concurrentes (P1)

**Goal**: Ensure repeated, concurrent, timed-out, and recovered operations cannot duplicate transfers.

**Independent test**: Two backend instances using the same key yield one result; rollback leaves no movements; a later retry recovers or safely re-executes a durable `PROCESSING` request.

- [ ] T032 [P] [US7] Add unit tests for same-key identical requests, conflicting parameters, `PROCESSING`, `CONFIRMED`, `REJECTED`, rollback, and lost-response state handling in `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/`.
- [ ] T033 Add PostgreSQL/Testcontainers concurrency tests for transaction-scoped advisory locks, request-row ownership, process failure simulation, timeout/pending recovery, and absence of duplicate movements in `backend/src/test/java/com/devchesterunited/devchesterunitedv2/persistence/`.
- [ ] T034 Implement idempotency claim, normalized-parameter hash, execution token, status transitions, stored result, and recovery queries under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/` and `service/impl/`.
- [ ] T035 Implement the PostgreSQL transaction-scoped advisory-lock executor and row-lock coordination in `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/`, ensuring a second executor returns pending while the first transaction is active.
- [ ] T036 Implement operation-status/recovery DTOs and endpoint in `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/` and `controller/`, returning stored results only after commit and never presenting pending/uncertain requests as confirmed trades.

**Checkpoint**: Idempotency and recovery are safe across concurrent backend instances and technical failures.

## Phase 6: User Story 8 - Operar con cotizaciones publicadas (P1)

**Goal**: Consume one immutable/versioned quote source and coordinate exact-version validation with future Player Valuation.

**Independent test**: Initial quotes operate before Player Valuation exists; replacements, invalidations, expiry, and concurrent publication reject stale accepted versions without substituting prices.

- [ ] T037 [P] [US8] Add unit tests for immutable quote versions, current-head replacement, explicit invalidation, non-expiring initial quote, dynamic validity policy, and exact-version acceptance in `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/`.
- [ ] T038 Add PostgreSQL/Testcontainers tests for concurrent quote publication/invalidation versus market validation and the single-source-of-truth constraint in `backend/src/test/java/com/devchesterunited/devchesterunitedv2/persistence/`.
- [ ] T039 Implement quote version/head persistence and repositories under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/`, distinguishing immutable versions from the per-player current quote reference/state.
- [ ] T040 Implement the internal quote publication/consumption services under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/` and `service/interfaces/`, with initial seeding and future dynamic publication sharing the same source.
- [ ] T041 Implement exact quote validation in the market operation service under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/`, rejecting replaced, invalidated, expired, or absent versions without automatic substitution.
- [ ] T042 Add the Player Valuation integration seam and contract tests under `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/`, documenting that valuation owns strategy/calculation/history while Token Market only consumes and records references.

**Checkpoint**: Token Market has one quote source, no valuation algorithm, and exact-version execution semantics.

## Phase 7: User Story 3 - Comprar tokens de un jugador activo (P1)

**Goal**: Execute an all-or-nothing authenticated purchase against the superuser.

**Independent test**: A valid purchase transfers credits/tokens, updates weighted acquisition cost, and records one confirmed transaction; every validation failure performs no partial mutation.

- [ ] T043 [P] [US3] Add service tests for purchase validation, integer quantity, active player, inventory, user balance, exact quote, and all-or-nothing behavior in `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/`.
- [ ] T044 Add PostgreSQL/Testcontainers tests for concurrent purchases, superuser inventory exhaustion, user double-spend prevention, deterministic lock order, and atomic movement/history commit in `backend/src/test/java/com/devchesterunited/devchesterunitedv2/persistence/`.
- [ ] T045 Implement credit/token movement records and atomic account/holding update services under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/` and repositories under `persistence/`.
- [ ] T046 Implement purchase orchestration with lock order idempotency → market state → player state → quote head → user balance → superuser balance → inventory → holding under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/`.
- [ ] T047 Add purchase request/response DTOs with `Idempotency-Key`, exact quote identity/version, positive integer quantity, and validation under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/`.
- [ ] T048 Add the authenticated purchase endpoint and error mapping under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/`, deriving the user only from the validated JWT.
- [ ] T049 Add MockMvc tests for successful purchase, missing/stale quote, inactive player, insufficient credits/inventory, invalid key, and cross-user account-selection attempts under `backend/src/test/java/com/devchesterunited/devchesterunitedv2/controller/`.

**Checkpoint**: Purchase is immediate, atomic, quote-versioned, idempotent, and limited to the superuser counterparty.

## Phase 8: User Story 4 - Vender tokens de un jugador activo (P1)

**Goal**: Execute an all-or-nothing authenticated sale against the finite superuser reserve.

**Independent test**: A valid sale transfers tokens to the superuser and credits to the user, updates remaining weighted cost and realized P/L, and never partially executes.

- [ ] T050 [P] [US4] Add service tests for sale validation, user holdings, reserve sufficiency, partial sale cost allocation, final-position closure, exact quote, and all-or-nothing behavior in `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/`.
- [ ] T051 Add PostgreSQL/Testcontainers tests for concurrent sales competing for one holding and the finite superuser reserve, proving no negative balance, oversell, or partial movement in `backend/src/test/java/com/devchesterunited/devchesterunitedv2/persistence/`.
- [ ] T052 Implement sale orchestration using the same deterministic lock order and idempotency executor as purchase under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/`.
- [ ] T053 Implement weighted-average cost reduction, realized gain/loss, final-position zeroing, and movement/history persistence under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/`.
- [ ] T054 Add sale request/response DTOs and authenticated sale endpoint under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/` and `controller/`, reusing the exact quote and JWT ownership rules.
- [ ] T055 Add MockMvc tests for partial sale, final sale, insufficient holding/reserve, inactive player, stale quote, idempotent retry, and cross-user access rejection under `backend/src/test/java/com/devchesterunited/devchesterunitedv2/controller/`.

**Checkpoint**: Sale is atomic, finite-reserve constrained, weighted-cost aware, and auditable.

## Phase 9: User Story 5 - Consultar el portfolio y la rentabilidad (P2)

**Goal**: Show only open positions of the authenticated user with complete realized/unrealized accounting semantics.

**Independent test**: Portfolio shows weighted average and current value for open positions, preserves realized P/L after closure, and marks missing current quotes without presenting incomplete totals as current.

- [ ] T056 [P] [US5] Add unit tests for weighted-average portfolio values, realized P/L after closed positions, unrealized P/L, historical quote display, missing quote handling, and combined-result completeness in `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/`.
- [ ] T057 Implement portfolio aggregation and valuation services under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/`, using current valid quotes only for current value and retaining realized aggregates independently of open holdings.
- [ ] T058 Add portfolio DTOs under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/` and service DTOs under `service/dto/`, including open positions, costs, quote status, realized/unrealized P/L, and completeness indicators.
- [ ] T059 Add the authenticated portfolio endpoint under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/` and MockMvc isolation tests under `backend/src/test/java/com/devchesterunited/devchesterunitedv2/controller/`.

**Checkpoint**: Portfolio is personal, accurate after partial/full sales, and explicit about historical or unavailable prices.

## Phase 10: User Story 6 - Consultar transacciones confirmadas y auditoría (P2)

**Goal**: Provide immutable personal confirmed history and a separate restricted rejection audit.

**Independent test**: Users see only their own confirmed trades newest-first with filters/pagination; rejected attempts and pending requests never appear as confirmed transactions.

- [ ] T060 [P] [US6] Add repository/service tests for immutable transaction snapshots, descending order, player/type/date filters, pagination, rejection separation, and user isolation in `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/` and `persistence/`.
- [ ] T061 Implement immutable confirmed transaction persistence and query services under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/` and `service/impl/`, including player-name snapshot, quote version, counterparty, idempotency reference, and realized P/L.
- [ ] T062 Implement separate internal rejection-audit persistence and restricted service under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/` and `service/impl/`, excluding credentials, JWTs, and sensitive payloads.
- [ ] T063 Add transaction-history DTOs, filter/pagination validation, and authenticated personal-history endpoint under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/` and `controller/`.
- [ ] T064 Add MockMvc tests for confirmed-only history, filters, pagination, newest-first ordering, rejection exclusion, pending exclusion, and cross-user access denial under `backend/src/test/java/com/devchesterunited/devchesterunitedv2/controller/`.

**Checkpoint**: Confirmed history is immutable and personal; rejection audit is separate and internal.

## Phase 11: User Story 9 - Utilizar la interfaz mínima del mercado (P2)

**Goal**: Provide the minimum market, balance, trading, portfolio, history, and operation-recovery experience without moving business logic into React.

**Independent test**: An authenticated user can complete the market flow and distinguish loading, rejection, missing/historical quote, pending, and uncertain states.

- [ ] T065 [P] [US9] Add typed market API client functions under `frontend/src/api/` for balance, players/quotes, purchase, sale, operation recovery, portfolio, and transaction history.
- [ ] T066 [P] [US9] Add reusable loading, error, stale-quote, pending, and uncertain-operation components under `frontend/src/components/`.
- [ ] T067 [P] [US9] Add market/player listing and balance view under `frontend/src/views/`, reusing `frontend/src/session/` authentication state and existing styles.
- [ ] T068 [P] [US9] Add buy/sell confirmation views under `frontend/src/views/` and `frontend/src/components/`, displaying accepted quote version, quantity, total, and rejection state.
- [ ] T069 [P] [US9] Add portfolio and history views with filters/pagination under `frontend/src/views/` and `frontend/src/components/`, showing historical prices and realized results after position closure.
- [ ] T070 [US9] Add frontend unit tests for API/client mapping and all market states in `frontend/tests/market-api.test.js` and `frontend/tests/market-views.test.jsx`, using the existing Vitest configuration in `frontend/vite.config.js`; Vitest is already declared in `frontend/package.json`, so do not install or modify dependencies.
- [ ] T071 [US9] Add Playwright flow coverage in `frontend/e2e/token-market.spec.js` for balance, market listing, purchase, sale, portfolio, history, pending recovery, and user isolation, using the existing `frontend/playwright.config.js` `testDir: './e2e'`; Playwright is already declared in `frontend/package.json`, so do not install or modify dependencies.

**Checkpoint**: The minimum UI is functional and delegates all ownership, pricing, and validation decisions to the backend.

## Phase 12: User Story 10 - Verificar el escenario reproducible de demostración (P3)

**Goal**: Reproduce the four-user/five-player controlled demonstration and separately verify future real integrations.

**Independent test**: Controlled fixtures produce comparable portfolios, later quote changes, sales, realized/unrealized results, and personal histories without mixing with real data.

- [ ] T072 [P] [US10] Define isolated test-only fixture builders for four users and five players under `backend/src/test/java/com/devchesterunited/devchesterunitedv2/` and/or `backend/src/test/resources/`, clearly marking data as demonstration-only.
- [ ] T073 [US10] Add the reproducible controlled demonstration test for initial quotes, purchases, later different quote versions, portfolio comparison, sales, realized/unrealized results, closed positions, and histories under `backend/src/test/java/com/devchesterunited/devchesterunitedv2/`.
- [ ] T074 [P] [US10] Add frontend demonstration data/loading coverage under `frontend/tests/market-demo.test.jsx` without inserting fixture data into production APIs or real catalog data.
- [ ] T075 [P] [US10] Add a separate, later integration-verification scenario under `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/` that runs only when both real Player Statistics Sync and Player Valuation are available, consumes their outputs, and never implements scraping, statistics persistence, or price calculation in Token Market; this task must not block T073 or independent Token Market validation.

**Checkpoint**: Controlled demonstration and real cross-feature verification are explicitly separated.

## Phase 13: Polish and Cross-Cutting Verification

**Purpose**: Validate all acceptance criteria and integration boundaries before implementation handoff.

- [ ] T076 [P] Run the complete backend unit, MockMvc, PostgreSQL/Testcontainers, and frontend test suites through the repository's existing Gradle/npm commands; do not execute database migrations against non-test databases.
- [ ] T077 [P] Verify the 52 functional requirements and 11 success criteria from `specs/003-token-market/spec.md` against implementation tests and record any unmet criterion in the implementation issue tracker.
- [ ] T078 [P] Verify OpenAPI/security behavior and confirm all private queries derive identity from JWT rather than client-supplied user identifiers under `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/`, `controller/`, and frontend API usage.
- [ ] T079 Verify that no Token Market file implements WhoScored scraping, statistics synchronization, valuation strategies, rankings, or a second price source; review `backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/` and quote persistence boundaries.
- [ ] T080 Verify Flyway ordering under `backend/src/main/resources/db/migration/` and V16 coordination after Player Statistics Sync integration is explicitly resolved; update only the Token Market migration if V16 is confirmed, never modify the statistics branch.
- [ ] T081 Run the `quickstart.md` validation sequence using isolated PostgreSQL/Testcontainers and record atomicity, concurrency, idempotency, issuance, portfolio, history, and demonstration evidence.

## Dependencies and Execution Order

### Phase dependencies

- Phase 1 is independent and must precede implementation.
- Phase 2 blocks every user story. T005 is an external coordination gate before T006; T006 must not be implemented with an assumed V16 conflict.
- US1 depends on Phase 2 and establishes market initialization, grants, issuance, and initial quotes. Its quote-seeding task T023 is additionally gated by the shared quote chain T014 → T015 → T039 → T040; the other initialization work can proceed independently.
- US2 depends on US1 state semantics but can develop its read DTOs/tests in parallel after the foundational model exists.
- US7 depends on Phase 2. US8 must execute its contract tasks T014–T015 before persistence/publication tasks T039–T040. T023 then consumes the verified real publication adapter; US8's dynamic publication work can continue independently afterward.
- US3 depends on US1, US2, US7, and US8.
- US4 depends on US3's movement/accounting infrastructure and US7/US8.
- US5 depends on US3 and US4 transaction/holding accounting.
- US6 depends on US3/US4 confirmed transaction and rejection records.
- US9 depends on the backend contracts for US2–US7 and can begin API-client work in parallel once DTO shapes are stable.
- US10 depends on US1–US8 for the controlled demonstration; its real integration verification additionally depends on Player Statistics Sync and Player Valuation availability.
- Phase 13 depends on all selected stories and on the V15/V16 coordination decision.

### Critical path

`T005 → T006 → T007–T015 → T039–T040 → T018–T024 (US1 quote seeding) → T034–T042 (US7/US8) → T045–T055 (US3/US4) → T057–T064 (US5/US6) → T073 → T076–T081`.

The external gates on the critical path are final V15 coordination, the stable player active-state contract, and the shared quote publication boundary with Player Valuation.

### Parallel execution opportunities

- T002–T004 can run in parallel during Setup.
- T007–T009, T012, and the preparation of T014 can run in parallel after the migration coordination shape is known, provided they do not edit the same file. T015 follows T014.
- T039–T040 can proceed after T014–T015 and must complete before T023; T023 is not parallelizable with that quote chain.
- US1 unit/database tests and initialization service design can be split by file after the foundational model exists.
- US2 read contracts, US7 idempotency, and US8 quote-version work can proceed in parallel after Phase 2; integration checkpoints remain ordered.
- Purchase and sale tests can be prepared in parallel, while shared movement/accounting implementation is coordinated before merging.
- Portfolio, history, frontend, and controlled-fixture work can proceed in parallel after the corresponding backend contracts stabilize.
- T073/T074 and all independent Token Market validation can complete without T075. Cross-feature integration T075 is parallelizable only after both Player Statistics Sync and Player Valuation are actually available and remains externally blocked until then.

## Implementation Strategy

### MVP

1. Complete Phase 1 and Phase 2, including the V15/V16 coordination gate.
2. Complete US1 automatic initialization and US2 market state/listing.
3. Complete US7 idempotency and US8 quote consumption.
4. Complete US3 purchase and its atomic tests.
5. Validate the purchase-only market increment before continuing to sales.

### Incremental delivery

1. Add US4 sales and finite-reserve accounting.
2. Add US5 portfolio and US6 confirmed history/rejection audit.
3. Add US9 frontend flows.
4. Add US10 controlled demonstration.
5. Run the later real Player Statistics Sync/Player Valuation integration verification when those features are available.

## Notes

- Every task uses the required checklist format and includes a concrete repository path.
- `[P]` means the task can proceed in parallel without editing an incomplete dependency or the same file.
- Story labels map to the ten user stories in `spec.md`.
- No task modifies Player Statistics Sync files, approved Specify/Plan documents, or performs migrations against a database during planning.
- No task implements scraping, statistics synchronization, valuation strategies, rankings, or a second quote source.
