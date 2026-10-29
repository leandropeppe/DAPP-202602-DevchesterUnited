---
description: "Tareas de implementación del simulador de cotización futura"
---

# Tasks: Simulador de cotización futura de jugadores

**Input**: Design documents from `/specs/005-player-valuation-simulator/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/valuation-simulator-api.yaml`, `quickstart.md`.

**Scope**: implementación incremental en backend y frontend. No crear tablas, migraciones ni refactors ajenos a la feature. No modificar ni borrar tests existentes.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: preparar el esqueleto de la feature respetando la arquitectura existente.

- [x] T001 Revisar los paquetes existentes de jugador, estadísticas, valorización y cotización y documentar en `specs/005-player-valuation-simulator/tasks.md` los repositorios/servicios de solo lectura que utilizará la simulación.
  - Repositorios de solo lectura: `JugadorRepository.buscarActivoPorId`, `EstadisticasJugadorRepository.findByJugadorId`, `CotizacionActualEstrategiaRepository.findByJugadorIdAndEstrategiaIdAndEstrategiaVersion`, `EstrategiaValorizacionRepository.findByIdAndVersion`, `EstrategiaVisibleRepository.buscar`, `HistoricoCotizacionEstrategiaRepository.findByJugadorIdAndEstrategiaIdAndEstrategiaVersionOrderByCalculadaAtDesc` y las consultas de población elegible de 004 que correspondan.
  - Servicios de solo lectura: `JugadorService.consultarDetalle`, `CotizacionConsultaService.consultarActual` y `EstrategiaVisibleService.obtener`.
  - La simulación no invocará `ValorizacionJugadorServiceImpl.ejecutar` ni repositorios de ejecución, publicación o persistencia de históricos.
- [x] T002 [P] Crear los paquetes backend de simulación en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/simulacion/`, `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/dto/simulacion/`, `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/interfaces/simulacion/` y `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/simulacion/` sin agregar dependencias a Spring en el modelo.
- [x] T003 [P] Crear la estructura de pruebas nueva en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/simulacion/`, `backend/src/test/java/com/devchesterunited/devchesterunitedv2/contract/simulacion/` e `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/simulacion/` usando fixtures nuevos bajo `backend/src/test/java/com/devchesterunited/devchesterunitedv2/support/fixtures/valorizacion/`.
- [x] T004 [P] Registrar en `frontend/src/api/http.js` el punto de extensión para los dos endpoints documentados, conservando comillas simples, ausencia de punto y coma y manejo explícito de Promise.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: construir los componentes transversales que bloquean cualquier historia. No deben existir escrituras de estadísticas, valorizaciones, cotizaciones o históricos.

**⚠️ CRITICAL**: esta fase debe completarse antes de las historias de usuario.

- [x] T005 [P] Implementar el catálogo inmutable de semántica, rango y etiqueta de métricas en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/simulacion/CatalogoMetricaSimulacion.java`, clasificando acumulativas (`GOALS`, `ASSISTS`, `MAN_OF_THE_MATCH` y acumulativas configuradas), promedios por partido (`SHOTS`, `KEY_PASSES`, `TACKLES`, `INTERCEPTIONS`, `CLEARANCES`, `AERIAL_WON` y demás `PerGame`), porcentaje (`PASS_SUCCESS`) y rating (`RATING`).
- [x] T006 [P] Crear los value objects temporales `EscenarioSimulacion`, `EstadisticasProyectadas` y `ResultadoSimulacion` en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/simulacion/`, con inmutabilidad, horizonte permitido `{3,5,8,10}` y sin referencias a entidades JPA.
- [x] T007 [P] Crear excepciones de dominio y códigos accionables para horizonte, métrica no editable, rango, datos insuficientes, posición no soportada/ambigua y estrategia ausente en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/simulacion/`.
- [x] T008 Implementar la proyección pura en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/simulacion/ProyectorEstadisticas.java`, aplicando `apps + horizonte`, `minsPlayed + horizonte * 90`, acumulativas como incremento y promedios/porcentajes/rating como valor final; rechazar edición de apps/minutos y preservar valores no modificados.
- [x] T009 [P] Añadir tests unitarios de catálogo y proyección en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/simulacion/ProyectorEstadisticasTest.java` para acumulativas, `shotsPerGame`, `keyPassPerGame`, `tacklePerGame`, `interceptionPerGame`, `clearancePerGame`, `aerialWonPerGame`, `rating`, `passSuccess`, los cuatro horizontes, nulos y valores no modificados.
- [x] T010 [P] Añadir tests unitarios de validación en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/simulacion/ValidacionEscenarioSimulacionTest.java` para horizonte inválido, negativos, porcentaje fuera de 0–100, rating fuera de rango, tipos inválidos, apps/minutos enviados y métricas ausentes.
- [x] T011 Definir los puertos de lectura del caso de uso en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/interfaces/simulacion/SimulacionCotizacionService.java` y los DTOs públicos internos en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/dto/simulacion/`, reflejando exactamente `valuation-simulator-api.yaml` sin exponer DTOs de WhoScored.
- [x] T012 Crear mappers de solo lectura desde estadísticas, jugador y cotización existentes hacia el modelo temporal en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/simulacion/SimulacionLecturaMapper.java`, dejando explícitos valores nulos y la cotización actual ausente.
- [x] T013 [P] Añadir test de serialización/forma de request y response del contrato en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/contract/simulacion/SimulacionApiSerializationContractTest.java`, incluyendo `modifications`, `projectedStatistics`, diferencias nulas y `blockingReasons`.

