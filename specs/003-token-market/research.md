# Investigación: Token Market

## Evidencia del repositorio

- El repositorio utiliza un backend Java 21 con Spring Boot, Gradle, PostgreSQL, Flyway, Spring Data JPA, seguridad JWT como resource server, validación y springdoc OpenAPI.
- El backend sigue la convención de paquetes verificada bajo `com.devchesterunited.devchesterunitedv2`: `adapter`, `configuration`, `controller`, `modelo`, `persistence` y `service`, con los subpaquetes documentados en `plan.md`. El frontend utiliza React/Vite con `api`, `components`, `session`, `styles` y `views`.
- Las responsabilidades existentes de autenticación y catálogo de jugadores permanecen fuera de Token Market. Token Market referencia la identidad estable y el estado activo del jugador; no modifica estadísticas ni realiza scraping de WhoScored.
- La rama actual tiene historial Flyway hasta V14. La rama de estadísticas disponible localmente mediante referencia remota contiene una migración V15, pero no está integrada en esta rama. Por lo tanto, V16 es únicamente una candidata provisional.
- La especificación funcional aprobada es la fuente de verdad. Este documento registra decisiones técnicas necesarias para planificar la implementación sin modificarla.

## Decisión 1: Utilizar las convenciones existentes de la aplicación

Utilizar servicios de aplicación Spring Boot, repositorios, DTOs, controladores, transacciones PostgreSQL, Flyway, resolución del principal JWT y las convenciones existentes de errores/respuestas. Evitar un módulo o framework nuevo porque el repositorio ya posee una estructura de aplicación por capas consistente.

## Decisión 2: Superusuario interno protegido

Representar la contraparte del mercado como exactamente una identidad interna protegida, separada de las cuentas `Usuario` autenticables públicamente. Posee una reserva de créditos y el inventario inicial de tokens de jugadores, pero no tiene contraseña, login JWT, registro público ni identificador seleccionable por el cliente.

Alternativas descartadas:

- Un usuario normal con un rol especial acoplaría la reserva al registro/login público y podría exponerla mediante flujos ordinarios de usuario.
- Un identificador de cuenta hardcodeado no proporcionaría unicidad persistente ni un propietario durable para saldo/inventario.
- Un saldo implícito sin identidad haría menos explícitas las referencias de contraparte y la auditoría de transacciones.

## Decisión 3: Locks deterministas y acotados a recursos

Todas las ejecuciones de compra y venta adquieren recursos bloqueables en este orden:

1. La fila de solicitud idempotente para `(authenticated user, idempotency key)`.
2. La fila del estado operativo del mercado mediante lectura con lock compartido para una operación normal, o lock exclusivo para inicialización/transición de estado.
3. La fila del jugador del catálogo seleccionado mediante lectura con lock compartido para coordinar el estado activo/inactivo.
4. La cotización vigente/cabecera del jugador seleccionado.
5. La fila de cuenta de créditos del usuario autenticado.
6. La fila de cuenta de créditos del superusuario protegido.
7. La fila de emisión/inventario del jugador seleccionado.
8. La fila de tenencia del usuario autenticado para ese jugador.

Compra y venta utilizan el mismo orden, incluso cuando no existe una fila de tenencia. Una tenencia faltante se representa mediante un flujo seguro de inicialización que crea o bloquea una fila canónica usuario/jugador bajo una restricción de unicidad y luego reintenta la operación ordenada; nunca depende de una comprobación desprotegida solo en la aplicación. Una transacción que debe crear la fila contempla el conflicto de unicidad y reintenta después de una inserción concurrente.

No existe un lock exclusivo global para las operaciones normales. Una operación normal toma un lock compartido de PostgreSQL sobre la fila singleton del estado del mercado y lo conserva hasta el commit de su transacción breve; los locks compartidos son compatibles entre operaciones, pero bloquean una transición de estado exclusiva. La inicialización o una transición toma el lock exclusivo de la fila de estado, espera las operaciones en curso, cambia el estado/época e impide nuevos lectores con lock compartido hasta el commit de la transición. Así, una operación no puede confirmar contra un estado cambiado concurrentemente sin serializar las operaciones normales entre sí.

El propietario del catálogo cambia el estado activo/inactivo bajo un lock exclusivo de la fila del jugador. Una operación toma un lock compartido de esa misma fila antes de validar el estado activo y lo conserva hasta el commit. Si la desactivación gana primero, la operación observa el jugador inactivo y rechaza; si la operación gana primero, la desactivación espera y se aplica después. El mercado no modifica el estado del catálogo. La publicación/invalidación de cotizaciones bloquea el recurso de coordinación de la cabecera del jugador y actualiza atómicamente la versión inmutable/cabecera. La operación valida la versión exacta después de adquirir ese lock y nunca sustituye una versión nueva. Las posiciones de la fila del jugador y de la cabecera de cotización son fijas en el orden anterior para evitar ciclos.

