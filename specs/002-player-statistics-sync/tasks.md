---

description: "Tareas de implementaciÃ³n para la sincronizaciÃ³n de estadÃ­sticas de jugadores"
---

# Tasks: SincronizaciÃ³n de estadÃ­sticas de jugadores

**Input**: Design documents from /specs/002-player-statistics-sync/

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/player-statistics-sync.md, quickstart.md

**Tests**: incluidos porque la especificaciÃ³n y la ConstituciÃ³n exigen pruebas unitarias, de Adapter, integraciÃ³n con PostgreSQL/Testcontainers y casos de fallo.

**Scope**: se agrega ÃƒÂºnicamente el extremo de lectura local del detalle de jugador, no se modifican ni eliminan tests existentes y no se reintroducen campos legacy ni representaciones alternativas de Jugador/Equipo.

**Estado historico**: T001-T048 representan la implementacion del diseÃ±o
anterior en el commit e886221. Las tareas que dependian del contrato HTTP
ficticio quedan identificadas como **superseded** por la nueva etapa de
adquisicion; se conservan para trazabilidad y no se borran.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Preparar configuraciÃ³n y convenciones para la integraciÃ³n estadÃ­stica sin modificar todavÃ­a el comportamiento publicado.

- [X] T001 Revisar las convenciones actuales de paquetes, configuraciÃ³n, migraciones y pruebas en backend/ y documentar en la implementaciÃ³n los puntos de extensiÃ³n sin modificar tests existentes
- [X] T002 [P] Incorporar las propiedades de infraestructura de WhoScored, tamaÃ±o de lote, timeouts y lÃ­mites de respuesta en backend/src/main/resources/application.properties sin versionar secretos
- [X] T003 [P] Crear la estructura de paquetes adapter/dto, adapter/impl, modelo, persistence/dto, persistence y service/interfaces para la feature segÃºn specs/002-player-statistics-sync/plan.md

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Construir los contratos, persistencia y componentes compartidos que bloquean las tres historias.

**CRITICAL**: Esta fase debe completarse antes de implementar historias de usuario.

- [X] T004 [P] Crear los value objects internos CompeticionEstadistica, FamiliaEstadistica, RegistroEstadisticasJugador y PaginaEstadisticasJugador en backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/
- [X] T005 [P] Crear el contrato ProveedorEstadisticasJugador en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/ProveedorEstadisticasJugador.java sin tipos HTTP, JSON ni DTOs de WhoScored
- [X] T006 Crear las invariantes de EstadisticasJugador y sus grupos de mÃ©tricas en backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/ con ausencias nullable y rechazo de valores invÃ¡lidos
- [X] T007 Crear la migraciÃ³n Flyway para jugador_estadisticas, estadisticas_sync y estadisticas_sync_staging en backend/src/main/resources/db/migration/V15__Create_player_statistics_sync.sql con FK, unicidad, Ã­ndices y restricciones numÃ©ricas
- [X] T008 [P] Crear las entidades JPA de estadÃ­sticas publicadas, ejecuciÃ³n y staging en backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/dto/
- [X] T009 [P] Crear los repositories de estadÃ­sticas publicadas, ejecuciones y staging en backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/
- [X] T010 [P] Ampliar JugadorRepository y EquipoRepository en backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/ para obtener candidatos activos en lote por equipo/liga/nombre sin crear un repository de matching
- [X] T011 Crear las excepciones de aplicaciÃ³n para proveedor invÃ¡lido, identidad no resuelta, concurrencia y promociÃ³n fallida en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/exception/
- [X] T012 Implementar el mecanismo de exclusiÃ³n de sincronizaciÃ³n sin mantener una transacciÃ³n abierta durante la red en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/ y backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/
- [X] T013 [P] Crear pruebas unitarias de las invariantes y value objects en backend/src/test/java/com/devchesterunited/devchesterunitedv2/modelo/EstadisticasJugadorTest.java y backend/src/test/java/com/devchesterunited/devchesterunitedv2/modelo/EstadisticasPaginaTest.java
- [X] T014 Crear prueba de integraciÃ³n de la migraciÃ³n, restricciones y repositories en backend/src/test/java/com/devchesterunited/devchesterunitedv2/persistence/EstadisticasSchemaRepositoryIntegrationTest.java usando PostgreSQL/Testcontainers

**Checkpoint**: contratos internos, esquema, repositories, exclusiÃ³n y reglas de dominio disponibles para las historias.

---

## Phase 3: User Story 1 - Obtener estadÃ­sticas de las ligas soportadas (Priority: P1) ðŸŽ¯ MVP

**Goal**: Obtener y normalizar todos los jugadores de las cinco competiciones, vincular identidades locales de forma conservadora y dejar una fotografÃ­a estadÃ­stica publicada.

**Independent Test**: Con un Adapter controlado para las cinco competiciones, mÃºltiples pÃ¡ginas y familias separadas, ejecutar el Service y verificar que cada jugador resoluble tenga sus estadÃ­sticas general, ofensiva y defensiva asociadas en jugador_estadisticas.

### Tests for User Story 1

- [X] T015 [P] [US1] Crear pruebas del contrato del Adapter para DTOs vÃ¡lidos, familias, metadata de paginaciÃ³n y traducciÃ³n de errores en backend/src/test/java/com/devchesterunited/devchesterunitedv2/adapter/WhoScoredStatisticsAdapterTest.java
- [X] T016 [P] [US1] Crear pruebas unitarias del matching conservador de equipos en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/IdentidadEquipoResolverTest.java cubriendo cero, una, mÃºltiples y evidencia insuficiente
- [X] T017 [P] [US1] Crear pruebas unitarias del matching conservador de jugadores en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/IdentidadJugadorResolverTest.java cubriendo equipo, nombre, posiciÃ³n, nacionalidad, cero y mÃºltiples candidatos
- [X] T018 [P] [US1] Crear prueba de integraciÃ³n de enrolamiento inicial en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/EnrolamientoIdentidadesWhoScoredIntegrationTest.java verificando que las identidades WHOSCORED nuevas solo sean visibles despuÃ©s de una promociÃ³n exitosa y se reviertan ante fallo

### Implementation for User Story 1

- [X] T019 [P] [US1] Crear los DTOs exclusivos de WhoScored y sus validadores en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/dto/
- [X] T020 [US1] Implementar WhoScoredStatisticsAdapter en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/impl/WhoScoredStatisticsAdapter.java con paginaciÃ³n guiada por metadata y traducciÃ³n a value objects internos
- [X] T021 [US1] Implementar la normalizaciÃ³n de familias, nombres, posiciones, nacionalidades y mÃ©tricas del proveedor en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/impl/ sin filtrar DTOs hacia Service
- [X] T022 [US1] Implementar la resoluciÃ³n/enrolamiento determinista de equipos en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/IdentidadEquipoResolver.java usando nombre canÃ³nico exacto y contexto de competiciÃ³n
- [X] T023 [US1] Implementar la resoluciÃ³n/enrolamiento determinista de jugadores en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/IdentidadJugadorResolver.java usando equipo resuelto, nombre exacto y evidencia compatible de posiciÃ³n/nacionalidad
- [X] T024 [US1] Implementar la consolidaciÃ³n por playerId y familia en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/ConsolidadorEstadisticasJugador.java conservando mÃ©tricas ausentes sin inventar valores
- [X] T025 [US1] Implementar la escritura por lote de estadisticas_sync y estadisticas_sync_staging en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstadisticasStagingService.java manteniendo las identidades nuevas fuera del estado visible
- [X] T026 [US1] Implementar el caso de uso y la promociÃ³n bÃ¡sica completa en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/interfaces/SincronizacionEstadisticasJugadorService.java, backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/SincronizacionEstadisticasJugadorServiceImpl.java y backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstadisticasPromocionService.java para publicar jugador_estadisticas e identidades WHOSCORED nuevas en una Ãºnica transacciÃ³n, sin llamadas de red
- [X] T027 [US1] Crear la prueba de integraciÃ³n del flujo completo de las cinco competiciones, paginaciÃ³n variable y tres familias en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/SincronizacionEstadisticasJugadorIntegrationTest.java

**Checkpoint**: la historia P1 principal puede obtener, vincular, validar y publicar la fotografÃ­a estadÃ­stica completa, incluyendo identidades WHOSCORED nuevas, sin endpoint HTTP.

---

## Phase 4: User Story 2 - Mantener estadÃ­sticas consistentes y sin duplicados (Priority: P1)

**Goal**: Repetir y actualizar sincronizaciones sin duplicar estadÃ­sticas, jugadores ni identidades, consolidando todas las familias en una Ãºnica fila lÃ³gica por jugador.

**Independent Test**: Ejecutar dos sincronizaciones iguales y una con cambios; verificar una sola identidad WHOSCORED por entidad/proveedor, una sola fila de estadÃ­sticas por jugador y actualizaciÃ³n equivalente.

### Tests for User Story 2

- [X] T028 [P] [US2] Crear pruebas unitarias de idempotencia y consolidaciÃ³n repetida en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/ConsolidadorEstadisticasJugadorIdempotenciaTest.java
- [X] T029 [P] [US2] Crear prueba de integraciÃ³n de resoluciÃ³n por identidad persistida en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/IdentidadPersistidaWhoScoredIntegrationTest.java verificando que no se repita matching
- [X] T030 [P] [US2] Crear prueba de integraciÃ³n de upsert y actualizaciÃ³n de mÃ©tricas en backend/src/test/java/com/devchesterunited/devchesterunitedv2/persistence/EstadisticasJugadorUpsertIntegrationTest.java

### Implementation for User Story 2

- [X] T031 [US2] Implementar la reutilizaciÃ³n prioritaria de identidades WHOSCORED persistidas en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/IdentidadJugadorResolver.java antes de ejecutar matching inicial
- [X] T032 [US2] Reforzar el upsert idempotente de jugador_estadisticas por jugador_id para ejecuciones repetidas en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstadisticasPromocionService.java sin crear filas nuevas para jugadores existentes
- [X] T033 [US2] Aplicar las restricciones y operaciones de persistencia para evitar duplicados de identidades y estadÃ­sticas durante reintentos en backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/
- [X] T034 [US2] Verificar y consolidar la actualizaciÃ³n de las familias general, ofensiva y defensiva en una Ãºnica fotografÃ­a por jugador durante ejecuciones repetidas en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/ConsolidadorEstadisticasJugador.java

**Checkpoint**: las ejecuciones repetidas son idempotentes y las modificaciones vÃ¡lidas actualizan el estado sin duplicados.