**Checkpoint**: el modelo temporal y sus reglas de clasificación/proyección están probados sin Spring ni base de datos.

---

## Phase 3: User Story 1 - Simular una cotización desde el detalle (Priority: P1) 🎯 MVP backend

**Goal**: preparar y ejecutar una simulación válida para un jugador con estadísticas, estrategia y cotización, completamente en memoria.

**Independent Test**: el endpoint GET carga un jugador elegible; el POST con horizonte 5 y `GOALS: 3` devuelve goles actuales + 3, apps + 5, minutos + 450, cotización simulada y diferencias sin persistir nada.

### Tests for User Story 1

- [x] T014 [P] [US1] Crear fixtures de jugadores con cotización, estadísticas y estrategias de 004 en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/support/fixtures/valorizacion/SimulacionFixtures.java`, incluyendo un escenario delantero con 5 goles.
- [x] T015 [P] [US1] Añadir test unitario del adaptador de escenario a `JugadorEstadistico` en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/simulacion/JugadorEstadisticoSimulacionTest.java`, verificando que la copia temporal conserva posición, ID y familias de métricas.
- [x] T016 [P] [US1] Añadir test de equivalencia en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/simulacion/CalculoSimulacionMotorEquivalenciaTest.java`, ejecutando `MotorValorizacion` con el mismo jugador/población/estrategia que la simulación y comparando score, cotización, normalizaciones y penalizaciones.
- [x] T017 [P] [US1] Añadir test de servicio en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/simulacion/SimulacionCotizacionServiceTest.java` para GET/POST, cotización actual, diferencia monetaria, diferencia porcentual y porcentaje no calculable cuando el valor actual es cero.

### Implementation for User Story 1

