# Especificación de Funcionalidad: Token Market P2P

**Rama de Funcionalidad**: `feature/token-market-p2p`

**Creado**: 2026-10-28

**Estado**: Borrador para revisión

**Entrada**: Ampliar Token Market para habilitar ofertas de venta entre usuarios registrados, utilizando exclusivamente la cotización oficial del jugador, sin alterar el mercado primario ni el motor de valorización.

**Referencia histórica**: `specs/003-token-market/`. Esta feature conserva sus garantías compatibles y reemplaza expresamente los requisitos que permitían ventas de usuarios al superusuario.

## Alcance y reglas de negocio

Token Market mantiene un mercado primario en el que el superusuario vende los tokens inicialmente emitidos a usuarios registrados. El mercado secundario permite que un usuario publique una oferta de venta y que otro usuario compre una parte o la totalidad de los tokens reservados.

El vendedor no puede fijar, editar ni enviar manualmente un precio. Cada compra P2P se liquida exclusivamente con la cotización oficial vigente del jugador al momento de confirmación. El motor de valorización, sus estrategias, la publicación de cotizaciones y el scraping de WhoScored permanecen fuera del alcance de esta feature.

Las ventas de usuarios al superusuario quedan prohibidas. El endpoint existente `POST /api/v1/market/sales` debe responder HTTP 410 Gone y no debe transferir créditos, tokens ni modificar historial.

## Escenarios de usuario y pruebas

### Historia de usuario 1 - Comprar tokens del mercado primario (Prioridad: P1)

Como usuario registrado quiero comprar tokens al superusuario con la cotización oficial vigente para incorporarlos a mi portfolio.

**Por qué esta prioridad**: Es el mecanismo primario existente y debe continuar funcionando sin regresiones.

**Prueba independiente**: Con mercado inicializado, jugador activo, inventario del superusuario y saldo suficientes, confirmar una compra primaria y verificar créditos, tokens, portfolio e historial.

**Escenarios de aceptación**:

1. **Given** un usuario autenticado, jugador activo, tokens disponibles del superusuario, saldo suficiente y cotización vigente, **When** confirma una cantidad entera positiva con la versión exacta y una clave de idempotencia válida, **Then** los créditos se transfieren al superusuario, los tokens al usuario y se registra una compra primaria confirmada.
2. **Given** una solicitud primaria inválida, con saldo insuficiente, inventario insuficiente, jugador inactivo o cotización obsoleta, **When** se procesa, **Then** se rechaza completa y no deja transferencias parciales.
3. **Given** una operación primaria confirmada, **When** se reintenta con la misma clave y parámetros, **Then** se recupera el resultado original sin repetir movimientos.

---

### Historia de usuario 2 - Publicar una oferta P2P (Prioridad: P1)

Como usuario registrado quiero ofrecer tokens que poseo a otros usuarios, reservándolos para no comprometerlos en más de una oferta.

**Por qué esta prioridad**: Es la puerta de entrada al mercado secundario y protege la propiedad de los tokens ofrecidos.

**Prueba independiente**: Con una posición abierta, publicar ofertas parciales y completas, verificar la reserva, la cantidad disponible restante y la ausencia de transferencia de créditos.

**Escenarios de aceptación**:

1. **Given** un usuario autenticado con una tenencia positiva, **When** publica una oferta con una cantidad entera positiva no superior a los tokens disponibles, **Then** la cantidad queda reservada inmediatamente y la oferta queda abierta.
2. **Given** una tenencia con tokens reservados en otras ofertas, **When** publica una nueva oferta, **Then** la cantidad máxima aceptable descuenta todas las reservas activas.
3. **Given** una solicitud de publicación que incluye un precio manual, **When** se procesa, **Then** se rechaza explícitamente con un error de entrada inválida y no se crea ninguna oferta.
4. **Given** una oferta abierta, **When** se consulta, **Then** muestra jugador, vendedor identificado según las reglas de privacidad, cantidad restante y cotización oficial disponible, sin presentar un precio independiente de la valorización.
5. **Given** una oferta abierta, **When** transcurre el tiempo, **Then** permanece abierta porque no tiene vencimiento automático.
6. **Given** un jugador inactivo o sin cotización oficial válida, **When** un usuario intenta publicar una oferta nueva, **Then** la publicación se rechaza y no se reserva ninguna cantidad.

