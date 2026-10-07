---

description: "Tareas de implementación para la sincronización de estadísticas de jugadores"
---

# Tasks: Sincronización de estadísticas de jugadores

**Input**: Design documents from /specs/002-player-statistics-sync/

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/player-statistics-sync.md, quickstart.md

**Tests**: incluidos porque la especificación y la Constitución exigen pruebas unitarias, de Adapter, integración con PostgreSQL/Testcontainers y casos de fallo.

**Scope**: no se agrega endpoint HTTP, no se modifican ni eliminan tests existentes y no se reintroducen campos legacy ni representaciones alternativas de Jugador/Equipo.

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

- [ ] T015 [P] [US1] Crear pruebas del contrato del Adapter para DTOs válidos, familias, metadata de paginación y traducción de errores en backend/src/test/java/com/devchesterunited/devchesterunitedv2/adapter/WhoScoredStatisticsAdapterTest.java
- [ ] T016 [P] [US1] Crear pruebas unitarias del matching conservador de equipos en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/IdentidadEquipoResolverTest.java cubriendo cero, una, múltiples y evidencia insuficiente
- [ ] T017 [P] [US1] Crear pruebas unitarias del matching conservador de jugadores en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/IdentidadJugadorResolverTest.java cubriendo equipo, nombre, posición, nacionalidad, cero y múltiples candidatos
- [ ] T018 [P] [US1] Crear prueba de integración de enrolamiento inicial en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/EnrolamientoIdentidadesWhoScoredIntegrationTest.java verificando que las identidades WHOSCORED nuevas solo sean visibles después de una promoción exitosa y se reviertan ante fallo

### Implementation for User Story 1

- [ ] T019 [P] [US1] Crear los DTOs exclusivos de WhoScored y sus validadores en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/dto/
- [ ] T020 [US1] Implementar WhoScoredStatisticsAdapter en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/impl/WhoScoredStatisticsAdapter.java con paginación guiada por metadata y traducción a value objects internos
- [ ] T021 [US1] Implementar la normalización de familias, nombres, posiciones, nacionalidades y métricas del proveedor en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/impl/ sin filtrar DTOs hacia Service
- [ ] T022 [US1] Implementar la resolución/enrolamiento determinista de equipos en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/IdentidadEquipoResolver.java usando nombre canónico exacto y contexto de competición
- [ ] T023 [US1] Implementar la resolución/enrolamiento determinista de jugadores en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/IdentidadJugadorResolver.java usando equipo resuelto, nombre exacto y evidencia compatible de posición/nacionalidad
- [ ] T024 [US1] Implementar la consolidación por playerId y familia en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/ConsolidadorEstadisticasJugador.java conservando métricas ausentes sin inventar valores
- [ ] T025 [US1] Implementar la escritura por lote de estadisticas_sync y estadisticas_sync_staging en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstadisticasStagingService.java manteniendo las identidades nuevas fuera del estado visible
- [ ] T026 [US1] Implementar el caso de uso y la promoción básica completa en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/interfaces/SincronizacionEstadisticasJugadorService.java, backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/SincronizacionEstadisticasJugadorServiceImpl.java y backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstadisticasPromocionService.java para publicar jugador_estadisticas e identidades WHOSCORED nuevas en una única transacción, sin llamadas de red
- [ ] T027 [US1] Crear la prueba de integración del flujo completo de las cinco competiciones, paginación variable y tres familias en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/SincronizacionEstadisticasJugadorIntegrationTest.java

**Checkpoint**: la historia P1 principal puede obtener, vincular, validar y publicar la fotografía estadística completa, incluyendo identidades WHOSCORED nuevas, sin endpoint HTTP.

---

## Phase 4: User Story 2 - Mantener estadísticas consistentes y sin duplicados (Priority: P1)

**Goal**: Repetir y actualizar sincronizaciones sin duplicar estadísticas, jugadores ni identidades, consolidando todas las familias en una única fila lógica por jugador.

**Independent Test**: Ejecutar dos sincronizaciones iguales y una con cambios; verificar una sola identidad WHOSCORED por entidad/proveedor, una sola fila de estadísticas por jugador y actualización equivalente.

### Tests for User Story 2

- [ ] T028 [P] [US2] Crear pruebas unitarias de idempotencia y consolidación repetida en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/ConsolidadorEstadisticasJugadorIdempotenciaTest.java
- [ ] T029 [P] [US2] Crear prueba de integración de resolución por identidad persistida en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/IdentidadPersistidaWhoScoredIntegrationTest.java verificando que no se repita matching
- [ ] T030 [P] [US2] Crear prueba de integración de upsert y actualización de métricas en backend/src/test/java/com/devchesterunited/devchesterunitedv2/persistence/EstadisticasJugadorUpsertIntegrationTest.java

### Implementation for User Story 2

