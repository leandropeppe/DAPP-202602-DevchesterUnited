# Modelo de datos: Token Market P2P

Los nombres de tablas y clases son decisiones de diseño para la implementación; el SQL no se genera en esta etapa.

## Entidades nuevas o extendidas

| Concepto | Diseño | Invariantes |
|---|---|---|
| Oferta P2P | Oferta append-only con `id`, `seller_user_id`, `player_id`, `original_quantity`, `remaining_quantity`, `sold_quantity`, estado y timestamps | Cantidades enteras; `original = sold + remaining + released`; vendedor no es superusuario; no hay columna de precio manual |
| Reserva de tenencia | Campo `reserved_quantity` dentro de `market_holdings`, inicializado en cero y actualizado junto con `quantity` | `0 <= reserved_quantity <= quantity`; disponible siempre es `quantity - reserved_quantity`; suma de `remaining_quantity` de ofertas activas coincide con la reserva |
| Trade P2P | Registro inmutable de una liquidación parcial o total, con oferta, jugador, cantidad, importe, quote id/version, comprador, vendedor e idempotencia | Una compra confirmada tiene un único efecto contable y una cotización exacta |
| Asiento P2P | Participación consultable por usuario y rol `P2P_PURCHASE`/`P2P_SALE` | Cada trade confirmado tiene un asiento para comprador y otro para vendedor; no aparece una oferta/cancelación |
| Idempotencia | Reutiliza la tabla/servicio actual con namespace o tipos P2P y hash normalizado | Misma key + mismos parámetros devuelve resultado; key + parámetros distintos da conflicto |
| Portfolio | DTO/proyección actual extendido con total, reservado y disponible | `available = total - reserved`; una compra cambia total; publicar/cancelar solo cambia reservado |

## Relaciones

- Un usuario puede tener muchas ofertas; una oferta pertenece a un único jugador y una única tenencia del vendedor.
- Una oferta puede tener muchos trades parciales, pero solo un estado terminal completado o cancelado.
- Un trade mueve créditos entre dos cuentas de usuario y tokens entre dos tenencias; no referencia la cuenta del superusuario.
- El historial legacy sigue leyendo `TransaccionConfirmadaMercado`; el historial unificado agrega asientos P2P sin reclasificar ventas históricas.
- El `quote_id`/`quote_version` del trade refiere al registro inmutable de cotización usado para liquidar; la cabecera vigente solo decide si es aceptable.

## Auditoría de cancelación

`original_quantity` es inmutable. Cada trade confirmado incrementa `sold_quantity`; una cancelación captura bajo lock `released_quantity = remaining_quantity`, conserva el actor y el instante, y deja el remanente en cero. La invariante auditable es `original_quantity = sold_quantity + remaining_quantity + released_quantity`. En una oferta activa, `released_quantity` es cero; en una cancelada, el evento de cancelación es la fuente del valor liberado. Ninguna cancelación modifica o revierte trades confirmados.

## Estados

`OPEN` con restante completo, `PARTIALLY_FILLED` con restante menor que el original, `FILLED` con restante cero y `CANCELLED` con restante liberado. `SUSPENDED` se recomienda como estado de respuesta derivado cuando jugador/cotización no permiten comprar; no debe cancelar ni liberar automáticamente la oferta.

## Invariantes de base de datos

1. `reserved_quantity` se inicializa en cero; la base impone `0 <= reserved_quantity <= quantity`.
2. No se puede insertar una oferta con precio manual; el contrato de request debe rechazar el campo y el esquema no debe usarlo como autoridad.
3. Una oferta nueva solo se inserta si la transacción valida jugador activo y cotización válida; los estados posteriores no borran la oferta.
4. La suma de `remaining_quantity` de ofertas activas por vendedor/jugador debe coincidir con `market_holdings.reserved_quantity`.
5. Reserva, restante, tenencia y movimientos se actualizan dentro de la misma transacción.
6. Un update de compra debe verificar `remaining_quantity >= requested_quantity` bajo lock.
7. Un update de saldo debe verificar saldo suficiente y mantener balance no negativo.
8. Una cancelación solo libera `remaining_quantity`; cantidades vendidas no vuelven a la reserva.
9. Una oferta completa deja restante/reserva en cero; una cancelada deja restante cero y conserva auditoría de cantidad liberada.
10. Las filas P2P y sus asientos son inmutables tras confirmación.
11. Índices para ofertas abiertas por jugador/fecha y para historial por usuario/fecha; unicidad de idempotencia se conserva.

## Migración prevista

La migración conceptual es `V21__create_token_market_p2p.sql`, después de V20. Debe incluir tablas/columnas P2P, `market_holdings.reserved_quantity` con backfill a cero y checks, FK a usuarios/jugadores/cuentas/tenencias/cotizaciones, checks de cantidades, índices de consultas e idempotencia. No se crea una tabla de reservas separada. Nunca se debe modificar destructivamente el historial legacy.

## Lock map

| Caso | Orden requerido |
|---|---|
| Publicar | idempotencia → estado mercado → jugador → cabecera quote → tenencia vendedor |
| Comprar | idempotencia → estado mercado → oferta → jugador → cabecera quote → cuentas ordenadas → tenencias ordenadas → trade/asientos |
| Cancelar | idempotencia → estado mercado → oferta → tenencia vendedor |

La ordenación de dos cuentas/tenencias se realiza por identificador estable ascendente; la aplicación de débitos/créditos se decide por rol después de obtener todos los locks.