---

### Historia de usuario 3 - Comprar una oferta P2P (Prioridad: P1)

Como usuario registrado quiero comprar una cantidad parcial o total de una oferta de otro usuario al precio de la cotización oficial vigente.

**Por qué esta prioridad**: Completa el intercambio secundario y transfiere valor entre comprador y vendedor sin utilizar la reserva del superusuario.

**Prueba independiente**: Crear una oferta y comprarla parcialmente y de forma completa, comprobando créditos, tokens, reserva restante, portfolio e historial de ambas partes.

**Escenarios de aceptación**:

1. **Given** una oferta abierta de otro usuario, jugador activo, cotización vigente y saldo suficiente, **When** el comprador confirma una cantidad válida con la versión de cotización visualizada, **Then** los créditos se transfieren del comprador al vendedor, los tokens del vendedor al comprador y la operación queda confirmada atómicamente.
2. **Given** una oferta con cantidad restante mayor que cero, **When** se compra una cantidad menor que la restante, **Then** la oferta continúa abierta con la cantidad reducida y libera únicamente la reserva consumida.
3. **Given** que la compra consume toda la cantidad restante, **When** finaliza la operación, **Then** la oferta queda completada y no conserva tokens reservados.
4. **Given** que la cotización cambia, se invalida, vence o deja de ser válida antes de confirmar, **When** el backend valida la operación, **Then** responde HTTP 409 con código `QUOTE_CHANGED` o el error de cotización correspondiente, sin transferencias ni reducción de la oferta.
5. **Given** un conflicto `QUOTE_CHANGED`, **When** el comprador visualiza la nueva cotización y confirma nuevamente con una nueva clave de idempotencia, **Then** la compra utiliza exclusivamente la nueva versión vigente.
6. **Given** que el comprador no tiene saldo suficiente, intenta comprar su propia oferta, solicita más tokens que los restantes o utiliza una oferta inexistente/no abierta, **When** se procesa, **Then** se rechaza sin mutaciones parciales.

---

### Historia de usuario 4 - Cancelar una oferta P2P (Prioridad: P1)

Como vendedor quiero cancelar una oferta propia y recuperar la disponibilidad de los tokens que todavía no fueron vendidos.

**Por qué esta prioridad**: Permite recuperar el control sobre tokens ofertados sin afectar operaciones ya confirmadas.

**Prueba independiente**: Publicar una oferta, ejecutar una compra parcial, cancelarla como vendedor y comprobar que solo se libera la cantidad restante.

**Escenarios de aceptación**:

1. **Given** una oferta propia abierta, **When** el vendedor la cancela, **Then** queda cancelada y la cantidad restante se libera inmediatamente.
2. **Given** una oferta parcialmente comprada, **When** el vendedor la cancela, **Then** se liberan únicamente los tokens restantes y las operaciones ya confirmadas permanecen inmutables.
3. **Given** una oferta de otro usuario, **When** un usuario intenta cancelarla, **Then** la solicitud se rechaza y no modifica la reserva.
4. **Given** una oferta asociada a un jugador inactivo o sin cotización válida, **When** el vendedor la cancela, **Then** puede cancelarla aunque las compras estén suspendidas.

---

### Historia de usuario 5 - Consultar ofertas y condiciones operativas (Prioridad: P1)

Como usuario autenticado quiero consultar ofertas P2P y conocer la cotización que se aplicaría antes de confirmarlas.

**Por qué esta prioridad**: El comprador debe poder tomar una decisión informada sin confundir la cotización visible con un precio fijo permanente.

