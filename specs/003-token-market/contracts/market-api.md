# Market API Contract (Proposed)

This is a planning contract, not an implementation commitment to final Java method names. It follows the repository's authenticated API, DTO validation, error handling, JWT, and OpenAPI conventions.

## Authentication and ownership

All personal resources require a validated JWT. The backend derives the user from the authenticated principal; request bodies and query parameters must not select another account. The internal superuser is never exposed as a normal user or client-selectable counterparty.

## Proposed resources

| Method | Resource | Purpose |
|---|---|---|
| GET | `/api/v1/market/balance` | Return the authenticated user's available credits. |
| GET | `/api/v1/market/players` | List catalog players operable in the market, current quote metadata, and superuser token availability. |
| POST | `/api/v1/market/purchases` | Request an all-or-nothing purchase using player, positive integer quantity, exact quote identity/version, and `Idempotency-Key`. |
| POST | `/api/v1/market/sales` | Request an all-or-nothing sale using player, positive integer quantity, exact quote identity/version, and `Idempotency-Key`. |
| GET | `/api/v1/market/operations/{idempotency-key}` | Recover the authenticated user's request status/result, including `PROCESSING`, without presenting an unconfirmed operation as a trade. |
| GET | `/api/v1/market/portfolio` | Return only the authenticated user's open positions and performance summary. |
| GET | `/api/v1/market/transactions` | Return only the authenticated user's confirmed transactions, newest first, with pagination and player/type/date filters. |

The exact path spelling can be aligned with the existing API convention during implementation; the contract must retain these ownership and semantic guarantees.

## Operation request semantics

The purchase/sale request identifies a stable player, integer quantity, quote identifier/version, and idempotency key. The server checks active status, issuance/inventory, balances, exact quote validity, and key semantics in one transactional flow. A failure rejects the whole request without partial transfers.

Repeated identical requests return the original committed result. Reuse of the same key with a different normalized request returns a conflict. A pending or uncertain request can be recovered by the same key or its operation reference.

## Portfolio response semantics

Open positions contain player, current quantity, weighted-average acquisition cost, pending cost, current valid quote/value when available, and unrealized P/L when calculable. The summary includes cumulative realized P/L even after positions close, unrealized P/L for open positions, and a combined result only when all required current valuations are available. A last known price may be marked historical and must not be represented as current.

## History response semantics

Only confirmed trades appear. Each item contains transaction identity, timestamp, player identity/name snapshot, buy/sell type, quantity, applied unit price and total, superuser counterparty reference, exact quote version, idempotency reference, and realized P/L when applicable. Rejected attempts belong to an internal audit resource, not this personal history.

## Error semantics

Use existing response/error DTO conventions. The implementation should distinguish authentication/authorization failures, invalid input, inactive player, missing or stale quote, insufficient credits/tokens/reserve, market not ready, idempotency conflict, and pending/uncertain status. Error payloads must not disclose credentials, JWTs, or another user's data.

## Security acceptance checks

- A valid user cannot supply another user's identifier to buy, sell, read balance, read portfolio, or read history.
- A user cannot invoke superuser initialization or internal counterparty operations.
- Requests without valid authentication are rejected according to existing security conventions.
- Pagination and filters cannot broaden the authenticated user's history scope.
