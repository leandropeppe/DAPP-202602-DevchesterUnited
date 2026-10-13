# Especificación de Funcionalidad: Sincronización de Estadísticas de Jugadores

**Rama de Funcionalidad (Feature Branch)**: `002-player-statistics-sync`

**Fecha de Creación**: 2026-10-03

**Estado**: Draft

**Entrada**: Descripción del usuario: "Crear el Feature 2 correspondiente a la obtención y sincronización de estadísticas de jugadores desde WhoScored, como parte de la segunda entrega de Devchester United."

## Escenarios de Usuario y Pruebas *(obligatorio)*

### User Story 1 - Obtener estadísticas de las ligas soportadas (Prioridad: P1)

Como responsable autorizado de la plataforma, quiero ejecutar una sincronización de estadísticas desde WhoScored para las cinco principales ligas europeas, de modo que el sistema disponga de información actualizada de todos los jugadores disponibles.

**Por qué esta prioridad**: Es el objetivo principal de la feature y habilita que las funcionalidades posteriores de cotización utilicen información estadística vigente.

**Prueba Independiente**: Con una fuente externa controlada que contenga jugadores de las cinco competiciones, se ejecuta una sincronización y se verifica que cada jugador disponible quede representado con sus estadísticas generales, ofensivas y defensivas cuando la fuente las proporcione.

**Escenarios de Aceptación**:

1. **Dado** que la fuente externa dispone de jugadores de Premier League, La Liga, Serie A, Bundesliga y Ligue 1, **cuando** se ejecuta una sincronización válida, **entonces** el sistema procesa las cinco competiciones y deja disponibles las estadísticas obtenidas para cada jugador disponible.
2. **Dado** que una competición contiene una cantidad de jugadores distinta en dos ejecuciones, **cuando** se ejecuta la segunda sincronización, **entonces** el sistema procesa todos los jugadores disponibles en esa ejecución sin depender de una cantidad prefijada.
3. **Dado** que la fuente entrega información general, ofensiva y defensiva en conjuntos separados, **cuando** se normalizan los datos, **entonces** las tres familias quedan asociadas al jugador correspondiente.

---

### User Story 2 - Mantener estadísticas consistentes y sin duplicados (Prioridad: P1)

Como consumidor de los datos de jugadores, quiero que cada sincronización actualice los registros correspondientes sin duplicarlos, para poder utilizar información confiable en funcionalidades posteriores.

**Por qué esta prioridad**: La consistencia del catálogo estadístico es necesaria para que cualquier cálculo futuro de cotización parta de una única representación vigente por jugador.

**Prueba Independiente**: Se ejecuta una sincronización, se repite sin cambios en la fuente y luego se ejecuta otra con cambios en algunas estadísticas; se comprueba que no aparecen duplicados, que los datos sin cambios permanecen equivalentes y que los modificados se actualizan.

**Escenarios de Aceptación**:

1. **Dado** que un jugador ya posee estadísticas sincronizadas, **cuando** una nueva sincronización contiene información actualizada del mismo jugador, **entonces** se actualiza su información existente sin crear una segunda representación.
2. **Dado** que los datos externos no cambiaron, **cuando** se repite la sincronización, **entonces** el estado local final es equivalente al estado anterior y no se generan duplicados.
3. **Dado** que un mismo jugador aparece en distintos conjuntos de información de la fuente, **cuando** se completa la asociación, **entonces** sus estadísticas se consolidan en un único registro lógico.

---

### User Story 3 - Conservar el último estado válido ante fallos (Prioridad: P1)

Como responsable de la plataforma, quiero que una sincronización fallida no exponga resultados parciales ni elimine estadísticas válidas, para mantener la confiabilidad de la información disponible.

**Por qué esta prioridad**: La disponibilidad de datos locales confiables debe mantenerse aunque WhoScored esté indisponible, devuelva información inválida o falle el procesamiento.

**Prueba Independiente**: Se parte de un estado local válido, se provoca un fallo durante la obtención, normalización o persistencia y se verifica que los consumidores sigan viendo el estado válido anterior, sin mezclas parciales.

