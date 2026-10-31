# Quickstart de verificación futura

Este documento describe cómo se verificará la implementación cuando exista código. No ejecuta migraciones ni pruebas en la etapa de Plan.

## Preparación

1. Levantar un PostgreSQL de pruebas y permitir que Flyway aplique, mediante el flujo normal del proyecto, todas las migraciones vigentes de Token Market, incluidas V21, V22 y V23.
2. Levantar el backend con las credenciales de prueba y el frontend con la configuración local existente.
3. Crear/usar dos usuarios registrados, un jugador activo con quote válida, saldo suficiente y una tenencia del vendedor.
4. Verificar que el superusuario tenga inventario para el flujo primario.

## Flujo manual mínimo

1. Comprar tokens al superusuario y comprobar que el flujo actual permanece igual.
2. Intentar `POST /api/v1/market/sales`: esperar 410 y comprobar que saldo, tenencia, movimientos e historial no cambian.
3. Publicar una oferta sin precio; comprobar reserva inmediata y `available = total - reserved`.
4. Repetir publicación incluyendo `price`; esperar `MANUAL_PRICE_NOT_ALLOWED` y cero mutaciones.
5. Comprar parcialmente con la quote observada; comprobar crédito comprador/vendedor, tokens, reserva y dos asientos de historial.
6. Cambiar la quote antes de confirmar; esperar 409 `QUOTE_CHANGED`, sin efectos; reconfirmar con nueva key.
7. Cancelar el remanente y verificar que solo se libera lo no vendido.
8. Desactivar jugador o invalidar quote; comprobar ofertas visibles/suspendidas y cancelables, sin nuevas publicaciones ni compras.
9. Repetir la cancelación con la misma key y comprobar que devuelve el resultado original; repetir con una key nueva y comprobar `OFFER_ALREADY_CANCELLED` sin liberar nuevamente.
10. Consultar el listado con `page`, `limit`, `playerId`, `status` y `mine`; comprobar metadatos, orden estable y ausencia de correo/saldo/identificador de cuenta del vendedor.

## Suites automatizadas previstas

- Unitarias: cantidades, estados, rechazo de precio manual, cálculo de disponible, mapeo de tipos y quote changed.
- Integración Testcontainers: reserva, parcial/total, cancelación, cuentas/tenencias/movimientos, constraints y migración V21.
- Concurrencia: dos compradores sobre el remanente, publicación doble sobre una tenencia, compra/cancelación y compras cruzadas entre los mismos usuarios; verificar ausencia de deadlock y saldos no negativos.
- Compatibilidad de locks: compra primaria concurrente con publicación/compra/cancelación P2P, comprobando el prefijo de locks existente y ausencia de deadlock.
- Idempotencia: replay idéntico y key reutilizada con parámetros distintos, incluyendo `QUOTE_CHANGED` con nueva key.
- MockMvc/E2E: autenticación, autorización, 410, contratos OpenAPI, portfolio e historial unificado.
- Frontend: listado, ausencia de input de precio, desglose de portfolio, confirmación/refresh por 409, estados suspendidos y manejo de 410.

## Invariantes de salida

- Ningún balance, tenencia o reserva negativo.
- La suma transferida por un trade es igual entre débito y crédito.
- La suma de tokens en inventario/tenencias/reservas respeta la emisión existente.
- Una key confirmada nunca produce duplicados.
- Las ventas legacy siguen visibles y no habilitan una venta nueva al superusuario.