---

## Phase 5: User Story 3 - Conservar el Ãºltimo estado vÃ¡lido ante fallos (Priority: P1)

**Goal**: Rechazar fallos de proveedor, validaciÃ³n, identidad, staging o promociÃ³n sin mostrar datos parciales ni perder el snapshot anterior.

**Independent Test**: Partir de estadÃ­sticas publicadas, provocar fallos antes, durante y despuÃ©s de la obtenciÃ³n, y comprobar que estadÃ­sticas e identidades visibles permanecen sin cambios.

### Tests for User Story 3

- [X] T035 [P] [US3] Crear pruebas del Adapter para HTTP no exitoso, timeout, JSON invÃ¡lido, cuota y metadata incompleta en backend/src/test/java/com/devchesterunited/devchesterunitedv2/adapter/WhoScoredStatisticsAdapterFailureTest.java
- [X] T036 [P] [US3] Crear prueba de integraciÃ³n de identidad no resuelta y evidencia ambigua en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/SincronizacionIdentidadNoResueltaIntegrationTest.java
- [X] T037 [P] [US3] Crear prueba de integraciÃ³n de rollback de promociÃ³n y conservaciÃ³n del snapshot anterior en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/SincronizacionEstadisticasAtomicidadIntegrationTest.java
- [X] T038 [P] [US3] Crear prueba de concurrencia de sincronizaciones en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/SincronizacionEstadisticasConcurrenciaIntegrationTest.java usando PostgreSQL/Testcontainers

### Implementation for User Story 3

- [X] T039 [US3] Implementar estados de ejecuciÃ³n, errores sanitizados y limpieza/marcado de staging fallido en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstadisticasSyncService.java
- [X] T040 [US3] Endurecer la promociÃ³n transaccional de estadÃ­sticas, identidades WHOSCORED nuevas y estado de ejecuciÃ³n ante errores de persistencia en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstadisticasPromocionService.java
- [X] T041 [US3] Garantizar rollback ante fallo de persistencia y conservaciÃ³n del snapshot anterior en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/SincronizacionEstadisticasJugadorServiceImpl.java
- [X] T042 [US3] Traducir errores de Adapter, matching y persistencia a excepciones de aplicaciÃ³n sin filtrar detalles de WhoScored en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/exception/ y backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/
- [X] T043 [US3] Integrar la exclusiÃ³n de concurrencia y liberaciÃ³n segura en finally en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/SincronizacionEstadisticasJugadorServiceImpl.java

**Checkpoint**: cualquier fallo conserva el Ãºltimo estado vÃ¡lido y una segunda sincronizaciÃ³n concurrente no publica ni consulta de forma indebida.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validar el resultado completo contra ConstituciÃ³n, quickstart y calidad del proyecto.

- [X] T044 [P] Revisar que ningÃºn DTO, URL, stage ID, parÃ¡metro HTTP o error interno de WhoScored cruce Adapter en backend/src/main/java/com/devchesterunited/devchesterunitedv2/
- [X] T045 [P] Revisar que no se hayan reintroducido external_id ni creado representaciones alternativas o tabla de matching en backend/src/main/java/ y backend/src/main/resources/db/migration/
- [X] T046 [P] AÃ±adir o ajustar documentaciÃ³n JavaDoc de los puertos, value objects, resolvers y estados en backend/src/main/java/com/devchesterunited/devchesterunitedv2/
- [X] T047 Ejecutar la validaciÃ³n completa definida en specs/002-player-statistics-sync/quickstart.md desde backend/ y corregir Ãºnicamente fallos introducidos por la feature
- [X] T048 Ejecutar compilaciÃ³n y suite completa con backend/gradlew.bat test --no-daemon --console=plain y verificar que todos los tests existentes permanecen intactos

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup (Phase 1): sin dependencias.
- Foundational (Phase 2): depende de Setup y bloquea todas las historias.
- User Story 1: depende de Foundational y es el MVP funcional.
- User Story 2: depende de los componentes de publicaciÃ³n de US1, aunque sus pruebas de idempotencia pueden prepararse en paralelo despuÃ©s de Foundational.
- User Story 3: depende de staging/promociÃ³n de US1 y del upsert de US2 para probar rollback completo.
- Polish: depende de las historias implementadas.
- Phase 7 (rediseÃ±o statisticsfeed): depende de Phase 2 y de las garantÃ­as de staging/promociÃ³n de US1-US3; T049-T051 preceden a T052-T054, T055-T056 validan el Adapter, T057 adapta el Service y T058-T060 cierran la validaciÃ³n.

### User Story Dependencies

- US1 (P1): despuÃ©s de Phase 2; entrega obtenciÃ³n, normalizaciÃ³n y enrolamiento inicial.
- US2 (P1): despuÃ©s de T026-T027; reutiliza la promociÃ³n de US1 y aÃ±ade reutilizaciÃ³n de identidades, upsert idempotente y ausencia de duplicados.
- US3 (P1): despuÃ©s de T031-T034; endurece la promociÃ³n de US1/US2 y valida atomicidad, fallos y concurrencia.

### Parallel Opportunities

- T002-T003 pueden ejecutarse en paralelo.
- T004-T006, T008-T011 y T013 pueden ejecutarse en paralelo dentro de Foundational cuando no compartan archivos.
- T015-T018 son pruebas independientes de US1.
- T019, T022 y T023 pueden prepararse en paralelo si se respetan sus archivos separados; T023 depende conceptualmente de la resoluciÃ³n de equipos.
- T028-T030 y T035-T038 son grupos de pruebas paralelizables.
- T044-T046 son revisiones independientes.
- En Phase 7, T049-T051 pueden prepararse en paralelo; T052 precede a T053-T054; T055-T056 pueden ejecutarse en paralelo despues del contrato del Adapter; T057 depende de T049-T054.

## Parallel Example: User Story 1

DespuÃ©s de completar Phase 2:

- T015: pruebas aisladas del Adapter.
- T016: pruebas del resolver de equipos.
- T017: pruebas del resolver de jugadores.
- T018: integraciÃ³n de enrolamiento inicial.

Luego se implementan T019-T026 respetando las dependencias de cada componente y se ejecuta T027 como validaciÃ³n independiente de la historia, verificando datos publicados e identidades visibles Ãºnicamente tras el commit.

## Implementation Strategy

### MVP First

1. Completar Setup y Foundational.
2. Completar US1, incluyendo enrolamiento inicial y flujo de las cinco ligas.
3. Ejecutar T027 y validar la publicaciÃ³n controlada.
4. Continuar con US2 y US3 antes de considerar la feature terminada, porque la ConstituciÃ³n exige idempotencia y atomicidad ante fallos.

### Incremental Delivery

1. Infraestructura y contratos internos.
2. ObtenciÃ³n, normalizaciÃ³n y enrolamiento inicial.
3. Upsert e idempotencia.
4. Rollback, conservaciÃ³n del snapshot y concurrencia.
5. ValidaciÃ³n completa y calidad.

## Phase 7: RediseÃ±o de adquisiciÃ³n statisticsfeed

**Purpose**: Reemplazar el mecanismo HTTP ficticio por la adquisiciÃ³n real
validada mediante Selenium, sin modificar las garantÃ­as funcionales de la
sincronizaciÃ³n.

**Estado**: completada y validada.

### Trazabilidad de tareas superseded

- T019, T020 y T021 quedan superseded por T049-T054: describÃ­an DTOs y
  paginaciÃ³n del contrato HTTP ficticio.
- T035 queda superseded por T055-T057: describÃ­a fallos HTTP del mecanismo
  anterior; sus garantÃ­as funcionales se conservan y se trasladan a Selenium,
  JSON controlado y validaciÃ³n de completitud.
- T044 queda superseded por T058: la revisiÃ³n debe adaptarse a Selenium,
  statisticsfeed y sus detalles encapsulados.
- T002 conserva su intenciÃ³n de configuraciÃ³n de infraestructura, pero los
  parÃ¡metros del proveedor deben revisarse conforme al nuevo Adapter.

### Nuevas tareas

- [X] T049 [P] [US1] Definir el contrato interno evolucionado de `ProveedorEstadisticasJugador` para adquirir un snapshot completo por competiciÃ³n, consolidado por playerId y sin exponer familias, pÃ¡ginas, pageSize, stageId, tournamentOptions, Selenium ni DTOs externos en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/ProveedorEstadisticasJugador.java, backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/SnapshotEstadisticasJugador.java, backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/SincronizacionEstadisticasJugadorServiceImpl.java, backend/src/test/java/com/devchesterunited/devchesterunitedv2/modelo/SnapshotEstadisticasJugadorTest.java y specs/002-player-statistics-sync/contracts/player-statistics-sync.md
- [X] T050 [P] [US1] Definir la configuraciÃ³n interna de las cinco parejas tournamentOptions/stageId y los parÃ¡metros comunes de statisticsfeed exclusivamente dentro de backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/ y backend/src/main/resources/application.properties
- [X] T051 [P] [US1] Crear los DTOs/fixtures internos para las respuestas reales de summary/all, summary/offensive y summary/defensive, manteniÃ©ndolos dentro de backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/dto/ y sin agregar mÃ©tricas de dominio fuera de FR-003/FR-004
- [X] T052 [US1] Implementar la creaciÃ³n, reutilizaciÃ³n y cierre garantizado de una Ãºnica sesiÃ³n WebDriver por sincronizaciÃ³n completa, incluyendo cierre ante excepciones, en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/impl/ y backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/
- [X] T053 [US1] Implementar la navegaciÃ³n Selenium al endpoint /statisticsfeed/1/getplayerstatistics, lectura del JSON del body, adquisiciÃ³n de las tres familias, traducciÃ³n y consolidaciÃ³n por playerId dentro de backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/impl/WhoScoredStatisticsAdapter.java sin llamadas desde Service

## Cierre final de la feature

