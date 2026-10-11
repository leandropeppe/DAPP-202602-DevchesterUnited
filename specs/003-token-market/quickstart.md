# Token Market Plan Verification Guide

This guide describes checks to execute after implementation. It does not run migrations, create fixtures, or prescribe final endpoint/class names.

## Preconditions

- Java 21, the repository's Gradle wrapper, PostgreSQL-compatible Testcontainers, and Docker are available.
- The application uses an isolated test database. No production database is altered.
- Demonstration fixtures are explicitly marked as test data and are kept separate from real catalog/users/statistics.
- Do not select a final Flyway version until V15 from Player Statistics Sync has been reconciled; V16 is only a candidate.

## Verification layers

### Unit-level checks

Cover market state transitions, one-time 1,000-credit grants, one-time 100-token issuance, positive integer quantities, weighted-average cost, proportional sale cost, final-position zeroing, realized/unrealized P/L, quote-version validation, and idempotency state transitions.

### PostgreSQL/Testcontainers checks

Verify:

1. unique superuser, account, issuance, holding, and `(user, key)` constraints;
2. non-negative balances/quantities and exactly 100 issued tokens per eligible player;
3. atomic rollback when a validation fails or a technical error occurs;
4. concurrent purchases cannot oversell superuser inventory or double-spend credits;
5. concurrent sales cannot oversell a holding or overdraw the superuser reserve;
6. all operations use the same lock order and do not deadlock under mixed buy/sell load;
7. quote publication/invalidation races reject stale exact versions without substituting a new one;
8. identical idempotent retries return one committed result, while conflicting parameters are rejected;
9. abandoned `PROCESSING` requests can be safely recovered without duplicate movements;
10. a concurrent normal trade and market initialization/state transition are serialized by the shared/exclusive state barrier without serializing normal trades with one another;
11. a concurrent player deactivation and trade resolves through the player-row lock, never trading against an inactive state;
12. no confirmed transaction exists without its committed movements and final request result.

### API/security checks

Use MockMvc and the existing API test conventions to verify JWT-derived ownership, forbidden cross-user access, market-not-ready behavior, exact quote acceptance, stale quote errors, pending/uncertain recovery, pagination/filter scope, and OpenAPI/error response consistency.

### Reproducible controlled demonstration

Prepare four clearly marked test users and five clearly marked test players. Publish initial 1.00 quotes, perform initial purchases, publish later different quote versions through the shared quote contract, compare open portfolio values, perform sales, verify realized gains/losses, close at least one position, and inspect personal histories. Confirm that closed positions disappear while realized results remain in the portfolio summary.

This controlled demonstration is separate from production data and from the later integration verification below.

The acceptance set in `spec.md` contains FR-001 through FR-052 and SC-001 through SC-011. The checks above and the API/security/UI scenarios cover those requirements; the later cross-feature run covers FR-051 and SC-011 specifically.

### Later cross-feature integration verification

When Player Statistics Sync and Player Valuation are available, verify that real synchronized statistics can feed valuation and that valuation publishes dynamic quote versions consumed by the market. Confirm that Token Market still performs no scraping, statistics persistence, strategy calculation, or ranking logic. Verify inactive/reactivated players and historical quotes across the real integration.

### Frontend checks

Use the repository's Vitest/Playwright conventions to verify market listing, balance, buy/sell confirmation, portfolio, history filters, loading states, stale/missing quote messaging, rejected operations, pending recovery, and uncertain-result messaging. No price calculation or advanced charts belong in the client.

## Completion evidence for Plan

Implementation should retain test evidence for atomicity, concurrency, idempotency, database invariants, security isolation, weighted-average accounting, and the four-user/five-player demonstration. Plan is complete without running these tests; Tasks/Implement will schedule them.
