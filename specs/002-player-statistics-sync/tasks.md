---

description: "Tareas de implementación para la sincronización de estadísticas de jugadores"
---

# Tasks: Sincronización de estadísticas de jugadores

**Input**: Design documents from /specs/002-player-statistics-sync/

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/player-statistics-sync.md, quickstart.md

**Tests**: incluidos porque la especificación y la Constitución exigen pruebas unitarias, de Adapter, integración con PostgreSQL/Testcontainers y casos de fallo.

**Scope**: se agrega Ãºnicamente el extremo de lectura local del detalle de jugador, no se modifican ni eliminan tests existentes y no se reintroducen campos legacy ni representaciones alternativas de Jugador/Equipo.

**Estado historico**: T001-T048 representan la implementacion del diseño
anterior en el commit e886221. Las tareas que dependian del contrato HTTP
ficticio quedan identificadas como **superseded** por la nueva etapa de
adquisicion; se conservan para trazabilidad y no se borran.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Preparar configuración y convenciones para la integración estadística sin modificar todavía el comportamiento publicado.

- [X] T001 Revisar las convenciones actuales de paquetes, configuración, migraciones y pruebas en backend/ y documentar en la implementación los puntos de extensión sin modificar tests existentes
- [X] T002 [P] Incorporar las propiedades de infraestructura de WhoScored, tamaño de lote, timeouts y límites de respuesta en backend/src/main/resources/application.properties sin versionar secretos
- [X] T003 [P] Crear la estructura de paquetes adapter/dto, adapter/impl, modelo, persistence/dto, persistence y service/interfaces para la feature según specs/002-player-statistics-sync/plan.md

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Construir los contratos, persistencia y componentes compartidos que bloquean las tres historias.

**CRITICAL**: Esta fase debe completarse antes de implementar historias de usuario.

- [X] T004 [P] Crear los value objects internos CompeticionEstadistica, FamiliaEstadistica, RegistroEstadisticasJugador y PaginaEstadisticasJugador en backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/
- [X] T005 [P] Crear el contrato ProveedorEstadisticasJugador en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/ProveedorEstadisticasJugador.java sin tipos HTTP, JSON ni DTOs de WhoScored
- [X] T006 Crear las invariantes de EstadisticasJugador y sus grupos de métricas en backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/ con ausencias nullable y rechazo de valores inválidos
- [X] T007 Crear la migración Flyway para jugador_estadisticas, estadisticas_sync y estadisticas_sync_staging en backend/src/main/resources/db/migration/V15__Create_player_statistics_sync.sql con FK, unicidad, índices y restricciones numéricas
- [X] T008 [P] Crear las entidades JPA de estadísticas publicadas, ejecución y staging en backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/dto/
- [X] T009 [P] Crear los repositories de estadísticas publicadas, ejecuciones y staging en backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/
- [X] T010 [P] Ampliar JugadorRepository y EquipoRepository en backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/ para obtener candidatos activos en lote por equipo/liga/nombre sin crear un repository de matching
- [X] T011 Crear las excepciones de aplicación para proveedor inválido, identidad no resuelta, concurrencia y promoción fallida en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/exception/
- [X] T012 Implementar el mecanismo de exclusión de sincronización sin mantener una transacción abierta durante la red en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/ y backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/
- [X] T013 [P] Crear pruebas unitarias de las invariantes y value objects en backend/src/test/java/com/devchesterunited/devchesterunitedv2/modelo/EstadisticasJugadorTest.java y backend/src/test/java/com/devchesterunited/devchesterunitedv2/modelo/EstadisticasPaginaTest.java
- [X] T014 Crear prueba de integración de la migración, restricciones y repositories en backend/src/test/java/com/devchesterunited/devchesterunitedv2/persistence/EstadisticasSchemaRepositoryIntegrationTest.java usando PostgreSQL/Testcontainers

**Checkpoint**: contratos internos, esquema, repositories, exclusión y reglas de dominio disponibles para las historias.

---

## Phase 3: User Story 1 - Obtener estadísticas de las ligas soportadas (Priority: P1) 🎯 MVP

**Goal**: Obtener y normalizar todos los jugadores de las cinco competiciones, vincular identidades locales de forma conservadora y dejar una fotografía estadística publicada.