La implementacion vigente queda cerrada con snapshots adquiridos por Selenium
headed, cinco competiciones, isMinApp=false, numberOfPlayersToPick=500,
page=1 y sin paginacion productiva. El mapping aprobado versionado es un
prerrequisito reproducible y no se regenera automaticamente. La evidencia
productiva validada es 2175 adquiridos, 1968 publicados y 207 omitidos.
Las referencias anteriores a HTTP, paginacion y PaginaEstadisticasJugador
permanecen solamente como trazabilidad historica superseded.
- [X] T054 [US1] Implementar la validaciÃ³n sin paginaciÃ³n de currentPage == 1, totalPages == 1, players.size == totalResults y ausencia de contradicciones en metadata, con fallo controlado ante respuesta incompleta en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/impl/
- [X] T055 [P] [US1] Reemplazar las pruebas acopladas al JSON ficticio por pruebas con JSON controlado, fixtures y fakes que cubran las tres familias, metadata vÃ¡lida e invÃ¡lida y traducciÃ³n en backend/src/test/java/com/devchesterunited/devchesterunitedv2/adapter/
- [X] T056 [P] [US3] Cubrir fallos de Selenium, body invÃ¡lido, metadata inconsistente, adquisiciÃ³n incompleta y errores sanitizados sin conectividad real en backend/src/test/java/com/devchesterunited/devchesterunitedv2/adapter/ y backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/
- [X] T057 [US1] Adaptar el flujo del Service para consumir un snapshot completo por competiciÃ³n, validar las cinco competiciones antes de staging validado y eliminar su conocimiento de familias y paginaciÃ³n del proveedor en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/
- [X] T058 [P] [US3] Revisar que URLs, stage IDs, tournamentOptions, Selenium, nombres JSON y errores del proveedor permanezcan confinados al Adapter en backend/src/main/java/com/devchesterunited/devchesterunitedv2/
- [X] T059 [US1] Verificar con fixtures que las mÃ©tricas persistidas continÃºan limitadas a FR-003/FR-004 aunque WhoScored entregue campos adicionales en backend/src/test/java/com/devchesterunited/devchesterunitedv2/adapter/ y backend/src/test/java/com/devchesterunited/devchesterunitedv2/modelo/
- [X] T060 [US3] Ejecutar la validaciÃ³n manual diagnÃ³stica con una Ãºnica sesiÃ³n WebDriver para las cinco competiciones, registrar completitud y cierre ante excepciÃ³n sin incorporar navegaciÃ³n real a la suite automatizada en specs/002-player-statistics-sync/quickstart.md

---

## ExtensiÃƒÂ³n de lectura del catÃƒÂ¡logo

La siguiente tarea extiende esta misma feature para comprobar la lectura de las
estadÃƒÂ­sticas ya publicadas. No reabre la adquisiciÃƒÂ³n ni crea una nueva feature.

- [X] T061 [US4] Implementar `GET /api/v1/players/{id}` con `ply_<uuid>`, detalle del jugador activo de equipo activo, `statistics` opcional leÃƒÂ­do exclusivamente mediante `JugadorRepository` y `EstadisticasJugadorRepository.findByJugadorId(UUID)`, HTTP 200 con estadÃƒÂ­sticas o `null`, HTTP 404 para inexistentes/inactivos, contrato de detalle separado del listado y sin invocar WhoScored/Selenium/sincronizaciÃƒÂ³n; modificar o crear ÃƒÂºnicamente los componentes productivos necesarios en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/JugadorController.java`, `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/interfaces/JugadorService.java`, `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/JugadorServiceImpl.java`, `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/JugadorRepository.java`, DTOs de `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/` y resultados de `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/dto/`, manteniendo intacto `GET /api/v1/players`.

## Notes

Las pruebas previstas para T061 deben crearse como una suite nueva, por
ejemplo `backend/src/test/java/com/devchesterunited/devchesterunitedv2/controller/JugadorDetalleE2ETest.java`, y pueden complementarse con una prueba nueva de Service o Repository sin modificar suites existentes.
- La resolucion de 404 puede requerir el handler y/o la excepcion de aplicacion
  correspondiente en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/exceptionhandler/` y `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/exception/`; esos archivos quedan dentro del alcance de T061 solo si son necesarios.

- Todas las tareas usan formato checklist, ID secuencial, marca [P] solo cuando corresponde, etiqueta de historia en fases de usuario y ruta de archivo.
- La implementaciÃ³n del endpoint HTTP queda acotada a T061; no incluye frontend.
- No se permite modificar o borrar tests existentes.
- T061 debe agregar pruebas nuevas para jugador con estadÃƒÂ­sticas, jugador sin
  estadÃƒÂ­sticas, inexistente, jugador/equipo inactivo, aislamiento frente a
  WhoScored y preservaciÃƒÂ³n del contrato existente del listado. No se pueden
  modificar ni borrar tests existentes sin autorizaciÃƒÂ³n explÃƒÂ­cita.
- Las identidades nuevas WHOSCORED solo son visibles si la promociÃ³n completa termina correctamente; staging nunca las publica.
- La red se ejecuta antes de la transacciÃ³n de promociÃ³n y nunca dentro de ella.

---

## Phase 8: User Story 5 - Visualizar estadisticas desde el catalogo (Priority: P1) ðŸŽ¯ MVP frontend

**Goal**: Permitir seleccionar un jugador desde el catalogo, consultar su detalle local y visualizar sus datos basicos y las diez metricas requeridas, incluyendo estados de carga, error e inexistencia.

**Independent Test**: Con respuestas controladas del endpoint existente, seleccionar una card/fila, verificar la navegacion a `/catalogo/jugadores/{id}`, el consumo de `/api/v1/players/{id}`, el renderizado de metricas, `statistics: null`, errores y el regreso al catalogo.

### Tests for User Story 5

- [X] T062 [P] [US5] Agregar pruebas de `getPlayerDetail` para URL con identificador opaco, envoltorio `data` y errores HTTP en frontend/tests/http-player-detail.test.js
- [X] T063 [P] [US5] Crear pruebas de la vista de detalle para carga, datos basicos y diez metricas, `statistics: null`, HTTP 404, error de red y accion de regreso en frontend/tests/player-detail.test.jsx

### Implementation for User Story 5

- [X] T064 [US5] Implementar `getPlayerDetail(id)` reutilizando `request` y el contrato `GET /api/v1/players/{id}` en frontend/src/api/http.js
- [X] T065 [US5] Crear `PlayerDetailPage` con estados loading, success-with-statistics, success-without-statistics y error, datos basicos, diez metricas y enlace accesible de regreso en frontend/src/views/PlayerDetailPage.jsx
- [X] T066 [US5] Extender la resolucion de rutas y el renderizado de vistas para `/catalogo/jugadores/{id}` sin agregar una dependencia de routing en frontend/src/App.jsx
- [X] T067 [P] [US5] Agregar estilos de detalle coherentes con el sistema visual existente y sus estados responsivos en frontend/src/styles/player-detail.css y frontend/src/App.jsx
- [X] T068 [US5] Crear el recorrido E2E catalogo -> detalle -> volver, incluyendo datos con estadisticas, `statistics: null`, error/404 y bloqueo de hosts externos en frontend/e2e/player-detail.spec.js

**Checkpoint**: La User Story 5 funciona de forma independiente consumiendo exclusivamente el endpoint backend documentado.

---

## Phase 9: User Story 6 - Identificar jugadores por nacionalidad en el catalogo (Priority: P2)

**Goal**: Reemplazar el dorsal no confiable por la nacionalidad disponible y conservar cards/filas navegables sin ampliar scraping, matching, sincronizacion ni persistencia.

**Independent Test**: Renderizar jugadores con nacionalidad y dorsal ausente, verificar que aparece Nacionalidad, que no aparece Dorsal ni `0`, y que la seleccion conserva la navegacion al detalle.

### Tests for User Story 6

- [X] T069 [P] [US6] Crear pruebas de la presentacion de nacionalidad, ausencia de dorsal, ausencia de nacionalidad y navegacion accesible de la card en frontend/tests/catalog-player-card.test.jsx

### Implementation for User Story 6

- [X] T070 [US6] Reemplazar la columna y los campos visuales Dorsal por Nacionalidad, envolver cada jugador en un enlace accesible al detalle y conservar filtros/paginacion en frontend/src/views/CatalogPage.jsx
- [X] T071 [P] [US6] Ajustar estilos de tabla responsive, foco de teclado y estado navegable de filas/cards sin rediseÃ±ar el catalogo en frontend/src/styles/catalog.css

**Checkpoint**: Las cards/filas muestran nacionalidad, no muestran dorsal ni `0`, y siguen conduciendo al detalle.

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Validar el cierre frontend contra la constitucion, el contrato y el quickstart sin modificar tests existentes.

- [X] T072 [P] Revisar que los cambios frontend respeten 2 espacios, comillas simples, ausencia de punto y coma, JSX legible y accesibilidad en frontend/src/
- [X] T073 Ejecutar `npm test -- --run`, `npm run build` y `npm run verify:frontend` desde frontend/ y documentar el resultado conforme a specs/002-player-statistics-sync/quickstart.md

---

## Updated Dependencies & Execution Order

### New User Story Dependencies

- **US5 (P1 frontend)**: depende de T061 y del contrato existente de detalle; T062-T063 pueden prepararse en paralelo, T064 precede a T065, y T066-T068 integran la navegaciÃ³n y validaciÃ³n.
- **US6 (P2)**: depende de la ruta de detalle de US5 para verificar la navegaciÃ³n completa; T069 puede prepararse en paralelo con T070-T071, y la historia se valida despuÃ©s de T070.
- **Polish**: T072-T073 dependen de US5 y US6.

### Parallel Opportunities

- T062 y T063 pueden ejecutarse en paralelo porque cubren archivos de prueba distintos.
- T067 puede ejecutarse en paralelo con T064-T066 al trabajar sobre la hoja de estilos y su importaciÃ³n definida.
- T069, T070 y T071 pueden prepararse en paralelo cuando se coordine la ediciÃ³n de `CatalogPage.jsx` y `catalog.css`.
- T072 y la preparaciÃ³n de T073 son independientes entre sÃ­.

## Parallel Example: User Story 5

```text
T062: prueba del cliente HTTP en frontend/tests/http-player-detail.test.js
T063: pruebas de PlayerDetailPage en frontend/tests/player-detail.test.jsx
T067: estilos de detalle en frontend/src/styles/player-detail.css
```

## Updated Implementation Strategy

### MVP frontend

1. Completar T062-T068 y validar la User Story 5 de forma independiente.
2. Confirmar que el detalle consume solo `GET /api/v1/players/{id}` y que los estados de error/ausencia son recuperables.
3. Continuar con T069-T071 para cerrar la sustitucion del dorsal por nacionalidad.
4. Ejecutar T072-T073 antes de considerar terminado el cierre frontend.

### Nota de testing

