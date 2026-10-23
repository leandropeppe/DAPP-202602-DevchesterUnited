# Especificación de Funcionalidad: Token Market

**Rama de Funcionalidad**: `feature/token-market`

**Creado**: 2026-10-10

**Estado**: Borrador

**Entrada**: Descripción del usuario: "Crear el mercado de tokens de jugadores con créditos virtuales, emisión inicial, compras, ventas, portfolio e historial de transacciones, integrado mediante contrato con Player Valuation y sin duplicar Player Statistics Sync."

## Escenarios de usuario y pruebas *(obligatorio)*

### Historia de usuario 1 - Inicializar el mercado de forma segura (Prioridad: P1)

Como responsable de la plataforma, quiero que el mercado se inicialice automáticamente mediante un proceso interno, para habilitar una única economía de créditos y una emisión inicial controlada sin intervención manual ni duplicaciones.

**Por qué esta prioridad**: La inicialización es un prerrequisito para todas las operaciones y debe proteger el suministro inicial de créditos y tokens.

**Prueba independiente**: Con un catálogo que contenga jugadores activos e inactivos y usuarios nuevos y existentes, ejecutar la inicialización varias veces y reiniciar el sistema; comprobar que las identidades, créditos y emisiones iniciales se mantienen únicas, que los jugadores elegibles reciben exactamente 100 tokens y que el mercado queda bloqueado si la inicialización obligatoria no termina correctamente.

**Escenarios de aceptación**:

1. **Given** que el mercado aún no fue inicializado, **When** se ejecuta el proceso interno con la configuración inicial por defecto, **Then** se crea una única identidad interna protegida de superusuario con 1.000.000 de créditos y cada jugador activo recibe una emisión inicial de 100 tokens propiedad del superusuario.
2. **Given** que existe una configuración de reserva inicial válida antes de la primera inicialización, **When** se inicializa el mercado, **Then** la reserva utiliza ese valor y los cambios posteriores de configuración no alteran el saldo ya inicializado.
3. **Given** usuarios registrados antes y después de habilitar el mercado, **When** se completa la inicialización y se procesan nuevos registros, **Then** cada usuario recibe exactamente 1.000 créditos una sola vez.
4. **Given** un jugador inicialmente inactivo, **When** se activa por primera vez, **Then** recibe exactamente una emisión inicial de 100 tokens; posteriores desactivaciones y reactivaciones no generan emisiones adicionales.
5. **Given** que la inicialización falla o queda incompleta, **When** un usuario intenta comprar o vender, **Then** la operación se rechaza y no se transfieren créditos ni tokens.
6. **Given** que la inicialización se repite o el sistema se reinicia, **When** el proceso interno vuelve a ejecutarse, **Then** no se duplican la identidad de superusuario, los créditos iniciales, las emisiones ni las asignaciones.

---

### Historia de usuario 2 - Consultar el estado operativo del mercado (Prioridad: P1)

Como usuario autenticado, quiero consultar mi saldo, los jugadores operables, los tokens disponibles y sus cotizaciones, para conocer las condiciones antes de confirmar una operación.

**Por qué esta prioridad**: El usuario necesita información consistente del mercado para tomar decisiones y evitar confirmar operaciones que no pueden ejecutarse.

**Prueba independiente**: Consultar el estado antes y después de una compra, durante una inicialización incompleta y con jugadores activos e inactivos; comprobar que el usuario solo ve su propio saldo y que la información distingue cotizaciones vigentes de precios históricos.

**Escenarios de aceptación**:

1. **Given** un usuario autenticado y un mercado inicializado, **When** consulta su estado, **Then** obtiene únicamente su saldo, los jugadores activos operables, la disponibilidad de tokens del superusuario y la cotización vigente o la indicación de que no existe.
2. **Given** un jugador inactivo con tenencias existentes, **When** el usuario consulta el mercado, **Then** el jugador no aparece como comprable ni vendible, pero sus tenencias y la última cotización conocida pueden identificarse como históricas cuando no estén vigentes.
3. **Given** que el mercado no terminó su inicialización, **When** se consulta su estado, **Then** el sistema informa que no está operativo para transacciones y no presenta la situación como disponibilidad normal.

---

### Historia de usuario 3 - Comprar tokens de un jugador activo (Prioridad: P1)

Como usuario autenticado, quiero comprar una cantidad entera de tokens de un jugador activo usando mis créditos y una cotización aceptada, para incorporarlos a mi portfolio.

**Por qué esta prioridad**: La compra es la operación principal mediante la cual un usuario adquiere una posición en el mercado.

**Prueba independiente**: Con mercado inicializado, saldo suficiente, inventario disponible y una cotización vigente, confirmar compras completas y verificar la transferencia de créditos, tokens, costo de adquisición y registro de la operación.

**Escenarios de aceptación**:

