# Contrato HTTP propuesto: Token Market P2P

Todos los endpoints requieren el JWT existente. El `user_id` efectivo siempre se deriva del principal y no se acepta como selector de identidad.

## Ofertas

### `GET /api/v1/market/p2p/offers`

Lista ofertas visibles con `offer_id`, jugador, un `seller_alias` no sensible, `remaining_quantity`, estado derivado, cotización oficial informativa (`quote_id`, `version`, `unit_price`, vigencia) y `purchasable`. Nunca incluye correo, hash, saldo, identificador de cuenta ni datos de autenticación del vendedor. Usa `page` desde 1 y `limit` entre 1 y 100, con metadatos `pagina`, `limite`, `totalPaginas`, `totalElementos`, como el historial actual. Filtros básicos: `playerId`, `status` y `mine`; orden estable `created_at DESC, id DESC`. Las ofertas sin jugador activo/cotización válida siguen visibles como suspendidas.

### `POST /api/v1/market/p2p/offers`

Request conceptual: `player_id`, `quantity`, `Idempotency-Key`. No se admite `price`, `unit_price` ni campo equivalente: si aparece, responder 400 con código `MANUAL_PRICE_NOT_ALLOWED`, sin crear oferta ni reserva. El backend valida jugador activo, quote válida y disponible no reservado. Respuesta 201/200 idempotente con la oferta creada y desglose de reserva.

### `POST /api/v1/market/p2p/offers/{offerId}/cancel`

Solo el vendedor puede cancelar. Libera la cantidad restante y devuelve el estado sin crear una transacción de compraventa. La respuesta/auditoría incluye `original_quantity`, `sold_quantity` y `released_quantity`. Repetir con la misma key devuelve el resultado original; una key nueva sobre una oferta ya cancelada responde conflicto de estado y no libera tokens otra vez. Otro usuario recibe 403/404 según la convención de autorización existente.

### `POST /api/v1/market/p2p/offers/{offerId}/purchases`

Request conceptual: `quantity`, `quote_id`, `quote_version`, `Idempotency-Key`. El servidor calcula `total = current_unit_price * quantity`. Si la identidad/versión no coincide o dejó de ser válida responde 409 con código `QUOTE_CHANGED`, sin mutaciones, y devuelve `current_quote` con la cotización vigente si existe. Si no existe cotización válida, devuelve `current_quote: null`, estado `SUSPENDED` y la oferta no es confirmable. El frontend debe obtener/mostrar la cotización válida y confirmar nuevamente con otra `Idempotency-Key`; no puede reutilizar la key que produjo el 409. La compra parcial devuelve restante y reserva actualizados; la total devuelve `FILLED`.

## Compatibilidad y consultas existentes

- `POST /api/v1/market/purchases` conserva request, validaciones, idempotencia y respuesta de compra primaria.
- `POST /api/v1/market/sales` responde siempre `410 Gone` para nuevos intentos, sin llamar al servicio de settlement ni insertar auditoría como transacción confirmada. El historial conserva las ventas anteriores.
- El endpoint actual de portfolio agrega por posición `total_quantity`, `reserved_quantity` y `available_quantity`.
- El endpoint actual de transacciones devuelve tipos normalizados `PRIMARY_PURCHASE`, `P2P_PURCHASE`, `P2P_SALE` y conserva el tipo histórico de ventas al superusuario.

## Errores mínimos

`400 MANUAL_PRICE_NOT_ALLOWED`, `400 INVALID_QUANTITY`, `401` sin JWT, `403 FORBIDDEN`, `404 OFFER_NOT_FOUND`, `409 QUOTE_CHANGED`, `409 IDEMPOTENCY_CONFLICT`, `409 OFFER_ALREADY_CANCELLED`, `409 INSUFFICIENT_AVAILABLE_QUANTITY`, `409 INSUFFICIENT_CREDITS`, `409 OFFER_NOT_PURCHASABLE`, `410` para la venta legacy y `503` si el mercado no está listo, según el envelope de errores vigente.

## OpenAPI

Los DTOs, códigos, esquemas de error, `Idempotency-Key`, scopes/autenticación y respuestas paginadas deben reflejarse en la anotación/documentación OpenAPI existente de `MarketController`. El contrato final debe mantener nombres y envelope consistentes con los endpoints actuales.