La constitucion prohÃ­be modificar o borrar tests existentes sin autorizacion
explicita. Las tareas nuevas agregan cobertura en archivos nuevos; si una
expectativa existente sobre Dorsal requiere actualizacion por el cambio de
requisito, debe solicitarse primero esa autorizacion y conservarse la cobertura
equivalente para Nacionalidad.

**Estado de validacion**: T062-T073 completadas. La asercion obsoleta de Dorsal
en frontend/tests/views.test.jsx fue actualizada unicamente a Nacionalidad con
autorizacion explicita. La suite frontend completa, el build y
verify:frontend pasan.

---

## AmpliaciÃ³n actual (tareas renumeradas)

---  description: "Tareas de implementaciï¿½n para la sincronizaciï¿½n de estadï¿½sticas de jugadores" ---  # Tasks: Sincronizaciï¿½n de estadï¿½sticas de jugadores  **Input**: Design documents from /specs/002-player-statistics-sync/  **Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/player-statistics-sync.md, quickstart.md  **Tests**: incluidos porque la especificaciï¿½n y la Constituciï¿½n exigen pruebas unitarias, de Adapter, integraciï¿½n con PostgreSQL/Testcontainers y casos de fallo.  **Scope**: se agrega Ãºnicamente el extremo de lectura local del detalle de jugador, no se modifican ni eliminan tests existentes y no se reintroducen campos legacy ni representaciones alternativas de Jugador/Equipo.  **Estado historico**: T074-T121 representan la implementacion del diseï¿½o anterior en el commit e886221. Las tareas que dependian del contrato HTTP ficticio quedan identificadas como **superseded** por la nueva etapa de adquisicion; se conservan para trazabilidad y no se borran.  ## Phase 1: Setup (Shared Infrastructure)  **Purpose**: Preparar configuraciï¿½n y convenciones para la integraciï¿½n estadï¿½stica sin modificar todavï¿½a el comportamiento publicado.  - [X] T074 Revisar las convenciones actuales de paquetes, configuraciï¿½n, migraciones y pruebas en backend/ y documentar en la implementaciï¿½n los puntos de extensiï¿½n sin modificar tests existentes - [X] T075 [P] Incorporar las propiedades de infraestructura de WhoScored, tamaï¿½o de lote, timeouts y lï¿½mites de respuesta en backend/src/main/resources/application.properties sin versionar secretos - [X] T076 [P] Crear la estructura de paquetes adapter/dto, adapter/impl, modelo, persistence/dto, persistence y service/interfaces para la feature segï¿½n specs/002-player-statistics-sync/plan.md  ---  ## Phase 2: Foundational (Blocking Prerequisites)  **Purpose**: Construir los contratos, persistencia y componentes compartidos que bloquean las tres historias.  **CRITICAL**: Esta fase debe completarse antes de implementar historias de usuario.  - [X] T077 [P] Crear los value objects internos CompeticionEstadistica, FamiliaEstadistica, RegistroEstadisticasJugador y PaginaEstadisticasJugador en backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/ - [X] T078 [P] Crear el contrato ProveedorEstadisticasJugador en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/ProveedorEstadisticasJugador.java sin tipos HTTP, JSON ni DTOs de WhoScored - [X] T079 Crear las invariantes de EstadisticasJugador y sus grupos de mï¿½tricas en backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/ con ausencias nullable y rechazo de valores invï¿½lidos - [X] T080 Crear la migraciï¿½n Flyway para jugador_estadisticas, estadisticas_sync y estadisticas_sync_staging en backend/src/main/resources/db/migration/V15__Create_player_statistics_sync.sql con FK, unicidad, ï¿½ndices y restricciones numï¿½ricas - [X] T081 [P] Crear las entidades JPA de estadï¿½sticas publicadas, ejecuciï¿½n y staging en backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/dto/ - [X] T082 [P] Crear los repositories de estadï¿½sticas publicadas, ejecuciones y staging en backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/ - [X] T083 [P] Ampliar JugadorRepository y EquipoRepository en backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/ para obtener candidatos activos en lote por equipo/liga/nombre sin crear un repository de matching - [X] T084 Crear las excepciones de aplicaciï¿½n para proveedor invï¿½lido, identidad no resuelta, concurrencia y promociï¿½n fallida en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/exception/ - [X] T085 Implementar el mecanismo de exclusiï¿½n de sincronizaciï¿½n sin mantener una transacciï¿½n abierta durante la red en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/ y backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/ - [X] T086 [P] Crear pruebas unitarias de las invariantes y value objects en backend/src/test/java/com/devchesterunited/devchesterunitedv2/modelo/EstadisticasJugadorTest.java y backend/src/test/java/com/devchesterunited/devchesterunitedv2/modelo/EstadisticasPaginaTest.java - [X] T087 Crear prueba de integraciï¿½n de la migraciï¿½n, restricciones y repositories en backend/src/test/java/com/devchesterunited/devchesterunitedv2/persistence/EstadisticasSchemaRepositoryIntegrationTest.java usando PostgreSQL/Testcontainers  **Checkpoint**: contratos internos, esquema, repositories, exclusiï¿½n y reglas de dominio disponibles para las historias.  ---  ## Phase 3: User Story 1 - Obtener estadï¿½sticas de las ligas soportadas (Priority: P1) ?? MVP  **Goal**: Obtener y normalizar todos los jugadores de las cinco competiciones, vincular identidades locales de forma conservadora y dejar una fotografï¿½a estadï¿½stica publicada.  **Independent Test**: Con un Adapter controlado para las cinco competiciones, mï¿½ltiples pï¿½ginas y familias separadas, ejecutar el Service y verificar que cada jugador resoluble tenga sus estadï¿½sticas general, ofensiva y defensiva asociadas en jugador_estadisticas.  ### Tests for User Story 1  - [X] T088 [P] [US1] Crear pruebas del contrato del Adapter para DTOs vï¿½lidos, familias, metadata de paginaciï¿½n y traducciï¿½n de errores en backend/src/test/java/com/devchesterunited/devchesterunitedv2/adapter/WhoScoredStatisticsAdapterTest.java - [X] T089 [P] [US1] Crear pruebas unitarias del matching conservador de equipos en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/IdentidadEquipoResolverTest.java cubriendo cero, una, mï¿½ltiples y evidencia insuficiente - [X] T090 [P] [US1] Crear pruebas unitarias del matching conservador de jugadores en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/IdentidadJugadorResolverTest.java cubriendo equipo, nombre, posiciï¿½n, nacionalidad, cero y mï¿½ltiples candidatos - [X] T091 [P] [US1] Crear prueba de integraciï¿½n de enrolamiento inicial en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/EnrolamientoIdentidadesWhoScoredIntegrationTest.java verificando que las identidades WHOSCORED nuevas solo sean visibles despuï¿½s de una promociï¿½n exitosa y se reviertan ante fallo  ### Implementation for User Story 1  - [X] T092 [P] [US1] Crear los DTOs exclusivos de WhoScored y sus validadores en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/dto/ - [X] T093 [US1] Implementar WhoScoredStatisticsAdapter en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/impl/WhoScoredStatisticsAdapter.java con paginaciï¿½n guiada por metadata y traducciï¿½n a value objects internos - [X] T094 [US1] Implementar la normalizaciï¿½n de familias, nombres, posiciones, nacionalidades y mï¿½tricas del proveedor en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/impl/ sin filtrar DTOs hacia Service - [X] T095 [US1] Implementar la resoluciï¿½n/enrolamiento determinista de equipos en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/IdentidadEquipoResolver.java usando nombre canï¿½nico exacto y contexto de competiciï¿½n - [X] T096 [US1] Implementar la resoluciï¿½n/enrolamiento determinista de jugadores en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/IdentidadJugadorResolver.java usando equipo resuelto, nombre exacto y evidencia compatible de posiciï¿½n/nacionalidad - [X] T097 [US1] Implementar la consolidaciï¿½n por playerId y familia en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/ConsolidadorEstadisticasJugador.java conservando mï¿½tricas ausentes sin inventar valores - [X] T098 [US1] Implementar la escritura por lote de estadisticas_sync y estadisticas_sync_staging en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstadisticasStagingService.java manteniendo las identidades nuevas fuera del estado visible - [X] T099 [US1] Implementar el caso de uso y la promociï¿½n bï¿½sica completa en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/interfaces/SincronizacionEstadisticasJugadorService.java, backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/SincronizacionEstadisticasJugadorServiceImpl.java y backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstadisticasPromocionService.java para publicar jugador_estadisticas e identidades WHOSCORED nuevas en una ï¿½nica transacciï¿½n, sin llamadas de red - [X] T100 [US1] Crear la prueba de integraciï¿½n del flujo completo de las cinco competiciones, paginaciï¿½n variable y tres familias en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/SincronizacionEstadisticasJugadorIntegrationTest.java  **Checkpoint**: la historia P1 principal puede obtener, vincular, validar y publicar la fotografï¿½a estadï¿½stica completa, incluyendo identidades WHOSCORED nuevas, sin endpoint HTTP.  ---  ## Phase 4: User Story 2 - Mantener estadï¿½sticas consistentes y sin duplicados (Priority: P1)  **Goal**: Repetir y actualizar sincronizaciones sin duplicar estadï¿½sticas, jugadores ni identidades, consolidando todas las familias en una ï¿½nica fila lï¿½gica por jugador.  **Independent Test**: Ejecutar dos sincronizaciones iguales y una con cambios; verificar una sola identidad WHOSCORED por entidad/proveedor, una sola fila de estadï¿½sticas por jugador y actualizaciï¿½n equivalente.  ### Tests for User Story 2  - [X] T101 [P] [US2] Crear pruebas unitarias de idempotencia y consolidaciï¿½n repetida en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/ConsolidadorEstadisticasJugadorIdempotenciaTest.java - [X] T102 [P] [US2] Crear prueba de integraciï¿½n de resoluciï¿½n por identidad persistida en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/IdentidadPersistidaWhoScoredIntegrationTest.java verificando que no se repita matching - [X] T103 [P] [US2] Crear prueba de integraciï¿½n de upsert y actualizaciï¿½n de mï¿½tricas en backend/src/test/java/com/devchesterunited/devchesterunitedv2/persistence/EstadisticasJugadorUpsertIntegrationTest.java  ### Implementation for User Story 2  - [X] T104 [US2] Implementar la reutilizaciï¿½n prioritaria de identidades WHOSCORED persistidas en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/IdentidadJugadorResolver.java antes de ejecutar matching inicial - [X] T105 [US2] Reforzar el upsert idempotente de jugador_estadisticas por jugador_id para ejecuciones repetidas en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstadisticasPromocionService.java sin crear filas nuevas para jugadores existentes - [X] T106 [US2] Aplicar las restricciones y operaciones de persistencia para evitar duplicados de identidades y estadï¿½sticas durante reintentos en backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/ - [X] T107 [US2] Verificar y consolidar la actualizaciï¿½n de las familias general, ofensiva y defensiva en una ï¿½nica fotografï¿½a por jugador durante ejecuciones repetidas en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/ConsolidadorEstadisticasJugador.java  **Checkpoint**: las ejecuciones repetidas son idempotentes y las modificaciones vï¿½lidas actualizan el estado sin duplicados.  ---  ## Phase 5: User Story 3 - Conservar el ï¿½ltimo estado vï¿½lido ante fallos (Priority: P1)  **Goal**: Rechazar fallos de proveedor, validaciï¿½n, identidad, staging o promociï¿½n sin mostrar datos parciales ni perder el snapshot anterior.  **Independent Test**: Partir de estadï¿½sticas publicadas, provocar fallos antes, durante y despuï¿½s de la obtenciï¿½n, y comprobar que estadï¿½sticas e identidades visibles permanecen sin cambios.  ### Tests for User Story 3  - [X] T108 [P] [US3] Crear pruebas del Adapter para HTTP no exitoso, timeout, JSON invï¿½lido, cuota y metadata incompleta en backend/src/test/java/com/devchesterunited/devchesterunitedv2/adapter/WhoScoredStatisticsAdapterFailureTest.java - [X] T109 [P] [US3] Crear prueba de integraciï¿½n de identidad no resuelta y evidencia ambigua en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/SincronizacionIdentidadNoResueltaIntegrationTest.java - [X] T110 [P] [US3] Crear prueba de integraciï¿½n de rollback de promociï¿½n y conservaciï¿½n del snapshot anterior en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/SincronizacionEstadisticasAtomicidadIntegrationTest.java - [X] T111 [P] [US3] Crear prueba de concurrencia de sincronizaciones en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/SincronizacionEstadisticasConcurrenciaIntegrationTest.java usando PostgreSQL/Testcontainers  ### Implementation for User Story 3  - [X] T112 [US3] Implementar estados de ejecuciï¿½n, errores sanitizados y limpieza/marcado de staging fallido en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstadisticasSyncService.java - [X] T113 [US3] Endurecer la promociï¿½n transaccional de estadï¿½sticas, identidades WHOSCORED nuevas y estado de ejecuciï¿½n ante errores de persistencia en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstadisticasPromocionService.java - [X] T114 [US3] Garantizar rollback ante fallo de persistencia y conservaciï¿½n del snapshot anterior en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/SincronizacionEstadisticasJugadorServiceImpl.java - [X] T115 [US3] Traducir errores de Adapter, matching y persistencia a excepciones de aplicaciï¿½n sin filtrar detalles de WhoScored en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/exception/ y backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/ - [X] T116 [US3] Integrar la exclusiï¿½n de concurrencia y liberaciï¿½n segura en finally en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/SincronizacionEstadisticasJugadorServiceImpl.java  **Checkpoint**: cualquier fallo conserva el ï¿½ltimo estado vï¿½lido y una segunda sincronizaciï¿½n concurrente no publica ni consulta de forma indebida.  ---  ## Phase 6: Polish & Cross-Cutting Concerns  **Purpose**: Validar el resultado completo contra Constituciï¿½n, quickstart y calidad del proyecto.  - [X] T117 [P] Revisar que ningï¿½n DTO, URL, stage ID, parï¿½metro HTTP o error interno de WhoScored cruce Adapter en backend/src/main/java/com/devchesterunited/devchesterunitedv2/ - [X] T118 [P] Revisar que no se hayan reintroducido external_id ni creado representaciones alternativas o tabla de matching en backend/src/main/java/ y backend/src/main/resources/db/migration/ - [X] T119 [P] Aï¿½adir o ajustar documentaciï¿½n JavaDoc de los puertos, value objects, resolvers y estados en backend/src/main/java/com/devchesterunited/devchesterunitedv2/ - [X] T120 Ejecutar la validaciï¿½n completa definida en specs/002-player-statistics-sync/quickstart.md desde backend/ y corregir ï¿½nicamente fallos introducidos por la feature - [X] T121 Ejecutar compilaciï¿½n y suite completa con backend/gradlew.bat test --no-daemon --console=plain y verificar que todos los tests existentes permanecen intactos  ---  ## Dependencies & Execution Order  ### Phase Dependencies  - Setup (Phase 1): sin dependencias. - Foundational (Phase 2): depende de Setup y bloquea todas las historias. - User Story 1: depende de Foundational y es el MVP funcional. - User Story 2: depende de los componentes de publicaciï¿½n de US1, aunque sus pruebas de idempotencia pueden prepararse en paralelo despuï¿½s de Foundational. - User Story 3: depende de staging/promociï¿½n de US1 y del upsert de US2 para probar rollback completo. - Polish: depende de las historias implementadas. - Phase 7 (rediseï¿½o statisticsfeed): depende de Phase 2 y de las garantï¿½as de staging/promociï¿½n de US1-US3; T122-T124 preceden a T125-T127, T128-T129 validan el Adapter, T130 adapta el Service y T131-T133 cierran la validaciï¿½n.  ### User Story Dependencies  - US1 (P1): despuï¿½s de Phase 2; entrega obtenciï¿½n, normalizaciï¿½n y enrolamiento inicial. - US2 (P1): despuï¿½s de T099-T100; reutiliza la promociï¿½n de US1 y aï¿½ade reutilizaciï¿½n de identidades, upsert idempotente y ausencia de duplicados. - US3 (P1): despuï¿½s de T104-T107; endurece la promociï¿½n de US1/US2 y valida atomicidad, fallos y concurrencia.  ### Parallel Opportunities  - T075-T076 pueden ejecutarse en paralelo. - T077-T079, T081-T084 y T086 pueden ejecutarse en paralelo dentro de Foundational cuando no compartan archivos. - T088-T091 son pruebas independientes de US1. - T092, T095 y T096 pueden prepararse en paralelo si se respetan sus archivos separados; T096 depende conceptualmente de la resoluciï¿½n de equipos. - T101-T103 y T108-T111 son grupos de pruebas paralelizables. - T117-T119 son revisiones independientes. - En Phase 7, T122-T124 pueden prepararse en paralelo; T125 precede a T126-T127; T128-T129 pueden ejecutarse en paralelo despues del contrato del Adapter; T130 depende de T122-T127.  ## Parallel Example: User Story 1  Despuï¿½s de completar Phase 2:  - T088: pruebas aisladas del Adapter. - T089: pruebas del resolver de equipos. - T090: pruebas del resolver de jugadores. - T091: integraciï¿½n de enrolamiento inicial.  Luego se implementan T092-T099 respetando las dependencias de cada componente y se ejecuta T100 como validaciï¿½n independiente de la historia, verificando datos publicados e identidades visibles ï¿½nicamente tras el commit.  ## Implementation Strategy  ### MVP First  1. Completar Setup y Foundational. 2. Completar US1, incluyendo enrolamiento inicial y flujo de las cinco ligas. 3. Ejecutar T100 y validar la publicaciï¿½n controlada. 4. Continuar con US2 y US3 antes de considerar la feature terminada, porque la Constituciï¿½n exige idempotencia y atomicidad ante fallos.  ### Incremental Delivery  1. Infraestructura y contratos internos. 2. Obtenciï¿½n, normalizaciï¿½n y enrolamiento inicial. 3. Upsert e idempotencia. 4. Rollback, conservaciï¿½n del snapshot y concurrencia. 5. Validaciï¿½n completa y calidad.  ## Phase 7: Rediseï¿½o de adquisiciï¿½n statisticsfeed  **Purpose**: Reemplazar el mecanismo HTTP ficticio por la adquisiciï¿½n real validada mediante Selenium, sin modificar las garantï¿½as funcionales de la sincronizaciï¿½n.  **Estado**: completada y validada.  ### Trazabilidad de tareas superseded  - T092, T093 y T094 quedan superseded por T122-T127: describï¿½an DTOs y   paginaciï¿½n del contrato HTTP ficticio. - T108 queda superseded por T128-T130: describï¿½a fallos HTTP del mecanismo   anterior; sus garantï¿½as funcionales se conservan y se trasladan a Selenium,   JSON controlado y validaciï¿½n de completitud. - T117 queda superseded por T131: la revisiï¿½n debe adaptarse a Selenium,   statisticsfeed y sus detalles encapsulados. - T075 conserva su intenciï¿½n de configuraciï¿½n de infraestructura, pero los   parï¿½metros del proveedor deben revisarse conforme al nuevo Adapter.  ### Nuevas tareas  - [X] T122 [P] [US1] Definir el contrato interno evolucionado de `ProveedorEstadisticasJugador` para adquirir un snapshot completo por competiciï¿½n, consolidado por playerId y sin exponer familias, pï¿½ginas, pageSize, stageId, tournamentOptions, Selenium ni DTOs externos en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/ProveedorEstadisticasJugador.java, backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/SnapshotEstadisticasJugador.java, backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/SincronizacionEstadisticasJugadorServiceImpl.java, backend/src/test/java/com/devchesterunited/devchesterunitedv2/modelo/SnapshotEstadisticasJugadorTest.java y specs/002-player-statistics-sync/contracts/player-statistics-sync.md - [X] T123 [P] [US1] Definir la configuraciï¿½n interna de las cinco parejas tournamentOptions/stageId y los parï¿½metros comunes de statisticsfeed exclusivamente dentro de backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/ y backend/src/main/resources/application.properties - [X] T124 [P] [US1] Crear los DTOs/fixtures internos para las respuestas reales de summary/all, summary/offensive y summary/defensive, manteniï¿½ndolos dentro de backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/dto/ y sin agregar mï¿½tricas de dominio fuera de FR-003/FR-004 - [X] T125 [US1] Implementar la creaciï¿½n, reutilizaciï¿½n y cierre garantizado de una ï¿½nica sesiï¿½n WebDriver por sincronizaciï¿½n completa, incluyendo cierre ante excepciones, en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/impl/ y backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/ - [X] T126 [US1] Implementar la navegaciï¿½n Selenium al endpoint /statisticsfeed/1/getplayerstatistics, lectura del JSON del body, adquisiciï¿½n de las tres familias, traducciï¿½n y consolidaciï¿½n por playerId dentro de backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/impl/WhoScoredStatisticsAdapter.java sin llamadas desde Service  ## Cierre final de la feature  La implementacion vigente queda cerrada con snapshots adquiridos por Selenium headed, cinco competiciones, isMinApp=false, numberOfPlayersToPick=500, page=1 y sin paginacion productiva. El mapping aprobado versionado es un prerrequisito reproducible y no se regenera automaticamente. La evidencia productiva validada es 2175 adquiridos, 1968 publicados y 207 omitidos. Las referencias anteriores a HTTP, paginacion y PaginaEstadisticasJugador permanecen solamente como trazabilidad historica superseded. - [X] T127 [US1] Implementar la validaciï¿½n sin paginaciï¿½n de currentPage == 1, totalPages == 1, players.size == totalResults y ausencia de contradicciones en metadata, con fallo controlado ante respuesta incompleta en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/impl/ - [X] T128 [P] [US1] Reemplazar las pruebas acopladas al JSON ficticio por pruebas con JSON controlado, fixtures y fakes que cubran las tres familias, metadata vï¿½lida e invï¿½lida y traducciï¿½n en backend/src/test/java/com/devchesterunited/devchesterunitedv2/adapter/ - [X] T129 [P] [US3] Cubrir fallos de Selenium, body invï¿½lido, metadata inconsistente, adquisiciï¿½n incompleta y errores sanitizados sin conectividad real en backend/src/test/java/com/devchesterunited/devchesterunitedv2/adapter/ y backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/ - [X] T130 [US1] Adaptar el flujo del Service para consumir un snapshot completo por competiciï¿½n, validar las cinco competiciones antes de staging validado y eliminar su conocimiento de familias y paginaciï¿½n del proveedor en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/ - [X] T131 [P] [US3] Revisar que URLs, stage IDs, tournamentOptions, Selenium, nombres JSON y errores del proveedor permanezcan confinados al Adapter en backend/src/main/java/com/devchesterunited/devchesterunitedv2/ - [X] T132 [US1] Verificar con fixtures que las mï¿½tricas persistidas continï¿½an limitadas a FR-003/FR-004 aunque WhoScored entregue campos adicionales en backend/src/test/java/com/devchesterunited/devchesterunitedv2/adapter/ y backend/src/test/java/com/devchesterunited/devchesterunitedv2/modelo/ - [X] T133 [US3] Ejecutar la validaciï¿½n manual diagnï¿½stica con una ï¿½nica sesiï¿½n WebDriver para las cinco competiciones, registrar completitud y cierre ante excepciï¿½n sin incorporar navegaciï¿½n real a la suite automatizada en specs/002-player-statistics-sync/quickstart.md  ---  ## ExtensiÃ³n de lectura del catÃ¡logo  La siguiente tarea extiende esta misma feature para comprobar la lectura de las estadÃ­sticas ya publicadas. No reabre la adquisiciÃ³n ni crea una nueva feature.  - [X] T134 [US4] Implementar `GET /api/v1/players/{id}` con `ply_<uuid>`, detalle del jugador activo de equipo activo, `statistics` opcional leÃ­do exclusivamente mediante `JugadorRepository` y `EstadisticasJugadorRepository.findByJugadorId(UUID)`, HTTP 200 con estadÃ­sticas o `null`, HTTP 404 para inexistentes/inactivos, contrato de detalle separado del listado y sin invocar WhoScored/Selenium/sincronizaciÃ³n; modificar o crear Ãºnicamente los componentes productivos necesarios en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/JugadorController.java`, `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/interfaces/JugadorService.java`, `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/JugadorServiceImpl.java`, `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/JugadorRepository.java`, DTOs de `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/` y resultados de `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/dto/`, manteniendo intacto `GET /api/v1/players`.  ## Notes  Las pruebas previstas para T134 deben crearse como una suite nueva, por ejemplo `backend/src/test/java/com/devchesterunited/devchesterunitedv2/controller/JugadorDetalleE2ETest.java`, y pueden complementarse con una prueba nueva de Service o Repository sin modificar suites existentes. - La resolucion de 404 puede requerir el handler y/o la excepcion de aplicacion   correspondiente en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/exceptionhandler/` y `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/exception/`; esos archivos quedan dentro del alcance de T134 solo si son necesarios.  - Todas las tareas usan formato checklist, ID secuencial, marca [P] solo cuando corresponde, etiqueta de historia en fases de usuario y ruta de archivo. - La implementaciï¿½n del endpoint HTTP queda acotada a T134; no incluye frontend. - No se permite modificar o borrar tests existentes. - T134 debe agregar pruebas nuevas para jugador con estadÃ­sticas, jugador sin   estadÃ­sticas, inexistente, jugador/equipo inactivo, aislamiento frente a   WhoScored y preservaciÃ³n del contrato existente del listado. No se pueden   modificar ni borrar tests existentes sin autorizaciÃ³n explÃ­cita. - Las identidades nuevas WHOSCORED solo son visibles si la promociï¿½n completa termina correctamente; staging nunca las publica. - La red se ejecuta antes de la transacciï¿½n de promociï¿½n y nunca dentro de ella.  ---  ## Phase 8: User Story 5 - Visualizar estadisticas desde el catalogo (Priority: P1) ?? MVP frontend  **Goal**: Permitir seleccionar un jugador desde el catalogo, consultar su detalle local y visualizar sus datos basicos y las diez metricas requeridas, incluyendo estados de carga, error e inexistencia.  **Independent Test**: Con respuestas controladas del endpoint existente, seleccionar una card/fila, verificar la navegacion a `/catalogo/jugadores/{id}`, el consumo de `/api/v1/players/{id}`, el renderizado de metricas, `statistics: null`, errores y el regreso al catalogo.  ### Tests for User Story 5  - [X] T135 [P] [US5] Agregar pruebas de `getPlayerDetail` para URL con identificador opaco, envoltorio `data` y errores HTTP en frontend/tests/http-player-detail.test.js - [X] T136 [P] [US5] Crear pruebas de la vista de detalle para carga, datos basicos y diez metricas, `statistics: null`, HTTP 404, error de red y accion de regreso en frontend/tests/player-detail.test.jsx  ### Implementation for User Story 5  - [X] T137 [US5] Implementar `getPlayerDetail(id)` reutilizando `request` y el contrato `GET /api/v1/players/{id}` en frontend/src/api/http.js - [X] T138 [US5] Crear `PlayerDetailPage` con estados loading, success-with-statistics, success-without-statistics y error, datos basicos, diez metricas y enlace accesible de regreso en frontend/src/views/PlayerDetailPage.jsx - [X] T139 [US5] Extender la resolucion de rutas y el renderizado de vistas para `/catalogo/jugadores/{id}` sin agregar una dependencia de routing en frontend/src/App.jsx - [X] T140 [P] [US5] Agregar estilos de detalle coherentes con el sistema visual existente y sus estados responsivos en frontend/src/styles/player-detail.css y frontend/src/App.jsx - [X] T141 [US5] Crear el recorrido E2E catalogo -> detalle -> volver, incluyendo datos con estadisticas, `statistics: null`, error/404 y bloqueo de hosts externos en frontend/e2e/player-detail.spec.js  **Checkpoint**: La User Story 5 funciona de forma independiente consumiendo exclusivamente el endpoint backend documentado.  ---  ## Phase 9: User Story 6 - Identificar jugadores por nacionalidad en el catalogo (Priority: P2)  **Goal**: Reemplazar el dorsal no confiable por la nacionalidad disponible y conservar cards/filas navegables sin ampliar scraping, matching, sincronizacion ni persistencia.  **Independent Test**: Renderizar jugadores con nacionalidad y dorsal ausente, verificar que aparece Nacionalidad, que no aparece Dorsal ni `0`, y que la seleccion conserva la navegacion al detalle.  ### Tests for User Story 6  - [X] T069 [P] [US6] Crear pruebas de la presentacion de nacionalidad, ausencia de dorsal, ausencia de nacionalidad y navegacion accesible de la card en frontend/tests/catalog-player-card.test.jsx  ### Implementation for User Story 6  - [X] T070 [US6] Reemplazar la columna y los campos visuales Dorsal por Nacionalidad, envolver cada jugador en un enlace accesible al detalle y conservar filtros/paginacion en frontend/src/views/CatalogPage.jsx - [X] T071 [P] [US6] Ajustar estilos de tabla responsive, foco de teclado y estado navegable de filas/cards sin rediseï¿½ar el catalogo en frontend/src/styles/catalog.css  **Checkpoint**: Las cards/filas muestran nacionalidad, no muestran dorsal ni `0`, y siguen conduciendo al detalle.  ---  ## Phase 10: Polish & Cross-Cutting Concerns  **Purpose**: Validar el cierre frontend contra la constitucion, el contrato y el quickstart sin modificar tests existentes.  - [X] T072 [P] Revisar que los cambios frontend respeten 2 espacios, comillas simples, ausencia de punto y coma, JSX legible y accesibilidad en frontend/src/ - [X] T073 Ejecutar `npm test -- --run`, `npm run build` y `npm run verify:frontend` desde frontend/ y documentar el resultado conforme a specs/002-player-statistics-sync/quickstart.md  ---  ## Updated Dependencies & Execution Order  ### New User Story Dependencies  - **US5 (P1 frontend)**: depende de T134 y del contrato existente de detalle; T135-T136 pueden prepararse en paralelo, T137 precede a T138, y T139-T141 integran la navegaciï¿½n y validaciï¿½n. - **US6 (P2)**: depende de la ruta de detalle de US5 para verificar la navegaciï¿½n completa; T069 puede prepararse en paralelo con T070-T071, y la historia se valida despuï¿½s de T070. - **Polish**: T072-T073 dependen de US5 y US6.  ### Parallel Opportunities  - T135 y T136 pueden ejecutarse en paralelo porque cubren archivos de prueba distintos. - T140 puede ejecutarse en paralelo con T137-T139 al trabajar sobre la hoja de estilos y su importaciï¿½n definida. - T069, T070 y T071 pueden prepararse en paralelo cuando se coordine la ediciï¿½n de `CatalogPage.jsx` y `catalog.css`. - T072 y la preparaciï¿½n de T073 son independientes entre sï¿½.  ## Parallel Example: User Story 5  ```text T135: prueba del cliente HTTP en frontend/tests/http-player-detail.test.js T136: pruebas de PlayerDetailPage en frontend/tests/player-detail.test.jsx T140: estilos de detalle en frontend/src/styles/player-detail.css ```  ## Updated Implementation Strategy  ### MVP frontend  1. Completar T135-T141 y validar la User Story 5 de forma independiente. 2. Confirmar que el detalle consume solo `GET /api/v1/players/{id}` y que los estados de error/ausencia son recuperables. 3. Continuar con T069-T071 para cerrar la sustitucion del dorsal por nacionalidad. 4. Ejecutar T072-T073 antes de considerar terminado el cierre frontend.  ### Nota de testing  La constitucion prohï¿½be modificar o borrar tests existentes sin autorizacion explicita. Las tareas nuevas agregan cobertura en archivos nuevos; si una expectativa existente sobre Dorsal requiere actualizacion por el cambio de requisito, debe solicitarse primero esa autorizacion y conservarse la cobertura equivalente para Nacionalidad.  **Estado de validacion**: T135-T073 completadas. La asercion obsoleta de Dorsal en frontend/tests/views.test.jsx fue actualizada unicamente a Nacionalidad con autorizacion explicita. La suite frontend completa, el build y verify:frontend pasan. 

