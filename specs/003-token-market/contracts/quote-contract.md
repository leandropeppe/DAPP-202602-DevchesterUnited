# Quote Contract: Player Valuation to Token Market

## Purpose

This is one functional quote contract and one source of truth shared by Player Valuation and Token Market. Token Market consumes published versions; it does not calculate prices or maintain a parallel initial-price table.

## Required quote data

Each immutable quote version identifies:

- stable player identifier;
- quote identifier and monotonically distinguishable version;
- price per token, represented with two-decimal monetary semantics;
- publication timestamp and validity start;
- optional validity end and explicit validity state;
- optional reference to the valuation strategy used.

Validity states distinguish at least valid, replaced, invalidated, and expired. The exact enum spelling is an implementation coordination item.

## Version and execution rules

Player Valuation publishes a new immutable version and advances the player's current quote head. Token Market receives the identifier/version selected by the user and, in the same ordered transaction flow as the trade, verifies that exact version is still valid. Replacement, invalidation, or expiry rejects the entire operation even when the replacement has the same numeric price. The market never silently substitutes another version.

The initial quote is 1.00 credit per token, persisted through the same publication boundary and data source. It does not expire automatically after seven days; it remains valid until explicitly replaced or invalidated. If no valid quote exists, trade execution is unavailable. The last known invalid/historical quote may be displayed with an explicit historical marker only.

## Initial publication before Player Valuation

Before the full Player Valuation feature exists, the automatic `MarketInitializationService` invokes the internal `QuotePublicationPort.publishInitialQuote(playerId, 1.00, nonExpiring)` for each eligible player. The port is the single publication boundary backed by the shared quote persistence adapter. It creates the same immutable quote version/current-head representation used by future dynamic publications. The initializer may use only the fixed configured value 1.00; it contains no strategies, statistics, scraping, or second quote structure.

The startup coordinator is triggered by application readiness, not by a public endpoint. It runs idempotently under the market-state initialization barrier: it creates/verifies the protected superuser and finite reserve, grants pre-existing users exactly once, emits once for active/first-activated players, and calls the publication port only when an eligible player has no initial quote. A failure leaves the market unavailable for trading; a retry resumes missing steps without replacing valid initial quotes or duplicating grants/emissions.

## Responsibility split

| Responsibility | Token Market | Player Valuation | Player Statistics Sync |
|---|---|---|---|
| Calculate price/strategy | No | Yes | No |
| Publish immutable quote versions | Orchestrates initial seeding through `QuotePublicationPort` | Functional authority and future dynamic publisher through the same port | No |
| Validate exact quote at trade execution | Yes | Provides validity/version contract | No |
| Maintain quote validity/history | Stores trade reference and uses published state | Owns quote lifecycle, policy, and recalc-failure records | No |
| Obtain/persist player statistics | No | Consumes as needed | Yes |
| Record confirmed market movement/history | Yes | No | No |

## Coordination requirements

Player Valuation must agree on the stable player key, quote/version identity, validity-state semantics, atomic head publication, and strategy reference format. The publication port and persistence representation are shared; no Token Market implementation may create a second source of truth. The remaining coordination is ownership of the shared adapter/package and the final naming of the internal port, not a change to the functional boundary.