**Prueba independiente**: Consultar ofertas con cotización vigente, cotización ausente, jugador inactivo y cambio de versión; verificar estados claros y ausencia de ofertas comprables cuando corresponda.

**Escenarios de aceptación**:

1. **Given** una oferta de un jugador activo con cotización válida, **When** se consulta, **Then** aparece como comprable con la cotización oficial vigente y la cantidad restante.
2. **Given** un jugador inactivo o sin cotización válida, **When** se consultan sus ofertas, **Then** permanecen registradas pero aparecen suspendidas y no pueden comprarse.
3. **Given** una oferta propia, **When** se consulta, **Then** se distingue de las ofertas de terceros y permite cancelación, pero no compra por el propio vendedor.
4. **Given** una cotización histórica o ausente, **When** se muestra una oferta, **Then** no se presenta como comprable ni se utiliza ese precio para liquidar.

---

### Historia de usuario 6 - Consultar portfolio e historial unificado (Prioridad: P2)

Como usuario autenticado quiero ver mis posiciones y un historial unificado que diferencie compras al superusuario, compras P2P y ventas P2P.

**Por qué esta prioridad**: El usuario debe poder auditar sus operaciones sin perder las ventas históricas al superusuario existentes.

**Prueba independiente**: Ejecutar operaciones primarias, compras P2P, ventas P2P, cancelaciones e intentos rechazados; consultar portfolio e historial de cada usuario y verificar aislamiento y clasificación.

**Escenarios de aceptación**:

1. **Given** transacciones confirmadas de distintos tipos, **When** el usuario consulta su historial, **Then** recibe únicamente sus registros confirmados, ordenados de más reciente a más antiguo y diferenciados como compra primaria, compra P2P o venta P2P.
2. **Given** ventas históricas realizadas al superusuario antes de esta feature, **When** se consulta el historial, **Then** permanecen visibles con su clasificación histórica original.
3. **Given** una venta P2P confirmada, **When** comprador y vendedor consultan sus historiales, **Then** ambos ven su propio asiento de la operación sin acceder a datos privados ajenos.
4. **Given** una oferta abierta, cancelada o una solicitud pendiente, **When** se consulta el historial confirmado, **Then** no aparece como compraventa confirmada.
5. **Given** una compra P2P confirmada, **When** se consulta el portfolio, **Then** los tokens recibidos incrementan la posición del comprador con el costo de adquisición calculado con el importe efectivamente liquidado.
6. **Given** una posición con tokens comprometidos en ofertas P2P, **When** el usuario consulta su portfolio, **Then** se muestran claramente la tenencia total, la cantidad reservada y la cantidad disponible para nuevas ofertas u operaciones permitidas.
7. **Given** una oferta P2P parcial, completa o cancelada, **When** el estado de la oferta cambia, **Then** el portfolio actualiza la cantidad reservada y disponible sin alterar la tenencia total salvo por una compra confirmada.

---

### Historia de usuario 7 - Mantener seguridad, atomicidad e idempotencia (Prioridad: P1)

Como responsable de la plataforma quiero que las operaciones primarias y P2P sean atómicas, idempotentes y seguras frente a concurrencia.

**Por qué esta prioridad**: Los saldos, reservas, tenencias e historiales no pueden quedar inconsistentes ante reintentos, conflictos o ejecuciones simultáneas.

**Prueba independiente**: Ejecutar operaciones concurrentes sobre la misma oferta, tenencia, saldo y clave de idempotencia, verificando invariantes contables y ausencia de duplicados.

**Escenarios de aceptación**:

1. **Given** dos compradores compitiendo por la misma oferta, **When** ejecutan compras concurrentes, **Then** el resultado conjunto no supera la cantidad reservada y cada movimiento confirmado es completo.
2. **Given** una publicación y una cancelación concurrentes, **When** ambas se procesan, **Then** el estado final y la reserva son consistentes y no existe doble disponibilidad.
3. **Given** una compra P2P repetida con la misma clave y parámetros, **When** se reintenta, **Then** se recupera el resultado original sin duplicar transferencias.
4. **Given** una clave reutilizada con parámetros distintos, **When** se procesa, **Then** se rechaza por conflicto sin modificar saldos, reservas, tenencias ni historial.
5. **Given** una solicitud de venta al superusuario mediante `POST /api/v1/market/sales`, **When** se procesa, **Then** responde HTTP 410 Gone y no modifica ningún estado.

## Casos límite

- La cantidad ofrecida es cero, negativa, fraccionaria o excede la disponibilidad no reservada.
- El usuario intenta crear varias ofertas que, sumadas, exceden su tenencia.
- La oferta ya fue completada, cancelada o no existe.
- El comprador intenta adquirir su propia oferta.
- La cantidad solicitada supera la cantidad restante.
- El comprador no tiene créditos suficientes al precio vigente.
- La cotización cambia entre consulta y confirmación.
- La cotización se invalida o vence mientras la oferta permanece abierta.
- El jugador se desactiva mientras existen ofertas abiertas.
- Un jugador sin cotización válida conserva ofertas publicadas visibles pero suspendidas, y no permite publicar nuevas ofertas.
- Se intenta publicar una oferta nueva para un jugador inactivo.
- El vendedor cancela una oferta parcialmente ejecutada.
- Dos compradores compiten por la última cantidad restante.
- Una compra P2P y una cancelación compiten por la misma oferta.
- Se pierde la respuesta después de una transferencia confirmada.
- Se reutiliza una clave de idempotencia con otra cantidad, oferta o versión de cotización.
- Se intenta alterar el precio de una oferta existente.
- Se intenta seleccionar otra identidad de usuario mediante el cuerpo de la solicitud.
- Se consulta portfolio, saldo, ofertas privadas o historial de otro usuario.
- Una operación primaria continúa compitiendo por créditos o inventario del superusuario sin verse afectada por reservas P2P.
- Una venta histórica al superusuario debe permanecer visible, pero no debe habilitar nuevas ventas al superusuario.

## Requisitos

### Requisitos funcionales