---

## Ampliaciï¿½n actual (tareas renumeradas)

 ---

description: "Tareas ejecutables para completar la sincronizaciÃ³n de estadÃ­sticas de jugadores"
---

# Tasks: SincronizaciÃ³n completa de estadÃ­sticas de jugadores

**Input**: Documentos de diseÃ±o en `/specs/002-player-statistics-sync/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/` y `quickstart.md`

**Regla de tests**: agregar tests nuevos sin Internet ni credenciales reales. No modificar ni eliminar tests existentes sin autorizaciÃ³n explÃ­cita.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Preparar el alcance y las rutas de trabajo sin alterar el comportamiento vigente.

- [X] T074 Confirmar en `specs/002-player-statistics-sync/spec.md` el inventario de 30 mÃ©tricas, familias propietarias y exclusiones de valorizaciÃ³n
- [X] T075 [P] Confirmar en `backend/src/main/resources/db/migration/V15__Create_player_statistics_sync.sql` que V15 queda sin modificaciones durante la feature
- [X] T076 [P] Revisar las rutas existentes de Adapter, dominio, persistence, controller y frontend documentadas en `specs/002-player-statistics-sync/plan.md`
- [X] T077 [P] Crear el inventario de fixtures controladas para las cuatro familias en `backend/src/test/resources/fixtures/whoscored/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establecer el modelo comÃºn y el esquema que bloquean la implementaciÃ³n de todas las historias.

**âš ï¸ CRITICAL**: Las historias no deben comenzar hasta completar esta fase.

- [X] T078 [P] Crear `MetricasGenerales` con sus 8 mÃ©tricas anulables en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/MetricasGenerales.java`
- [X] T079 [P] Crear `MetricasOfensivas` con sus 9 mÃ©tricas anulables en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/MetricasOfensivas.java`
- [X] T080 [P] Crear `MetricasDefensivas` con sus 8 mÃ©tricas anulables en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/MetricasDefensivas.java`
- [X] T081 [P] Crear `MetricasPases` con sus 5 mÃ©tricas anulables y porcentaje 0..100 en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/MetricasPases.java`
- [X] T082 Actualizar `MetricasEstadisticasJugador` en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/MetricasEstadisticasJugador.java` para componer las cuatro familias sin record plano
- [X] T083 Actualizar `RegistroEstadisticasJugador` y `SnapshotEstadisticasJugador` en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/` para transportar la composiciÃ³n completa
- [X] T084 [P] Crear tests unitarios de composiciÃ³n y nulabilidad en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/modelo/MetricasEstadisticasJugadorComposicionTest.java`
- [X] T085 Crear `V16__Expand_player_statistics_sync.sql` en `backend/src/main/resources/db/migration/` para agregar las columnas faltantes sin modificar V15 ni crear tablas por familia
- [X] T086 Actualizar `EstadisticasJugadorEntity` en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/dto/EstadisticasJugadorEntity.java` con las 30 columnas consolidadas y nulables
- [X] T087 Actualizar `EstadisticasSyncStagingEntity` en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/dto/EstadisticasSyncStagingEntity.java` para las cuatro familias y sus mÃ©tricas
- [X] T088 [P] Agregar validaciÃ³n de columnas V16 y unicidad por jugador en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/persistence/EstadisticasSchemaV16IntegrationTest.java`

**Checkpoint**: modelo, migraciÃ³n y entidades soportan el snapshot completo sin alterar V15.

---

## Phase 3: User Story 1 - Sincronizar las cuatro familias (Priority: P1) ðŸŽ¯ MVP

**Goal**: adquirir, parsear y consolidar `summary`, `offensive`, `defensive` y `passing` en una composiciÃ³n de 30 mÃ©tricas por jugador.

**Independent Test**: ejecutar el Adapter con cuatro fixtures controladas y verificar el snapshot 8/9/8/5, la precedencia de repetidas y `precisionPases` en escala 0..100.

### Tests for User Story 1

- [X] T089 [P] [US1] Crear fixture `summary-passing.json` con `totalPassesPerGame`, `passSuccess`, centros, pases largos y pases filtrados en `backend/src/test/resources/fixtures/whoscored/summary-passing.json`
- [X] T090 [P] [US1] Crear fixtures completas `summary-complete.json`, `offensive-complete.json` y `defensive-complete.json` en `backend/src/test/resources/fixtures/whoscored/`
- [X] T091 [P] [US1] Crear tests de parsing de las cuatro respuestas en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/adapter/dto/WhoScoredCompleteStatisticsParsingTest.java`
- [X] T092 [P] [US1] Crear test del Adapter para adquirir las cuatro categorÃ­as y cerrar la sesiÃ³n ante Ã©xito o error en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/adapter/impl/WhoScoredStatisticsAdapterPassingTest.java`
- [X] T093 [P] [US1] Crear test de consolidaciÃ³n de 30 mÃ©tricas y precedencia canÃ³nica en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/ConsolidadorEstadisticasJugadorCompleteTest.java`

