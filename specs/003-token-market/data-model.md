# Data Model: Token Market

This is a conceptual persistence design for Plan. Names are descriptive and are not definitive table or class names.

## Core records

| Concept | Purpose | Key constraints |
|---|---|---|
| Market state | Singleton lifecycle and initialization status | One row; states include not initialized, initializing, ready, and failed; normal trading requires ready; a state epoch/version changes on each transition. |
| Internal market counterparty | Protected superuser identity | Exactly one singleton of kind `SUPERUSER`; no public credentials or login; referenced by movements/history. |
| Credit account | Balance for a user or the counterparty | Owner identity unique; non-negative balance; user initial grant and reserve initialization are one-time effects. |
| Token issuance/inventory | The initial 100-token emission for each eligible player | One row per stable player; exactly 100 issued; initial owner is the superuser; unique issuance prevents reactivation duplication. |
| Holding | User's current tokens and acquisition cost for one player | Unique `(user, player)`; integer quantity >= 0; cost uses internal precision; closed holding has quantity and residual cost zero. |
| Quote/version reference | Versioned price published under the shared quote contract | Immutable version; player, quote identity, price, publication, validity, and optional strategy reference; current head is coordinated per player. |
| Idempotency request | Durable operation claim and result | Unique `(user, key)`; normalized parameter hash; status, execution token, and stored response/result reference; no secrets. |
| Confirmed transaction | Immutable successful buy or sell | Stable user/player references plus player-name snapshot, quantity, price, total, counterparty, quote version, idempotency reference, timestamp, and realized P/L when applicable. |
| Rejection audit | Internal record of rejected attempts | Separate from confirmed transactions; no credentials, JWTs, or sensitive payloads; restricted access. |

## Relationships

- A stable catalog player can have at most one initial issuance record and one initial 100-token allocation. Active status determines operability, not whether an already-issued token supply disappears.
- The superuser owns the initial inventory and is the only trade counterparty. A buy moves tokens from the superuser to the user and credits to the superuser. A sell reverses those movements, subject to the reserve balance.
- A user can have one holding per player. Confirmed transactions are append-only and preserve player-name and quote-version snapshots needed for audit.
- A confirmed transaction references exactly one idempotency request and exactly one accepted quote version.
- Quote records are shared with Player Valuation. Token Market stores a reference and the accepted version; it must not maintain a second price source.

## Invariants and database protections

1. The market cannot execute trades unless the singleton state is `READY`.
2. The protected superuser is unique, non-public, and not addressable as an authenticated user account.
3. User credit-account initialization has a uniqueness/once-only marker or equivalent constraint; rerunning initialization cannot add another 1,000-credit grant.
4. Each eligible player has one issuance record with total issued quantity exactly 100. Inactive players become eligible on first activation; reactivation does not create another issuance.
5. Credit balances, token quantities, and superuser inventory cannot become negative. The per-player total across holders and superuser inventory cannot exceed the 100-token issuance.
6. Token quantity is an integer positive quantity in requests; zero/negative/fractional values are rejected before movement.
7. A confirmed buy/sell, its credit and token movements, holding/cost update, immutable transaction, and final idempotency result share one commit boundary.
8. Idempotency key reuse with a different normalized parameter hash is a conflict; it cannot alter the original result.
9. Confirmed history is immutable. Rejected attempts and abandoned/uncertain request states are not inserted into confirmed history.
10. A quote version is accepted only if the exact requested version is still valid at execution. Replacement, invalidation, or expiry rejects the operation even if the numeric price is unchanged.
11. Initial quote validity is not automatically capped at seven days; it ends only by explicit replacement or invalidation.

## Initialization and state transitions

An internal application-startup coordinator invokes initialization; no public endpoint is used. The singleton state row is locked with `FOR UPDATE` while the coordinator transitions to `INITIALIZING`, verifies the single superuser and its configured one-time reserve, grants existing users through a unique initial-grant marker, emits exactly 100 tokens for each eligible player, and publishes the initial quote through the shared quote publication port. A failure leaves `FAILED`; a retry resumes missing steps and can reach `READY` only after all mandatory steps succeed. Existing grants, issuances, and valid quotes are never reset or duplicated.

Registration invokes an internal idempotent grant hook using the same unique marker for the new user. The user may be persisted independently of a failed market initialization, but cannot operate in the market until its 1,000-credit account/grant exists. Activation of an initially inactive player runs the same issuance-once path. Reactivation sees the existing issuance and does not create another 100-token emission.

## Portfolio accounting

For an open holding, retain current quantity and total pending acquisition cost; the weighted average is total cost divided by quantity for display. On a partial sale, remove the proportional cost from the holding and record realized P/L against the exact sale price. On the final sale, set quantity and residual cost to zero. Keep realized P/L in transaction history and an aggregate independent of open holdings so the portfolio summary continues to show it after closure. Current valuation and unrealized P/L are calculated only from a valid current quote; a historical quote may be displayed as historical but cannot be treated as current.

## Lockable resources and ordering

For both purchase and sale, acquire locks in this single order:

1. Idempotency request.
2. Market state with a shared lock for normal operations, or an exclusive lock for initialization/state transition.
3. Catalog player row with a shared lock for active-state validation.
4. Player quote head/version coordination row.
5. User credit account.
6. Superuser credit account.
7. Player issuance/inventory.
8. User holding, creating a canonical zero row under a unique key if absent.

Normal trades keep the shared market-state and player shared locks until commit. Initialization/state transitions and player activation/deactivation use exclusive locks, so they wait for active operations and prevent new conflicting readers without serializing normal trades with each other. The idempotency request also has a PostgreSQL transaction-scoped advisory lock derived from its persistent identity; a concurrent executor cannot process the same request while the first transaction is active. If that transaction rolls back or its process dies, the advisory lock is released and the durable `PROCESSING` row can be recovered. Quote publication/invalidation uses the quote head in the same per-player order and commits the new immutable version/head atomically. It never changes an already accepted transaction's quote.

## Quote publication and shared source

The internal `MarketInitializationService` is the sole initial seeder and calls `QuotePublicationPort.publishInitialQuote` with 1.00 and non-expiring validity. The port writes the same immutable version/head representation later used by Player Valuation's dynamic publisher. Token Market owns orchestration and exact-version consumption; Player Valuation owns valuation and future dynamic publication; Player Statistics Sync owns statistics only. No parallel initial-price structure is allowed.

## Statistics and migration boundary

The model references the stable player/catalog identity and active state only. It does not require a statistics table, WhoScored payload, scraping result, or Player Statistics Sync migration. Any foreign-key target or player-state contract must be coordinated with the existing catalog/statistics work. Flyway V16 remains provisional until the remote V15 migration is reconciled; this document intentionally contains no SQL.