- **FR-001**: El sistema DEBE conservar una única identidad interna protegida de superusuario y NO DEBE exponerla como usuario seleccionable.
- **FR-002**: El superusuario DEBE ser la única fuente de venta primaria de tokens inicialmente emitidos a usuarios registrados.
- **FR-003**: Las compras primarias usuario-superusuario DEBEN conservar las validaciones, cotización oficial, idempotencia, atomicidad y seguridad existentes.
- **FR-004**: Los usuarios NO DEBEN poder vender tokens al superusuario en nuevas operaciones.
- **FR-005**: `POST /api/v1/market/sales` DEBE responder HTTP 410 Gone y NO DEBE transferir créditos, tokens, reservas ni crear transacciones nuevas.
- **FR-006**: Un usuario autenticado DEBE poder publicar una oferta de venta para otro usuario registrado únicamente cuando el jugador esté activo, tenga una cotización oficial válida y el usuario posea tokens disponibles del jugador.
- **FR-007**: Una oferta DEBE aceptar únicamente una cantidad entera positiva y NO DEBE aceptar un precio definido o modificado manualmente por el vendedor; toda solicitud que incluya un precio manual DEBE rechazarse explícitamente y no crear una oferta.
- **FR-008**: Al publicar una oferta, la cantidad ofrecida DEBE reservarse inmediatamente y dejar de estar disponible para otras ofertas o transferencias P2P.
- **FR-009**: La suma de tokens reservados en ofertas activas más la cantidad no reservada NO DEBE exceder la tenencia del vendedor.
- **FR-010**: Las ofertas DEBEN permanecer registradas sin vencimiento automático hasta ser completadas, canceladas o quedar en un estado terminal definido por el negocio.
- **FR-011**: El vendedor DEBE poder cancelar una oferta propia y recuperar únicamente la cantidad restante no vendida, incluso después de compras parciales.
- **FR-012**: Otro usuario autenticado DEBE poder comprar una cantidad menor o igual a la cantidad restante de una oferta abierta.
- **FR-013**: El vendedor NO DEBE poder comprar su propia oferta.
- **FR-014**: Cada compra P2P DEBE utilizar exclusivamente la cotización oficial vigente del jugador al momento de confirmación.
- **FR-015**: El precio total de una compra P2P DEBE calcularse en el backend como cotización vigente multiplicada por la cantidad confirmada.
- **FR-016**: El frontend y el vendedor NO DEBEN poder convertir un precio manual en autoridad de liquidación.
- **FR-017**: El comprador DEBE aceptar una identidad y versión de cotización antes de confirmar una compra P2P.
- **FR-018**: Si la cotización aceptada cambió, fue reemplazada, invalidada, venció o dejó de ser válida antes de confirmar, el backend DEBE responder HTTP 409 con código `QUOTE_CHANGED` o el error específico de cotización correspondiente, sin transferencias ni reducción de la oferta.
- **FR-019**: Después de `QUOTE_CHANGED`, el comprador DEBE confirmar explícitamente el nuevo precio y utilizar una nueva clave de idempotencia.
- **FR-020**: Si el jugador está inactivo o no tiene cotización válida, sus ofertas DEBEN permanecer registradas y cancelables, pero las compras DEBEN quedar suspendidas.
- **FR-021**: Una compra P2P confirmada DEBE debitar créditos al comprador, acreditar el mismo importe al vendedor, reducir la tenencia del vendedor e incrementar la tenencia del comprador en una única operación atómica.
- **FR-022**: Una compra P2P parcial DEBE reducir la cantidad restante y la reserva de la oferta en la misma operación que las transferencias.
- **FR-023**: Una compra P2P completa DEBE dejar la oferta sin cantidad restante y sin tokens reservados.
- **FR-024**: Las operaciones primarias y P2P NO DEBEN producir saldos, tenencias, reservas o inventarios negativos.
- **FR-025**: Las operaciones P2P DEBEN ser seguras frente a solicitudes concurrentes que compitan por la misma oferta, tenencia, reserva o saldo.
- **FR-026**: Cada compra P2P, publicación y cancelación que pueda reintentarse DEBE tener semántica idempotente apropiada y no duplicar movimientos.
- **FR-027**: Una clave de idempotencia reutilizada con parámetros distintos DEBE rechazarse sin mutaciones.
- **FR-028**: Las solicitudes pendientes, inciertas o rechazadas NO DEBEN presentarse como transacciones confirmadas.
- **FR-029**: El sistema DEBE mantener un historial unificado y distinguir como mínimo compras primarias, compras P2P y ventas P2P.
- **FR-030**: Las ventas históricas al superusuario realizadas antes de esta feature DEBEN conservarse y seguir siendo consultables con su clasificación histórica.
- **FR-031**: El historial DEBE mostrar a cada usuario únicamente sus propios asientos confirmados y debe preservar jugador, nombre snapshot, cantidad, importe, cotización aplicada, contraparte permitida, oferta cuando corresponda, idempotencia y resultado realizado.
- **FR-032**: Las ofertas abiertas, canceladas y suspendidas NO DEBEN aparecer como transacciones confirmadas.
- **FR-033**: El portfolio DEBE incorporar compras P2P en las posiciones del comprador y reflejar las ventas P2P en las posiciones del vendedor según las reglas contables existentes.
- **FR-034**: El sistema DEBE derivar la identidad de usuarios desde el JWT validado y NO DEBE permitir seleccionar otra cuenta para publicar, comprar, cancelar o consultar datos privados.
- **FR-035**: La interfaz DEBE permitir publicar, consultar, comprar y cancelar ofertas según los permisos del usuario, sin permitir ingresar precios manuales.
- **FR-036**: La interfaz DEBE mostrar claramente la cotización oficial visualizada, la cantidad, el total estimado y el cambio de cotización que requiera una nueva confirmación.
- **FR-037**: La interfaz NO DEBE ofrecer una acción de venta al superusuario y DEBE tratar HTTP 410 de `POST /market/sales` como funcionalidad retirada.
- **FR-038**: Player Valuation DEBE continuar siendo responsable de calcular y publicar cotizaciones, y Token Market NO DEBE modificar sus estrategias, ponderaciones o histórico.
- **FR-039**: Player Statistics Sync y WhoScored DEBEN conservar sus responsabilidades actuales; Token Market NO DEBE agregar scraping, sincronización ni persistencia adicional de estadísticas.
- **FR-040**: Los endpoints P2P públicos DEBEN documentar autenticación, solicitudes, respuestas, errores de cotización, permisos, idempotencia y semántica de HTTP 410 conforme a las convenciones existentes.
- **FR-041**: El portfolio DEBE mostrar claramente, para cada posición con ofertas P2P, la tenencia total, la cantidad reservada en ofertas activas y la cantidad disponible, manteniendo la relación `disponible = total - reservada`.