**Independent Test**: Con un Adapter controlado para las cinco competiciones, múltiples páginas y familias separadas, ejecutar el Service y verificar que cada jugador resoluble tenga sus estadísticas general, ofensiva y defensiva asociadas en jugador_estadisticas.

### Tests for User Story 1

- [X] T015 [P] [US1] Crear pruebas del contrato del Adapter para DTOs válidos, familias, metadata de paginación y traducción de errores en backend/src/test/java/com/devchesterunited/devchesterunitedv2/adapter/WhoScoredStatisticsAdapterTest.java
- [X] T016 [P] [US1] Crear pruebas unitarias del matching conservador de equipos en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/IdentidadEquipoResolverTest.java cubriendo cero, una, múltiples y evidencia insuficiente
- [X] T017 [P] [US1] Crear pruebas unitarias del matching conservador de jugadores en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/IdentidadJugadorResolverTest.java cubriendo equipo, nombre, posición, nacionalidad, cero y múltiples candidatos
- [X] T018 [P] [US1] Crear prueba de integración de enrolamiento inicial en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/EnrolamientoIdentidadesWhoScoredIntegrationTest.java verificando que las identidades WHOSCORED nuevas solo sean visibles después de una promoción exitosa y se reviertan ante fallo

### Implementation for User Story 1

- [X] T019 [P] [US1] Crear los DTOs exclusivos de WhoScored y sus validadores en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/dto/
- [X] T020 [US1] Implementar WhoScoredStatisticsAdapter en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/impl/WhoScoredStatisticsAdapter.java con paginación guiada por metadata y traducción a value objects internos
- [X] T021 [US1] Implementar la normalización de familias, nombres, posiciones, nacionalidades y métricas del proveedor en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/impl/ sin filtrar DTOs hacia Service
- [X] T022 [US1] Implementar la resolución/enrolamiento determinista de equipos en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/IdentidadEquipoResolver.java usando nombre canónico exacto y contexto de competición
- [X] T023 [US1] Implementar la resolución/enrolamiento determinista de jugadores en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/IdentidadJugadorResolver.java usando equipo resuelto, nombre exacto y evidencia compatible de posición/nacionalidad
- [X] T024 [US1] Implementar la consolidación por playerId y familia en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/ConsolidadorEstadisticasJugador.java conservando métricas ausentes sin inventar valores
- [X] T025 [US1] Implementar la escritura por lote de estadisticas_sync y estadisticas_sync_staging en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstadisticasStagingService.java manteniendo las identidades nuevas fuera del estado visible
- [X] T026 [US1] Implementar el caso de uso y la promoción básica completa en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/interfaces/SincronizacionEstadisticasJugadorService.java, backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/SincronizacionEstadisticasJugadorServiceImpl.java y backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstadisticasPromocionService.java para publicar jugador_estadisticas e identidades WHOSCORED nuevas en una única transacción, sin llamadas de red
- [X] T027 [US1] Crear la prueba de integración del flujo completo de las cinco competiciones, paginación variable y tres familias en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/SincronizacionEstadisticasJugadorIntegrationTest.java

**Checkpoint**: la historia P1 principal puede obtener, vincular, validar y publicar la fotografía estadística completa, incluyendo identidades WHOSCORED nuevas, sin endpoint HTTP.

---

## Phase 4: User Story 2 - Mantener estadísticas consistentes y sin duplicados (Priority: P1)

**Goal**: Repetir y actualizar sincronizaciones sin duplicar estadísticas, jugadores ni identidades, consolidando todas las familias en una única fila lógica por jugador.

**Independent Test**: Ejecutar dos sincronizaciones iguales y una con cambios; verificar una sola identidad WHOSCORED por entidad/proveedor, una sola fila de estadísticas por jugador y actualización equivalente.

### Tests for User Story 2

- [X] T028 [P] [US2] Crear pruebas unitarias de idempotencia y consolidación repetida en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/ConsolidadorEstadisticasJugadorIdempotenciaTest.java
- [X] T029 [P] [US2] Crear prueba de integración de resolución por identidad persistida en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/IdentidadPersistidaWhoScoredIntegrationTest.java verificando que no se repita matching
- [X] T030 [P] [US2] Crear prueba de integración de upsert y actualización de métricas en backend/src/test/java/com/devchesterunited/devchesterunitedv2/persistence/EstadisticasJugadorUpsertIntegrationTest.java

