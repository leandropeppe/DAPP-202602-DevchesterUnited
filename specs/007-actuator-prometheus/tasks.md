# Tasks: Observabilidad del backend con Actuator y Prometheus

**Input**: Documentos de diseño de `/specs/007-actuator-prometheus/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Incluidos porque la especificación exige escenarios verificables de salud, métricas, seguridad, aislamiento Docker y no regresión.

**Organization**: Las tareas están agrupadas por historia de usuario para permitir implementación y validación incremental.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Preparar dependencias, rutas y artefactos de ejecución sin modificar los Compose existentes.

- [X] T001 Actualizar `backend/build.gradle` con `spring-boot-starter-actuator` y `micrometer-registry-prometheus` usando las versiones gestionadas por Spring Boot 3.5.16.
- [X] T002 [P] Definir en `backend/src/main/resources/application.properties` las propiedades configurables de exposición mínima, puerto de administración y dirección de escucha sin fijar IP ni CIDR arbitrarios.
- [X] T003 [P] Crear `backend/Dockerfile` para empaquetar y ejecutar el backend Java 21 con separación verificable entre puerto de negocio y puerto de administración.
- [X] T004 [P] Crear `backend/.dockerignore` para excluir `.git`, `.gradle`, `build`, secretos y artefactos no necesarios cuando `backend/` sea el contexto Docker.
- [X] T005 Crear `backend/docker/observability-compose.yml` con servicios `backend`, `postgres`, `collector-probe` y `unauthorized-probe`, sin modificar `frontend/e2e/compose.yml` ni `performance/compose.yml`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establecer los límites de seguridad y la topología que bloquean las historias posteriores.

**⚠️ CRITICAL**: Ninguna historia de usuario puede considerarse implementable hasta completar esta fase.

- [ ] T006 [P] Definir la red privada interna y las membresías de `backend`, `postgres` y `collector-probe` en `backend/docker/observability-compose.yml`, manteniendo `unauthorized-probe` fuera de esa red y sin publicar el puerto de administración.
- [ ] T007 [P] Configurar en `backend/docker/observability-compose.yml` la conectividad backend→PostgreSQL por nombre de servicio, sin publicar el puerto de PostgreSQL ni agregar una segunda red de aplicación.
- [ ] T008 [P] Crear la prueba fundacional de contexto para ambas cadenas en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/observability/ManagementSecurityFoundationTest.java`, verificando el contexto de administración, el contexto de negocio, la no superposición insegura y el rechazo de paths no aprobados.
- [ ] T009 Implementar en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/management/ManagementSecurityConfig.java` la cadena Spring Security específica del contexto de administración, limitada a health/metrics y con denegación de paths restantes, hasta satisfacer T008.
- [ ] T010 Implementar en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/security/SecurityConfig.java` la preservación de la cadena de negocio existente, JWT, API keys, autorización y `anyRequest().denyAll()`, evitando solapamiento inseguro con la cadena de Actuator y satisfaciendo T008.
- [ ] T011 Verificar que cualquier método o constructor nuevo respete Clean Code, SOLID y un máximo de seis parámetros, dejando responsabilidades de configuración separadas y cohesivas en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/`.

**Checkpoint**: La prueba fundacional T008 pasa después de T009–T010; los dos contextos de seguridad están configurados y verificados antes de implementar endpoints o pruebas de historia.

---

## Phase 3: User Story 1 - Verificar la salud del backend (Priority: P1) 🎯 MVP

**Goal**: Exponer un estado de salud mínimo y seguro desde el contexto de administración privado.

**Independent Test**: El collector autorizado consulta health por HTTP real desde la red privada; un contenedor ajeno y el host no obtienen health por el puerto de administración; el resultado no revela detalles sensibles.

### Tests for User Story 1

> Escribir las pruebas antes de la implementación y comprobar que fallan por la ausencia de la funcionalidad.

- [ ] T012 [P] [US1] Crear prueba MockMvc/context para `GET /actuator/health`, paths administrativos no permitidos y ocultamiento de detalles en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/observability/HealthEndpointSecurityTest.java`.
- [ ] T013 [P] [US1] Crear prueba de degradación o dependencia no saludable y verificar estado seguro sin credenciales, URLs internas ni cadenas de conexión en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/observability/HealthEndpointStatusTest.java`.
- [ ] T014 [P] [US1] Crear el ejecutor reproducible de HTTP real para health desde `scripts/observability/validate-health-docker.ps1`, separado de Gradle, con escenarios para `collector-probe`, `unauthorized-probe` y host.

### Implementation for User Story 1

- [ ] T015 [US1] Configurar la exposición mínima de health y el ocultamiento de detalles en `backend/src/main/resources/application.properties`, sin habilitar endpoints Actuator adicionales.
- [ ] T016 [US1] Configurar el listener de administración y su dirección efectiva en `backend/src/main/resources/application.properties`, asegurando que `collector-probe` pueda acceder sin fijar una IP Docker arbitraria.
- [ ] T017 [US1] Ejecutar `scripts/observability/validate-health-docker.ps1` contra `backend/docker/observability-compose.yml`, incluyendo acceso autorizado, rechazo desde otra red y el intento negativo desde el host contra la IP dinámica del backend.

**Checkpoint**: Health funciona desde el collector autorizado, queda aislado de otros orígenes y no expone detalles sensibles.

---

## Phase 4: User Story 2 - Recolectar métricas operativas (Priority: P1)

**Goal**: Exponer métricas JVM, memoria y HTTP en formato Prometheus con baja cardinalidad y sin información sensible.

**Independent Test**: Generar tráfico controlado, consultar `/actuator/prometheus` desde `collector-probe` y verificar familias de métricas, etiquetas seguras y rechazo desde orígenes no autorizados.

### Tests for User Story 2

- [ ] T018 [P] [US2] Crear prueba de contrato para formato y familias JVM/memoria/HTTP de `GET /actuator/prometheus` en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/observability/PrometheusMetricsContractTest.java`.
- [ ] T019 [P] [US2] Crear prueba de etiquetas HTTP que ejercite query strings, headers sensibles, tokens, payloads y rutas variables en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/observability/PrometheusSensitiveTagsTest.java`.
- [ ] T020 [P] [US2] Crear el ejecutor reproducible de HTTP real para metrics desde `scripts/observability/validate-metrics-docker.ps1`, separado de Gradle, con tráfico controlado desde `collector-probe` y comprobaciones de métricas sensibles.
- [ ] T021 [US2] Incorporar en `scripts/observability/validate-metrics-docker.ps1` los escenarios negativos desde `unauthorized-probe` y host, verificando que no reciben métricas ni detalles operativos.

### Implementation for User Story 2

- [ ] T022 [US2] Configurar `micrometer-registry-prometheus` y la exposición exclusiva de `/actuator/prometheus` en `backend/src/main/resources/application.properties`.
- [ ] T023 [US2] Configurar métricas HTTP con nombres y etiquetas de baja cardinalidad en `backend/src/main/resources/application.properties` y componentes de configuración bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/management/`.
- [ ] T024 [US2] Verificar la implementación contra el contrato aprobado en `specs/007-actuator-prometheus/contracts/observability.md` y registrar cualquier incompatibilidad sin modificar el documento SDD.
- [ ] T025 [US2] Validar en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/observability/PrometheusSensitiveTagsTest.java` que la respuesta de metrics no contiene Authorization, API keys, cookies, payloads, query strings, URLs sensibles, datos personales ni etiquetas de cardinalidad alta.

**Checkpoint**: El collector autorizado recibe métricas útiles y seguras; ningún origen no autorizado obtiene información operativa.

---

## Phase 5: User Story 3 - Mantener la frontera de seguridad (Priority: P1)

**Goal**: Demostrar que la observabilidad no es pública ni permite eludir JWT, API keys o autorización.

**Independent Test**: Probar cada camino desde collector autorizado, `unauthorized-probe`, host y clientes de negocio; verificar listeners, redes, puertos efectivos y reglas de ambas cadenas.

### Tests for User Story 3

- [ ] T026 [P] [US3] Crear prueba de ampliación de no bypass para la cadena ya verificada en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/observability/ManagementSecurityChainTest.java`, cubriendo Actuator, `/api/**`, JWT, API keys y paths administrativos no aprobados.
- [ ] T027 [P] [US3] Crear prueba de regresión de JWT y API key para endpoints de negocio sin cambios en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/auth/ObservabilityBusinessSecurityRegressionTest.java`.
- [ ] T028 [P] [US3] Crear el ejecutor reproducible de aislamiento HTTP real desde `scripts/observability/validate-network-isolation.ps1`, separado de Gradle, para `collector-probe` y `unauthorized-probe` con inspección de redes efectivas.
- [ ] T029 [US3] Incorporar en `scripts/observability/validate-network-isolation.ps1` la inspección de `docker compose port`, `docker port`, listeners, IP dinámica y el intento real desde el host contra el puerto de administración.
- [ ] T030 [P] [US3] Crear prueba de superficie mínima para rechazar `env`, `beans`, `configprops`, `mappings`, `loggers`, `threaddump` y cualquier endpoint Actuator no aprobado en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/observability/ManagementExposureTest.java`.

