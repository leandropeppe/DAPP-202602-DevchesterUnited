---

description: "Task list for transversal audit and secure logging"
---

# Tasks: Auditoría transversal y logging seguro

**Input**: Design documents from `specs/008-aop-auditoria-logging/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Scope rule**: Todas las tareas de implementación agregan archivos nuevos o modifican únicamente `backend/build.gradle` para la dependencia AOP. No se modifican tests existentes, lógica de negocio, Spring Security, Actuator, Prometheus, tablas ni migraciones.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Preparar la dependencia y el espacio de pruebas sin alterar el comportamiento actual.

- [X] T001 Incorporar `org.springframework.boot:spring-boot-starter-aop` en `backend/build.gradle` usando la gestión de versiones de Spring Boot 3.5.16, sin fijar una versión manual.
- [X] T002 [P] Crear los directorios nuevos `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/auditoria/` y `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/auditoria/` sin mover paquetes existentes.
- [X] T003 [P] Crear los directorios de pruebas `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/auditoria/`, `backend/src/test/java/com/devchesterunited/devchesterunitedv2/e2e/auditoria/` y `backend/src/test/java/com/devchesterunited/devchesterunitedv2/performance/auditoria/`.
- [X] T004 Verificar en `backend/build.gradle` que la dependencia AOP queda dentro de las dependencias gestionadas, conserva Java 21 y no introduce dependencias de base de datos ni observabilidad adicional.

**Checkpoint**: El proyecto reconoce Spring AOP mediante la dependencia gestionada y no se ha modificado código de negocio, seguridad ni observabilidad.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Construir el contrato común y una implementación base del writer que necesitan todas las historias.

**⚠️ CRITICAL**: Ninguna historia puede comenzar antes de completar esta fase.

- [X] T005 Crear el record efímero `AuditoriaEvento` en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/auditoria/AuditoriaEvento.java` con `eventName`, `operation`, `stage`, `result`, `durationMs`, `timestamp`, `requestCorrelationId`, `executionCorrelationId`, `externalCorrelationId`, `contextType` y `errorCode`, respetando el contrato de `contracts/audit-event.md`.
- [X] T006 Crear `AuditoriaOperationCatalog` en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/auditoria/AuditoriaOperationCatalog.java` con exactamente estas 10 operaciones (`catalog.sync`, `statistics.sync`, `valuation.manual`, `valuation.weekly`, `market.purchase`, `market.sale`, `market.p2p.offer.publish`, `market.p2p.purchase`, `market.p2p.offer.cancel`, `market.initialize`) y sin fallback que audite métodos no incluidos.
- [X] T007 Crear `CorrelacionContext` en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/auditoria/CorrelacionContext.java` para exponer de forma segura `requestCorrelationId` obligatorio en HTTP o `executionCorrelationId` obligatorio en BACKGROUND, sin persistencia ni IDs HTTP sintéticos.
- [X] T008 [P] Crear los tests unitarios del contrato de evento y catálogo en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/auditoria/AuditoriaEventoContractTest.java` y `AuditoriaOperationCatalogTest.java`, verificando campos obligatorios, duración y cardinalidad exacta de 10 operaciones.
- [X] T009 [P] Crear una prueba de contexto aislado en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/auditoria/CorrelacionContextTest.java` que compruebe instalación, lectura, ausencia fuera de HTTP y limpieza.
- [X] T010 Implementar desde la primera versión un `AuditoriaEventWriter` seguro en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/auditoria/AuditoriaEventWriter.java`, con una allowlist estricta y completa de los campos de `contracts/audit-event.md` (`eventName`, `operation`, `stage`, `result`, `durationMs`, `timestamp`, `contextType`, el identificador obligatorio del contexto, `externalCorrelationId` validado cuando corresponda y `errorCode` controlado). El writer no puede registrar argumentos, valores de retorno, headers HTTP, contraseñas, JWT, API keys, secretos, mensajes arbitrarios de excepciones, stack traces, `Throwable` completos ni objetos de negocio; debe usar un mensaje SLF4J constante, campos estructurados/MDC, aplicar best effort ante fallos propios y preservar resultados y excepciones originales. Ejecutar los tests de la fase verificando además que los nuevos paquetes no dependan de `modelo`, `persistence` ni `service`.

**Checkpoint**: El contrato de datos, el catálogo cerrado y el contexto de ejecución están definidos y comprobados sin tocar lógica funcional.

---

## Phase 3: User Story 1 - Auditar operaciones críticas (Priority: P1) 🎯 MVP

**Goal**: Emitir exactamente un evento START y un evento END para cada una de las 10 operaciones seleccionadas, con duración, resultado y códigos de error controlados.

**Independent Test**: Invocar cada boundary proxyado con éxito y con error, inspeccionar el sink de eventos de prueba y verificar un único par START/END sin cambio de resultado ni excepción.

### Tests for User Story 1

- [ ] T011 [P] [US1] Crear `ProxyInterceptabilityTest` en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/auditoria/ProxyInterceptabilityTest.java` para verificar que los 10 servicios seleccionados son beans proxyados e invocables mediante sus interfaces públicas.
- [ ] T012 [P] [US1] Crear `SelectedOperationsContractTest` en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/auditoria/SelectedOperationsContractTest.java` que mapee cada método real a su operación lógica y falle si aparece una operación adicional.
- [ ] T013 [P] [US1] Crear `AuditoriaAspectUnitTest` en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/auditoria/AuditoriaAspectUnitTest.java` para éxito, excepción, duración no negativa, START antes de `proceed()` y END en `finally`.
- [ ] T014 [P] [US1] Crear `AuditoriaOperationsIntegrationTest` en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/auditoria/AuditoriaOperationsIntegrationTest.java` para comprobar exactamente START/END en sincronización, valorización y mercado con fixtures sintéticos.

### Implementation for User Story 1

- [ ] T015 [US1] Implementar `AuditoriaErrorCodeResolver` en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/auditoria/AuditoriaErrorCodeResolver.java` con códigos estables por tipo/categoría conocida y `UNEXPECTED_ERROR` como fallback, sin leer mensajes ni causas completas.
- [ ] T016 [US1] Implementar `AuditoriaAspect` en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/auditoria/AuditoriaAspect.java` con advice `@Around`, reloj monotónico para duración, START antes de `proceed()` y END en éxito o error.
- [ ] T017 [US1] Verificar antes de instrumentar los métodos de sincronización que `SincronizacionCatalogoServiceImpl.sincronizar()` y `SincronizacionEstadisticasJugadorServiceImpl.sincronizar()` atraviesan proxies, y registrar únicamente `catalog.sync` y `statistics.sync` en `AuditoriaOperationCatalog`.
- [ ] T018 [US1] Verificar con una prueba real de Spring que el bean de `ValorizacionJugadorServiceImpl.ejecutarManual(...)` está proxyado y que una invocación de prueba a través del bean/interface atraviesa el proxy; no asumir un caller productivo no demostrado. Verificar también `ejecutarSemanal(...)` desde sus entradas reales y registrar únicamente `valuation.manual` y `valuation.weekly`.
- [ ] T019 [US1] Verificar antes de instrumentar los métodos de mercado que `MarketPurchaseServiceImpl.comprar(...)`, `MarketSaleServiceImpl.vender(...)`, `MarketP2POfferServiceImpl.publicar(...)`, `MarketP2PPurchaseExecutionServiceImpl.comprar(UUID, String, UUID, Long, MarketP2PQuoteIdentity)`, `MarketP2PCancellationServiceImpl.cancelar(...)` y `MarketInitializationServiceImpl.initialize()` atraviesan proxies, registrando solo `market.purchase`, `market.sale`, `market.p2p.offer.publish`, `market.p2p.purchase`, `market.p2p.offer.cancel` y `market.initialize`.
- [ ] T020 [US1] Ajustar el pointcut de `AuditoriaAspect` para excluir el overload interno `comprar(MarketP2PPurchaseCommand)`, repositorios, consultas, locks, mappers, `QuotePublicationPort` y self-invocation, evitando eventos duplicados.
- [ ] T021 [US1] Completar `AuditoriaOperationsIntegrationTest` con escenarios de error de sincronización, valorización, compra, venta, P2P e inicialización, verificando `errorCode`, excepción original y ausencia de mensaje/stack trace en el evento.

**Checkpoint**: US1 es demostrable de forma independiente; las 10 operaciones producen auditoría cerrada y ninguna operación no seleccionada es interceptada.

---

## Phase 4: User Story 2 - Relacionar eventos de una solicitud (Priority: P1)

**Goal**: Generar un ID interno único por solicitud, validar por separado el ID externo, devolver el interno en `X-Correlation-ID` y limpiar MDC siempre.

**Independent Test**: Ejecutar solicitudes HTTP concurrentes con y sin header externo, comprobar respuesta, eventos correlacionados y limpieza posterior.

### Tests for User Story 2

- [ ] T022 [P] [US2] Crear `CorrelacionHttpFilterTest` en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/auditoria/CorrelacionHttpFilterTest.java` para generación UUID, valores válidos de 1–128 caracteres, caracteres inválidos y header de respuesta.
- [ ] T023 [P] [US2] Crear `CorrelationMdcIntegrationTest` en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/auditoria/CorrelationMdcIntegrationTest.java` para comprobar MDC durante la solicitud y limpieza en éxito, error y excepción del chain.
- [ ] T024 [P] [US2] Crear `CorrelationConcurrencyTest` en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/auditoria/CorrelationConcurrencyTest.java` para solicitudes paralelas con el mismo `X-Correlation-ID` externo, verificando IDs internos distintos y ausencia de mezcla.