### Implementation for User Story 2

- [X] T031 [US2] Implementar la reutilización prioritaria de identidades WHOSCORED persistidas en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/IdentidadJugadorResolver.java antes de ejecutar matching inicial
- [X] T032 [US2] Reforzar el upsert idempotente de jugador_estadisticas por jugador_id para ejecuciones repetidas en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstadisticasPromocionService.java sin crear filas nuevas para jugadores existentes
- [X] T033 [US2] Aplicar las restricciones y operaciones de persistencia para evitar duplicados de identidades y estadísticas durante reintentos en backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/
- [X] T034 [US2] Verificar y consolidar la actualización de las familias general, ofensiva y defensiva en una única fotografía por jugador durante ejecuciones repetidas en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/ConsolidadorEstadisticasJugador.java

**Checkpoint**: las ejecuciones repetidas son idempotentes y las modificaciones válidas actualizan el estado sin duplicados.

---

## Phase 5: User Story 3 - Conservar el último estado válido ante fallos (Priority: P1)

**Goal**: Rechazar fallos de proveedor, validación, identidad, staging o promoción sin mostrar datos parciales ni perder el snapshot anterior.

**Independent Test**: Partir de estadísticas publicadas, provocar fallos antes, durante y después de la obtención, y comprobar que estadísticas e identidades visibles permanecen sin cambios.

### Tests for User Story 3

- [X] T035 [P] [US3] Crear pruebas del Adapter para HTTP no exitoso, timeout, JSON inválido, cuota y metadata incompleta en backend/src/test/java/com/devchesterunited/devchesterunitedv2/adapter/WhoScoredStatisticsAdapterFailureTest.java
- [X] T036 [P] [US3] Crear prueba de integración de identidad no resuelta y evidencia ambigua en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/SincronizacionIdentidadNoResueltaIntegrationTest.java
- [X] T037 [P] [US3] Crear prueba de integración de rollback de promoción y conservación del snapshot anterior en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/SincronizacionEstadisticasAtomicidadIntegrationTest.java
- [X] T038 [P] [US3] Crear prueba de concurrencia de sincronizaciones en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/SincronizacionEstadisticasConcurrenciaIntegrationTest.java usando PostgreSQL/Testcontainers

### Implementation for User Story 3

- [X] T039 [US3] Implementar estados de ejecución, errores sanitizados y limpieza/marcado de staging fallido en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstadisticasSyncService.java
- [X] T040 [US3] Endurecer la promoción transaccional de estadísticas, identidades WHOSCORED nuevas y estado de ejecución ante errores de persistencia en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstadisticasPromocionService.java
- [X] T041 [US3] Garantizar rollback ante fallo de persistencia y conservación del snapshot anterior en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/SincronizacionEstadisticasJugadorServiceImpl.java
- [X] T042 [US3] Traducir errores de Adapter, matching y persistencia a excepciones de aplicación sin filtrar detalles de WhoScored en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/exception/ y backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/
- [X] T043 [US3] Integrar la exclusión de concurrencia y liberación segura en finally en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/SincronizacionEstadisticasJugadorServiceImpl.java

**Checkpoint**: cualquier fallo conserva el último estado válido y una segunda sincronización concurrente no publica ni consulta de forma indebida.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validar el resultado completo contra Constitución, quickstart y calidad del proyecto.

- [X] T044 [P] Revisar que ningún DTO, URL, stage ID, parámetro HTTP o error interno de WhoScored cruce Adapter en backend/src/main/java/com/devchesterunited/devchesterunitedv2/
- [X] T045 [P] Revisar que no se hayan reintroducido external_id ni creado representaciones alternativas o tabla de matching en backend/src/main/java/ y backend/src/main/resources/db/migration/
- [X] T046 [P] Añadir o ajustar documentación JavaDoc de los puertos, value objects, resolvers y estados en backend/src/main/java/com/devchesterunited/devchesterunitedv2/
- [X] T047 Ejecutar la validación completa definida en specs/002-player-statistics-sync/quickstart.md desde backend/ y corregir únicamente fallos introducidos por la feature
- [X] T048 Ejecutar compilación y suite completa con backend/gradlew.bat test --no-daemon --console=plain y verificar que todos los tests existentes permanecen intactos

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup (Phase 1): sin dependencias.
- Foundational (Phase 2): depende de Setup y bloquea todas las historias.
- User Story 1: depende de Foundational y es el MVP funcional.
- User Story 2: depende de los componentes de publicación de US1, aunque sus pruebas de idempotencia pueden prepararse en paralelo después de Foundational.
- User Story 3: depende de staging/promoción de US1 y del upsert de US2 para probar rollback completo.
- Polish: depende de las historias implementadas.
- Phase 7 (rediseño statisticsfeed): depende de Phase 2 y de las garantías de staging/promoción de US1-US3; T049-T051 preceden a T052-T054, T055-T056 validan el Adapter, T057 adapta el Service y T058-T060 cierran la validación.