### Implementation for User Story 1

- [X] T094 [US1] Extender `WhoScoredStatisticsFeedResponseDTO` en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/dto/WhoScoredStatisticsFeedResponseDTO.java` solo con los campos necesarios del contrato de 30 mÃ©tricas
- [X] T095 [US1] Agregar la categorÃ­a `passing` y sus parÃ¡metros al flujo del Adapter en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/impl/WhoScoredStatisticsAdapter.java`
- [X] T096 [US1] Actualizar la configuraciÃ³n de familias y categorÃ­as en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/WhoScoredStatisticsConfiguration.java` sin exponer IDs al dominio
- [X] T097 [US1] Implementar el mapeo de campos externos a las cuatro familias en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/ConsolidadorEstadisticasJugador.java`
- [X] T098 [US1] Aplicar precedencia Ãºnica para `minsPlayed`, `rating`, `assistTotal`, `keyPassPerGame` y `passSuccess` en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/ConsolidadorEstadisticasJugador.java`
- [X] T099 [US1] Mantener la validaciÃ³n de completitud por familia y traducir errores externos en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/impl/WhoScoredStatisticsAdapter.java`
- [X] T100 [US1] Verificar el flujo MVP de cuatro familias y 30 mÃ©tricas conforme a `specs/002-player-statistics-sync/quickstart.md`