## Decisión 4: Idempotencia y recuperación persistentes

La clave de idempotencia es única por usuario autenticado. La solicitud almacenada incluye un hash de parámetros normalizados y un estado como `PROCESSING`, `CONFIRMED`, `REJECTED` o `CONFLICT`.

El flujo es:

1. Una transacción breve de reclamación inserta la solicitud como `PROCESSING` con un token de ejecución único. Una solicitud `CONFIRMED` o `REJECTED` idéntica devuelve su resultado almacenado. Un hash diferente para la misma clave devuelve conflicto. La reclamación es durable para que un proceso caído deje una solicitud recuperable.
2. El ejecutor inicia inmediatamente la transacción de negocio y obtiene un advisory lock transaccional de PostgreSQL derivado de la identidad persistida de la solicitud; luego bloquea la fila de solicitud. Un ejecutor concurrente intenta el advisory lock sin bloqueo: si el primer ejecutor aún posee la transacción, devuelve `PROCESSING` y no puede ejecutar. Si el primer proceso cayó, PostgreSQL libera el lock transaccional; el reintento lo adquiere, bloquea la fila y toma la solicitud aún `PROCESSING`. El token de propietario se comprueba/actualiza condicionalmente mientras se mantiene el lock de fila.
3. El único ejecutor ejecuta la transacción de negocio ordenada. La cotización aceptada, los movimientos de saldo/tokens, la actualización de tenencias/costos, la transacción confirmada inmutable y el resultado final de idempotencia se confirman juntos. Ningún ejecutor puede realizar movimientos sin mantener el lock transaccional de la solicitud.
4. Un fallo de validación funcional se confirma como `REJECTED` con un motivo no sensible y sin movimientos. No es una operación confirmada.
5. Un rollback técnico no deja movimientos ni transacción confirmada. La solicitud durable permanece `PROCESSING`; después de liberar el advisory lock transaccional, un reintento puede tomarla de forma segura. Un timeout mientras la primera transacción de base de datos sigue activa no puede ser tomado por otro ejecutor porque el advisory lock está retenido; el reintento informa pendiente o espera según la política de API. Después de una caída de conexión/proceso, el lock se libera y el siguiente reintento recupera el resultado o vuelve a ejecutar atómicamente.
6. Una respuesta perdida se recupera repitiendo la misma clave. Una respuesta no se declara exitosa hasta completar el commit. El advisory lock es transaccional y por eso no puede sobrevivir a una sesión de base de datos caída; no existe una ventana de expiración de lease en la que dos ejecutores activos puedan mover saldos o tokens simultáneamente.

Alternativas descartadas:

- La idempotencia en memoria no sobrevive al reinicio ni coordina múltiples instancias.
- Marcar una solicitud como confirmada antes de confirmar los movimientos puede exponer un éxito falso.
- Mantener una transacción de base de datos larga durante toda la solicitud del cliente aumentaría la duración de los locks y el riesgo de deadlock; la transacción queda limitada al ejecutor de negocio y no espera al cliente.

## Decisión 5: Barrera transaccional del estado del mercado

La fila singleton del estado del mercado es una barrera de locks compartidos, no un lock exclusivo global para cada operación. Cada operación normal la lee con `FOR SHARE` y verifica `READY` más la época actual. La inicialización y los cambios explícitos de estado utilizan `FOR UPDATE`, atraviesan `INITIALIZING`/`FAILED`/`READY` y confirman el cambio de época solo después de completar su trabajo idempotente. Como los lectores compartidos y la transición exclusiva se superponen durante toda la transacción breve, ninguna operación puede confirmar mientras una transición modifica el estado operativo. Una inicialización fallida deja el mercado no operativo, pero las funcionalidades independientes de la aplicación pueden continuar.

## Decisión 6: Un contrato y una fuente de verdad para cotizaciones

La cotización inicial de 1.00 crédito se publica mediante el mismo almacén/contrato de cotizaciones versionado que consume Token Market y que posteriormente utilizará Player Valuation. Es una versión de cotización inmutable con estado válido y sin vencimiento automático a los siete días. Una cotización posterior de Player Valuation reemplaza la cabecera actual al publicarse; no migra datos desde una tabla paralela de precio inicial.