### Implementation for User Story 3

- [ ] T031 [US3] Ampliar la validación de aislamiento y no bypass en `scripts/observability/validate-network-isolation.ps1` y `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/observability/ManagementSecurityChainTest.java`, sin repetir la implementación de cadenas completada en T009–T010.
- [ ] T032 [US3] Configurar en `backend/docker/observability-compose.yml` la red privada interna, ausencia de publicación del puerto de administración, exclusión de `unauthorized-probe` y publicación sólo del puerto de negocio.
- [ ] T033 [US3] Crear escenarios deliberadamente inseguros aislados bajo `backend/docker/observability-scenarios/` para probar listener inválido, puerto publicado, segunda red, collector fuera de red, host alcanzable y cadena amplia, manteniendo `backend/docker/observability-compose.yml` seguro y sin variantes vulnerables en el despliegue normal.
- [ ] T034 [US3] Verificar que la pertenencia a la red privada se documenta y aplica como límite de confianza, sin introducir autenticación individual ni reutilizar secretos JWT/API key en `backend/docker/observability-compose.yml` y `backend/src/main/resources/application.properties`.

**Checkpoint**: La seguridad de negocio conserva su comportamiento; la observabilidad sólo es accesible por el límite de confianza previsto y las pruebas detectan cualquier bypass o exposición.