**Checkpoint**: US1 entrega snapshots completos por competiciÃ³n y puede validarse sin persistencia ni Internet.

---

## Phase 4: User Story 2 - Mantener consistencia e idempotencia (Priority: P1)

**Goal**: persistir una Ãºnica fila consolidada, actualizarla sin duplicados y conservar el Ãºltimo snapshot vÃ¡lido ante fallos.

**Independent Test**: ejecutar dos sincronizaciones del mismo jugador, comprobar actualizaciÃ³n sin duplicado, `null` por ausencia vÃ¡lida y rollback ante fallo de una familia.

### Tests for User Story 2

- [X] T101 [P] [US2] Crear test de upsert de las 30 mÃ©tricas y Ãºnica fila por jugador en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/persistence/EstadisticasJugadorCompleteUpsertIntegrationTest.java`
- [X] T102 [P] [US2] Crear test de staging para las cuatro familias y sus marcas en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/persistence/EstadisticasSyncStagingCompleteIntegrationTest.java`
- [X] T103 [P] [US2] Crear test de actualizaciÃ³n idempotente y reemplazo por `null` en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/ConsolidadorEstadisticasJugadorNullUpdateTest.java`
- [X] T104 [P] [US2] Crear test de fallo de una familia sin publicaciÃ³n parcial en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/SincronizacionEstadisticasPassingFailureIntegrationTest.java`

### Implementation for User Story 2

- [X] T105 [US2] Actualizar `EstadisticasJugadorRepository` en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/EstadisticasJugadorRepository.java` para leer y actualizar la fila completa sin duplicados
- [X] T106 [US2] Actualizar `EstadisticasSyncStagingRepository` en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/EstadisticasSyncStagingRepository.java` para persistir el snapshot de las cuatro familias
- [X] T107 [US2] Mapear las 30 mÃ©tricas entre dominio y persistencia en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/mapper/EstadisticasJugadorMapper.java`
- [X] T108 [US2] Actualizar staging, promociÃ³n y rollback en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstadisticasStagingService.java` y `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstadisticasPromocionService.java`
- [X] T109 [US2] Garantizar que una sincronizaciÃ³n vÃ¡lida escriba `null` para mÃ©tricas ausentes y que una falla conserve el snapshot anterior en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/SincronizacionEstadisticasJugadorServiceImpl.java`
- [X] T110 [US2] Ejecutar los escenarios de idempotencia, atomicidad y concurrencia definidos en `specs/002-player-statistics-sync/quickstart.md`

**Checkpoint**: US1 y US2 dejan una Ãºnica fotografÃ­a persistida, actualizable y atÃ³mica por jugador.

---

## Phase 5: User Story 3 - Consultar el detalle por API (Priority: P1)

**Goal**: exponer las cuatro familias mediante el detalle local, con espaÃ±ol `camelCase`, nulabilidad y porcentaje de pases.

**Independent Test**: consultar un jugador con 30 mÃ©tricas, uno con `null`, uno sin fila y uno inexistente, sin invocar WhoScored.

### Tests for User Story 3

- [X] T111 [P] [US3] Crear test de serializaciÃ³n de las cuatro familias y 30 mÃ©tricas en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/controller/JugadorDetalleCompleteStatisticsE2ETest.java`
- [X] T112 [P] [US3] Crear test de `precisionPases` igual a 86.44 y campos ausentes como `null` en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/controller/EstadisticasJugadorResponseSerializationTest.java`
- [X] T113 [P] [US3] Crear test de lectura local sin Adapter ni proveedor externo en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/JugadorDetalleLocalStatisticsContractTest.java`

