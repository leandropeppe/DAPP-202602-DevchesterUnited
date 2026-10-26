# Contrato de API del mercado (propuesto)

Este es un contrato de planificación, no un compromiso de implementación con nombres finales de métodos Java. Sigue las convenciones de API autenticada, validación de DTOs, manejo de errores, JWT y OpenAPI del repositorio.

## Autenticación y propiedad

Todos los recursos personales requieren un JWT validado. El backend deriva el usuario del principal autenticado; los cuerpos de solicitud y parámetros de consulta no deben seleccionar otra cuenta. El superusuario interno nunca se expone como usuario normal ni como contraparte seleccionable por el cliente.

## Recursos propuestos

| Método | Recurso | Propósito |
|---|---|---|
| GET | `/api/v1/market/balance` | Devuelve los créditos disponibles del usuario autenticado. |
| GET | `/api/v1/market/players` | Lista los jugadores del catálogo operables en el mercado, los metadatos de la cotización actual y los tokens disponibles del superusuario. |
| POST | `/api/v1/market/purchases` | Solicita una compra completa utilizando jugador, cantidad entera positiva, identidad/versión exacta de cotización y `Idempotency-Key`. |
| POST | `/api/v1/market/sales` | Solicita una venta completa utilizando jugador, cantidad entera positiva, identidad/versión exacta de cotización y `Idempotency-Key`. |
| GET | `/api/v1/market/operations/{idempotency-key}` | Recupera el estado/resultado de la solicitud del usuario autenticado, incluido `PROCESSING`, sin presentar una operación no confirmada como compraventa. |
| GET | `/api/v1/market/portfolio` | Devuelve únicamente las posiciones abiertas y el resumen de rentabilidad del usuario autenticado. |
| GET | `/api/v1/market/transactions` | Devuelve únicamente las transacciones confirmadas del usuario autenticado, de la más nueva a la más antigua, con paginación y filtros por jugador/tipo/fecha. |

La escritura exacta de las rutas puede alinearse con la convención API existente durante la implementación; el contrato debe conservar estas garantías semánticas y de propiedad.

## Semántica de las solicitudes de operación

La solicitud de compra/venta identifica un jugador estable, una cantidad entera, una identidad/versión de cotización y una clave de idempotencia. El servidor verifica estado activo, emisión/inventario, saldos, vigencia exacta de la cotización y semántica de la clave en un único flujo transaccional. Un fallo rechaza toda la solicitud sin transferencias parciales.

Las solicitudes idénticas repetidas devuelven el resultado confirmado original. Reutilizar la misma clave con una solicitud normalizada diferente devuelve un conflicto. Una solicitud pendiente o incierta puede recuperarse mediante la misma clave o su referencia de operación.

## Semántica de la respuesta del portfolio

Las posiciones abiertas contienen jugador, cantidad actual, costo de adquisición promedio ponderado, costo pendiente, cotización/valor vigente cuando esté disponible y P/L no realizada cuando pueda calcularse. El resumen incluye P/L realizada acumulada incluso después del cierre de posiciones, P/L no realizada de posiciones abiertas y un resultado combinado solo cuando estén disponibles todas las valoraciones actuales necesarias. Un último precio conocido puede marcarse como histórico y no debe representarse como actual.

## Semántica de la respuesta del historial

Solo aparecen operaciones confirmadas. Cada elemento contiene identidad de transacción, fecha, identidad/nombre snapshot del jugador, tipo compra/venta, cantidad, precio unitario aplicado y total, referencia de contraparte superusuario, versión exacta de cotización, referencia de idempotencia y P/L realizada cuando corresponda. Los intentos rechazados pertenecen a un recurso de auditoría interno, no a este historial personal.

## Semántica de errores

Utilizar las convenciones existentes de DTOs de respuesta/error. La implementación debe distinguir fallos de autenticación/autorización, entrada inválida, jugador inactivo, cotización ausente u obsoleta, créditos/tokens/reserva insuficientes, mercado no operativo, conflicto de idempotencia y estado pendiente/incierto. Los payloads de error no deben revelar credenciales, JWT ni datos de otro usuario.

## Extension propuesta de la respuesta del portfolio

Los siguientes campos son nuevos para las mejoras de Portfolio. Los campos de posiciones y P/L ya implementados conservan sus nombres y semantica actuales.

### Campos nuevos en `data` del Portfolio

- `open_players`: cantidad de jugadores distintos con posiciones abiertas.
- `total_tokens`: suma de tokens de todas las posiciones abiertas.
- `total_pending_acquisition_cost`: costo pendiente total de todas las posiciones abiertas.
- `total_current_value`: valor actual total cuando todas las posiciones tienen cotizacion vigente; `null` si falta informacion suficiente.

### Campos descriptivos nuevos por posicion

- `player_name`: nombre canonico actual del jugador, si esta disponible.
- `team`: equipo canonico, si esta disponible.
- `league`: liga canonica, si esta disponible.

`player_id` continua siendo el identificador interno UUID de la posicion. El frontend puede mostrar los campos descriptivos, pero debe utilizar `player_id` para construir operaciones.

### Semantica contable

- `realized_pnl` sigue siendo obligatorio y acumulado para el usuario, incluso sin posiciones abiertas.
- `unrealized_pnl` sigue siendo `null` cuando alguna posicion abierta no puede valorarse con cotizacion vigente.
- `combined_result` sigue siendo `null` cuando `combined_result_complete` es `false`.
- `total_pending_acquisition_cost` puede conocerse aunque existan cotizaciones ausentes.
- `total_current_value` no debe convertirse en cero por falta de cotizacion.
- Los totales se calculan en backend sobre toda la cartera, sin paginacion de posiciones y con la precision interna existente.

### Venta desde Portfolio

Una posicion puede iniciar venta solamente cuando `quote_availability` es `CURRENT` y contiene `quote_id`, `quote_version` y `unit_price`. Las posiciones `HISTORICAL` o `ABSENT` se muestran sin accion de venta operable y nunca deben reutilizar una cotizacion obsoleta.

La solicitud continua utilizando el contrato existente de `POST /api/v1/market/sales`, con identidad del usuario derivada del JWT, `player_id`, cantidad, `quote_id`, `quote_version` e `Idempotency-Key`. El backend conserva la autoridad final sobre vigencia, tenencia, saldo, concurrencia e idempotencia.

## Extension propuesta para actividad grafica

`GET /api/v1/market/transactions` sigue siendo paginado y devuelve solamente operaciones confirmadas. Un grafico temporal no puede interpretar una sola pagina como historial completo. La primera implementacion debe cargar todas las paginas necesarias usando el mismo usuario y filtros, o incorporar posteriormente un endpoint agregado si el volumen o la consistencia temporal lo justifican.

## Comprobaciones de seguridad

- Un usuario válido no puede proporcionar el identificador de otro usuario para comprar, vender, leer saldo, consultar portfolio ni consultar historial.
- Un usuario no puede invocar la inicialización del superusuario ni operaciones de la contraparte interna.
- Las solicitudes sin autenticación válida se rechazan según las convenciones de seguridad existentes.
- La paginación y los filtros no pueden ampliar el alcance del historial del usuario autenticado.
