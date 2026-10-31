# Contrato HTTP propuesto: Token Market P2P

Todos los endpoints requieren el JWT existente. El `user_id` efectivo siempre se deriva del principal y no se acepta como selector de identidad.

## Ofertas

### `GET /api/v1/market/p2p/offers`

Lista ofertas visibles con `offer_id`, jugador, un `seller_alias` no sensible, `mine`, `remaining_quantity`, estado derivado, cotización oficial informativa (`quote_id`, `version`, `unit_price`, vigencia) y `purchasable`. `mine` es un booleano calculado exclusivamente comparando el vendedor persistido con el usuario autenticado; nunca se acepta desde el cliente. Nunca incluye correo, hash, saldo, identificador de cuenta ni datos de autenticación del vendedor. Usa `page` desde 1 y `limit` entre 1 y 100, con metadatos `pagina`, `limite`, `totalPaginas`, `totalElementos`, como el historial actual. Filtros básicos: `playerId`, `status` y `mine`; orden estable `created_at DESC, id DESC`. Las ofertas sin jugador activo/cotización válida siguen visibles como suspendidas.

### `POST /api/v1/market/p2p/offers`

Request conceptual: `player_id`, `quantity`, `Idempotency-Key`. No se admite `price`, `unit_price` ni campo equivalente: si aparece, responder 400 con código `MANUAL_PRICE_NOT_ALLOWED`, sin crear oferta ni reserva. El backend valida jugador activo, quote válida y disponible no reservado. Respuesta 201/200 idempotente con la oferta creada y desglose de reserva.

### `POST /api/v1/market/p2p/offers/{offerId}/cancel`

Solo el vendedor puede cancelar. Libera la cantidad restante y devuelve el estado sin crear una transacción de compraventa. La respuesta/auditoría incluye `original_quantity`, `sold_quantity` y `released_quantity`. Repetir con la misma key devuelve el resultado original; una key nueva sobre una oferta ya cancelada responde conflicto de estado y no libera tokens otra vez. Otro usuario recibe 403/404 según la convención de autorización existente.

### `POST /api/v1/market/p2p/offers/{offerId}/purchases`

Request conceptual: `quantity`, `quote_id`, `quote_version`, `Idempotency-Key`. El servidor calcula `total = current_unit_price * quantity`. Si la identidad/versión no coincide o dejó de ser válida responde 409 con código `QUOTE_CHANGED`, sin mutaciones, y devuelve `current_quote` con la cotización vigente si existe. Si no existe cotización válida, devuelve `current_quote: null`, estado `SUSPENDED` y la oferta no es confirmable. El frontend debe obtener/mostrar la cotización válida y confirmar nuevamente con otra `Idempotency-Key`; no puede reutilizar la key que produjo el 409. La compra parcial devuelve restante y reserva actualizados; la total devuelve `FILLED`.

En una respuesta exitosa, el envelope conserva `status: "success"`. Dentro de `data`, `status` representa exclusivamente el estado persistido de la oferta: `PARTIALLY_FILLED` para una compra parcial y `FILLED` para una compra total. `data.operation_status` representa el estado de la operación/idempotencia (`CONFIRMED` o `PROCESSING`) y solo se incluye cuando existe semánticamente; una compra liquidada siempre devuelve `operation_status: "CONFIRMED"`. Una operación `PROCESSING` no se presenta como compra liquidada ni contiene un settlement confirmado.

Cuando la cotización no está disponible, la respuesta es HTTP 409 con el código de error correspondiente, `current_quote: null` serializado explícitamente y `offer_status: "SUSPENDED"`. `SUSPENDED` es un estado operativo derivado para la respuesta: no se persiste en la oferta, no cancela ni libera reservas y no cambia el estado del mercado.

Si una solicitud idempotente confirmada referencia un trade histórico sin snapshot de oferta, el replay responde HTTP 409 con código `HISTORICAL_RESULT_UNAVAILABLE`. No se reconstruyen cantidades desde la oferta actual ni se ejecuta una nueva compra.

## Compatibilidad y consultas existentes

- `POST /api/v1/market/purchases` conserva request, validaciones, idempotencia y respuesta de compra primaria.
- `POST /api/v1/market/sales` responde siempre `410 Gone` para nuevos intentos, sin llamar al servicio de settlement ni insertar auditoría como transacción confirmada. El historial conserva las ventas anteriores.
- El endpoint actual de portfolio agrega por posición `total_quantity`, `reserved_quantity` y `available_quantity`.
- El endpoint actual de transacciones devuelve tipos normalizados `PRIMARY_PURCHASE`, `P2P_PURCHASE`, `P2P_SALE` y conserva el tipo histórico de ventas al superusuario.

### `GET /api/v1/market/portfolio`

Requiere JWT y siempre consulta el portfolio del usuario autenticado; no acepta un
identificador de usuario como selector. Cada elemento de `data.open_positions`
mantiene los campos existentes (`quantity`, `pending_acquisition_cost`,
`weighted_average_cost` y la información de cotización) y agrega:

- `total_quantity`: cantidad total persistida en la tenencia.
- `reserved_quantity`: cantidad comprometida en ofertas P2P activas.
- `available_quantity`: `total_quantity - reserved_quantity`.

Las tres cantidades son no negativas y se calculan a partir de `market_holdings`.
Publicar o cancelar una oferta solo modifica `reserved_quantity`; una compra P2P
confirmada modifica también `total_quantity` según la liquidación.

### `GET /api/v1/market/transactions`

Requiere JWT y devuelve únicamente los asientos confirmados del usuario autenticado.
Los filtros disponibles son `playerId`, `type`, `from`, `to`, `page` y `limit`.
`from` es inclusivo, `to` exclusivo, `page` comienza en 1 y `limit` admite de 1 a
100 registros. La respuesta conserva el envelope y los metadatos de paginación
existentes.

Los valores de `operation_type` son:

- `PRIMARY_PURCHASE`: compra primaria persistida originalmente como `PURCHASE`.
- `SALE`: venta legacy al superusuario, conservada sin reclasificación.
- `P2P_PURCHASE`: asiento P2P del comprador.
- `P2P_SALE`: asiento P2P del vendedor.

El resultado se ordena de forma determinista por `confirmed_at DESC` y
`transaction_id DESC`. `realized_pnl` se incluye cuando existe para el asiento
correspondiente, especialmente en las ventas P2P y operaciones legacy que lo
persistan. Nunca se incluyen asientos de otra identidad JWT.

## Errores mínimos

`400 MANUAL_PRICE_NOT_ALLOWED`, `400 INVALID_QUANTITY`, `401` sin JWT, `403 FORBIDDEN`, `404 OFFER_NOT_FOUND`, `409 QUOTE_CHANGED`, `409 IDEMPOTENCY_CONFLICT`, `409 HISTORICAL_RESULT_UNAVAILABLE`, `409 OFFER_ALREADY_CANCELLED`, `409 INSUFFICIENT_AVAILABLE_QUANTITY`, `409 INSUFFICIENT_CREDITS`, `409 OFFER_NOT_PURCHASABLE`, `410` para la venta legacy y `503` si el mercado no está listo, según el envelope de errores vigente.

## OpenAPI

Los DTOs, códigos, esquemas de error, `Idempotency-Key`, scopes/autenticación y respuestas paginadas deben reflejarse en la anotación/documentación OpenAPI existente de `MarketController`. El contrato final debe mantener nombres y envelope consistentes con los endpoints actuales.