### Implementation for User Story 3

- [X] T114 [US3] Actualizar `EstadisticasJugadorResponseDTO` en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/EstadisticasJugadorResponseDTO.java` con `generales`, `ofensivas`, `defensivas` y `pases` en espaÃ±ol `camelCase`
- [X] T115 [US3] Actualizar el mapeo de detalle en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/JugadorServiceImpl.java` y `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/dto/JugadorDetalleResultado.java`
- [X] T116 [US3] Actualizar `JugadorDetalleResponseDTO` y documentaciÃ³n OpenAPI en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/JugadorDetalleResponseDTO.java` y `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/JugadorController.java`
- [X] T117 [US3] Conservar `statistics: null`, 404 y lectura exclusivamente local en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/JugadorServiceImpl.java`
- [X] T118 [US3] Validar el contrato actualizado conforme a `specs/002-player-statistics-sync/contracts/player-statistics-sync.md`

**Checkpoint**: US3 expone el dataset local completo sin dependencia de WhoScored.

---

## Phase 6: User Story 4 - Navegar las cuatro pestaÃ±as (Priority: P1)

**Goal**: habilitar Resumen, Ataque, Defensa y Pases en `PlayerDetailPage` y distribuir las mÃ©tricas respetando el diseÃ±o existente.

**Independent Test**: cargar un detalle con estadÃ­sticas y activar cada pestaÃ±a; repetir con `statistics: null`, 404 y error de red.

### Tests for User Story 4

- [X] T119 [P] [US4] Crear tests de estados de carga, Ã©xito, ausencia y error en `frontend/src/views/PlayerDetailPage.statistics.test.jsx`
- [X] T120 [P] [US4] Crear tests de activaciÃ³n y contenido de Resumen, Ataque, Defensa y Pases en `frontend/src/views/PlayerDetailPage.tabs.test.jsx`
- [X] T121 [P] [US4] Crear test de cliente HTTP para `GET /api/v1/players/{id}` en `frontend/src/api/http.player-detail.test.js`
- [X] T122 [P] [US4] Crear recorrido E2E de catÃ¡logo â†’ detalle â†’ cuatro pestaÃ±as â†’ regreso en `frontend/e2e/player-detail-statistics.spec.js`

### Implementation for User Story 4

- [X] T123 [US4] Agregar la operaciÃ³n de detalle y manejo explÃ­cito de Promise en `frontend/src/api/http.js`
- [X] T124 [US4] Actualizar `frontend/src/views/PlayerDetailPage.jsx` para consumir las cuatro familias y mantener `loading`, Ã©xito, ausencia y error
- [X] T125 [US4] Habilitar controles semÃ¡nticos accesibles y estado activo de las cuatro pestaÃ±as en `frontend/src/views/PlayerDetailPage.jsx`
- [X] T126 [US4] Distribuir mÃ©tricas y mostrar `precisionPases` como porcentaje en `frontend/src/views/PlayerDetailPage.jsx`
- [X] T127 [US4] Ajustar estilos sin alterar el diseÃ±o actual en `frontend/src/styles/player-detail.css`
- [X] T128 [US4] Verificar la navegaciÃ³n desde el catÃ¡logo y regreso usando las rutas existentes en `frontend/src/App.jsx` y `frontend/src/views/CatalogPage.jsx`

**Checkpoint**: US4 permite revisar las cuatro familias sin llamadas directas a WhoScored.

---

## Phase 7: User Story 5 - Conservar catÃ¡logo e identidad (Priority: P2)

**Goal**: demostrar que la ampliaciÃ³n no rompe matching, catÃ¡logo, identidad, navegaciÃ³n ni tests existentes.

**Independent Test**: ejecutar regresiones nuevas sobre catÃ¡logo, identidad y sincronizaciÃ³n completa y comprobar que los artefactos existentes permanecen intactos.

### Tests for User Story 5

- [X] T129 [P] [US5] Crear test de asociaciÃ³n de las cuatro familias al jugador local existente en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/SincronizacionEstadisticasExistingPlayerRegressionTest.java`
- [X] T130 [P] [US5] Crear test de identidad ambigua omitida sin duplicar jugador en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/IdentidadJugadorResolverStatisticsRegressionTest.java`
- [X] T131 [P] [US5] Crear test frontend de catÃ¡logo, nacionalidad y navegaciÃ³n existente en `frontend/src/views/CatalogPage.statisticsRegression.test.jsx`

### Implementation for User Story 5

- [X] T132 [US5] Revisar y ajustar compatibilidad de mapeos sin modificar tests existentes en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/IdentidadJugadorResolver.java`
- [X] T133 [US5] Verificar que el listado de jugadores conserve su contrato en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/JugadorController.java` y `frontend/src/api/http.js`
- [X] T134 [US5] Verificar que las cuatro familias no creen jugadores, equipos ni identidades alternativas en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/SincronizacionEstadisticasJugadorServiceImpl.java`

**Checkpoint**: las historias anteriores siguen funcionando y la ampliaciÃ³n solo agrega el dataset solicitado.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: validar calidad, documentaciÃ³n, seguridad y Definition of Done sin cambiar el alcance.

- [X] T135 [P] Actualizar documentaciÃ³n de contratos y ejemplos en `specs/002-player-statistics-sync/contracts/player-statistics-sync.md`
- [X] T136 [P] Actualizar guÃ­a de validaciÃ³n con resultados observados en `specs/002-player-statistics-sync/quickstart.md`
- [X] T137 Ejecutar la suite backend completa desde `backend` con `./gradlew.bat test --no-daemon --console=plain`
- [X] T138 Ejecutar build, tests y verificaciÃ³n frontend desde `frontend` con `npm.cmd run build`, `npm.cmd run test -- --run` y `npm.cmd run verify:frontend`
- [X] T139 Verificar con `git diff` que `V15__Create_player_statistics_sync.sql` y los tests existentes no fueron modificados en `backend/src/main/resources/db/migration/V15__Create_player_statistics_sync.sql` y `backend/src/test/`
- [ ] T140 Ejecutar revisiÃ³n SonarCloud conforme a `backend/build.gradle` y resolver o documentar issues nuevas de severidad alta o crÃ­tica
- [X] T141 Ejecutar todos los escenarios de `specs/002-player-statistics-sync/quickstart.md` y registrar el resultado final

---

## Dependencias y orden de ejecuciÃ³n

### Dependencias entre fases

- **Setup (Phase 1)**: sin dependencias.
- **Foundational (Phase 2)**: depende de Setup y bloquea todas las historias.
- **US1 (Phase 3)**: depende de Phase 2; entrega el MVP de adquisiciÃ³n y dominio.
- **US2 (Phase 4)**: depende de US1 para recibir el snapshot completo; queda validable de forma independiente una vez integrado.
- **US3 (Phase 5)**: depende de US2 para leer las 30 mÃ©tricas persistidas.
- **US4 (Phase 6)**: depende de US3 para consumir el contrato pÃºblico actualizado.
- **US5 (Phase 7)**: depende de US1â€“US4 para ejecutar la regresiÃ³n completa.
- **Polish (Phase 8)**: depende de todas las historias deseadas.

### Orden recomendado de historias

```text
Phase 1 â†’ Phase 2 â†’ US1 â†’ US2 â†’ US3 â†’ US4 â†’ US5 â†’ Polish
```

### Oportunidades de paralelizaciÃ³n

- T078â€“T081 pueden ejecutarse en paralelo: cada record de familia estÃ¡ en un archivo distinto.
- T089â€“T093 pueden ejecutarse en paralelo: fixtures y tests nuevos son independientes.
- T101â€“T104 pueden ejecutarse en paralelo tras disponer de V16 y el snapshot completo.
- T111â€“T113 pueden ejecutarse en paralelo porque cubren serializaciÃ³n, servicio y contrato.
- T119â€“T122 pueden ejecutarse en paralelo porque son suites frontend separadas.
- T129â€“T131 pueden ejecutarse en paralelo porque cubren backend de identidad y frontend.
- T135â€“T136 pueden ejecutarse en paralelo con la preparaciÃ³n del cierre, antes de T141.

## Ejemplos de ejecuciÃ³n paralela

### User Story 1

```text
Paralelo A: T089 + T090 â€” fixtures de las cuatro familias
Paralelo B: T091 â€” parsing controlado
Paralelo C: T092 â€” Adapter y passing
Paralelo D: T093 â€” consolidaciÃ³n y precedencia
Secuencial: T094â€“T099 â€” implementaciÃ³n sobre los tests
```

### User Story 3

```text
Paralelo A: T111 â€” respuesta HTTP completa
Paralelo B: T112 â€” porcentaje y nulls
Paralelo C: T113 â€” lectura local sin Adapter
Secuencial: T114â€“T118 â€” DTO, service, controller y validaciÃ³n
```

### User Story 4

```text
Paralelo A: T119 â€” estados de la vista
Paralelo B: T120 â€” pestaÃ±as
Paralelo C: T121 â€” cliente HTTP
Paralelo D: T122 â€” E2E
Secuencial: T123â€“T128 â€” integraciÃ³n de UI y navegaciÃ³n
```

## Estrategia de implementaciÃ³n

### MVP

1. Completar Phase 1 y Phase 2.
2. Completar US1, incluyendo parsing de las cuatro familias y las 30 mÃ©tricas.
3. Validar el snapshot consolidado sin Internet.
4. Continuar con US2 para hacer el MVP persistente e idempotente.

### Entrega incremental

1. US1: adquisiciÃ³n y dominio completo.
2. US2: staging, V16, upsert, `null` y atomicidad.
3. US3: API local con cuatro familias.
4. US4: cuatro pestaÃ±as del detalle.
5. US5: regresiÃ³n de identidad, catÃ¡logo y navegaciÃ³n.
6. Polish: suites completas, OpenAPI, frontend y SonarCloud.

## ValidaciÃ³n de formato

Todas las tareas siguen el formato obligatorio `- [ ] Tnnn [P?] [USn?]
DescripciÃ³n con ruta exacta`. Las tareas de Setup, Foundational y Polish no
usan etiqueta de historia; las tareas de historias usan `[US1]` a `[US5]`.