- [x] T018 [US1] Implementar el resolver de estrategia visible/versionada y su configuración de elegibilidad en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/simulacion/EstrategiaSimulacionResolver.java`, reutilizando los servicios/configuración de 004 sin copiar pesos ni fórmulas.
- [x] T019 [US1] Implementar la carga de población elegible usada por la normalización de 004 en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/simulacion/PoblacionElegibleSimulacionReader.java`, reemplazando en memoria solo al jugador solicitado por su escenario y sin guardar snapshots ni ejecuciones.
- [x] T020 [US1] Implementar el cálculo aislado en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/simulacion/MotorSimulacionEnMemoria.java`, invocando únicamente `MotorValorizacion.calcular`/`evaluarElegibilidad` y transformando su resultado a `ResultadoSimulacion`.
- [x] T021 [US1] Implementar `SimulacionCotizacionServiceImpl` en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/simulacion/SimulacionCotizacionServiceImpl.java`, orquestando lectura, resolución, proyección, población, motor y diferencias sin invocar `ValorizacionJugadorServiceImpl.ejecutar`, repositorios de escritura, `QuotePublicationPort` ni históricos.
- [x] T022 [US1] Añadir DTOs de entrada/salida y validación Bean Validation en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/jugador/dto/SimulacionCotizacionRequestHttpDTO.java`, `SimulacionConfiguracionHttpDTO.java` y `SimulacionResultadoHttpDTO.java`, rechazando campos adicionales y separando validación HTTP de invariantes de dominio.
- [x] T023 [US1] Exponer `GET` y `POST /api/v1/players/{playerId}/valuation-simulator` en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/jugador/JugadorController.java` o un controller de jugador dedicado, delegando exclusivamente al Service y documentando OpenAPI v3 con los códigos del contrato.
- [x] T024 [P] [US1] Añadir tests MockMvc del contrato en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/contract/simulacion/SimulacionCotizacionControllerContractTest.java` para 200, 400, 404 y 409, incluyendo mensajes accionables en español.
- [x] T025 [P] [US1] Añadir integración con PostgreSQL/Testcontainers en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/simulacion/SimulacionNoPersistenciaPostgresIntegrationTest.java`, comprobando resultado válido y que no cambian filas de cotización/histórico/estadísticas ni se crean migraciones/tablas nuevas.
- [x] T026 [US1] Añadir verificaciones explícitas con mocks/espías en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/simulacion/SimulacionNoEscrituraTest.java` para demostrar que no se invocan repositorios de escritura, publicación de cotización, ejecución oficial ni persistencia de histórico.

**Checkpoint**: el backend permite una simulación válida y reproducible, con equivalencia al motor 004 y sin efectos persistentes.

---

## Phase 4: User Story 2 - Ajustar métricas según posición (Priority: P1)

**Goal**: derivar dinámicamente los campos editables de estrategia, posición y disponibilidad, cubriendo FW, MF, DF y GK sin inventar métricas.

**Independent Test**: GET para delantero, mediocampista, defensor y arquero devuelve exactamente las métricas utilizables; una posición múltiple se resuelve o se bloquea con causa explícita.

### Tests for User Story 2

- [x] T027 [P] [US2] Añadir fixtures de FW, MF y DF con las métricas de 004 en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/support/fixtures/valorizacion/SimulacionPosicionesFixtures.java`.
- [x] T028 [P] [US2] Añadir fixture GK en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/support/fixtures/valorizacion/SimulacionGoalkeeperFixtures.java` usando únicamente `RATING`, `PASS_SUCCESS`, `LONG_PASSES` y `MAN_OF_THE_MATCH` cuando estén presentes, sin agregar atajadas/clean sheets/paradas.
- [x] T029 [P] [US2] Añadir tests de configuración dinámica para FW/MF/DF en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/simulacion/CamposEditablesPorPosicionTest.java`, verificando goles, asistencias, rating, tiros, pases clave, tackles, intercepciones, despejes, duelos aéreos y penalizaciones configuradas.
- [x] T030 [P] [US2] Añadir tests GK en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/simulacion/ConfiguracionArqueroSimulacionTest.java`, verificando estrategia provisoria, campos realmente disponibles y rechazo cuando falta una métrica positiva requerida.
- [x] T031 [P] [US2] Añadir tests de posiciones múltiples en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/simulacion/ResolucionPosicionSimulacionTest.java` para resolución determinista, posición ambigua y posición no soportada.

### Implementation for User Story 2

