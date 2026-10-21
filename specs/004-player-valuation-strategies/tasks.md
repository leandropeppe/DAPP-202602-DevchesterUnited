---

description: "Tareas de implementación para valorización multi-estrategia de jugadores"
---

# Tasks: Valorización de jugadores según estrategia

**Input**: Design documents from `/specs/004-player-valuation-strategies/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/valuation-api.yaml, quickstart.md

**Tests**: Incluidos porque la constitución exige pruebas unitarias, de integración y de contrato para completar la feature.

**Implementation Strategy**: Construir primero el dominio y la persistencia común; entregar US1 como MVP funcional de cálculo simultáneo; agregar consultas visibles/específicas y ejecución programada en incrementos independientes.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Preparar la migración y la estructura de la feature sin asumir una versión Flyway fija.

- [x] T001 Inspeccionar `backend/src/main/resources/db/migration/` y documentar en `specs/004-player-valuation-strategies/quickstart.md` la próxima versión Flyway disponible para la migración de valorización
- [x] T002 [P] Crear los paquetes base de valorización en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/`, `persistence/`, `service/` y `controller/dto/` respetando Package by Layer
- [x] T003 [P] Preparar fixtures deterministas de jugadores, posiciones y estadísticas completas/incompletas en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/valorizacion/ValorizacionFixtures.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Crear las invariantes y componentes compartidos que bloquean todas las historias.

**⚠️ CRITICAL**: Ninguna historia puede comenzar hasta completar esta fase.

- [x] T004 Diseñar y validar los value objects de score, peso, valorBase y factorEscala en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/valorizacion/`
- [x] T005 Crear la migración Flyway con la versión disponible en T001 y tablas para estrategias, selección visible, ejecuciones con snapshot lógico (`snapshot_at`), cotización actual por estrategia/version, histórico, elegibilidad y trazabilidad
- [x] T006 [P] Crear entidades de persistencia y mappers para estrategias, selección visible, ejecuciones, cotizaciones actuales, históricos y resultados de elegibilidad en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/dto/` y `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/mapper/`
- [x] T007 [P] Crear repositorios para estrategias habilitadas, estrategia visible, ejecuciones, cotización actual por jugador/estrategia/version e histórico en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/`
- [x] T008 Implementar las invariantes de habilitación y visibilidad, incluyendo como máximo una estrategia visible, selección no vacía con configuración operativa y cambio visible atómico, en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/valorizacion/` y `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/EstrategiaVisibleServiceImpl.java`
- [x] T009 [P] Implementar la captura de un snapshot lógico único de estadísticas persistidas en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/SnapshotEstadisticoService.java`
- [x] T010 [P] Agregar pruebas de invariantes de estrategia, pesos, visibilidad y snapshot común en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/modelo/valorizacion/EstrategiaValorizacionTest.java` y `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/SnapshotEstadisticoServiceTest.java`

**Checkpoint**: La estructura persistente, las invariantes y el snapshot común están listos; las historias pueden implementarse en orden de prioridad.

---

## Phase 3: User Story 1 - Calcular cotizaciones para estrategias habilitadas (Priority: P1) 🎯 MVP

**Goal**: Calcular GENERAL v1 y POSITIONAL v1 en una misma ejecución, con resultados e históricos separados por estrategia/version.

**Independent Test**: Ejecutar una valorización manual con ambas estrategias habilitadas y un snapshot fijo; comprobar un resultado independiente por cada combinación jugador + estrategia valorizada correctamente.

### Tests for User Story 1

- [x] T011 [P] [US1] Crear pruebas unitarias de normalización Min-Max global/posicional, `min == max` y límites [0,1] en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/modelo/valorizacion/NormalizadorMetricasTest.java`
- [x] T012 [P] [US1] Crear pruebas de elegibilidad por estrategia, métricas positivas obligatorias, métricas negativas ausentes y umbral configurable sin default en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/modelo/valorizacion/ElegibilidadValorizacionTest.java`
- [x] T013 [P] [US1] Crear prueba de ejecución con dos estrategias que verifique captura de versiones y snapshot estadístico único en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/ValorizacionSnapshotConsistencyTest.java`
- [x] T014 [P] [US1] Crear prueba de integración PostgreSQL para cotización actual e histórico por jugador + estrategia + version en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/persistence/CotizacionPorEstrategiaIntegrationTest.java`

