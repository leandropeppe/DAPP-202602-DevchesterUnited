# Implementation Plan: Token Market

**Branch**: `feature/token-market` | **Date**: 2026-10-10 | **Spec**: [spec.md](spec.md)

**Input**: Approved feature specification in `specs/003-token-market/spec.md`.

## Summary

Token Market introduces the internal market lifecycle, virtual-credit accounts, one-time player-token issuance, immediate buy/sell operations against the protected superuser, personal holdings and portfolio reporting, immutable confirmed transactions, rejection audit, and persistent idempotent request recovery. It consumes one versioned quote contract owned functionally by Player Valuation; the initial quote is 1.00 credit per token and uses the same source of truth.

The design uses Spring Boot application services over PostgreSQL transactions. Normal operations lock only the resources involved, in one deterministic order, while persistent idempotency and atomic movement/transaction writes prevent duplicate transfers. Token Market does not calculate prices, scrape statistics, or change player-statistics ownership.

## Technical Context

**Language/Version**: Java 21; TypeScript/React for the existing frontend.

**Primary Dependencies**: Spring Boot 3.5.16, Spring Web, Spring Security OAuth2 Resource Server/JWT, Spring Data JPA, Bean Validation, springdoc OpenAPI, React, Vite.

**Storage**: PostgreSQL 16 with Flyway; Hibernate schema validation is the existing convention.

**Testing**: JUnit 5, Spring Boot Test, MockMvc, Testcontainers/PostgreSQL, Vitest, and Playwright as already used by the repository.

**Target Platform**: Spring Boot backend and React/Vite web client.

**Project Type**: Web application with backend API and frontend client.

**Performance Goals**: Keep normal market transactions short and row-scoped; do not serialize the whole market. Initialization may be serialized independently.

**Constraints**: JWT-derived ownership; integer positive token quantities; two-decimal credits/prices with higher internal cost precision; no parallel price source; no partial transfers; no confirmed transaction before commit; no changes to approved Specify or Player Statistics Sync.

**Scale/Scope**: Current player catalog and authenticated-user model, with a reproducible demonstration of four users and five players.

## Constitution Check

*GATE: Must pass before Phase 0 research and after Phase 1 design.*

| Principle / gate | Result | Plan evidence |
|---|---|---|
| Layered backend and clear responsibility | PASS | Market domain/application/API concerns are planned in existing package conventions; no valuation or statistics logic is added. |
| Secure identity and authorization | PASS | Private resources derive the user from validated JWT; the internal superuser has no public credentials or login path. |
| PostgreSQL as source of persistent business truth | PASS | Balances, issuance, holdings, idempotency, quotes, and immutable history are transactionally persisted. |
| Atomicity and consistency | PASS | Resource-scoped PostgreSQL row locks, deterministic ordering, constraints, and atomic movement/history writes. |
| API and documentation conventions | PASS | Proposed contracts reuse existing response, validation, error, JWT, and OpenAPI conventions without fixing implementation details prematurely. |
| Testability | PASS | Unit, MockMvc, Testcontainers, and UI demonstrations cover the approved acceptance criteria. |
| Cross-feature coordination | PASS WITH COORDINATION | V16 is provisional until V15 from Player Statistics Sync is reconciled; quote publication ownership is coordinated with Player Valuation. |

## Project Structure

### Documentation (this feature)

```text
specs/003-token-market/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── market-api.md
│   └── quote-contract.md
└── checklists/requirements.md
```

`tasks.md` is intentionally not created by this Plan execution.

### Source Code (repository root)

```text
backend/src/main/java/com/devchesterunited/devchesterunitedv2/
├── adapter/
│   ├── dto/
│   └── impl/
├── configuration/
├── controller/
│   ├── dto/
│   ├── dto/validation/
│   └── exceptionhandler/
├── modelo/
│   └── exception/
├── persistence/
│   ├── dto/
│   └── mapper/
└── service/
    ├── config/
    ├── dto/
    ├── exception/
    ├── impl/
    └── interfaces/

backend/src/main/resources/
├── db/migration/
└── application*.yml

frontend/src/
├── api/
├── components/
├── session/
├── styles/
└── views/
```

**Structure Decision**: Extend the verified backend packages `configuration`, `modelo`, `persistence`, `service`, and `controller`, using `adapter` where an internal boundary requires it. Extend the verified frontend `api`, `views`, and `components` conventions. The feature introduces market-specific domain concepts without changing player or statistics ownership. The quote contract is shared conceptually with Player Valuation; it must not create a second initial-price structure. Exact class/table names remain implementation tasks, not Specify/Plan API commitments.

## Phase 0: Research

Research decisions and repository evidence are recorded in [research.md](research.md). The key decisions are:

1. Use the existing Spring Boot/JPA/PostgreSQL/Flyway/JWT conventions.
2. Represent the superuser as one protected internal market counterparty, separate from public `Usuario` authentication.
3. Use one deterministic lock order for idempotency request, shared market state, player active state, quote head, user balance, superuser balance, player inventory, and user holding; normal operations do not take a global exclusive lock.
4. Persist idempotency state and use a PostgreSQL transaction-scoped advisory lock plus row ownership to prevent concurrent execution; commit the request result, movements, and confirmed history atomically.
5. Use an automatic application-readiness coordinator and `MarketInitializationService` to seed grants, issuance, and the initial 1.00 quote through one shared `QuotePublicationPort`.
6. Treat Flyway V16 as provisional only after coordination with the remote V15 statistics branch.

## Phase 1: Design

The design artifacts are:

- [data-model.md](data-model.md): conceptual persistence model, relationships, invariants, and lockable resources.
- [contracts/market-api.md](contracts/market-api.md): proposed authenticated API surface and response/error semantics.
- [contracts/quote-contract.md](contracts/quote-contract.md): stable versioned quote contract and feature ownership boundary.
- [quickstart.md](quickstart.md): future verification sequence, Testcontainers checks, and separated demonstration/integration scenarios.

## Post-Design Constitution Check

| Gate | Result | Evidence |
|---|---|---|
| Responsibility boundaries | PASS | Statistics remain upstream inputs; valuation publishes quotes; market consumes quotes and owns trading/accounting. |
| Security | PASS | JWT principal is the only user selector; internal superuser operations are not public. |
| Transactional integrity | PASS | Deterministic row locks, database constraints, idempotency states, and one commit boundary for confirmed operations. |
| No unnecessary global serialization | PASS | Only initialization/state transitions are serialized; ordinary operations lock their own resources. |
| Contract compatibility | PASS WITH COORDINATION | Initial and dynamic quotes share one contract; Player Valuation publication ownership and V15/V16 migration ordering remain coordination items. |
| Testability | PASS | PostgreSQL/Testcontainers and security/API/UI verification are explicitly planned. |

## Complexity Tracking

No constitutional violations are introduced. Persistent idempotency, resource-scoped locking, and a shared quote boundary are required by the approved functional specification and are not optional complexity.