### Implementation for User Story 2

- [ ] T025 [US2] Implementar la política de validación en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/auditoria/CorrelacionContext.java` o clase auxiliar del mismo paquete: 1–128 caracteres ASCII permitidos, ignorar inválidos y nunca usar el valor externo como identidad interna.
- [ ] T026 [US2] Implementar `CorrelacionHttpFilter` en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/auditoria/CorrelacionHttpFilter.java` como `OncePerRequestFilter`, ordenado antes de seguridad y sin leer `Authorization` ni `X-API-KEY`.
- [ ] T027 [US2] Establecer `requestCorrelationId`, `externalCorrelationId` y `contextType=HTTP` en MDC antes del chain, devolver el ID interno en `X-Correlation-ID` y eliminar MDC/contexto en `finally` en `CorrelacionHttpFilter.java`.
- [ ] T028 [US2] Integrar `CorrelacionContext` con `AuditoriaAspect` y la versión segura de `AuditoriaEventWriter` producida por T010/Foundational para que los eventos HTTP usen `requestCorrelationId`, los eventos BACKGROUND usen `executionCorrelationId` y nunca se invente un ID HTTP.
- [ ] T029 [US2] Completar `CorrelationMdcIntegrationTest` con endpoints protegidos por JWT/API key y endpoints de health/prometheus, comprobando que la correlación no modifica autenticación, autorización ni exposición.