### Implementation for User Story 1

- [x] T015 [P] [US1] Implementar `ResultadoNormalizacion`, `ResultadoElegibilidad`, causas de exclusión y trazabilidad de ausencias en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/valorizacion/`
- [x] T016 [P] [US1] Implementar `EstrategiaGeneralV1` con sus pesos exactos y score general en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/valorizacion/EstrategiaGeneralV1.java`
- [x] T017 [P] [US1] Implementar `EstrategiaPosicionalV1` con configuraciones DL, MC, DF y PO, penalizaciones y marca provisoria de PO en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/valorizacion/EstrategiaPosicionalV1.java`
- [x] T018 [US1] Implementar el motor de normalización y cálculo de score sobre un snapshot en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/valorizacion/MotorValorizacion.java`
- [x] T019 [US1] Implementar la conversión común `valorBase + score * factorEscala` y el clamp de score en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/valorizacion/ConversorCotizacion.java`
- [x] T020 [US1] Implementar el caso de uso que captura estrategias habilitadas/versiones y snapshot, calcula cada estrategia de forma independiente y continúa ante exclusiones en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/ValorizacionJugadorServiceImpl.java`
- [x] T021 [US1] Persistir una cotización actual y un histórico por combinación jugador + estrategia + version, incluyendo ejecución, score, valorBase, factorEscala y trazabilidad, en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/ValorizacionJugadorServiceImpl.java` y `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/`
- [x] T022 [US1] Registrar GENERAL v1 y POSITIONAL v1 habilitadas para cálculo y seleccionar POSITIONAL v1 como estrategia visible inicial mediante `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/config/ValorizacionInicialConfig.java`, manteniendo la selección visible como caso de uso/servicio interno sin endpoint, rol ADMIN ni autorización específica

**Checkpoint**: US1 permite ejecutar ambas estrategias habilitadas con el mismo snapshot y conserva resultados separados sin depender de tokenización.

---

## Phase 4: User Story 2 - Comparar rendimiento general y posicional (Priority: P1)

**Goal**: Garantizar que las dos fórmulas definidas producen scores comparables pero no idénticos, con normalización y penalización según alcance.

**Independent Test**: Usar un dataset con las cuatro posiciones y verificar fórmulas, pesos, población de referencia, goles de defensores y penalizaciones por rol.

### Tests for User Story 2

- [x] T023 [P] [US2] Crear pruebas de pesos y score GENERAL v1 con la fórmula exacta en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/modelo/valorizacion/EstrategiaGeneralV1Test.java`
- [x] T024 [P] [US2] Crear pruebas de pesos y score POSITIONAL v1 para DL, MC, DF y PO en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/modelo/valorizacion/EstrategiaPosicionalV1Test.java`
- [x] T025 [P] [US2] Crear pruebas de normalización por posición, goles positivos de DF, roja significativa y wasDribbled con impacto cero/pequeño/considerable según posición en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/modelo/valorizacion/PenalizacionesPosicionalesTest.java`

### Implementation for User Story 2

- [x] T026 [US2] Completar la configuración explícita de métricas, pesos y penalizaciones versionadas en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/config/ValorizacionInicialConfig.java`
- [x] T027 [US2] Validar que todas las estrategias versionadas rechacen pesos positivos distintos de 100% y configuraciones posicionales incompletas en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/valorizacion/ConfiguracionEstrategia.java`
- [x] T028 [US2] Agregar pruebas de integración que comparen resultados GENERAL v1/POSITIONAL v1 sin compartir ni sobrescribir históricos en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/ComparacionEstrategiasIntegrationTest.java`

**Checkpoint**: Las fórmulas y penalizaciones de ambas estrategias están verificadas con las poblaciones de normalización correctas.

---

## Phase 5: User Story 3 - Consultar cotización visible, específica e histórico trazable (Priority: P1)

**Goal**: Exponer por defecto la estrategia visible y permitir consulta explícita de una estrategia/version sin mezclar series.

**Independent Test**: Consultar sin estrategia, consultar GENERAL v1 explícitamente, cambiar la visible y repetir; verificar que cambia solo la selección de lectura.

### Tests for User Story 3

- [x] T029 [P] [US3] Crear pruebas de servicio para selección visible atómica, cambio sin recálculo y rechazo de estrategia no habilitada en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/EstrategiaVisibleServiceIntegrationTest.java`
- [x] T030 [P] [US3] Crear pruebas MockMvc de cotización actual por defecto/explícita en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/controller/CotizacionActualE2ETest.java`
- [x] T031 [P] [US3] Crear pruebas MockMvc de histórico por defecto/explícito y separación por estrategia/version en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/controller/HistoricoCotizacionE2ETest.java`