**Escenarios de Aceptación**:

1. **Dado** un conjunto de estadísticas locales válido, **cuando** la fuente externa no está disponible, **entonces** la sincronización informa el fallo y conserva sin cambios el último estado local válido.
2. **Dado** que una sincronización falla después de haber procesado parte de la información, **cuando** termina la operación, **entonces** no quedan visibles actualizaciones parciales ni una combinación inconsistente de datos nuevos y antiguos.
3. **Dado** que la fuente devuelve información incompleta, inválida o imposible de asociar, **cuando** se intenta sincronizar, **entonces** el sistema rechaza la sincronización afectada, conserva el estado local anterior y no persiste registros corruptos.

### Casos límite

- Una competición puede devolver cero jugadores disponibles; la sincronización debe distinguir ese resultado de un fallo de obtención y aplicar únicamente un resultado que haya sido validado como completo y válido.
- La cantidad de jugadores puede aumentar o disminuir entre ejecuciones; el procesamiento debe incluir todos los disponibles sin truncar el resultado por una cantidad esperada.
- Un jugador puede no disponer legítimamente de determinados valores estadísticos en la fuente; dicha ausencia no debe interpretarse como un fallo de sincronización ni debe completarse con valores inventados. La ausencia esperada de información debe distinguirse de la imposibilidad de obtener una familia de estadísticas requerida para completar la sincronización.
- La misma persona puede aparecer repetida dentro de la información externa; la sincronización no debe crear duplicados locales.
- Una estadística puede no estar informada para un jugador; el sistema no debe inventar valores y debe conservar la ausencia de información de forma coherente.
- Puede ocurrir un fallo antes, durante o después del procesamiento de una competición; el resultado visible debe seguir siendo el último estado local válido.
- Una sincronización repetida puede coincidir con otra en curso; el comportamiento de concurrencia deberá definirse durante el diseño técnico sin comprometer la atomicidad ni producir duplicados.

## Extension de lectura del catalogo

### User Story 4 - Consultar el detalle persistido de un jugador (Prioridad: P1)

Como consumidor del catalogo, quiero consultar el detalle de un jugador con sus
estadisticas ya sincronizadas, para obtener una respuesta local sin depender en
tiempo real de WhoScored.

**Prueba Independiente**: Con un jugador activo de un equipo activo, verificar
que el endpoint devuelva el detalle y las estadisticas persistidas; repetir con
un jugador sin fila de estadisticas y con jugadores inexistentes o inactivos.

**Escenarios de Aceptacion**:

1. **Dado** un jugador activo de un equipo activo con estadisticas persistidas,
   **cuando** se consulta `GET /api/v1/players/{id}` usando `ply_<uuid>`,
   **entonces** responde HTTP 200 con el detalle del jugador y un objeto
   `statistics` con las metricas persistidas.
2. **Dado** un jugador activo de un equipo activo sin estadisticas persistidas,
   **cuando** se consulta su detalle, **entonces** responde HTTP 200 y
   `statistics` es `null`.
3. **Dado** un identificador inexistente, de un jugador inactivo o de un jugador
   cuyo equipo esta inactivo, **cuando** se consulta el detalle, **entonces**
   responde HTTP 404.
4. **Dado** cualquier consulta de detalle, **entonces** los datos se obtienen
   exclusivamente de la persistencia local y no se consulta WhoScored, no se
   ejecuta Selenium ni se inicia una sincronizacion.

---

### User Story 5 - Visualizar estadisticas desde el catalogo (Prioridad: P1)

Como usuario del catalogo, quiero seleccionar un jugador y consultar su detalle
con sus estadisticas, para conocer su rendimiento desde la interfaz sin buscar
manualmente un identificador ni depender de una fuente externa.

**Por que esta prioridad**: Completa el flujo visible de la feature y permite
utilizar el detalle estadistico ya disponible desde la experiencia principal del
catalogo.

**Prueba Independiente**: Con un jugador del catalogo con estadisticas, otro
sin estadisticas y un identificador inexistente, seleccionar cards, verificar la
navegacion al detalle, los datos mostrados y los estados de error o ausencia.