1. **Given** un usuario autenticado, un jugador activo, tokens disponibles, créditos suficientes y una cotización vigente, **When** confirma una cantidad entera positiva con su versión exacta y una clave de idempotencia válida, **Then** los créditos se transfieren al superusuario, los tokens al usuario, se actualiza su costo de adquisición y se registra una compra confirmada.
2. **Given** una solicitud de compra que no cumple una validación, **When** se procesa, **Then** se rechaza la solicitud completa sin transferencias parciales ni registro como compraventa confirmada.
3. **Given** que la cotización aceptada fue reemplazada, invalidada o venció antes de ejecutar la operación, **When** se procesa la compra, **Then** se rechaza aunque el precio numérico nuevo sea igual o más favorable.
4. **Given** que el usuario repite una solicitud idéntica con la misma clave de idempotencia, **When** se procesa el reintento, **Then** se recupera el resultado original sin repetir la transferencia.

---

### Historia de usuario 4 - Vender tokens de un jugador activo (Prioridad: P1)

Como usuario autenticado, quiero vender una cantidad entera positiva de tokens que poseo al superusuario, para recuperar créditos virtuales al precio de una cotización aceptada.

**Por qué esta prioridad**: La venta permite cerrar o reducir posiciones y materializar resultados económicos.

**Prueba independiente**: Con una posición abierta, reserva suficiente del superusuario y cotización vigente, ejecutar ventas completas y parciales; verificar la transferencia inversa, la actualización del costo restante y la ganancia o pérdida realizada.

**Escenarios de aceptación**:

1. **Given** un usuario con suficientes tokens de un jugador activo, una reserva del superusuario suficiente y una cotización vigente, **When** confirma una venta con cantidad positiva, versión exacta y clave válida, **Then** transfiere los tokens al superusuario, recibe el importe total y se registra la venta confirmada.
2. **Given** que la reserva del superusuario no alcanza para pagar la venta, **When** se procesa, **Then** se rechaza la solicitud completa sin transferencias parciales.
3. **Given** que el usuario solicita vender más tokens de los que posee, **When** se procesa, **Then** se rechaza la solicitud sin dejar tenencias negativas.
4. **Given** que el usuario vende parte de una posición, **When** la venta se confirma, **Then** se conserva la posición restante y se actualiza su costo de adquisición mediante promedio ponderado móvil.
5. **Given** que el usuario vende la última unidad de una posición, **When** la venta se confirma, **Then** la posición deja de estar abierta y no quedan residuos de costo asociados.

---

### Historia de usuario 5 - Consultar el portfolio y la rentabilidad (Prioridad: P2)

Como usuario autenticado, quiero consultar exclusivamente mis posiciones abiertas y su rentabilidad, para conocer el valor actual de mis tenencias y sus resultados acumulados.

**Por qué esta prioridad**: El portfolio transforma las operaciones individuales en una visión útil de la posición del usuario.

**Prueba independiente**: Crear posiciones mediante compras y ventas parciales, consultar el portfolio con cotizaciones vigentes y ausentes, y verificar cantidades, costo promedio, valor actual y resultados sin exponer posiciones de otros usuarios.

**Escenarios de aceptación**:

1. **Given** que el usuario posee al menos un token de un jugador, **When** consulta su portfolio, **Then** se muestra una posición abierta con jugador, cantidad, costo promedio ponderado móvil, costo total pendiente y cotización vigente si existe.
2. **Given** una posición con cotización vigente, **When** se calcula el portfolio, **Then** se muestra su valor actual y la ganancia o pérdida no realizada.
3. **Given** que falta una cotización vigente, **When** se consulta el portfolio, **Then** la última cotización conocida se identifica como histórica y no se presenta una rentabilidad actual completa como si fuera vigente.
4. **Given** que una posición fue vendida completamente, **When** se consulta el portfolio, **Then** deja de aparecer como posición abierta, pero sus resultados históricos permanecen disponibles en el historial.
5. **Given** que el usuario cerró completamente una o más posiciones, **When** consulta el resumen de su portfolio, **Then** las ganancias y pérdidas realizadas acumuladas de esas posiciones continúan disponibles aunque ya no existan como posiciones abiertas.
6. **Given** que el usuario consulta el portfolio, **When** la solicitud se procesa, **Then** no puede acceder a saldos, posiciones ni rentabilidad de otro usuario.

---

### Historia de usuario 6 - Consultar transacciones confirmadas y auditoría (Prioridad: P2)

Como usuario autenticado, quiero consultar mis transacciones confirmadas de forma paginada y filtrable, para revisar mis operaciones y resultados históricos.

**Por qué esta prioridad**: El historial permite auditar las operaciones realizadas sin confundirlas con intentos rechazados o solicitudes todavía inciertas.

**Prueba independiente**: Ejecutar compras y ventas confirmadas, intentos rechazados y solicitudes pendientes; verificar que el historial personal solo contiene operaciones confirmadas, ordenadas de la más reciente a la más antigua y filtrables por jugador, tipo y fechas.

**Escenarios de aceptación**:

1. **Given** transacciones confirmadas del usuario, **When** consulta su historial, **Then** recibe únicamente sus operaciones confirmadas, ordenadas de la más reciente a la más antigua, con paginación y filtros disponibles.
2. **Given** un intento rechazado, **When** se procesa, **Then** no se crea una compraventa confirmada y el intento se conserva exclusivamente en una auditoría separada de acceso interno autorizado.
3. **Given** una solicitud pendiente o incierta, **When** el usuario consulta su referencia de solicitud, **Then** obtiene su estado o resultado sin que aparezca como transacción confirmada.
4. **Given** un registro de transacción confirmado, **When** se consulta, **Then** conserva jugador, nombre al momento de operar, tipo, cantidad, precio, importe, contraparte superusuario, versión de cotización, referencia de idempotencia y ganancia o pérdida realizada cuando corresponda.

---

### Historia de usuario 7 - Recuperar solicitudes idempotentes y concurrentes (Prioridad: P1)

Como usuario autenticado, quiero recuperar el resultado de una solicitud de compra o venta mediante la misma clave de idempotencia, para poder reintentar de forma segura ante una respuesta perdida o incierta.

**Por qué esta prioridad**: La idempotencia y la consistencia concurrente protegen los créditos, tokens y tenencias contra doble gasto y operaciones duplicadas.

**Prueba independiente**: Repetir solicitudes idénticas, reutilizar una clave con parámetros distintos y ejecutar operaciones concurrentes sobre el mismo saldo e inventario; verificar resultados únicos, conflictos y ausencia de sobregiros o tenencias negativas.

**Escenarios de aceptación**:

1. **Given** una operación previamente confirmada, **When** el usuario reintenta con la misma clave y los mismos parámetros, **Then** se recupera el resultado original sin repetir transferencias.
2. **Given** una clave ya utilizada con parámetros distintos, **When** se reutiliza, **Then** la solicitud se rechaza por conflicto y no modifica saldos ni tenencias.
3. **Given** una operación aún en proceso, **When** se consulta o reintenta con la misma clave, **Then** se informa que está pendiente sin declarar una nueva operación.
4. **Given** operaciones concurrentes que compiten por los mismos créditos, tokens o reserva del superusuario, **When** se ejecutan, **Then** el resultado conjunto no produce doble gasto, saldos negativos, tenencias negativas, emisión adicional ni inventario total superior a 100 tokens por jugador.

---

### Historia de usuario 8 - Operar con cotizaciones publicadas (Prioridad: P1)

Como mercado, quiero consumir cotizaciones publicadas mediante un contrato único y versionado, para ejecutar operaciones con el precio exacto que el usuario aceptó sin duplicar la responsabilidad de Player Valuation.

**Por qué esta prioridad**: La integridad del precio aplicado es necesaria para que compras, ventas, portfolios e historial sean reproducibles.

**Prueba independiente**: Publicar una cotización inicial de 1,00 crédito mediante el mismo contrato y fuente de verdad que las cotizaciones futuras de Player Valuation, reemplazarla por una cotización posterior, invalidarla y dejarla vencer según una política dinámica; comprobar qué operaciones se aceptan, cuáles se rechazan y cómo se muestra un precio histórico.

**Escenarios de aceptación**:

1. **Given** que aún no existe una cotización dinámica, **When** se inicializa el mercado, **Then** se persiste una cotización inicial de 1,00 crédito por token utilizando el mismo contrato funcional y la misma fuente de verdad que las futuras cotizaciones de Player Valuation; la responsabilidad técnica de publicarla se resolverá en Plan sin crear estructuras paralelas de precios.
2. **Given** una cotización inicial no reemplazada ni invalidada, **When** transcurren siete días, **Then** continúa vigente y no caduca automáticamente por ese solo motivo.
3. **Given** una cotización publicada, **When** el usuario confirma una operación, **Then** la solicitud identifica el jugador, la versión, el precio unitario y la aceptación de esa versión.
4. **Given** que la versión aceptada fue reemplazada, invalidada o venció, **When** el backend ejecuta la operación, **Then** rechaza toda la operación y no sustituye automáticamente el precio aceptado.
5. **Given** que faltan estadísticas nuevas o falla un recálculo de Player Valuation, **When** existe una cotización válida según su política de vigencia, **Then** el mercado puede continuar utilizando esa última cotización válida sin inventar estadísticas ni precios dinámicos.
6. **Given** que no existe ninguna cotización vigente, **When** el usuario intenta comprar o vender, **Then** la operación se rechaza y un precio histórico solo puede mostrarse como histórico.

---

### Historia de usuario 9 - Utilizar la interfaz mínima del mercado (Prioridad: P2)

Como usuario autenticado, quiero consultar y operar sobre el mercado desde la interfaz web, para completar el flujo sin acceder directamente a la persistencia ni a proveedores externos.

**Por qué esta prioridad**: La interfaz permite validar el uso del mercado como producto, manteniendo las reglas de negocio en el backend.