### User Story Dependencies

- US1 (P1): después de Phase 2; entrega obtención, normalización y enrolamiento inicial.
- US2 (P1): después de T026-T027; reutiliza la promoción de US1 y añade reutilización de identidades, upsert idempotente y ausencia de duplicados.
- US3 (P1): después de T031-T034; endurece la promoción de US1/US2 y valida atomicidad, fallos y concurrencia.

### Parallel Opportunities

- T002-T003 pueden ejecutarse en paralelo.
- T004-T006, T008-T011 y T013 pueden ejecutarse en paralelo dentro de Foundational cuando no compartan archivos.
- T015-T018 son pruebas independientes de US1.
- T019, T022 y T023 pueden prepararse en paralelo si se respetan sus archivos separados; T023 depende conceptualmente de la resolución de equipos.
- T028-T030 y T035-T038 son grupos de pruebas paralelizables.
- T044-T046 son revisiones independientes.
- En Phase 7, T049-T051 pueden prepararse en paralelo; T052 precede a T053-T054; T055-T056 pueden ejecutarse en paralelo despues del contrato del Adapter; T057 depende de T049-T054.

## Parallel Example: User Story 1

Después de completar Phase 2:

- T015: pruebas aisladas del Adapter.
- T016: pruebas del resolver de equipos.
- T017: pruebas del resolver de jugadores.
- T018: integración de enrolamiento inicial.

Luego se implementan T019-T026 respetando las dependencias de cada componente y se ejecuta T027 como validación independiente de la historia, verificando datos publicados e identidades visibles únicamente tras el commit.

## Implementation Strategy

### MVP First

1. Completar Setup y Foundational.
2. Completar US1, incluyendo enrolamiento inicial y flujo de las cinco ligas.
3. Ejecutar T027 y validar la publicación controlada.
4. Continuar con US2 y US3 antes de considerar la feature terminada, porque la Constitución exige idempotencia y atomicidad ante fallos.

### Incremental Delivery

1. Infraestructura y contratos internos.
2. Obtención, normalización y enrolamiento inicial.
3. Upsert e idempotencia.
4. Rollback, conservación del snapshot y concurrencia.
5. Validación completa y calidad.

## Phase 7: Rediseño de adquisición statisticsfeed

**Purpose**: Reemplazar el mecanismo HTTP ficticio por la adquisición real
validada mediante Selenium, sin modificar las garantías funcionales de la
sincronización.

**Estado**: completada y validada.

### Trazabilidad de tareas superseded

- T019, T020 y T021 quedan superseded por T049-T054: describían DTOs y
  paginación del contrato HTTP ficticio.
- T035 queda superseded por T055-T057: describía fallos HTTP del mecanismo
  anterior; sus garantías funcionales se conservan y se trasladan a Selenium,
  JSON controlado y validación de completitud.
- T044 queda superseded por T058: la revisión debe adaptarse a Selenium,
  statisticsfeed y sus detalles encapsulados.
- T002 conserva su intención de configuración de infraestructura, pero los
  parámetros del proveedor deben revisarse conforme al nuevo Adapter.

### Nuevas tareas