**Escenarios de Aceptacion**:

1. **Dado** un jugador visible en el catalogo, **cuando** el usuario selecciona
   su card, **entonces** se abre la pagina de detalle correspondiente y se
   consulta el endpoint de detalle existente para ese jugador.
2. **Dado** un detalle con estadisticas, **cuando** termina la carga, **entonces**
   se muestran los datos basicos disponibles y minutos jugados, goles,
   asistencias, tiros, pases clave, regates, tackles, valoracion, tarjetas
   amarillas y tarjetas rojas.
3. **Dado** un detalle sin estadisticas, **cuando** la respuesta indica
   `statistics = null`, **entonces** la interfaz informa claramente que no hay
   estadisticas disponibles sin inventar ni ocultar los datos basicos del jugador.
4. **Dado** que la consulta esta en curso, **cuando** se muestra la pagina de
   detalle, **entonces** la interfaz presenta un estado de carga comprensible.
5. **Dado** que el jugador no existe o la consulta falla, **cuando** se recibe el
   error, **entonces** la interfaz informa el problema y ofrece una forma clara
   de volver al catalogo.
6. **Dado** la pagina de detalle abierta, **cuando** el usuario utiliza la accion
   de volver, **entonces** regresa al catalogo sin quedar bloqueado en el detalle.

---

### User Story 6 - Identificar jugadores por nacionalidad en el catalogo (Prioridad: P2)

Como usuario del catalogo, quiero ver la nacionalidad en cada card, para
identificar al jugador sin mostrar un dorsal que no representa un dato fiable.

**Por que esta prioridad**: Corrige una representacion enganosa del catalogo y
utiliza un dato de jugador ya disponible, sin ampliar la adquisicion externa.

**Prueba Independiente**: Con jugadores cuya nacionalidad esta disponible y con
un dorsal ausente, verificar que las cards muestran nacionalidad y no muestran
el dorsal ni un valor `0` como sustituto.

**Escenarios de Aceptacion**:

1. **Dado** un jugador con nacionalidad disponible, **cuando** se muestra su
   card, **entonces** se visualiza la nacionalidad en el lugar destinado a la
   identificacion complementaria.
2. **Dado** un jugador cuyo dorsal no esta disponible, **cuando** se muestra su
   card, **entonces** no aparece el dorsal ni un `0` derivado de una ausencia.
3. **Dado** el cambio de presentacion de la card, **cuando** se carga el
   catalogo, **entonces** no se realizan cambios en scraping, matching,
   sincronizacion ni fuentes de datos para obtener el dorsal.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE obtener estadísticas de todos los jugadores disponibles de cada una de las siguientes competiciones: Premier League, La Liga, Serie A, Bundesliga y Ligue 1.
- **FR-002**: El sistema DEBE procesar la cantidad de jugadores que la fuente externa entregue en cada ejecución, sin asumir una cantidad fija por competición.
- **FR-003**: El sistema DEBE obtener y normalizar, como mínimo, la posición del jugador, minutos jugados, goles, asistencias, tiros, pases clave, regates, tackles, valoración general, tarjetas amarillas y tarjetas rojas.
- **FR-004**: El sistema DEBE contemplar información estadística general, ofensiva y defensiva necesaria para las estrategias posteriores de valoración.
- **FR-005**: Las estadísticas sincronizadas DEBEN conservar su significado individual y permanecer desacopladas de las reglas que posteriormente determinen su peso o impacto sobre la cotización de un jugador.- **FR-006**: El sistema DEBE actualizar las estadísticas de jugadores existentes cuando la nueva información corresponda al mismo jugador.
- **FR-007**: El sistema DEBE evitar la creación de duplicados cuando un jugador ya fue sincronizado o cuando aparece en más de un conjunto de información.
- **FR-008**: El sistema DEBE producir un resultado idempotente cuando los datos externos no hayan cambiado.
- **FR-009**: El sistema DEBE procesar los datos de manera controlada, sin requerir mantener simultáneamente en memoria la totalidad de jugadores de las cinco competiciones.
- **FR-010**: Cada sincronización DEBE tratarse como una única operación lógica sobre los datos persistidos.
- **FR-011**: El sistema NO DEBE hacer visibles actualizaciones parciales si la sincronización no puede completarse correctamente.
- **FR-012**: Ante un fallo de sincronización, el sistema DEBE conservar los últimos datos locales válidos previamente disponibles.
- **FR-013**: Los errores, respuestas inválidas o indisponibilidad de WhoScored DEBEN gestionarse sin corromper, eliminar ni reemplazar parcialmente la información local válida.
- **FR-014**: El mecanismo de obtención de datos externos DEBE permanecer desacoplado del caso de uso de sincronización.
- **FR-015**: El caso de uso DEBE poder utilizar otro mecanismo de obtención de datos sin modificar su lógica funcional de sincronización.
- **FR-016**: Los datos sincronizados DEBEN quedar disponibles para su utilización posterior por las funcionalidades de cotización, sin incluir en esta feature reglas de valorización.
- **FR-017**: El sistema DEBE asociar las estadísticas obtenidas al Jugador existente correspondiente y NO DEBE crear una representación alternativa de Jugador como parte del modelo estadístico.