### Reemplazo explícito de requisitos de `specs/003-token-market`

Esta feature reemplaza para el comportamiento futuro los siguientes requisitos de `specs/003-token-market/spec.md`:

- `FR-013`: deja de regir la exclusividad de operaciones contra el superusuario; se conserva solo la compra primaria y se agrega el mercado secundario P2P.
- `FR-031` y `FR-032`: se reemplazan por publicación, reserva y compra P2P; la reserva del superusuario ya no financia ventas de usuarios.
- `FR-043`: se amplía para distinguir compras primarias, compras P2P y ventas P2P, incluyendo usuarios y oferta cuando corresponda.
- `FR-048`: se reemplaza la venta directa por publicación, consulta, compra y cancelación de ofertas.
- `FR-050`: el escenario de demostración debe incluir operaciones P2P y no nuevas ventas usuario-superusuario.
- `FR-057`, `FR-058` y `FR-059`: se reemplaza la venta desde Portfolio por publicación/cancelación de ofertas y compra P2P desde el mercado secundario.
- Las entidades principales de `specs/003-token-market` que describen al superusuario como contraparte única o los tokens como transferibles solo entre usuario y superusuario quedan supersedidas por las entidades y relaciones P2P de esta feature.

Las compras primarias, portfolio, historial, cotizaciones exactas, idempotencia, atomicidad, seguridad, emisión inicial y responsabilidades de Player Valuation/Player Statistics Sync se conservan salvo donde esta especificación indique lo contrario.

### Entidades principales

- **Oferta P2P**: intención persistente de un usuario de transferir una cantidad reservada de tokens a otros usuarios, sin precio manual y sin vencimiento automático.
- **Reserva de oferta**: cantidad de tokens apartada de la disponibilidad del vendedor para impedir sobreofertas o doble compromiso.
- **Compra primaria**: adquisición de tokens por un usuario al superusuario con la cotización oficial vigente.
- **Compra P2P**: adquisición parcial o total de una oferta por otro usuario, liquidada con la cotización oficial vigente al confirmar.
- **Venta P2P**: asiento histórico del vendedor en una compra P2P confirmada.
- **Cotización oficial**: valor publicado por Player Valuation que constituye la única fuente de precio operativo.
- **Transacción unificada**: registro inmutable que diferencia compra primaria, compra P2P y venta P2P, conservando las referencias necesarias para auditar cada operación.
- **Tenencia**: posición del usuario sobre un jugador, incluyendo cantidad total, cantidad reservada y costo de adquisición según las reglas existentes.
- **Portfolio de tenencias**: vista personal que distingue la cantidad total poseída, la cantidad reservada en ofertas P2P activas y la cantidad disponible para nuevas ofertas u operaciones permitidas.

## Criterios de éxito

