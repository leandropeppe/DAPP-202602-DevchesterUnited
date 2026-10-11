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

## Comprobaciones de seguridad

- Un usuario válido no puede proporcionar el identificador de otro usuario para comprar, vender, leer saldo, consultar portfolio ni consultar historial.
- Un usuario no puede invocar la inicialización del superusuario ni operaciones de la contraparte interna.
- Las solicitudes sin autenticación válida se rechazan según las convenciones de seguridad existentes.
- La paginación y los filtros no pueden ampliar el alcance del historial del usuario autenticado.