**Checkpoint**: US2 es demostrable independientemente; cada respuesta HTTP tiene el ID interno y el contexto no contamina otra solicitud.

---

## Phase 5: User Story 3 - Proteger la información registrada (Priority: P1)

**Goal**: Emitir eventos estructurados solo desde una allowlist y aislar fallos del mecanismo de auditoría sin alterar negocio.

**Independent Test**: Ejecutar operaciones con datos sintéticos sensibles y fallos del writer, inspeccionar los eventos y comprobar ausencia de secretos y preservación del resultado/excepción.

### Tests for User Story 3

- [ ] T030 [P] [US3] Crear `AuditoriaEventWriterSecurityTest` en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/auditoria/AuditoriaEventWriterSecurityTest.java` con argumentos, retornos, contraseñas, JWT, API keys, headers, datos personales, mensajes y stack traces sintéticos.
- [ ] T031 [P] [US3] Crear `AuditoriaErrorCodeResolverSecurityTest` en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/auditoria/AuditoriaErrorCodeResolverSecurityTest.java` para probar que el código depende del tipo/categoría y nunca del mensaje o cause chain.
- [ ] T032 [P] [US3] Crear `AuditoriaBestEffortTest` en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/auditoria/AuditoriaBestEffortTest.java` con un writer controladamente fallido, verificando que se conserva el retorno y la misma instancia de excepción del negocio.
- [ ] T033 [P] [US3] Crear `AuditSecurityIntegrationTest` en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/auditoria/AuditSecurityIntegrationTest.java` para JWT/API key sintéticos y revisión de eventos completos.