### Resultados medibles

- **SC-001**: En el 100% de las pruebas de compras primarias válidas, el usuario recibe tokens y el superusuario recibe créditos sin regresiones respecto del comportamiento existente.
- **SC-002**: En el 100% de los intentos de venta al superusuario, `POST /api/v1/market/sales` responde HTTP 410 y no produce movimientos, cambios de saldo, cambios de tenencia ni transacciones nuevas.
- **SC-003**: En el 100% de las publicaciones válidas, la cantidad reservada no supera los tokens disponibles y queda reflejada antes de aceptar otra oferta concurrente.
- **SC-004**: En el 100% de las compras P2P confirmadas, el importe se calcula con la cotización oficial vigente y los créditos y tokens cambian en sentidos opuestos y por la misma operación.
- **SC-005**: En el 100% de los casos de cambio, invalidez o vencimiento de cotización antes de confirmar, la respuesta es un conflicto sin transferencias ni reducción de oferta.
- **SC-006**: En el 100% de las compras parciales, la cantidad restante y la reserva coinciden con la cantidad no vendida; al completar una oferta, ambas quedan en cero.
- **SC-007**: En el 100% de las cancelaciones, solo se libera la cantidad restante y nunca se revierte una compra P2P ya confirmada.
- **SC-008**: En el 100% de las pruebas concurrentes no se observan saldos, tenencias o reservas negativos, doble gasto ni cantidad comprada superior a la oferta.
- **SC-009**: En el 100% de los reintentos idempotentes se conserva un único resultado y no se duplican movimientos ni asientos históricos.
- **SC-010**: El 100% de los historiales personales contiene únicamente asientos propios confirmados, diferenciando compra primaria, compra P2P y venta P2P y conservando ventas históricas al superusuario.
- **SC-011**: En el 100% de las ofertas de jugadores inactivos o sin cotización válida, la oferta permanece consultable y cancelable, pero no puede producir una compra confirmada.
- **SC-012**: En el 100% de las operaciones exitosas el frontend no permite ingresar un precio manual y muestra el total basado en la cotización oficial devuelta por el backend.
- **SC-013**: Player Valuation, sus estrategias, Player Statistics Sync y WhoScored permanecen sin cambios de responsabilidad funcional durante la verificación de esta feature.
- **SC-014**: En el 100% de las consultas de portfolio con ofertas P2P activas, la cantidad total, reservada y disponible se muestran por separado y cumplen `disponible = total - reservada`.

## Supuestos

- La autenticación JWT, usuarios, jugadores, cuentas de créditos, emisión inicial, cotizaciones y portfolio existentes se reutilizan.
- Las ofertas no vencen automáticamente y se conservan hasta completar o cancelar.
- Una compra P2P puede ser parcial y el comprador elige una cantidad entera no superior a la restante.
- El cambio de cotización requiere una nueva confirmación y una nueva clave de idempotencia, tal como establecen las reglas aprobadas.
- Una oferta suspendida por jugador inactivo o cotización inválida sigue disponible para cancelación del vendedor.
- El historial unificado conservará las ventas históricas al superusuario aunque no permita crear nuevas.
- La auditoría de intentos rechazados permanece separada del historial personal confirmado.
- Las decisiones sobre entidades físicas, migraciones, locks concretos, nombres finales de clases y estructura de endpoints P2P detallados se definirán en Plan, sin cambiar estas garantías funcionales.

## Fuera de alcance

- Subastas, ofertas con precio manual, libro de órdenes complejo o emparejamiento automático.
- Comisiones, impuestos, cuotas o moneda externa, salvo aprobación posterior.
- Modificación de Player Valuation, estrategias, cotizaciones o histórico de valorización.
- Scraping, sincronización o persistencia adicional de WhoScored/Player Statistics Sync.
- Reposición automática de la reserva del superusuario.
- Transferencias gratuitas o donaciones de tokens fuera de una compra P2P confirmada.