**Prueba independiente**: Con el mercado inicializado, iniciar sesión, consultar saldo y jugadores, comprar, vender, revisar portfolio e historial, y comprobar mensajes claros para cotizaciones ausentes, precios históricos, rechazos y estados pendientes.

**Escenarios de aceptación**:

1. **Given** un usuario autenticado, **When** abre el mercado, **Then** puede consultar saldo, jugadores activos, precio vigente y tokens disponibles del superusuario.
2. **Given** una operación válida, **When** confirma una compra o venta en la interfaz, **Then** puede revisar cantidad, precio, versión e importe antes de enviarla y observa el resultado devuelto.
3. **Given** una operación rechazada, pendiente o incierta, **When** la interfaz recibe el estado, **Then** muestra un mensaje comprensible y permite recuperar la solicitud sin presentar una compraventa no confirmada como exitosa.
4. **Given** un usuario autenticado, **When** consulta portfolio o historial, **Then** la interfaz muestra únicamente sus propios datos y respeta paginación y filtros.

---

### Historia de usuario 10 - Verificar el escenario reproducible de demostración (Prioridad: P3)

Como responsable del producto, quiero ejecutar una demostración controlada con cuatro usuarios y cinco jugadores, para verificar el ciclo completo de inicialización, compra, valoración, venta y rentabilidad.

**Por qué esta prioridad**: La demostración valida la integración funcional entre Token Market, Player Valuation y el catálogo sin fijar todavía la preparación técnica de fixtures o seeds.

**Prueba independiente**: Ejecutar el escenario con datos claramente identificados como prueba, registrar compras iniciales, publicar cotizaciones posteriores distintas, comparar portfolios, realizar ventas y verificar ganancias o pérdidas realizadas y no realizadas; cuando estén disponibles, repetir la verificación de integración utilizando estadísticas reales de Player Statistics Sync y cotizaciones publicadas por Player Valuation, sin incorporar esas responsabilidades a Token Market.

**Escenarios de aceptación**:

1. **Given** cuatro usuarios y cinco jugadores de prueba separados de los datos reales, **When** se inicializa el mercado y se realizan compras con cotizaciones iniciales, **Then** se pueden comparar los portfolios resultantes sin duplicar emisiones ni créditos.
2. **Given** cotizaciones posteriores diferentes publicadas por Player Valuation, **When** se consultan portfolios y se realizan ventas, **Then** los valores actuales, resultados realizados y resultados no realizados reflejan las versiones de cotización utilizadas.
3. **Given** que finaliza la demostración controlada, **When** se consultan los historiales, **Then** las transacciones confirmadas, referencias de cotización y resultados son reproducibles, y los datos de prueba permanecen identificados y separados de los reales.
4. **Given** que Player Statistics Sync y Player Valuation están disponibles, **When** se ejecuta la verificación de integración con estadísticas reales y cotizaciones publicadas por esas features, **Then** Token Market consume sus contratos sin realizar scraping, persistir estadísticas ni calcular precios internamente.

### Historia de usuario 11 - Consultar un portfolio totalizado y operarlo visualmente (Prioridad: P2)

Como usuario autenticado, quiero ver un resumen completo de mi cartera, identificar cada jugador por su nombre y vender desde mis posiciones, para controlar mis tenencias sin volver al listado general del mercado.

**Prueba independiente**: Consultar una cartera con varias posiciones, cotizaciones actuales e historicas, vender parcial y totalmente desde el portfolio y comprobar que los totales, el saldo, las posiciones y el historial conservan la semantica contable existente.

**Escenarios de aceptacion**:

1. **Given** una cartera con posiciones abiertas, **When** el usuario consulta el portfolio, **Then** observa jugadores distintos, tokens totales, costo pendiente total, valor actual total cuando pueda determinarse y P/L realizada, no realizada y combinada segun la completitud de las valuaciones.
2. **Given** posiciones con datos descriptivos canonicos, **When** se muestran, **Then** cada posicion presenta nombre real y equipo/liga cuando esten disponibles, conservando el UUID para identificacion tecnica.
3. **Given** una posicion con cotizacion vigente, **When** el usuario inicia una venta desde el portfolio, **Then** se reutiliza el formulario de operacion existente con la cotizacion exacta, la version y un maximo de `min(100, tokens disponibles)`.
4. **Given** que una posicion no tiene cotizacion operable, **When** se muestra, **Then** no ofrece una venta ejecutable ni transforma una cotizacion historica o ausente en actual.
5. **Given** operaciones confirmadas de compra y venta, **When** el usuario consulta los graficos, **Then** la interfaz visualiza solamente datos propios confirmados y no trata una pagina parcial del historial como el historial completo.
6. **Given** una cotizacion desconocida o una respuesta incompleta, **When** se calculan resumen y graficos, **Then** los valores insuficientes permanecen null o se excluyen explicitamente, nunca se presentan como cero sin explicacion.