### Requisitos de lectura del detalle

- **FR-018**: El catalogo DEBE exponer `GET /api/v1/players/{id}` con el identificador publico opaco `ply_<uuid>`.
- **FR-019**: Para un jugador activo de un equipo activo, el detalle DEBE responder HTTP 200 y conservar los campos actuales del jugador.
- **FR-020**: Con estadisticas persistidas, el detalle DEBE incluir `statistics` con `position`, `minutes_played`, `goals`, `assists`, `shots`, `key_passes`, `dribbles`, `tackles`, `rating`, `yellow_cards` y `red_cards`.
- **FR-021**: Sin fila de `jugador_estadisticas`, el detalle DEBE responder HTTP 200 con `statistics: null`.
- **FR-022**: El detalle DEBE responder HTTP 404 para jugadores inexistentes, inactivos o pertenecientes a equipos inactivos.
- **FR-023**: La consulta DEBE leer exclusivamente datos persistidos mediante `JugadorRepository` y `EstadisticasJugadorRepository`.
- **FR-024**: La consulta NO DEBE invocar WhoScored, Selenium, `WhoScoredStatisticsAdapter` ni sincronizaciones.
- **FR-025**: El contrato de detalle NO DEBE exponer ids tecnicos ni metadatos de sincronizacion, y NO DEBE modificar `GET /api/v1/players`.

### Requisitos de visualizacion frontend

- **FR-026**: Las cards de jugadores del catalogo DEBEN permitir seleccionar un jugador y navegar a su pagina de detalle correspondiente.
- **FR-027**: La pagina de detalle DEBE consumir el endpoint existente `GET /api/v1/players/{id}` y NO DEBE consultar WhoScored ni otra fuente externa directamente.
- **FR-028**: La pagina de detalle DEBE mostrar los datos basicos disponibles del jugador y, cuando `statistics` no sea nulo, minutos jugados, goles, asistencias, tiros, pases clave, regates, tackles, valoracion, tarjetas amarillas y tarjetas rojas.
- **FR-029**: La pagina de detalle DEBE presentar un estado de carga mientras espera la respuesta del endpoint.
- **FR-030**: Ante un jugador inexistente o un error de consulta, la pagina DEBE mostrar un mensaje comprensible y una accion clara para volver al catalogo.
- **FR-031**: Cuando `statistics = null`, la pagina DEBE informar que el jugador no tiene estadisticas disponibles y DEBE conservar visibles sus datos basicos disponibles.
- **FR-032**: La pagina de detalle DEBE ofrecer una forma clara de regresar al catalogo.
- **FR-033**: Las cards del catalogo NO DEBEN mostrar el dorsal/jersey number y DEBEN mostrar la nacionalidad disponible del jugador en su lugar.
- **FR-034**: La ausencia del dorsal NO DEBE representarse como `0` ni requerir una nueva fuente, scraping o modificacion del matching para esta presentacion.
- **FR-035**: Esta extension NO DEBE incorporar compra, venta, cotizacion, portfolio ni transacciones.