---

## Phase 6: User Story 4 - Preservar el comportamiento del producto (Priority: P2)

**Goal**: Confirmar que la observabilidad no altera negocio, PostgreSQL, E2E, rendimiento ni la calidad del backend.

**Independent Test**: Ejecutar compilación, pruebas existentes, regresión de seguridad, E2E y quickstart Docker sin modificar los Compose existentes ni la lógica de negocio.

### Tests for User Story 4

- [ ] T035 [P] [US4] Crear prueba de conectividad backend→PostgreSQL en la topología privada de `backend/docker/observability-compose.yml` en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/e2e/observability/PostgresDockerConnectivityTest.java`.
- [ ] T036 [P] [US4] Crear prueba de regresión de endpoints y contratos de negocio en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/web/ObservabilityBusinessRegressionTest.java` sin modificar pruebas existentes.
- [ ] T037 [P] [US4] Crear verificación de no regresión de E2E y rendimiento en `scripts/observability/validate-existing-suites.ps1`, invocando los Compose existentes sin editarlos.

### Implementation for User Story 4

- [ ] T038 [US4] Ajustar el `Dockerfile` y el Compose específico para que el backend arranque con PostgreSQL por nombre de servicio y mantenga sólo la conectividad de red aprobada en `backend/Dockerfile` y `backend/docker/observability-compose.yml`.
- [ ] T039 [US4] Ejecutar la documentación operativa aprobada en `specs/007-actuator-prometheus/quickstart.md` y verificar que los comandos y resultados esperados coinciden, sin modificar documentos SDD.
- [ ] T040 [US4] Ejecutar la regresión del backend con `backend/gradlew test`, `backend/gradlew bootJar` y las suites E2E existentes, registrando que JWT, API keys y negocio conservan sus resultados.

**Checkpoint**: El backend compila, sus pruebas pasan y las funcionalidades de negocio y Compose existentes permanecen intactos.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Validación final, calidad y trazabilidad antes de revisión manual.