### Casos límite

- El registro público intenta crear o seleccionar la identidad de superusuario.
- La configuración de reserva cambia después de la primera inicialización.
- El proceso de inicialización se reinicia o se ejecuta concurrentemente.
- Un usuario existente recibe créditos más de una vez.
- Un jugador pasa de activo a inactivo durante una compra o venta.
- Un jugador inactivo se reactiva después de haber recibido su emisión inicial.
- La cantidad solicitada es cero, negativa, fraccionaria o no representable como unidad entera.
- La compra excede créditos o inventario disponible.
- La venta excede tenencias o la reserva de créditos del superusuario.
- Una operación intenta superar el máximo de 100 tokens emitidos por jugador.
- La cotización fue reemplazada por otra con el mismo precio numérico.
- La cotización inicial tiene más de siete días, pero no fue reemplazada ni invalidada.
- La cotización vigente vence entre la confirmación del usuario y la ejecución.
- No existe cotización vigente, aunque exista un precio histórico.
- Faltan estadísticas nuevas o falla un recálculo de Player Valuation.
- Se reutiliza una clave de idempotencia con parámetros distintos.
- Se pierde la respuesta después de una transferencia confirmada.
- Una operación permanece pendiente o su resultado no puede determinarse inmediatamente.
- Dos operaciones concurrentes compiten por el mismo saldo, inventario o reserva.
- Se vende parcialmente una posición y luego se vende su última unidad.
- Se consulta el portfolio o historial de otro usuario.
- Un intento rechazado se confunde con una transacción confirmada.
- Un nombre de jugador cambia después de una operación, pero el historial debe conservar el nombre al momento de operar.

## Requisitos *(obligatorio)*

### Requisitos funcionales

