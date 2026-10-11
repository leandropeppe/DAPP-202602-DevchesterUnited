# Modelo de datos: Token Market

Este es un diseño conceptual de persistencia para Plan. Los nombres son descriptivos y no constituyen nombres definitivos de tablas o clases.

## Registros principales

| Concepto | Propósito | Restricciones clave |
|---|---|---|
| Estado del mercado | Ciclo de vida singleton y estado de inicialización | Una fila; estados no inicializado, inicializando, listo y fallido; el trading normal exige estado listo; una época/versión cambia en cada transición. |
| Contraparte interna del mercado | Identidad de superusuario protegida | Exactamente un singleton de tipo `SUPERUSER`; sin credenciales públicas ni login; referenciado por movimientos/historial. |
| Cuenta de créditos | Saldo de un usuario o de la contraparte | Propietario único; saldo no negativo; asignación inicial del usuario y reserva inicial son efectos de una sola vez. |
| Emisión/inventario de tokens | Emisión inicial de 100 tokens para cada jugador elegible | Una fila por jugador estable; exactamente 100 emitidos; propietario inicial superusuario; una emisión única impide duplicar por reactivación. |
| Referencia de cotización/versión | Precio versionado publicado bajo el contrato compartido | Versión inmutable; jugador, identidad de cotización, precio, publicación, vigencia y referencia opcional de estrategia; cabecera actual coordinada por jugador. |
| Tenencia | Tokens actuales y costo de adquisición del usuario para un jugador | `(user, player)` único; cantidad entera >= 0; costo con precisión interna; tenencia cerrada con cantidad y costo residual en cero. |
| Solicitud idempotente | Reclamación y resultado durable de una operación | `(user, key)` único; hash de parámetros normalizados; estado, token de ejecución y referencia de respuesta/resultado almacenado; sin secretos. |
| Transacción confirmada | Compra o venta exitosa inmutable | Referencias estables de usuario/jugador más snapshot del nombre, cantidad, precio, total, contraparte, versión de cotización, referencia de idempotencia, fecha y P/L realizado cuando corresponda. |
| Auditoría de rechazo | Registro interno de intentos rechazados | Separada de transacciones confirmadas; sin credenciales, JWT ni payloads sensibles; acceso restringido. |

## Relaciones

- Un jugador estable del catálogo puede tener como máximo un registro de emisión inicial y una asignación inicial de 100 tokens. El estado activo determina si puede operarse, no si desaparece un suministro ya emitido.
- El superusuario posee el inventario inicial y es la única contraparte de trading. Una compra mueve tokens del superusuario al usuario y créditos al superusuario. Una venta invierte esos movimientos, sujeta al saldo de la reserva.
- Un usuario puede tener una tenencia por jugador. Las transacciones confirmadas son append-only y conservan snapshots del nombre del jugador y de la versión de cotización necesarios para auditoría.
- Una transacción confirmada referencia exactamente una solicitud idempotente y exactamente una versión de cotización aceptada.
- Los registros de cotizaciones se comparten con Player Valuation. Token Market almacena la referencia y la versión aceptada; no debe mantener una segunda fuente de precios.

## Invariantes y protecciones de base de datos

1. El mercado no puede ejecutar operaciones si el estado singleton no es `READY`.
2. El superusuario protegido es único, no público y no se puede direccionar como cuenta de usuario autenticado.
3. La inicialización de la cuenta de créditos del usuario tiene un marcador de unicidad/una sola vez o una restricción equivalente; repetir la inicialización no puede agregar otra asignación de 1.000 créditos.
4. Cada jugador elegible tiene un registro de emisión con cantidad total exactamente 100. Los jugadores inactivos se vuelven elegibles en su primera activación; reactivar no crea otra emisión.
5. Los saldos de créditos, cantidades de tokens e inventario del superusuario no pueden ser negativos. El total por jugador entre tenencias y el inventario del superusuario no puede superar la emisión de 100 tokens.
6. La cantidad de tokens es entera y positiva en las solicitudes; los valores cero/negativos/fraccionarios se rechazan antes del movimiento.
7. Una compra/venta confirmada, sus movimientos de créditos y tokens, actualización de tenencia/costo, transacción inmutable y resultado final de idempotencia comparten un único límite de commit.
8. Reutilizar una clave de idempotencia con un hash de parámetros normalizados distinto es un conflicto; no puede alterar el resultado original.
9. El historial confirmado es inmutable. Los intentos rechazados y los estados de solicitud abandonados/inciertos no se insertan en el historial confirmado.
10. Una versión de cotización se acepta solo si la versión exacta solicitada sigue válida al ejecutar. Reemplazo, invalidación o vencimiento rechaza la operación aunque el precio numérico no cambie.
11. La vigencia de la cotización inicial no está limitada automáticamente a siete días; termina solo por reemplazo o invalidación explícitos.