- [ ] T041 [P] Revisar `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/management/` para eliminar duplicación, mantener responsabilidades únicas y respetar el máximo de seis parámetros por método o constructor.
- [ ] T042 [P] Revisar `backend/src/test/java/com/devchesterunited/devchesterunitedv2/` para asegurar separación entre MockMvc/contexto y HTTP real Docker, nombres de pruebas claros y casos felices/borde.
- [ ] T043 Ejecutar la matriz completa de `specs/007-actuator-prometheus/quickstart.md`, incluyendo collector autorizado, `unauthorized-probe`, host, PostgreSQL, puertos y listeners efectivos.
- [ ] T044 Ejecutar `backend/gradlew clean build --no-daemon --console=plain` y verificar generación de JaCoCo en `backend/build/reports/jacoco/test/jacocoTestReport.xml`.
- [ ] T045 Ejecutar el análisis SonarCloud únicamente después de una integración autorizada manualmente en `main`, revisar vulnerabilidades/issues y dejar explícito que esta tarea no autoriza commits, merges, push, cambios de rama ni gates adicionales para `feature/actuator-prometheus`.
- [ ] T046 Revisar el resultado final contra `specs/007-actuator-prometheus/spec.md`, `specs/007-actuator-prometheus/plan.md` y `specs/007-actuator-prometheus/contracts/observability.md`, sin modificar esos documentos durante la implementación.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias; prepara Gradle, Dockerfile, Compose y propiedades.
- **Foundational (Phase 2)**: depende de Setup y bloquea todas las historias; fija cadenas de seguridad y topología.
- **User Story 1 (Phase 3)**: depende de Foundational; entrega el MVP de health.
- **User Story 2 (Phase 4)**: depende de Foundational y de la separación de administración de US1; agrega metrics.
- **User Story 3 (Phase 5)**: depende de Foundational, US1 y US2 para probar la superficie completa.
- **User Story 4 (Phase 6)**: depende de US1–US3; valida no regresión, PostgreSQL y suites existentes.
- **Polish (Phase 7)**: depende de todas las historias deseadas y de sus checkpoints.

### Ejecución de pruebas Docker

- Las pruebas HTTP reales de `scripts/observability/validate-health-docker.ps1`, `scripts/observability/validate-metrics-docker.ps1` y `scripts/observability/validate-network-isolation.ps1` son obligatorias para aceptar la feature.
- Esas pruebas se ejecutan explícitamente contra `backend/docker/observability-compose.yml` y permanecen separadas de `backend/gradlew test`, `backend/gradlew build` y las suites habituales de Gradle; Docker no es requisito para cada ejecución rutinaria de la suite.
- El pipeline o la validación de aceptación debe ejecutar los runners Docker en un entorno con Docker disponible antes de considerar completados los checkpoints de aislamiento.

### User Story Dependencies

- **US1 (P1)**: depende de la infraestructura y seguridad fundacional; es el MVP independiente.
- **US2 (P1)**: depende de la disponibilidad del contexto de administración de US1; no cambia lógica de negocio.
- **US3 (P1)**: depende de endpoints de US1/US2 para validar aislamiento, exposición mínima y no bypass.
- **US4 (P2)**: depende de la topología y seguridad completas para verificar PostgreSQL y regresión.

### Parallel Opportunities

- T002–T004 pueden ejecutarse en paralelo después de confirmar la estructura del backend.
- T006 y T007 pueden prepararse en paralelo. T008 debe crear primero la prueba fundacional; después T009 y T010 pueden implementarse en paralelo sobre sus archivos separados y deben pasar T008 antes de las historias.
- T012–T014 son pruebas independientes de US1.
- T018–T020 son pruebas independientes de US2; T021 depende de T020 porque amplía el mismo runner Docker.
- T026–T030 son pruebas independientes de US3 cuando exista el fixture Docker común; T031 depende de la validación previa y amplía aislamiento/no bypass sin repetir implementación.
- T028 debe preceder T029 porque ambos amplían el runner de aislamiento de red.
- T035–T037 pueden ejecutarse en paralelo con el código de US4.
- T041–T042 pueden ejecutarse en paralelo durante Polish.

### Within Each User Story

- Escribir pruebas antes de la implementación y confirmar que fallan por la ausencia de la funcionalidad.
- Implementar sólo después de que los contratos y escenarios estén definidos.
- Ejecutar el checkpoint de la historia antes de avanzar a la siguiente prioridad.
- No modificar tests existentes; cualquier regresión debe resolverse agregando o ajustando pruebas nuevas con autorización explícita si fuera necesario.

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Setup y Foundational.
2. Implementar health y su cadena de administración.
3. Validar desde `collector-probe`, `unauthorized-probe` y host.
4. Detenerse para revisar el MVP antes de incorporar metrics.

### Incremental Delivery

1. Añadir US2 para métricas JVM/memoria/HTTP.
2. Completar US3 para aislamiento y no bypass.
3. Completar US4 para PostgreSQL, regresión, E2E y calidad.
4. Ejecutar Polish y revisión manual antes de cerrar la feature.

## Notes

- Cada tarea tiene checkbox, identificador secuencial y ruta de archivo concreta.
- `[P]` sólo se usa cuando la tarea puede ejecutarse en paralelo sin depender de archivos incompletos.
- Las tareas no autorizan modificar `whoscored-local.patch`, `backend/.gradle-codex/` ni los Compose E2E/performance.
- No se instala Prometheus, no se crean dashboards y no se modifica frontend ni lógica de negocio.