- **FR-001**: El sistema DEBE mantener una única identidad interna y protegida de superusuario, que NO DEBE poder crearse, seleccionarse ni operarse mediante el registro público de usuarios.
- **FR-002**: El sistema DEBE inicializar el mercado automáticamente mediante un proceso interno sin endpoint público ni intervención manual.
- **FR-003**: El sistema DEBE asignar a la reserva inicial del superusuario 1.000.000 de créditos por defecto, permitiendo configurar otro valor antes de la primera inicialización.
- **FR-004**: El sistema NO DEBE modificar la reserva ya inicializada cuando cambie posteriormente la configuración inicial.
- **FR-005**: El sistema DEBE otorgar exactamente 1.000 créditos iniciales a cada usuario nuevo y a cada usuario registrado previamente que aún no los haya recibido, una sola vez por usuario.
- **FR-006**: El sistema DEBE emitir exactamente 100 tokens enteros por cada jugador elegible y asignarlos inicialmente al superusuario.
- **FR-007**: Los jugadores activos al inicializar el mercado y los jugadores que se vuelvan activos posteriormente DEBEN recibir su emisión inicial una sola vez.
- **FR-008**: Los jugadores inicialmente inactivos NO DEBEN recibir tokens antes de su primera activación, y una desactivación o reactivación posterior NO DEBE generar una emisión adicional.
- **FR-009**: La inicialización DEBE ser idempotente y NO DEBE duplicar identidades, créditos, tokens ni emisiones al repetirse o después de reiniciar el sistema.
- **FR-010**: Si la inicialización obligatoria falla o queda incompleta, el sistema DEBE impedir compras y ventas sin impedir necesariamente otras funcionalidades independientes.
- **FR-011**: Los tokens DEBEN ser unidades enteras estrictamente positivas; NO DEBEN permitirse fracciones.
- **FR-012**: Los créditos y precios monetarios DEBEN expresarse con dos decimales para el comportamiento visible al usuario.
- **FR-013**: Las compras y ventas DEBEN realizarse exclusivamente contra el superusuario; NO DEBE existir mercado entre usuarios, libro de órdenes ni emparejamiento de contrapartes.
- **FR-014**: Una operación DEBE ejecutarse inmediatamente cuando cumpla todas sus validaciones y NO DEBE ejecutarse parcialmente por una cantidad distinta de la solicitada.
- **FR-015**: Todas las transferencias de créditos y tokens DEBEN ser atómicas y consistentes frente a solicitudes concurrentes.
- **FR-016**: El sistema NO DEBE permitir gastar más créditos disponibles, vender más tokens poseídos, producir saldos o tenencias negativas ni superar 100 tokens emitidos por jugador.
- **FR-017**: Los jugadores inactivos DEBEN conservar tenencias e historial, pero NO DEBEN poder comprarse ni venderse mientras estén inactivos.
- **FR-018**: La reactivación de un jugador NO DEBE generar nuevas emisiones y DEBE permitir reanudar operaciones únicamente si existe una cotización vigente.
- **FR-019**: Token Market DEBE consumir cotizaciones publicadas por Player Valuation mediante un contrato funcional único que identifique jugador, versión, precio por token, fecha, vigencia y estrategia utilizada cuando corresponda.
- **FR-020**: La cotización inicial de 1,00 crédito por token DEBE persistirse utilizando el mismo contrato funcional y la misma fuente de verdad que las futuras cotizaciones de Player Valuation; la responsabilidad técnica de su publicación se resolverá en Plan y NO DEBE crear estructuras paralelas de precios.
- **FR-021**: La cotización inicial NO DEBE caducar automáticamente después de siete días y DEBE permanecer válida hasta ser reemplazada o invalidada explícitamente.
- **FR-022**: La política de vigencia de cotizaciones dinámicas DEBE poder configurarse, y una última cotización válida podrá utilizarse ante falta de estadísticas nuevas o fallo de recálculo conforme a dicha política.
- **FR-023**: El sistema NO DEBE inventar estadísticas ni cotizaciones dinámicas; si no existe cotización vigente, DEBE rechazar compras y ventas.
- **FR-024**: Un precio conocido cuya cotización no esté vigente PODRÁ mostrarse como histórico, pero NO DEBE utilizarse como precio operativo.
- **FR-025**: El usuario DEBE aceptar una versión específica de cotización al confirmar una compra o venta, y el backend DEBE verificar que esa misma versión continúe vigente al ejecutar la operación.
- **FR-026**: Si la versión aceptada fue reemplazada, invalidada o venció, el sistema DEBE rechazar la operación completa, aun cuando el nuevo precio numérico sea igual o más favorable.
- **FR-027**: Player Valuation DEBE ser responsable de algoritmos, estrategias, ponderaciones, recálculos, histórico de cotizaciones y registro de fallos de valoración; Token Market NO DEBE asumir esas responsabilidades.
- **FR-028**: Player Statistics Sync DEBE ser responsable de obtener y persistir estadísticas; Token Market NO DEBE duplicar scraping, sincronización, staging ni persistencia de estadísticas.
- **FR-029**: Una compra DEBE requerir identidad autenticada, jugador activo, tokens emitidos y disponibles, cantidad entera positiva, créditos suficientes, versión exacta de cotización vigente y clave de idempotencia válida.
- **FR-030**: Una compra confirmada DEBE transferir créditos al superusuario, transferir tokens al usuario, actualizar su costo de adquisición y generar un registro de transacción confirmada.
- **FR-031**: Una venta DEBE requerir identidad autenticada, jugador activo, cantidad entera positiva no superior a las tenencias, reserva suficiente del superusuario, versión exacta vigente y clave de idempotencia válida.
- **FR-032**: Una venta confirmada DEBE transferir tokens al superusuario, acreditar el importe total al usuario, actualizar el costo de adquisición restante y registrar la ganancia o pérdida realizada.
- **FR-033**: El sistema DEBE calcular el costo de adquisición mediante promedio ponderado móvil con precisión interna suficiente para evitar errores de redondeo.
- **FR-034**: Al vender la última unidad de una posición, el sistema DEBE cerrar la posición y eliminar cualquier costo residual asociado.
- **FR-035**: El sistema DEBE exigir una clave de idempotencia para cada solicitud de compra o venta.
- **FR-036**: La misma clave, usuario, operación y parámetros DEBEN recuperar el resultado original sin repetir transferencias.
- **FR-037**: Una clave reutilizada con parámetros distintos DEBE rechazarse por conflicto y NO DEBE modificar saldos, tenencias ni historial confirmado.
- **FR-038**: Las solicitudes pendientes o inciertas DEBEN poder consultarse o recuperarse mediante la misma clave de idempotencia, sin presentarse como compraventas confirmadas antes de determinar su resultado.
- **FR-039**: El portfolio DEBE mostrar exclusivamente posiciones abiertas del usuario autenticado, con jugador, cantidad, costo promedio, costo total pendiente y cotización vigente cuando esté disponible.
- **FR-040**: El portfolio DEBE mostrar ganancia o pérdida no realizada cuando la posición tenga cotización vigente y NO DEBE presentar una rentabilidad total incompleta como si fuera actual cuando falte una cotización vigente.
- **FR-041**: El portfolio DEBE incluir ganancias y pérdidas realizadas acumuladas y un resultado combinado únicamente cuando las posiciones puedan valorarse correctamente.
- **FR-042**: Las posiciones completamente vendidas NO DEBEN aparecer como abiertas, pero sus resultados históricos DEBEN permanecer disponibles.
- **FR-043**: Cada compraventa confirmada DEBE generar un registro inmutable con identificador, fecha y hora, usuario, jugador estable, nombre al momento de operar, tipo, cantidad, precio unitario, importe, contraparte superusuario, versión de cotización, referencia de idempotencia y ganancia o pérdida realizada cuando corresponda.
- **FR-044**: El historial personal DEBE contener exclusivamente transacciones confirmadas, presentarse de la más reciente a la más antigua y permitir paginación y filtros por jugador, tipo y rango de fechas.
- **FR-045**: Los intentos rechazados DEBEN registrarse en una auditoría separada de acceso interno autorizado y NO DEBEN aparecer como transacciones confirmadas.
- **FR-046**: El sistema DEBE obtener la identidad del usuario desde su autenticación JWT validada y DEBE impedir seleccionar otra cuenta para operar o consultar recursos privados.
- **FR-047**: Las operaciones internas del superusuario NO DEBEN quedar expuestas a usuarios comunes.
- **FR-048**: La interfaz mínima DEBE permitir consultar saldo, jugadores activos, cotización vigente, tokens disponibles, compras, ventas, portfolio e historial, mostrando claramente precios históricos, falta de cotización, rechazos y estados pendientes o inciertos.
- **FR-049**: La interfaz NO DEBE contener la lógica de negocio del dominio ni acceder directamente a persistencia o proveedores externos.
- **FR-050**: El sistema DEBE soportar un escenario reproducible con cuatro usuarios y cinco jugadores de prueba, compras iniciales, cotizaciones posteriores diferentes, comparación de portfolios, ventas y verificación de resultados realizados y no realizados.
- **FR-051**: Cuando Player Statistics Sync y Player Valuation estén disponibles, el sistema DEBE permitir verificar la integración del escenario con estadísticas reales y cotizaciones publicadas por esas features, sin incorporar scraping, persistencia de estadísticas ni cálculo de precios a Token Market.
- **FR-052**: Los datos del escenario de demostración DEBEN identificarse como datos de prueba y mantenerse separados de los datos reales; la preparación técnica de fixtures o seeds queda para la etapa Plan.

 - **FR-053**: El Portfolio DEBE incluir un resumen de jugadores distintos con posiciones abiertas, tokens totales y costo pendiente total para toda la cartera del usuario autenticado.
 - **FR-054**: El Portfolio DEBE incluir el valor actual total cuando todas las posiciones puedan valorarse con cotizaciones vigentes; si falta informacion suficiente, el campo DEBE ser null y no cero.
 - **FR-055**: El Portfolio DEBE conservar la semantica existente de P/L realizada, P/L no realizada, resultado combinado y completitud, calculada exclusivamente en backend.
 - **FR-056**: Cada posicion del Portfolio DEBE poder presentar nombre canonico del jugador y equipo/liga cuando esten disponibles, manteniendo el identificador estable para operaciones.
 - **FR-057**: La pantalla Mercado DEBE ofrecer Comprar y NO DEBE ofrecer Vender; la pantalla Portfolio DEBE ofrecer Vender solamente para posiciones con cotizacion operable.
 - **FR-058**: La venta iniciada desde el Portfolio DEBE reutilizar el flujo de venta existente, enviar jugador, cantidad, quote_id, quote_version e Idempotency-Key, y limitar la cantidad visible a `min(100, tokens disponibles)`.
 - **FR-059**: Una venta confirmada desde el Portfolio DEBE provocar la recarga de la cartera y conservar las garantias de saldo, tenencia, idempotencia, concurrencia, seguridad y estados inciertos del backend.
 - **FR-060**: La interfaz DEBE mostrar graficos de distribucion de valor actual, costo pendiente frente a valor actual y actividad temporal de compras/ventas confirmadas usando datos reales del usuario.
 - **FR-061**: Los graficos DEBEN ofrecer estados de carga, error y ausencia de datos, ser responsive, incluir titulo/resumen y una tabla equivalente accesible, y NO DEBEN presentar cotizaciones desconocidas ni historial paginado incompleto como ceros o totales.