- [x] T032 [US2] Implementar el resolver de posiciones múltiples en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/simulacion/ResolutorPosicionSimulacion.java`, delegando en la normalización existente de estrategia y devolviendo posición original, posición aplicada y causa si no es resoluble.
- [x] T033 [US2] Implementar la derivación de campos editables en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/simulacion/ConfiguradorMetricasSimulacion.java`, intersectando estrategia, posición, catálogo y valores presentes; apps/minutos siempre serán solo lectura.
- [x] T034 [US2] Integrar el resolver de posición y configurador de métricas en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/simulacion/SimulacionCotizacionServiceImpl.java`, diferenciando ausencia de estadísticas, posición no soportada, estrategia faltante y métrica positiva ausente.
- [x] T035 [US2] Completar el mapeo de familias y etiquetas públicas en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/simulacion/SimulacionLecturaMapper.java` y `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/jugador/dto/SimulacionConfiguracionResponseDTO.java` para que el frontend no mantenga listas de negocio duplicadas.
- [x] T036 [US2] Añadir tests de endpoint por posición en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/contract/simulacion/SimulacionPosicionesControllerContractTest.java`, verificando campos, `resolvedPosition`, `provisionalStrategy` y `blockingReasons`.

**Checkpoint**: el backend entrega una configuración posicional coherente con la estrategia vigente y trata GK según los datos reales disponibles.

---

## Phase 5: User Story 3 - Recalcular y comparar sin persistir (Priority: P1)

**Goal**: integrar el simulador al detalle del jugador, permitir recalcular/restablecer escenarios y preservar datos oficiales con feedback asíncrono correcto.

**Independent Test**: abrir el modal, ejecutar cinco escenarios, restablecer, comprobar cotización/histórico sin cambios y verificar loader mínimo de 3 s solo para éxitos rápidos.

### Tests for User Story 3

- [x] T037 [P] [US3] Añadir tests de cliente HTTP en `frontend/src/api/http.valuation-simulator.test.js` para GET/POST, payload de solo modificaciones, errores 400/409 y respuestas con diferencias nulas.
- [x] T038 [P] [US3] Añadir tests del modal en `frontend/src/components/PlayerValuationSimulator.test.jsx` para selector exacto 3/5/8/10, actuales/proyectados, apps/minutos deshabilitados, reset, recálculo y estado de error conservando edición.
- [x] T039 [P] [US3] Añadir tests de loader en `frontend/src/components/MaradonaLoader.test.jsx` y `frontend/src/components/PlayerValuationSimulator.loader.test.jsx` para éxito rápido ≥3 s, éxito lento sin espera extra y error sin espera artificial.
- [x] T040 [P] [US3] Añadir test de integración de vista en `frontend/src/views/PlayerDetailPage.valuation-simulator.test.jsx` que abra el simulador desde detalle y mantenga el contexto de la página durante múltiples simulaciones.
- [x] T041 [P] [US3] Añadir escenarios Playwright en `frontend/e2e/player-valuation-simulator.spec.js` para FW, MF, DF, GK soportado, posiciones múltiples, datos faltantes, cotización ausente, horizonte inválido y cinco recalculos consecutivos.

### Implementation for User Story 3

- [x] T042 [US3] Implementar `getValuationSimulatorConfig` y `simulatePlayerValuation` en `frontend/src/api/http.js`, consumiendo solo `/api/v1/players/{playerId}/valuation-simulator` y enviando únicamente `horizonMatches` y `modifications`.
- [x] T043 [P] [US3] Implementar el loader accesible y contextual en `frontend/src/components/MaradonaLoader.jsx` y `frontend/public/images/maradona-loader.*`, usando el recurso disponible/provisto sin bloquear errores.
- [x] T044 [US3] Implementar el modal y estado local en `frontend/src/components/PlayerValuationSimulator.jsx`, con selector de horizonte, inputs derivados de `statistics`, actuales/proyectados, reset, recálculo, diferencias y estados diferenciados del contrato.
- [x] T045 [P] [US3] Crear estilos responsive y accesibles del modal en `frontend/src/styles/player-valuation-simulator.css`, manteniendo la convención de 2 espacios, comillas simples y jerarquía JSX legible.
- [x] T046 [US3] Integrar botón de apertura, carga de configuración y cierre seguro en `frontend/src/views/PlayerDetailPage.jsx`, evitando recargar el detalle y manteniendo intactas estadísticas/cotización oficiales.
- [x] T047 [US3] Implementar en `frontend/src/components/PlayerValuationSimulator.jsx` el cálculo de espera `max(0, 3000ms - elapsed)` únicamente después de respuesta exitosa; mostrar inmediatamente respuestas lentas y errores.
- [x] T048 [US3] Añadir mensajes accesibles y accionables para ausencia de estadísticas, cotización ausente, estrategia, posición y métricas GK en `frontend/src/components/PlayerValuationSimulator.jsx`, sin reproducir reglas de valorización del backend.

**Checkpoint**: la experiencia completa funciona desde `PlayerDetailPage`, permite explorar escenarios y no altera datos oficiales.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: cerrar documentación, calidad, compatibilidad y validación final sin ampliar el alcance.

- [x] T049 [P] Actualizar la documentación OpenAPI del endpoint en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/jugador/` para que Swagger UI refleje `contracts/valuation-simulator-api.yaml` y todos los códigos de error.
- [x] T050 [P] Actualizar la colección Postman existente bajo `backend`/`scripts`/`specs` donde el proyecto la mantenga, agregando GET/POST del simulador sin credenciales ni datos externos reales.
- [x] T051 Ejecutar `.\gradlew.bat test --no-daemon --console=plain` desde `backend/` y corregir únicamente fallos introducidos por esta feature, sin modificar ni borrar tests existentes.
- [x] T052 Ejecutar `npm run test -- --run`, `npm run build` y `npm run test:e2e -- --grep 'simulador|valuation'` desde `frontend/`; corregir formato, accesibilidad, Promises no manejadas y regresiones de la feature.
- [x] T053 Ejecutar el flujo completo de `specs/005-player-valuation-simulator/quickstart.md` y verificar que no existan migraciones/tablas nuevas ni invocaciones a persistencia oficial en el diff de la feature.
- [x] T054 Ejecutar la revisión SonarCloud/quality gate definida por el proyecto y documentar en `specs/005-player-valuation-simulator/quickstart.md` cualquier issue nueva que deba resolverse antes del cierre.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias; prepara paquetes, tests y API client.
- **Foundational (Phase 2)**: depende de Setup y bloquea las historias; contiene el modelo temporal, catálogo, proyección, validación y contrato base.
- **US1 (Phase 3)**: depende de Phase 2; entrega el MVP backend y debe completarse antes de conectar la UI.
- **US2 (Phase 4)**: depende de US1 porque amplía la configuración/resolución usada por los endpoints, aunque sus tests de dominio pueden prepararse en paralelo con US1.
- **US3 (Phase 5)**: depende de los endpoints estables de US1 y de la configuración dinámica de US2; entrega la experiencia completa frontend.
- **Polish (Phase 6)**: depende de las historias que se quieran entregar y de sus checkpoints.