### Implementation for User Story 3

- [x] T032 [US3] Implementar consultas de cotización actual e histórico con selección visible por defecto y filtros opcionales strategyId/strategyVersion en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/CotizacionConsultaServiceImpl.java`
- [x] T033 [US3] Agregar DTOs de cotización, trazabilidad e histórico en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/`
- [x] T034 [US3] Agregar endpoints GET definidos en `specs/004-player-valuation-strategies/contracts/valuation-api.yaml` al controlador apropiado en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/JugadorController.java`
- [x] T035 [US3] Documentar los endpoints y errores con OpenAPI v3, sin agregar endpoint administrativo, en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/JugadorController.java`

**Checkpoint**: El frontend/consumidor recibe la estrategia visible por defecto y puede consultar una serie específica sin mezclar históricos.

---

## Phase 6: User Story 4 - Ejecutar valorizaciones periódicas y tolerantes a datos incompletos (Priority: P2)

**Goal**: Compartir el mismo caso de uso entre ejecución manual y job semanal, con aislamiento por estrategia y tolerancia a fallas parciales.

**Independent Test**: Ejecutar manualmente y mediante el job con el mismo snapshot; introducir exclusiones/fallas por estrategia y comprobar que las combinaciones válidas se completan.

### Tests for User Story 4

- [x] T036 [P] [US4] Crear prueba de equivalencia entre disparador manual y semanal usando el mismo caso de uso en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/EjecucionValorizacionManualSemanalTest.java`
- [x] T037 [P] [US4] Crear prueba de aislamiento de falla por jugador/estrategia en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/ValorizacionFallaParcialTest.java`
- [x] T038 [P] [US4] Crear prueba de población vacía, estrategias sin elegibles y causas trazables en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/ValorizacionDatosInsuficientesTest.java`

### Implementation for User Story 4

- [x] T039 [US4] Exponer la operación manual interna mediante `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/interfaces/ValorizacionJugadorService.java` sin endpoint administrativo
- [x] T040 [US4] Implementar el disparador semanal que delega en el mismo servicio en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/ValorizacionScheduler.java`
- [x] T041 [US4] Registrar resultados parciales, exclusiones y fallas por jugador/estrategia sin rollback de combinaciones válidas, usando el aislamiento simple compatible con la arquitectura actual y sin introducir transacciones independientes o `REQUIRES_NEW` salvo justificación técnica, en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/ValorizacionJugadorServiceImpl.java`
- [x] T042 [US4] Configurar frecuencia semanal y parámetros de elegibilidad sin fijar un umbral funcional por defecto en `backend/src/main/resources/application.properties`

**Checkpoint**: La ejecución manual y semanal son equivalentes, comparten snapshot y toleran fallas parciales por estrategia/jugador.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Validar documentación, contratos, calidad y límites del feature.

- [x] T043 [P] Actualizar el contrato OpenAPI versionado con los DTOs finales en `specs/004-player-valuation-strategies/contracts/valuation-api.yaml`
- [x] T044 [P] Agregar pruebas de regresión que verifiquen ausencia de dependencias con tokenización y mercado en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/ValorizacionAlcanceTest.java`
- [x] T045 Ejecutar todos los escenarios de `specs/004-player-valuation-strategies/quickstart.md` desde `backend/` y documentar resultados en la entrega
- [ ] T046 Ejecutar compilación, suite de tests y revisión Sonar/quality gate según la constitución desde `backend/`

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**: Sin dependencias.
- **Phase 2 Foundational**: Depende de Phase 1 y bloquea todas las historias.
- **Phase 3 US1**: Depende de Phase 2; es el MVP y habilita la infraestructura de cálculo multi-estrategia.
- **Phase 4 US2**: Depende de Phase 3 para validar/ajustar las dos estrategias calculadas.
- **Phase 5 US3**: Depende de Phase 3 para consultar resultados persistidos; puede ejecutarse en paralelo con US2 si los contratos de persistencia están estabilizados.
- **Phase 6 US4**: Depende de Phase 3; puede ejecutarse en paralelo con US2 y US3 después de estabilizar el servicio común.
- **Phase 7 Polish**: Depende de las historias que se decida entregar.