### Entidades Clave

- **Jugador**: Entidad ya existente en el dominio de Devchester United. Esta feature no redefine ni reemplaza su modelado. Las estadísticas sincronizadas deben asociarse al jugador correspondiente.
- **Estadísticas de jugador**: Información estadística normalizada obtenida desde la fuente externa y asociada a un Jugador existente. Debe contener la información necesaria para que funcionalidades posteriores puedan evaluar su rendimiento y calcular su cotización.
- **Competición soportada**: Una de las cinco ligas europeas incluidas en el alcance de la sincronización.
- **Sincronización**: Operación lógica que obtiene, valida, normaliza y actualiza las estadísticas asociadas a los jugadores existentes, conservando atomicidad y el último estado local válido ante fallos.

## Criterios de Éxito *(obligatorio)*

### Resultados de lectura del detalle

- **SC-007**: Los escenarios con estadisticas, sin estadisticas e inexistente/inactivo cumplen respectivamente HTTP 200 con objeto, HTTP 200 con `null` y HTTP 404.
- **SC-008**: El 100% de las consultas de detalle verificadas funciona con informacion local aunque WhoScored no este disponible, sin ejecutar Selenium ni sincronizaciones.

### Resultados de visualizacion frontend

- **SC-009**: En las pruebas del flujo principal, al menos el 95% de las selecciones de cards conduce al detalle correcto del jugador en un unico intento.
- **SC-010**: El 100% de los escenarios de carga, error/inexistencia y `statistics = null` muestra un estado comprensible y una ruta clara de recuperacion o regreso al catalogo.
- **SC-011**: El 100% de los detalles con estadisticas muestra las diez metricas requeridas y los datos basicos disponibles sin incorporar datos inventados.
- **SC-012**: El 100% de las cards verificadas muestra la nacionalidad disponible y no muestra el dorsal ni un `0` sustituto por ausencia de dorsal.

### Resultados Medibles

- **SC-001**: En una sincronización válida, el 100% de las cinco competiciones soportadas queda procesado y todos los jugadores válidos disponibles en la fuente quedan correctamente considerados por la sincronización.
- **SC-002**: En ejecuciones repetidas con datos externos sin cambios, el 100% de los jugadores conserva una única representación local y el estado final de sus estadísticas es equivalente al anterior.
- **SC-003**: En una ejecución con cambios externos válidos, el 100% de los jugadores afectados queda actualizado sin duplicar jugadores ni familias de estadísticas.
- **SC-004**: En el 100% de las sincronizaciones fallidas de las pruebas, no quedan visibles actualizaciones parciales y se conserva íntegramente el último estado local válido.
- **SC-005**: La solución puede procesar una competición con una cantidad variable de jugadores sin truncar el resultado por una cantidad fija definida previamente.
- **SC-006**: Las funcionalidades posteriores pueden consultar las estadísticas sincronizadas sin depender de la disponibilidad en tiempo real de WhoScored.

## Supuestos

- La sincronización será ejecutada por un responsable o proceso autorizado según las reglas de acceso ya existentes en el sistema; esta feature no redefine autenticación ni autorización.
- WhoScored es la fuente externa de esta entrega y sus datos constituyen la referencia para actualizar las estadísticas locales cuando la información sea válida.
- La identificación necesaria para reconocer al mismo jugador entre ejecuciones y conjuntos de información será resuelta durante el diseño técnico a partir de los datos disponibles, sin exponer detalles del proveedor en el dominio.
- La ausencia de una estadística individual no invalida por sí misma al jugador; se conserva como dato no informado, salvo que la validación determine que el conjunto es inconsistente.
- La selección de temporadas, criterios de actualización y política de concurrencia que no estén definidas por esta especificación se resolverán en `/speckit-plan` sin alterar el alcance funcional.

## Aclaracion de alcance frontend