### User Story Dependencies

- **US1 (P1)**: Phase 2 → backend mínimo de simulación; es el MVP recomendado.
- **US2 (P1)**: Phase 2 + US1; añade cobertura FW/MF/DF/GK y posiciones múltiples.
- **US3 (P1)**: US1 + US2; integra modal, loader, reset, recálculo y no persistencia observable.

### Parallel Opportunities

- T002–T004 pueden ejecutarse en paralelo.
- T005–T007, T009–T010 y T013 pueden ejecutarse en paralelo antes de integrar el servicio.
- En US1, T014–T017 y T024–T026 pueden distribuirse entre tests de dominio, contrato e integración una vez definidos los DTOs.
- En US2, T027–T031 y luego T036 pueden ejecutarse en paralelo con implementación del resolver si se usan contratos/fakes claros.
- En US3, T037–T041 y T043–T045 pueden ejecutarse en paralelo; T046–T048 requieren el componente y API client.
- T049–T050 son independientes entre sí; T051–T054 deben ejecutarse al final.

## Implementation Strategy

### MVP First

1. Completar Phase 1 y Phase 2.
2. Completar US1: proyección, población, motor, servicio y endpoints.
3. Ejecutar T016, T024–T026 y validar equivalencia/no escritura.
4. Detenerse para validar el MVP backend con el quickstart.

### Incremental Delivery

1. Agregar US2 para derivación posicional y cobertura FW/MF/DF/GK.
2. Validar posiciones múltiples y métricas faltantes.
3. Agregar US3 para modal, reset, múltiples simulaciones y loader.
4. Ejecutar Phase 6, build, E2E, quickstart y quality gate.

## Notes

- Todas las tareas siguen `- [ ] Txxx`, incluyen `[P]` solo cuando son paralelizables, usan `[US1]`, `[US2]` o `[US3]` únicamente en fases de historia y mencionan rutas concretas.
- Las pruebas nuevas deben ser aditivas; la constitución prohíbe modificar o borrar tests existentes sin autorización explícita.
- Ninguna tarea permite crear tablas/migraciones, persistir escenarios o duplicar las fórmulas del motor 004.