- [X] T049 [P] [US1] Definir el contrato interno evolucionado de `ProveedorEstadisticasJugador` para adquirir un snapshot completo por competición, consolidado por playerId y sin exponer familias, páginas, pageSize, stageId, tournamentOptions, Selenium ni DTOs externos en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/ProveedorEstadisticasJugador.java, backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/SnapshotEstadisticasJugador.java, backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/SincronizacionEstadisticasJugadorServiceImpl.java, backend/src/test/java/com/devchesterunited/devchesterunitedv2/modelo/SnapshotEstadisticasJugadorTest.java y specs/002-player-statistics-sync/contracts/player-statistics-sync.md
- [X] T050 [P] [US1] Definir la configuración interna de las cinco parejas tournamentOptions/stageId y los parámetros comunes de statisticsfeed exclusivamente dentro de backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/ y backend/src/main/resources/application.properties
- [X] T051 [P] [US1] Crear los DTOs/fixtures internos para las respuestas reales de summary/all, summary/offensive y summary/defensive, manteniéndolos dentro de backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/dto/ y sin agregar métricas de dominio fuera de FR-003/FR-004
- [X] T052 [US1] Implementar la creación, reutilización y cierre garantizado de una única sesión WebDriver por sincronización completa, incluyendo cierre ante excepciones, en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/impl/ y backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/
- [X] T053 [US1] Implementar la navegación Selenium al endpoint /statisticsfeed/1/getplayerstatistics, lectura del JSON del body, adquisición de las tres familias, traducción y consolidación por playerId dentro de backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/impl/WhoScoredStatisticsAdapter.java sin llamadas desde Service

## Cierre final de la feature

La implementacion vigente queda cerrada con snapshots adquiridos por Selenium
headed, cinco competiciones, isMinApp=false, numberOfPlayersToPick=500,
page=1 y sin paginacion productiva. El mapping aprobado versionado es un
prerrequisito reproducible y no se regenera automaticamente. La evidencia
productiva validada es 2175 adquiridos, 1968 publicados y 207 omitidos.
Las referencias anteriores a HTTP, paginacion y PaginaEstadisticasJugador
permanecen solamente como trazabilidad historica superseded.
- [X] T054 [US1] Implementar la validación sin paginación de currentPage == 1, totalPages == 1, players.size == totalResults y ausencia de contradicciones en metadata, con fallo controlado ante respuesta incompleta en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/impl/
- [X] T055 [P] [US1] Reemplazar las pruebas acopladas al JSON ficticio por pruebas con JSON controlado, fixtures y fakes que cubran las tres familias, metadata válida e inválida y traducción en backend/src/test/java/com/devchesterunited/devchesterunitedv2/adapter/
- [X] T056 [P] [US3] Cubrir fallos de Selenium, body inválido, metadata inconsistente, adquisición incompleta y errores sanitizados sin conectividad real en backend/src/test/java/com/devchesterunited/devchesterunitedv2/adapter/ y backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/
- [X] T057 [US1] Adaptar el flujo del Service para consumir un snapshot completo por competición, validar las cinco competiciones antes de staging validado y eliminar su conocimiento de familias y paginación del proveedor en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/
- [X] T058 [P] [US3] Revisar que URLs, stage IDs, tournamentOptions, Selenium, nombres JSON y errores del proveedor permanezcan confinados al Adapter en backend/src/main/java/com/devchesterunited/devchesterunitedv2/
- [X] T059 [US1] Verificar con fixtures que las métricas persistidas continúan limitadas a FR-003/FR-004 aunque WhoScored entregue campos adicionales en backend/src/test/java/com/devchesterunited/devchesterunitedv2/adapter/ y backend/src/test/java/com/devchesterunited/devchesterunitedv2/modelo/
- [X] T060 [US3] Ejecutar la validación manual diagnóstica con una única sesión WebDriver para las cinco competiciones, registrar completitud y cierre ante excepción sin incorporar navegación real a la suite automatizada en specs/002-player-statistics-sync/quickstart.md

---

## ExtensiÃ³n de lectura del catÃ¡logo

La siguiente tarea extiende esta misma feature para comprobar la lectura de las
estadÃ­sticas ya publicadas. No reabre la adquisiciÃ³n ni crea una nueva feature.