La presentacion de estadisticas en el frontend, antes indicada como fuera de
alcance, queda incorporada por las User Stories 5 y 6 y los requisitos FR-026 a
FR-035 de esta extension. La implementacion debe reutilizar la arquitectura,
convenciones visuales y estrategia de testing frontend existentes.

El backend de adquisicion, matching, sincronizacion, persistencia y contrato de
detalle ya implementado permanece fuera de modificacion, salvo un ajuste
estrictamente necesario para permitir el consumo del contrato desde el
frontend. Esta extension no incorpora compra, venta, cotizacion, portfolio ni
transacciones.

## Fuera de Alcance

- Cálculo de cotizaciones o reglas de valorización de jugadores.
- Compra, venta o cualquier funcionalidad de mercado.
- Presentación de estadísticas en el frontend, salvo que una historia funcional posterior la requiera explícitamente.
- Nuevos contratos públicos de sincronización o detalles técnicos del mecanismo de obtención. El contrato de lectura de detalle definido por la User Story 4 queda dentro del alcance de esta extensión.
# Mecanismo de adquisicion vigente (actualizacion tecnica)

La adquisicion real validada de WhoScored se realiza mediante Selenium
navegando a `/statisticsfeed/1/getplayerstatistics`, cuyo body devuelve JSON.
Esta decision no modifica el alcance funcional ni las garantias de identidad,
idempotencia, atomicidad, staging, rollback y persistencia local.

Para cada competicion deben adquirirse las familias `summary/all`,
`summary/offensive` y `summary/defensive`. La adquisicion concreta, la
validacion de metadata, cualquier paginacion y la traduccion a estructuras
internas pertenecen al Adapter. El Service no conoce Selenium, JSON, URLs,
stage IDs, tournamentOptions ni parametros HTTP.

La implementacion debe validar completitud de cada respuesta. Un resultado con
`totalPages > 1` o `players.size != totalResults` no puede considerarse completo
silenciosamente. La decision productiva final es `isMinApp=false` y
`numberOfPlayersToPick=500`. El diagnostico real verifico un maximo actual de
472 jugadores por competicion, por lo que se realiza una llamada por
familia/competicion y no se implementa paginacion. La validacion estricta de
completitud permanece activa: `currentPage == 1`, `totalPages == 1`, cantidad
recibida igual a `totalResults` y `totalResults <= resultsPerPage`.

El contrato ficticio `GET /statistics` con `competition`, `family`, `page`,
`pageSize` y Bearer API key queda **superseded**. No se agrega un endpoint
publico ni se agregan al dominio los IDs de infraestructura de las competiciones.

## Decision final de identidad de jugadores WhoScored

- Los candidatos se restringen exclusivamente a jugadores activos del Equipo
  local previamente resuelto por identidad WhoScored; el resolver de jugadores
  no vuelve a resolver equipos.
- La equivalencia por normalizacion canonica (Unicode NFKD, eliminacion de
  marcas, lowercase ROOT, apostrofes eliminados, guiones como espacios,
  puntuacion eliminada y espacios colapsados) no se considera fuzzy.
- Despues se aplican componentes nominales: apellido como tramo compartido de
  la parte final/compuesta y nombre compatible por igualdad, prefijo o
  similitud algoritmica; compartir un token generico no alcanza.
- Levenshtein sobre el nombre canonico completo solo se utiliza como ultimo
  fallback, con score minimo 0.85 y margen minimo 0.15 frente al segundo.
- Una identidad individual ambigua o insuficiente se omite, no entra en
  staging ni promocion, y se informa en el resultado/log. No aborta la
  sincronizacion. Los errores tecnicos, estructurales, de equipo, staging o
  promocion siguen siendo fatales.
- La publicacion de todas las identidades validas sigue siendo transaccional y
  el resultado informa adquiridos, publicados y omitidos.

La validacion productiva final registro 2175 jugadores adquiridos y 1968
publicados. El mapping aprobado de equipos WhoScored/Football-Data, versionado
en `backend/config/whoscored-team-enrollment-approved.csv`, es un prerrequisito
reproducible del flujo.