### Implementation for User Story 3

- [ ] T034 [US3] Agregar pruebas adicionales, endurecimiento defensivo y validación de seguridad de `AuditoriaEventWriter` en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/auditoria/AuditoriaEventWriter.java`, inspeccionando el evento/sink capturado y los pares MDC sin asumir renderizado JSON. Confirmar que las garantías mínimas de allowlist completa, exclusión de datos sensibles, códigos controlados y best effort ya provienen de T010 y que T034 no es requisito previo para T028.
- [ ] T035 [US3] Implementar el manejo best effort en `AuditoriaEventWriter.java` y `AuditoriaAspect.java`, capturando solo fallos de runtime del mecanismo, sin capturar `Error`, sin reintentos ilimitados y sin modificar la excepción original.
- [ ] T036 [US3] Verificar que el writer no recibe argumentos, retornos, headers ni `Throwable` completo desde `AuditoriaAspect.java`, y que los eventos de error contienen únicamente el `errorCode` controlado.
- [ ] T037 [US3] Completar los casos BACKGROUND de `AuditoriaEventWriter` y `AuditoriaAspect` para `EstadisticasScheduler.ejecutarSemanal()`, `ValorizacionScheduler.ejecutarSemanal()` y `MarketInitializationApplicationReadyListener`, generando `executionCorrelationId` por ejecución y omitiendo `requestCorrelationId` y `externalCorrelationId`.

**Checkpoint**: US3 es demostrable independientemente; ningún dato prohibido aparece en eventos y la auditoría no puede romper el negocio.

---

## Phase 6: User Story 4 - Mantener compatibilidad funcional y arquitectónica (Priority: P2)

**Goal**: Validar que auditoría, seguridad, observabilidad, arquitectura y rendimiento conviven sin regresiones.

**Independent Test**: Ejecutar ArchUnit, regresiones de seguridad/observabilidad, benchmark SC-008 y la batería completa del backend.

### Tests and validation for User Story 4

- [ ] T038 [P] [US4] Crear `BackgroundAuditIntegrationTest` en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/auditoria/BackgroundAuditIntegrationTest.java` para schedulers, startup, `contextType=BACKGROUND`, ausencia de IDs HTTP y propagación original de errores.
- [ ] T039 [P] [US4] Crear `ProxyBoundaryRegressionTest` en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/auditoria/ProxyBoundaryRegressionTest.java` para self-invocation, métodos no públicos, métodos no seleccionados y overload P2P interno.
- [ ] T040 [US4] Ejecutar las 21 reglas de `backend/src/test/java/com/devchesterunited/devchesterunitedv2/architecture/ArchitectureTest.java` y registrar el resultado de cada regla; esta tarea es la ejecución del gate, no la revisión manual de dependencias nuevas.
- [ ] T041 [P] [US4] Crear `ObservabilitySecurityRegressionTest` en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/auditoria/ObservabilitySecurityRegressionTest.java` para health, Prometheus, management port, JWT, API key y rutas existentes, sin cambiar configuraciones.
- [ ] T042 [P] [US4] Preparar el escenario reproducible de SC-008 en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/performance/auditoria/AuditoriaPerformanceScenario.java` con la misma compilación, configuración, operación, dataset sintético y entorno, warm-up controlado, baseline sin aspecto, tratamiento con AOP y writer habilitados, y exclusión del tiempo externo del destino de logs.
- [ ] T043 [US4] Ejecutar 30 pares alternados baseline/tratamiento por escenario, calcular mediana y p95, guardar muestras, método y entorno en `backend/build/diagnostics/auditoria-performance/`, y ejecutar una serie diagnóstica adicional con writer controlado sin emisión para estimar solo el costo del writer, sin afirmar que desaparece el costo total de AOP ni agregar métricas.
- [ ] T044 [US4] Verificar que los tests nuevos no modifican ni eliminan tests existentes y que las pruebas de negocio continúan usando fixtures sintéticos, Testcontainers y sin proveedores reales.

### Compatibility implementation checks

- [ ] T045 [US4] Revisar `backend/build.gradle` y los cambios de configuración para confirmar que no se modificaron Actuator, Micrometer Prometheus, `SecurityConfig`, `ApiKeyAuthenticationFilter`, Flyway ni propiedades de exposición.
- [ ] T046 [US4] Revisar el grafo de dependencias de las clases nuevas de `configuration.auditoria` y comprobar que no existan ciclos ni dependencias nuevas prohibidas; esta revisión final es distinta de la ejecución de las 21 reglas realizada en T040.

**Checkpoint**: US4 está completa cuando ArchUnit, seguridad, observabilidad, rendimiento y regresión funcional pasan sin cambios de lógica ni contratos existentes.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Cerrar la validación completa y dejar evidencia reproducible para la entrega.

- [ ] T047 Ejecutar la batería completa del backend desde `backend/` con `./gradlew.bat test --no-daemon --console=plain` y registrar cualquier fallo sin modificar tests existentes.
- [ ] T048 Ejecutar la validación de `quickstart.md`, incluyendo ArchUnit, pruebas de auditoría, seguridad, correlación, background, concurrencia y regresión de Actuator/Prometheus.
- [ ] T049 Revisar `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md` y `tasks.md` para confirmar correspondencia entre las 10 operaciones, los campos del evento y los criterios de aceptación.
- [ ] T050 Ejecutar la revisión final de SonarCloud/quality gate definida por la Constitution y documentar issues nuevas de seguridad, duplicación, complejidad o dependencias sin modificar el alcance funcional.
- [ ] T051 Ejecutar el smoke test obligatorio en `backend/`: compilar, levantar el backend con la configuración local válida existente, verificar el arranque y consultar endpoints básicos representativos (incluyendo health y un endpoint funcional no destructivo), comprobando `X-Correlation-ID` sin exponer secretos ni modificar Actuator, Prometheus, seguridad o puertos; documentar el resultado en `specs/008-aop-auditoria-logging/quickstart.md`.

**Final checkpoint**: La feature queda lista para cierre solo con batería backend aprobada, 21 reglas ArchUnit aprobadas, SC-008 documentado, sin métricas duplicadas y sin cambios no autorizados.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No depende de otras fases; T001–T004 preparan AOP y directorios.
- **Foundational (Phase 2)**: Depende de Setup; T005–T010 bloquean las historias. T010 deja disponible la versión segura del writer antes de cualquier integración HTTP, incluida T028.
- **US1 (Phase 3)**: Depende de Foundational; es el MVP y define el aspecto y las operaciones.
- **US2 (Phase 4)**: Depende de Foundational y usa el contrato de evento; puede comenzar en paralelo con US1 si no modifica el mismo archivo simultáneamente.
- **US3 (Phase 5)**: Depende de Foundational y de los contratos del writer; requiere integrar con el aspecto de US1.
- **US4 (Phase 6)**: Depende de US1, US2 y US3 para validar el sistema integrado.
- **Polish (Phase 7)**: Depende de todas las historias y checkpoints anteriores.

### User Story Dependencies

- **US1 (P1)**: Puede comenzar después de Foundational; entrega el MVP de auditoría de operaciones.
- **US2 (P1)**: Puede comenzar después de Foundational; sus pruebas son independientes, T028 usa el writer seguro de T010 y la integración con el aspecto de US1 queda explícita.
- **US3 (P1)**: T010 deja disponible el writer seguro; T030–T033 pueden prepararse después de Foundational y T034–T037 agregan pruebas, endurecimiento defensivo y validación adicional sin ser prerrequisito de T028.
- **US4 (P2)**: Requiere las tres historias P1 para validar compatibilidad completa.

### Within Each User Story

- Las pruebas nuevas deben crearse antes de la implementación correspondiente y fallar por ausencia de la capacidad.
- La verificación de interceptabilidad precede a cada grupo de métodos instrumentados.
- El contrato de datos precede al aspecto y al writer.
- Cada checkpoint debe pasar antes de iniciar el siguiente grupo dependiente.

## Parallel Opportunities

- T002 y T003 pueden ejecutarse en paralelo después de T001.
- T008 y T009 pueden ejecutarse en paralelo después de T005–T007.
- T011–T014 son pruebas independientes de US1 y pueden prepararse en paralelo.
- T017, T018 y T019 pueden ejecutarse en paralelo si solo actualizan el catálogo/plan de selección en coordinación; no deben editar simultáneamente el mismo archivo.
- T022–T024 son pruebas independientes de US2.
- T030–T033 son pruebas independientes de US3.
- T038–T043 pueden distribuirse por suite, conservando T040 y T046 como gates antes del cierre.
- US1, US2 y US3 pueden prepararse en paralelo después de Foundational con separación estricta de archivos; T028 depende explícitamente de T010, mientras T034 agrega validación posterior sin introducir una ventana en la que HTTP utilice un writer sin garantías mínimas.

## Implementation Strategy

### MVP First

1. Completar Setup y Foundational.
2. Completar US1 con las 10 operaciones, START/END, duración y códigos controlados.
3. Ejecutar su checkpoint y demostrar que no se auditan métodos no seleccionados.
4. Continuar con correlación y seguridad antes de considerar la feature entregable.

### Incremental Delivery

1. Entregar el aspecto y contrato de eventos como MVP interno.
2. Agregar correlación HTTP y respuesta `X-Correlation-ID`.
3. Agregar allowlist, códigos de error y best effort.
4. Completar background, proxies, ArchUnit, regresiones y rendimiento.
5. Ejecutar suite completa y quality gate.

## Risk Controls

- **Cobertura AOP incompleta**: T011, T017–T020 y T039 prueban proxies, overloads y self-invocation.
- **Filtración de secretos**: T030–T036 usan allowlist y datos sintéticos prohibidos.
- **Pérdida de correlación**: T022–T029 y T024 prueban limpieza y concurrencia.
- **Regresión de seguridad/observabilidad**: T029, T041 y T045 conservan las configuraciones existentes.
- **Overhead no reproducible**: T042–T043 fijan entorno, warm-up, 30 pares, mediana, p95 y separan el costo del writer del costo total AOP.
- **Ciclos arquitectónicos**: T040 ejecuta las 21 reglas y T046 revisa el grafo de dependencias nuevas antes del cierre.

## Checkpoints y commits sugeridos (no ejecutar en esta etapa)

- Después de T001–T010: `feat(auditoria): preparar contratos y writer base`.
- Después de T011–T021: `feat(auditoria): instrumentar operaciones seleccionadas`.
- Después de T022–T037: `feat(auditoria): agregar correlacion y logging seguro`.
- Después de T038–T046: `test(auditoria): validar arquitectura, seguridad y rendimiento`.
- Después de T047–T051: `docs(auditoria): cerrar validacion y smoke test`.

Los mensajes son checkpoints sugeridos en español funcional; no implican ejecutar commits, push, merge ni cambios de rama durante esta fase documental.