- [X] T061 [US4] Implementar `GET /api/v1/players/{id}` con `ply_<uuid>`, detalle del jugador activo de equipo activo, `statistics` opcional leÃ­do exclusivamente mediante `JugadorRepository` y `EstadisticasJugadorRepository.findByJugadorId(UUID)`, HTTP 200 con estadÃ­sticas o `null`, HTTP 404 para inexistentes/inactivos, contrato de detalle separado del listado y sin invocar WhoScored/Selenium/sincronizaciÃ³n; modificar o crear Ãºnicamente los componentes productivos necesarios en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/JugadorController.java`, `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/interfaces/JugadorService.java`, `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/JugadorServiceImpl.java`, `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/JugadorRepository.java`, DTOs de `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/` y resultados de `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/dto/`, manteniendo intacto `GET /api/v1/players`.

## Notes

Las pruebas previstas para T061 deben crearse como una suite nueva, por
ejemplo `backend/src/test/java/com/devchesterunited/devchesterunitedv2/controller/JugadorDetalleE2ETest.java`, y pueden complementarse con una prueba nueva de Service o Repository sin modificar suites existentes.
- La resolucion de 404 puede requerir el handler y/o la excepcion de aplicacion
  correspondiente en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/exceptionhandler/` y `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/exception/`; esos archivos quedan dentro del alcance de T061 solo si son necesarios.

- Todas las tareas usan formato checklist, ID secuencial, marca [P] solo cuando corresponde, etiqueta de historia en fases de usuario y ruta de archivo.
- La implementación del endpoint HTTP queda acotada a T061; no incluye frontend.
- No se permite modificar o borrar tests existentes.
- T061 debe agregar pruebas nuevas para jugador con estadÃ­sticas, jugador sin
  estadÃ­sticas, inexistente, jugador/equipo inactivo, aislamiento frente a
  WhoScored y preservaciÃ³n del contrato existente del listado. No se pueden
  modificar ni borrar tests existentes sin autorizaciÃ³n explÃ­cita.
- Las identidades nuevas WHOSCORED solo son visibles si la promoción completa termina correctamente; staging nunca las publica.
- La red se ejecuta antes de la transacción de promoción y nunca dentro de ella.

---

## Phase 8: User Story 5 - Visualizar estadisticas desde el catalogo (Priority: P1) 🎯 MVP frontend

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
- [X] T071 [P] [US6] Ajustar estilos de tabla responsive, foco de teclado y estado navegable de filas/cards sin rediseñar el catalogo en frontend/src/styles/catalog.css

**Checkpoint**: Las cards/filas muestran nacionalidad, no muestran dorsal ni `0`, y siguen conduciendo al detalle.

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Validar el cierre frontend contra la constitucion, el contrato y el quickstart sin modificar tests existentes.

- [X] T072 [P] Revisar que los cambios frontend respeten 2 espacios, comillas simples, ausencia de punto y coma, JSX legible y accesibilidad en frontend/src/
- [X] T073 Ejecutar `npm test -- --run`, `npm run build` y `npm run verify:frontend` desde frontend/ y documentar el resultado conforme a specs/002-player-statistics-sync/quickstart.md

---

## Updated Dependencies & Execution Order

### New User Story Dependencies

- **US5 (P1 frontend)**: depende de T061 y del contrato existente de detalle; T062-T063 pueden prepararse en paralelo, T064 precede a T065, y T066-T068 integran la navegación y validación.
- **US6 (P2)**: depende de la ruta de detalle de US5 para verificar la navegación completa; T069 puede prepararse en paralelo con T070-T071, y la historia se valida después de T070.
- **Polish**: T072-T073 dependen de US5 y US6.

### Parallel Opportunities

- T062 y T063 pueden ejecutarse en paralelo porque cubren archivos de prueba distintos.
- T067 puede ejecutarse en paralelo con T064-T066 al trabajar sobre la hoja de estilos y su importación definida.
- T069, T070 y T071 pueden prepararse en paralelo cuando se coordine la edición de `CatalogPage.jsx` y `catalog.css`.
- T072 y la preparación de T073 son independientes entre sí.

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

La constitucion prohíbe modificar o borrar tests existentes sin autorizacion
explicita. Las tareas nuevas agregan cobertura en archivos nuevos; si una
expectativa existente sobre Dorsal requiere actualizacion por el cambio de
requisito, debe solicitarse primero esa autorizacion y conservarse la cobertura
equivalente para Nacionalidad.

**Estado de validacion**: T062-T073 completadas. La asercion obsoleta de Dorsal
en frontend/tests/views.test.jsx fue actualizada unicamente a Nacionalidad con
autorizacion explicita. La suite frontend completa, el build y
verify:frontend pasan.