- [ ] T031 [US2] Implementar la reutilización prioritaria de identidades WHOSCORED persistidas en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/ResolucionIdentidadesEstadisticas.java antes de ejecutar matching inicial
- [ ] T032 [US2] Reforzar el upsert idempotente de jugador_estadisticas por jugador_id para ejecuciones repetidas en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstadisticasPromocionService.java sin crear filas nuevas para jugadores existentes
- [ ] T033 [US2] Aplicar las restricciones y operaciones de persistencia para evitar duplicados de identidades y estadísticas durante reintentos en backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/
- [ ] T034 [US2] Verificar y consolidar la actualización de las familias general, ofensiva y defensiva en una única fotografía por jugador durante ejecuciones repetidas en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/ConsolidadorEstadisticasJugador.java

**Checkpoint**: las ejecuciones repetidas son idempotentes y las modificaciones válidas actualizan el estado sin duplicados.

---

## Phase 5: User Story 3 - Conservar el último estado válido ante fallos (Priority: P1)

**Goal**: Rechazar fallos de proveedor, validación, identidad, staging o promoción sin mostrar datos parciales ni perder el snapshot anterior.

**Independent Test**: Partir de estadísticas publicadas, provocar fallos antes, durante y después de la obtención, y comprobar que estadísticas e identidades visibles permanecen sin cambios.

### Tests for User Story 3

- [ ] T035 [P] [US3] Crear pruebas del Adapter para HTTP no exitoso, timeout, JSON inválido, cuota y metadata incompleta en backend/src/test/java/com/devchesterunited/devchesterunitedv2/adapter/WhoScoredStatisticsAdapterFailureTest.java
- [ ] T036 [P] [US3] Crear prueba de integración de identidad no resuelta y evidencia ambigua en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/SincronizacionIdentidadNoResueltaIntegrationTest.java
- [ ] T037 [P] [US3] Crear prueba de integración de rollback de promoción y conservación del snapshot anterior en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/SincronizacionEstadisticasAtomicidadIntegrationTest.java
- [ ] T038 [P] [US3] Crear prueba de concurrencia de sincronizaciones en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/SincronizacionEstadisticasConcurrenciaIntegrationTest.java usando PostgreSQL/Testcontainers

### Implementation for User Story 3

- [ ] T039 [US3] Implementar estados de ejecución, errores sanitizados y limpieza/marcado de staging fallido en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstadisticasSyncService.java
- [ ] T040 [US3] Endurecer la promoción transaccional de estadísticas, identidades WHOSCORED nuevas y estado de ejecución ante errores de persistencia en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstadisticasPromocionService.java
- [ ] T041 [US3] Garantizar rollback ante fallo de persistencia y conservación del snapshot anterior en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/SincronizacionEstadisticasJugadorServiceImpl.java
- [ ] T042 [US3] Traducir errores de Adapter, matching y persistencia a excepciones de aplicación sin filtrar detalles de WhoScored en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/exception/ y backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/
- [ ] T043 [US3] Integrar la exclusión de concurrencia y liberación segura en finally en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/SincronizacionEstadisticasJugadorServiceImpl.java

**Checkpoint**: cualquier fallo conserva el último estado válido y una segunda sincronización concurrente no publica ni consulta de forma indebida.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validar el resultado completo contra Constitución, quickstart y calidad del proyecto.

- [ ] T044 [P] Revisar que ningún DTO, URL, stage ID, parámetro HTTP o error interno de WhoScored cruce Adapter en backend/src/main/java/com/devchesterunited/devchesterunitedv2/
- [ ] T045 [P] Revisar que no se hayan reintroducido external_id ni creado representaciones alternativas o tabla de matching en backend/src/main/java/ y backend/src/main/resources/db/migration/
- [ ] T046 [P] Añadir o ajustar documentación JavaDoc de los puertos, value objects, resolvers y estados en backend/src/main/java/com/devchesterunited/devchesterunitedv2/
- [ ] T047 Ejecutar la validación completa definida en specs/002-player-statistics-sync/quickstart.md desde backend/ y corregir únicamente fallos introducidos por la feature
- [ ] T048 Ejecutar compilación y suite completa con backend/gradlew.bat test --no-daemon --console=plain y verificar que todos los tests existentes permanecen intactos

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup (Phase 1): sin dependencias.
- Foundational (Phase 2): depende de Setup y bloquea todas las historias.
- User Story 1: depende de Foundational y es el MVP funcional.
- User Story 2: depende de los componentes de publicación de US1, aunque sus pruebas de idempotencia pueden prepararse en paralelo después de Foundational.
- User Story 3: depende de staging/promoción de US1 y del upsert de US2 para probar rollback completo.
- Polish: depende de las historias implementadas.

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

## Notes

- Todas las tareas usan formato checklist, ID secuencial, marca [P] solo cuando corresponde, etiqueta de historia en fases de usuario y ruta de archivo.
- No hay tareas de endpoint HTTP ni frontend porque están fuera de alcance.
- No se permite modificar o borrar tests existentes.
- Las identidades nuevas WHOSCORED solo son visibles si la promoción completa termina correctamente; staging nunca las publica.
- La red se ejecuta antes de la transacción de promoción y nunca dentro de ella.