## Inicialización y transiciones de estado

Un coordinador interno de inicio de la aplicación invoca la inicialización; no se utiliza endpoint público. La fila del estado singleton se bloquea con `FOR UPDATE` mientras el coordinador pasa a `INITIALIZING`, verifica el único superusuario y su reserva inicial configurable, asigna créditos a usuarios existentes mediante un marcador inicial único, emite exactamente 100 tokens para cada jugador elegible y publica la cotización inicial mediante el port compartido de publicación. Un fallo deja el estado en `FAILED`; un reintento reanuda los pasos faltantes y puede llegar a `READY` solo después de completar todos los pasos obligatorios. Las asignaciones, emisiones y cotizaciones válidas existentes nunca se restablecen ni duplican.

El registro invoca un hook interno de asignación idempotente utilizando el mismo marcador único para el nuevo usuario. El usuario puede persistirse independientemente de una inicialización fallida, pero no puede operar en el mercado hasta que exista su cuenta/asignación de 1.000 créditos. La activación de un jugador inicialmente inactivo ejecuta el mismo flujo de emisión única. La reactivación encuentra la emisión existente y no crea otra de 100 tokens.

## Contabilidad del portfolio

Para una tenencia abierta, conservar la cantidad actual y el costo total de adquisición pendiente; el promedio ponderado es el costo total dividido por la cantidad para mostrarlo. En una venta parcial, retirar del holding el costo proporcional y registrar la P/L realizada contra el precio exacto de venta. En la venta final, establecer cantidad y costo residual en cero. Conservar la P/L realizada en el historial de transacciones y en un agregado independiente de las tenencias abiertas para que el resumen del portfolio continúe mostrándola después del cierre. La valoración actual y la P/L no realizada se calculan únicamente con una cotización vigente; una cotización histórica puede mostrarse como histórica, pero no tratarse como actual.

## Recursos bloqueables y orden

Para compra y venta, adquirir locks en este orden único:

1. Solicitud idempotente.
2. Estado del mercado con lock compartido para operaciones normales, o exclusivo para inicialización/transición.
3. Fila del jugador del catálogo con lock compartido para validar actividad.
4. Cabecera/coordinación de versión de cotización del jugador.
5. Cuenta de créditos del usuario.
6. Cuenta de créditos del superusuario.
7. Emisión/inventario del jugador.
8. Tenencia del usuario, creando una fila canónica cero bajo una clave única si no existe.

Las operaciones normales conservan los locks compartidos del estado del mercado y del jugador hasta el commit. La inicialización/transición y la activación/desactivación de un jugador usan locks exclusivos, esperan las operaciones activas e impiden nuevos lectores en conflicto sin serializar entre sí las operaciones normales. La solicitud idempotente también posee un advisory lock transaccional de PostgreSQL derivado de su identidad persistente; un ejecutor concurrente no puede procesar la misma solicitud mientras la primera transacción está activa. Si esa transacción hace rollback o el proceso muere, el advisory lock se libera y la fila durable `PROCESSING` puede recuperarse. La publicación/invalidación de cotizaciones utiliza la cabecera en el mismo orden por jugador y confirma atómicamente la nueva versión/cabecera inmutable. Nunca cambia la cotización de una transacción ya aceptada.

## Publicación de cotizaciones y fuente compartida

El `MarketInitializationService` interno es el único sembrador inicial y llama a `QuotePublicationPort.publishInitialQuote` con 1.00 y vigencia no vencible. El port escribe la misma representación de versión inmutable/cabecera que luego utilizará la publicación dinámica de Player Valuation. Token Market posee la orquestación y el consumo de versiones exactas; Player Valuation posee la valoración y la publicación dinámica futura; Player Statistics Sync posee únicamente las estadísticas. No se permite una estructura paralela para el precio inicial.

## Límite con estadísticas y migraciones

El modelo referencia únicamente la identidad estable del jugador/catálogo y su estado activo. No requiere una tabla de estadísticas, payload de WhoScored, resultado de scraping ni migración de Player Statistics Sync. Toda clave foránea o contrato de estado del jugador debe coordinarse con el trabajo existente de catálogo/estadísticas. Flyway V16 permanece provisional hasta reconciliar la migración V15 remota; este documento intencionalmente no contiene SQL.