Player Valuation es responsable del cálculo, la selección de estrategia, la semántica de publicación, la política de vigencia de cotizaciones dinámicas y los registros de fallos de recálculo. Token Market consume y valida versiones exactas, almacena la referencia de cotización en el historial confirmado y nunca calcula ni sustituye precios. El publicador inicial concreto es el `MarketInitializationService` interno, invocado por un coordinador automático de disponibilidad de la aplicación; llama al `QuotePublicationPort.publishInitialQuote(playerId, 1.00, nonExpiring)` para cada jugador elegible. El port es implementado por el único adaptador de persistencia de cotizaciones y será también el límite utilizado por Player Valuation para publicaciones dinámicas futuras. Esto es siembra, no valoración: el inicializador no contiene estadísticas, scraping ni estrategias de cálculo.

## Decisión 7: Inicialización y recuperación automáticas

Un coordinador interno de inicio escucha la disponibilidad de la aplicación e invoca el servicio de inicialización; no existe endpoint público ni acción manual del usuario. El servicio bloquea la fila singleton del estado del mercado, pasa a `INITIALIZING` y ejecuta pasos idempotentes: crear/verificar el único superusuario protegido y su reserva inicial configurable; asignar exactamente 1.000 créditos a los usuarios preexistentes mediante un marcador de asignación único; garantizar la emisión única de 100 tokens y el inventario del superusuario para los jugadores activos; invocar la publicación de la cotización inicial compartida para los jugadores elegibles. Un jugador inicialmente inactivo recibe su emisión y cotización inicial cuando se vuelve elegible por primera vez; una reactivación encuentra la emisión existente y nunca emite otra.

El registro nuevo utiliza un hook interno posterior al registro con un marcador único `(user, initial-grant)` y reintento. El registro del usuario puede confirmarse independientemente, mientras que las operaciones de mercado permanecen deshabilitadas para ese usuario hasta que exista la asignación. Esto conserva el requisito de que el registro reciba exactamente 1.000 créditos sin hacer que la autenticación independiente dependa de un fallo de inicialización del mercado. Los reinicios, reintentos y eventos de activación concurrentes utilizan restricciones de unicidad y locks de filas, por lo que cada asignación, emisión y versión inicial de cotización se crea una sola vez.

Si la inicialización falla, el estado es `FAILED` y el trading se rechaza. Un inicio/reintento posterior vuelve a adquirir el lock de estado, reanuda únicamente los pasos faltantes y vuelve a `READY` solo después de completar la reserva, las asignaciones, las emisiones y las cotizaciones iniciales requeridas. El inicializador nunca restablece saldos existentes, vuelve a emitir tokens ni reemplaza una cotización inicial ya válida. La publicación dinámica de Player Valuation espera la misma coordinación de la cabecera de cotización y puede reemplazar la inicial únicamente mediante el límite de publicación compartido.

## Decisión 8: Representación contable

Los saldos de créditos y precios utilizan semántica monetaria visible con dos decimales. El costo de adquisición de las tenencias utiliza precisión interna suficiente para los cálculos de promedio ponderado. En una venta parcial, el costo retirado es proporcional a la cantidad vendida; en la venta final, cantidad y costo residual quedan en cero. La ganancia/pérdida realizada se conserva en el historial confirmado y en un agregado de cuenta para que siga visible después del cierre de una posición.

## Decisión 9: Coordinación de Flyway

No asumir que V15 está disponible en esta rama. Antes de implementar, comparar la migración final de estadísticas con esta rama y coordinar una decisión ordenada de integración. V16 es una candidata provisional para la primera migración de Token Market, no una versión reservada. Este Plan no genera SQL ni migraciones.

## Decisión 10: Estrategia de verificación

Utilizar pruebas unitarias para cálculos y transiciones de dominio, MockMvc para el comportamiento de API autenticada, PostgreSQL/Testcontainers para restricciones/locks/rollback/concurrencia y las convenciones de pruebas frontend existentes para los estados de UI. El escenario de cuatro usuarios/cinco jugadores utiliza datos controlados de demostración. La integración con estadísticas reales y Player Valuation dinámico es una verificación posterior entre features, no parte de la lógica de cálculo de Token Market.

## Coordinación pendiente

- Confirmar los contenidos finales de V15 y el orden de migraciones antes de seleccionar un número real de migración.
- Acordar con Player Valuation el límite compartido de publicación de cotizaciones, identificadores, vocabulario de estados de vigencia y ubicación del adaptador; el disparador inicial queda fijado aquí como el coordinador automático de `MarketInitializationService`.
- Confirmar el identificador estable del jugador y el contrato de estado activo expuestos por el trabajo de catálogo/estadísticas sin modificar esa feature.