### User Story Dependencies

- **US1 (P1)**: Depende solo de Foundational; MVP.
- **US2 (P1)**: Depende de US1 porque verifica las estrategias implementadas por el cálculo común.
- **US3 (P1)**: Depende de US1; puede paralelizarse con US2 tras estabilizar repositorios.
- **US4 (P2)**: Depende de US1; puede paralelizarse con US2/US3 después de estabilizar el caso de uso.

### Parallel Opportunities

- T002 y T003 pueden ejecutarse en paralelo.
- T006, T007, T009 y T010 pueden ejecutarse en paralelo después de acordar el esquema de T005.
- En US1, T011–T014 son paralelizables; T016 y T017 también lo son antes de T018/T020.
- En US2, T023–T025 son paralelizables.
- En US3, T029–T031 son paralelizables; T033 puede avanzar junto con pruebas de contrato.
- En US4, T036–T038 son paralelizables.
- US3 y US4 pueden avanzar en paralelo con US2 tras US1.

## Parallel Example: User Story 1

```text
T011 Normalización
T012 Elegibilidad
T013 Snapshot común
T014 Persistencia por estrategia/version

T016 Estrategia GENERAL v1
T017 Estrategia POSITIONAL v1
```

## MVP Scope

El MVP recomendado es Phase 1 + Phase 2 + US1 (T001–T022): calcular GENERAL v1 y POSITIONAL v1 habilitadas sobre un snapshot único y persistir cotización actual e histórico separados por estrategia/version. US3 agrega la consulta consumible y US4 la automatización semanal.

## Notes

- [x] T047 Registrar GENERAL v1 y POSITIONAL v1 en un bootstrap idempotente posterior a Flyway, usando los valores externos y sin modificar filas existentes.
- [x] T048 Agregar validacion previa de estrategias persistidas y pruebas de integracion para base vacia, visible inicial, idempotencia y valorizacion sin violacion de FK.

### Incremento POSITIONAL v2

- [x] T049 Implementar `EstrategiaPosicionalV2` con z-score por posición, sigmoide, métricas por 90, bloques, confiabilidad progresiva y sin rating/factor de mercado.
- [x] T050 Registrar POSITIONAL v2 sin eliminar V1 y seleccionar v2 como estrategia visible/default, manteniendo GENERAL v1.
- [x] T051 Verificar persistencia, histórico, consultas por defecto y consulta explícita de POSITIONAL v1 mediante la selección versionada existente.
- [x] T052 Agregar pruebas de V2 para nulls, desviación cero, inversión de métricas, confiabilidad, score acotado y bootstrap de v2.
- [x] T053 Ejecutar tests backend, tests/build/verify frontend y revisar tareas de calidad disponibles.

- Estado de implementación: T001–T042 completadas y verificadas; T043–T045 completadas y verificadas; T046 permanece pendiente por autenticación externa de SonarCloud.
- Todas las tareas siguen el formato `- [ ] Txxx [P?] [USx?] descripción con ruta exacta`.
- Ninguna tarea agrega usuario ADMIN, roles, autorización administrativa o endpoint para cambiar estrategia visible.
- La migración debe seleccionar la próxima versión Flyway disponible durante T001/T005; no fijar V17.