### Entidades principales

- **Superusuario interno**: Identidad protegida que actúa como contraparte única del mercado y posee la reserva de créditos y los tokens emitidos inicialmente.
- **Crédito virtual**: Unidad monetaria interna con dos decimales, asignada a usuarios y al superusuario para liquidar operaciones.
- **Emisión inicial de tokens**: Asignación única de 100 tokens enteros por jugador elegible, inicialmente propiedad del superusuario.
- **Token de jugador**: Unidad entera positiva asociada a un jugador estable y transferible únicamente entre un usuario y el superusuario.
- **Tenencia**: Posición de un usuario sobre un jugador, con cantidad, costo promedio ponderado y costo total pendiente.
- **Cotización publicada**: Precio versionado por token con jugador, fecha, vigencia y referencia de estrategia cuando corresponda; constituye el único precio operativo aceptable.
- **Operación de mercado**: Intención de compra o venta identificada por usuario, parámetros y clave de idempotencia, con estado recuperable.
- **Transacción confirmada**: Registro inmutable de una compra o venta ejecutada, incluyendo la cotización aceptada y el resultado realizado.
- **Auditoría de intento rechazado**: Registro separado, interno y autorizado de intentos que no llegaron a constituir una compraventa confirmada.
- **Portfolio**: Vista de las posiciones abiertas y resultados del usuario autenticado.

## Criterios de éxito *(obligatorio)*


### Resultados medibles

- **SC-001**: En el 100% de las inicializaciones repetidas de prueba, cada usuario recibe créditos iniciales como máximo una vez, cada jugador elegible conserva una única emisión de 100 tokens y existe una única identidad de superusuario.
- **SC-002**: En el 100% de los casos de inicialización fallida probados, las compras y ventas son rechazadas y no se observan transferencias parciales.
- **SC-003**: En el 100% de las compras y ventas confirmadas probadas, los cambios de créditos, tokens, tenencias y transacción confirmada son consistentes con la cantidad y cotización aceptadas.
- **SC-004**: En el 100% de los reintentos idempotentes con parámetros idénticos, se recupera un único resultado y no se duplican transferencias ni registros confirmados.
- **SC-005**: En el 100% de los conflictos por reutilización de clave, vencimiento, reemplazo, invalidación, saldo insuficiente, inventario insuficiente o jugador inactivo, no se producen mutaciones parciales.
- **SC-006**: En el 100% de las operaciones concurrentes de prueba, nunca se observan saldos negativos, tenencias negativas, doble gasto ni más de 100 tokens emitidos por jugador.
- **SC-007**: El 100% de los historiales personales consultados contiene únicamente transacciones confirmadas del usuario solicitante y mantiene el orden descendente por fecha.
- **SC-008**: En el 100% de las ventas parciales y cierres completos probados, el costo promedio ponderado y los costos residuales cumplen las reglas definidas, y una posición cerrada no conserva residuos.
- **SC-009**: El escenario de demostración con cuatro usuarios y cinco jugadores puede repetirse con datos de prueba identificados, produciendo portfolios comparables, ventas y resultados realizados y no realizados verificables.
- **SC-010**: Los usuarios pueden completar una consulta de portfolio e historial sin acceder a datos de otro usuario y pueden distinguir visualmente cotizaciones vigentes, históricas, inexistentes y operaciones pendientes.
- **SC-011**: Cuando estén disponibles Player Statistics Sync y Player Valuation, la verificación de integración utiliza estadísticas reales y cotizaciones publicadas por esas features sin que Token Market realice scraping, persista estadísticas ni calcule precios.

 - **SC-012**: En el 100% de las respuestas de Portfolio con posiciones de prueba, los totales de jugadores, tokens y costo pendiente coinciden con toda la cartera del usuario, no con una pagina parcial.
 - **SC-013**: En el 100% de los casos con alguna posicion sin cotizacion vigente, el valor actual total, P/L no realizada o resultado combinado incompleto se representa como null/no disponible y no como cero contable.
 - **SC-014**: En el 100% de las posiciones con datos canonicos disponibles, la interfaz muestra nombre real y no utiliza el UUID como texto principal.
 - **SC-015**: En el 100% de las ventas iniciadas desde Portfolio, la cantidad no supera `min(100, tokens disponibles)`, se envia la cotizacion exacta y una venta confirmada actualiza cartera e historial sin duplicacion.
 - **SC-016**: En el 100% de las pruebas de graficos, la distribucion, comparacion y actividad usan datos propios confirmados, distinguen ausencia de valuacion y ofrecen una alternativa tabular accesible.

## Supuestos

- La autenticación JWT y la entidad de usuario existentes son reutilizadas; Token Market no redefine registro ni autenticación.
- El catálogo existente es la fuente de verdad para la identidad y el estado activo o inactivo de los jugadores.
- Player Statistics Sync mantiene las estadísticas y Player Valuation publica las cotizaciones; Token Market consume ambos contratos sin duplicar sus responsabilidades.
- La cotización inicial de 1,00 crédito se publicará bajo el mismo contrato funcional y la misma fuente de verdad de cotizaciones que utilizará Player Valuation, aunque esa feature todavía no esté implementada; la responsabilidad técnica de publicarla se definirá en Plan.
- La reserva inicial del superusuario es finita y no se repone automáticamente; la falta de reserva bloquea una venta completa.
- Los usuarios registrados antes de habilitar el mercado pueden recibir su asignación inicial mediante la inicialización, exactamente una vez.
- La precisión interna necesaria para el promedio ponderado puede ser mayor que la precisión visible de dos decimales, siempre que los valores presentados y liquidados respeten las reglas de créditos y precios.
- La auditoría de intentos rechazados es de acceso interno autorizado y no forma parte del historial personal de transacciones.
- Las operaciones pendientes o inciertas tienen una referencia recuperable mediante su clave de idempotencia y no se consideran confirmadas hasta que exista un resultado seguro.
- Las fixtures o seeds del escenario de demostración se definirán durante Plan y no forman parte de esta especificación funcional.
- Rankings, gráficos avanzados, estrategias de valoración, cálculo de precios, scraping, histórico de cotizaciones y recálculos pertenecen a funcionalidades independientes y quedan fuera de Token Market.
- Los detalles de endpoints, clases, tablas, migraciones, mecanismos de bloqueo y arquitectura técnica se definirán posteriormente en Plan y Contracts.
