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

- [X] T006 [P] Definir la red privada interna y las membresías de `backend`, `postgres` y `collector-probe` en `backend/docker/observability-compose.yml`, manteniendo `unauthorized-probe` fuera de esa red y sin publicar el puerto de administración.
- [X] T007 [P] Configurar en `backend/docker/observability-compose.yml` la conectividad backend→PostgreSQL por nombre de servicio, sin publicar el puerto de PostgreSQL ni agregar una segunda red de aplicación.
- [X] T008 [P] Crear la prueba fundacional en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/observability/ManagementSecurityFoundationTest.java`, verificando que el contexto administrativo hijo reutiliza el filtro efectivo del contexto principal, que la autorización exige conjuntamente listener/puerto administrativo y ruta aprobada, que las mismas rutas no se autorizan por el puerto de negocio y que las rutas administrativas desconocidas son rechazadas por Spring Security, no sólo por `404`.
- [X] T009 Implementar en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/security/SecurityConfig.java` la clasificación explícita de solicitudes administrativas mediante APIs públicas de Spring Security: permitir sólo health/Prometheus cuando la solicitud llega al listener/puerto administrativo efectivo y denegar el resto por defecto. No crear `ManagementSecurityConfig`, `FilterChainProxy` manual, reflexión ni una cadena independiente del contexto hijo; retirar, si ya no son necesarios tras la migración, `backend/src/main/java/com/devchesterunited/observability/management/ManagementSecurityConfig.java` y su registro en `backend/src/main/resources/META-INF/spring/org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration.imports`.
- [X] T010 Verificar y conservar en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/security/SecurityConfig.java` JWT, API keys, filtros, autorizaciones existentes y `anyRequest().denyAll()`, demostrando con T008 que la clasificación administrativa no relaja rutas de negocio ni permite bypass desde ninguno de los dos puertos.
- [X] T011 Verificar que cualquier método o constructor nuevo respete Clean Code, SOLID y un máximo de seis parámetros, dejando responsabilidades de configuración separadas y cohesivas en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/`.

**Checkpoint**: La prueba fundacional T008 pasa después de T009–T010; el contexto administrativo hijo y el de negocio utilizan el filtro efectivo principal, la separación por listener/puerto y ruta está verificada, y ninguna ruta administrativa no aprobada queda autorizada antes de implementar endpoints o pruebas de historia.

---

## Phase 3: User Story 1 - Verificar la salud del backend (Priority: P1) 🎯 MVP

**Goal**: Exponer un estado de salud mínimo y seguro desde el contexto de administración privado.

**Independent Test**: El collector autorizado consulta health por HTTP real desde la red privada; un contenedor ajeno y el host no obtienen health por el puerto de administración; el resultado no revela detalles sensibles.

### Tests for User Story 1

> Escribir las pruebas antes de la implementación y comprobar que fallan por la ausencia de la funcionalidad.

- [x] T012 [P] [US1] Crear prueba MockMvc/context para `GET /actuator/health`, paths administrativos no permitidos y ocultamiento de detalles en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/observability/HealthEndpointSecurityTest.java`.
- [x] T013 [P] [US1] Crear prueba de degradación o dependencia no saludable y verificar estado seguro sin credenciales, URLs internas ni cadenas de conexión en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/observability/HealthEndpointStatusTest.java`.
- [x] T014 [P] [US1] Crear el ejecutor reproducible de HTTP real para health desde `scripts/observability/validate-health-docker.ps1`, separado de Gradle, con escenarios para `collector-probe`, `unauthorized-probe` y host.

### Implementation for User Story 1

- [x] T015 [US1] Configurar la exposición mínima de health y el ocultamiento de detalles en `backend/src/main/resources/application.properties`, sin habilitar endpoints Actuator adicionales.
- [x] T016 [US1] Configurar el listener de administración y su dirección efectiva en `backend/src/main/resources/application.properties`, asegurando que `collector-probe` pueda acceder sin fijar una IP Docker arbitraria.
- [x] T017 [US1] Ejecutar `scripts/observability/validate-health-docker.ps1` contra `backend/docker/observability-compose.yml`, incluyendo acceso autorizado, rechazo desde otra red y el intento negativo desde el host contra la IP dinámica del backend.

> **Cierre T017 (2026-11-24)**: `validate-health-docker.ps1` completó 10 controles PASS contra el Compose aprobado: `collector-probe` obtuvo health HTTP 200 con respuesta interpretable y sin detalles sensibles; `unauthorized-probe` y el host no alcanzaron el listener administrativo por la IP dinámica del backend.

**Checkpoint**: Health funciona desde el collector autorizado, queda aislado de otros orígenes y no expone detalles sensibles.

---

## Phase 4: User Story 2 - Recolectar métricas operativas (Priority: P1)

**Goal**: Exponer métricas JVM, memoria y HTTP en formato Prometheus con baja cardinalidad y sin información sensible.

**Independent Test**: Generar tráfico controlado, consultar `/actuator/prometheus` desde `collector-probe` y verificar familias de métricas, etiquetas seguras y rechazo desde orígenes no autorizados.

### Tests for User Story 2

- [x] T018 [P] [US2] Crear prueba de contrato para formato y familias JVM/memoria/HTTP de `GET /actuator/prometheus` en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/observability/PrometheusMetricsContractTest.java`.
- [x] T019 [P] [US2] Crear prueba de etiquetas HTTP que ejercite query strings, headers sensibles, tokens, payloads y rutas variables en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/observability/PrometheusSensitiveTagsTest.java`.
- [x] T020 [P] [US2] Crear el ejecutor reproducible de HTTP real para metrics desde `scripts/observability/validate-metrics-docker.ps1`, separado de Gradle, con tráfico controlado desde `collector-probe` y comprobaciones de métricas sensibles.
- [x] T021 [US2] Incorporar en `scripts/observability/validate-metrics-docker.ps1` los escenarios negativos desde `unauthorized-probe` y host, verificando que no reciben métricas ni detalles operativos.

### Implementation for User Story 2

- [x] T022 [US2] Configurar `micrometer-registry-prometheus` y la exposición exclusiva de `/actuator/prometheus` en `backend/src/main/resources/application.properties`.
- [x] T023 [US2] Configurar métricas HTTP con nombres y etiquetas de baja cardinalidad en `backend/src/main/resources/application.properties` y componentes de configuración bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/management/`.
- [x] T024 [US2] Verificar la implementación contra el contrato aprobado en `specs/007-actuator-prometheus/contracts/observability.md` y registrar cualquier incompatibilidad sin modificar el documento SDD.
- [x] T025 [US2] Validar en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/observability/PrometheusSensitiveTagsTest.java` que la respuesta de metrics no contiene Authorization, API keys, cookies, payloads, query strings, URLs sensibles, datos personales ni etiquetas de cardinalidad alta.

> **Trazabilidad ADR-007-01**: la decisión aprobada de una cadena efectiva única y la interpretación de T023 basada en instrumentación HTTP predeterminada están documentadas en `research.md`.

**Checkpoint**: El collector autorizado recibe métricas útiles y seguras; ningún origen no autorizado obtiene información operativa.

> **Cierre US2**: `ManagementSecurityFoundationTest`, `PrometheusMetricsContractTest`, `PrometheusSensitiveTagsTest` y `ManagementPortRequestMatcherTest` finalizaron con `BUILD SUCCESSFUL`. `validate-metrics-docker.ps1` completó 13 controles `PASS`. T023 conserva la instrumentación predeterminada de Spring Boot/Micrometer, sin configuración personalizada, conforme a la interpretación aprobada. T024 aplica la excepción autorizada para actualizar el contrato SDD, documentada mediante ADR-007-01.

---

## Phase 5: User Story 3 - Mantener la frontera de seguridad (Priority: P1)

**Goal**: Demostrar que la observabilidad no es pública ni permite eludir JWT, API keys o autorización.

**Independent Test**: Probar cada camino desde collector autorizado, `unauthorized-probe`, host y clientes de negocio; verificar listeners, redes, puertos efectivos y la clasificación por listener/puerto más ruta dentro del filtro principal reutilizado.

### Tests for User Story 3

- [x] T026 [P] [US3] Crear prueba de ampliación de no bypass para la cadena ya verificada en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/observability/ManagementSecurityChainTest.java`, cubriendo Actuator, `/api/**`, JWT, API keys y paths administrativos no aprobados.
- [x] T027 [P] [US3] Crear prueba de regresión de JWT y API key para endpoints de negocio sin cambios en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/auth/ObservabilityBusinessSecurityRegressionTest.java`.
- [x] T028 [P] [US3] Crear el ejecutor reproducible de aislamiento HTTP real desde `scripts/observability/validate-network-isolation.ps1`, separado de Gradle, para `collector-probe` y `unauthorized-probe` con inspección de redes efectivas.
- [x] T029 [US3] Incorporar en `scripts/observability/validate-network-isolation.ps1` la inspección de `docker compose port`, `docker port`, listeners, IP dinámica y el intento real desde el host contra el puerto de administración.
- [x] T030 [P] [US3] Crear prueba de superficie mínima para rechazar `env`, `beans`, `configprops`, `mappings`, `loggers`, `threaddump` y cualquier endpoint Actuator no aprobado en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/observability/ManagementExposureTest.java`.

### Implementation for User Story 3

- [x] T031 [US3] Ampliar la validación de aislamiento y no bypass en `scripts/observability/validate-network-isolation.ps1` y `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/observability/ManagementSecurityChainTest.java`, verificando desde ambos puertos la clasificación por listener/puerto más ruta y sin repetir la configuración de seguridad completada en T009–T010.
- [x] T032 [US3] Configurar en `backend/docker/observability-compose.yml` la red privada interna, ausencia de publicación del puerto de administración, exclusión de `unauthorized-probe` y publicación sólo del puerto de negocio.
- [x] T033 [US3] Crear escenarios deliberadamente inseguros aislados bajo `backend/docker/observability-scenarios/` para probar listener inválido, puerto publicado, segunda red, collector fuera de red, host alcanzable y cadena amplia, manteniendo `backend/docker/observability-compose.yml` seguro y sin variantes vulnerables en el despliegue normal.
- [x] T034 [US3] Verificar que la pertenencia a la red privada se documenta y aplica como límite de confianza, sin introducir autenticación individual ni reutilizar secretos JWT/API key en `backend/docker/observability-compose.yml` y `backend/src/main/resources/application.properties`.

> **Cierre US3-A (T033–T034, 2026-11-23)**: `validate-insecure-scenarios.ps1 -Scenario all` finalizó con 8 PASS: `invalid-listener`, `invalid-port`, `loopback-listener`, `published-management-port`, `backend-second-network`, `collector-outside-private`, `host-reachable` y `broad-chain`; el runner informó que no tocó el Compose normal. El límite de confianza está documentado en los comentarios de `backend/docker/observability-compose.yml` y `backend/src/main/resources/application.properties`: es topología de red Docker, no autenticación individual entre contenedores, y no reutiliza JWT ni API keys de negocio.

> **Cierre US3 (2026-11-24)**: `ManagementSecurityChainTest` completó 3 casos sin fallos para Actuator, rutas de negocio, JWT, API keys y ambos listeners; `ObservabilityBusinessSecurityRegressionTest` completó 3 casos sin fallos para transacciones JWT y flujos de API key; `ManagementExposureTest` completó 2 casos sin fallos para la superficie administrativa mínima. `validate-network-isolation.ps1` completó 14 PASS y 0 FAIL, con 1 SKIP JWT opcional que no se contabiliza como PASS, verificando redes efectivas, puertos, listener, IP dinámica y los intentos reales desde host y probes. El Compose seguro mantuvo backend sin puertos publicados y gateway sólo de negocio.

**Checkpoint**: La seguridad de negocio conserva su comportamiento; la observabilidad sólo es accesible por el límite de confianza previsto y las pruebas detectan cualquier bypass o exposición.

---

## Phase 6: User Story 4 - Preservar el comportamiento del producto (Priority: P2)

**Goal**: Confirmar que la observabilidad no altera negocio, PostgreSQL, E2E, rendimiento ni la calidad del backend.

**Independent Test**: Ejecutar compilación, pruebas existentes, regresión de seguridad, E2E y quickstart Docker sin modificar los Compose existentes ni la lógica de negocio.

### Tests for User Story 4

- [x] T035 [P] [US4] Crear prueba de conectividad backend→PostgreSQL en la topología privada de `backend/docker/observability-compose.yml` en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/e2e/observability/PostgresDockerConnectivityTest.java`.
- [x] T036 [P] [US4] Crear prueba de regresión de endpoints y contratos de negocio en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/web/ObservabilityBusinessRegressionTest.java` sin modificar pruebas existentes.
- [x] T037 [P] [US4] Crear verificación de no regresión de E2E y rendimiento en `scripts/observability/validate-existing-suites.ps1`, invocando los Compose existentes sin editarlos.

> **Cierre T037 (2026-11-24)**: PASS de las suites Playwright existentes; validación real en PowerShell 5.1 del árbol de procesos con terminación confirmada de raíz e hijo antes del `finally`, y finalización natural con exit code 0. PASS de sonda JDBC (`SELECT 1`, base y usuario esperados), Flyway directo V1–V23, aplicación de `performance/seed.sql` y conteos de 400 equipos, 10.000 jugadores, 1.000 usuarios y 0 API keys. El smoke de performance finalizó sin recursos Docker ajenos; las consultas posteriores de contenedores `obs-us4` y procesos Java del Flyway temporal no devolvieron resultados. Esta validación de performance es un smoke de infraestructura, no un benchmark de latencia ni throughput. En ese cierre inicial, T038 permanecía pendiente de validación runtime; su cierre posterior consta en la evidencia T035–T038.

### Implementation for User Story 4

- [x] T038 [US4] Ajustar el `Dockerfile` y el Compose específico para que el backend arranque con PostgreSQL por nombre de servicio y mantenga sólo la conectividad de red aprobada en `backend/Dockerfile` y `backend/docker/observability-compose.yml`.

> **Cierre T035–T038 (2026-11-24)**: T035 PASS con Testcontainers, `SELECT 1` y migraciones Flyway; T036 PASS con contrato público de jugadores y transacciones autenticadas (3 tests conjuntos, BUILD SUCCESSFUL). La ruta `GET /api/v1/players` consulta PostgreSQL mediante `JugadorServiceImpl` y las consultas nativas `contar`/`buscar` de `JugadorRepository`; la respuesta vacía observada no es estática. T038 PASS en runtime: cinco contenedores, PostgreSQL healthy, `validate-network-isolation.ps1` con 14 PASS, 1 SKIP opcional y 0 FAIL; backend sin puertos publicados, gateway sólo de negocio, listener administrativo 8081 accesible sólo al collector autorizado, y endpoint público de jugadores HTTP 200 con envoltorio y paginación válidos. La comprobación JWT positiva runtime quedó opcionalmente omitida por ausencia de `OBSERVABILITY_BUSINESS_AUTHORIZATION`.

> **Estado US4**: el bloque T035–T040 está completado; US4 queda formalmente cerrada. Las tareas posteriores de Polish permanecen fuera de este cierre.
- [x] T039 [US4] Ejecutar la documentación operativa aprobada en `specs/007-actuator-prometheus/quickstart.md` y verificar que los comandos y resultados esperados coinciden, sin modificar documentos SDD.
- [x] T040 [US4] Ejecutar la regresión del backend con `backend/gradlew test`, `backend/gradlew bootJar` y las suites E2E existentes, registrando que JWT, API keys y negocio conservan sus resultados.

> **Cierre T039 (2026-11-24)**: La matriz operativa del quickstart fue contrastada: `docker compose config` terminó con exit code 0; el Compose levantó cinco servicios con PostgreSQL healthy; `business-gateway` publicó negocio en 18080 y backend no publicó 8081. Health completó 10 PASS, métricas 13 PASS, aislamiento 14 PASS, 1 SKIP JWT opcional y 0 FAIL, y los ocho escenarios inseguros fueron detectados. El residual temporal observado en `invalid-listener` fue limpiado selectivamente sin volúmenes; no se atribuye una causa. La verificación posterior de limpieza pasó cuatro simulaciones, sintaxis y revisión de espacios, y la revalidación focalizada de `invalid-listener` terminó con exit code 0 y cero contenedores temporales. El Compose normal se preservó y se detuvo mediante `docker compose stop`; gateway y PostgreSQL finalizaron con exit code 0, mientras backend y probes finalizaron con 137.

> **Cierre T040 (2026-11-24)**: La suite completa `backend/gradlew test` y `backend/gradlew bootJar` finalizaron PASS (este último UP-TO-DATE); `npm run verify:frontend` PASS sobre 63 archivos y `npm run build` PASS, con sólo una advertencia no bloqueante de tamaño de chunk. Playwright completó 6/6 PASS tanto en la ejecución original (2,4 min) como con el backend local activo (2,1 min). El backend local conservó su PID, health HTTP 200 con estado `UP` y Prometheus HTTP 200; E2E eliminó sus contenedores y liberó sus puertos. No se capturó directamente la dirección de escucha administrativa E2E. La regresión de negocio preservó el contrato público de jugadores y las transacciones JWT; el reporte existente de `ObservabilityBusinessSecurityRegressionTest` registra 3 casos sin fallos, incluidos JWT de transacciones, emisión de API key y autorización API key. El smoke de infraestructura de T037 no se interpreta como benchmark de latencia o throughput.

**Checkpoint**: El backend compila, sus pruebas pasan y las funcionalidades de negocio y Compose existentes permanecen intactos.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Validación final, calidad y trazabilidad antes de revisión manual.

- [x] T041 [P] Revisar `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/management/` para eliminar duplicación, mantener responsabilidades únicas y respetar el máximo de seis parámetros por método o constructor.
- [x] T042 [P] Revisar `backend/src/test/java/com/devchesterunited/devchesterunitedv2/` para asegurar separación entre MockMvc/contexto y HTTP real Docker, nombres de pruebas claros y casos felices/borde.
- [x] T043 Ejecutar la matriz completa de `specs/007-actuator-prometheus/quickstart.md`, incluyendo collector autorizado, `unauthorized-probe`, host, PostgreSQL, puertos y listeners efectivos.

> **Cierre T041 (2026-11-24)**: La revisión formal confirmó Clean Code, SOLID y separación de responsabilidades en la implementación real de observabilidad, ubicada en `configuration/security/`: `ManagementPortRequestMatcher` clasifica exclusivamente el contexto y puerto administrativo, mientras `SecurityConfig` conserva el cierre por defecto y la autorización mínima. El alcance revisado respeta un máximo de seis parámetros por método o constructor; la excepción de tres `if` anidados para documentación Swagger está justificada por perfiles `local`/`test` y flags independientes, sin afectar Actuator. Se difirieron como mejoras opcionales la descomposición de `Stop-NativeProcessTree` y una mayor visibilidad de fallos de teardown E2E. `ConversionContext` de catálogo queda fuera de esta revisión y no se afirma ese límite para todo el proyecto.

> **Cierre T042 (2026-11-24)**: La revisión formal confirmó separación entre MockMvc para regresión de negocio/autorización, HTTP real de contexto para listeners administrativos, Testcontainers para PostgreSQL/Flyway, runners Compose black-box para puertos y aislamiento, y Playwright para E2E. Las pruebas cubren rutas permitidas y denegadas, listener desconocido, JWT/API key, datos sensibles en métricas, redes no autorizadas, publicación accidental y limpieza mediante `finally`, proyectos aislados y verificación de contenedores residuales. Los nombres y aserciones son significativos; no se identificó duplicación bloqueante. Se preservan los límites de evidencia ya registrados: SKIP JWT Docker opcional, smoke no equivalente a benchmark y dirección administrativa E2E no capturada directamente.

> **Cierre T043 (2026-11-24)**: La matriz fue ejecutada y documentada por T039–T040: collector autorizado, `unauthorized-probe`, host, PostgreSQL healthy, puertos y listener administrativo efectivo del Compose fueron validados. La comprobación E2E no capturó directamente la dirección de escucha administrativa; ese límite no afecta la validación Docker de esta tarea.
- [x] T044 Ejecutar `backend/gradlew clean build --no-daemon --console=plain` y verificar generación de JaCoCo en `backend/build/reports/jacoco/test/jacocoTestReport.xml`.

> **Cierre T044 (2026-11-24)**: Desde `backend/` se ejecutó `.\gradlew.bat clean build --no-daemon --console=plain` con `BUILD SUCCESSFUL`, compilación limpia, suite completa de backend y generación JaCoCo satisfactorias. Se verificó y procesó correctamente con PowerShell `backend/build/reports/jacoco/test/jacocoTestReport.xml` (1.580.073 bytes; 2026-11-24 16:27:17) y existe el índice HTML. Cobertura global del backend: INSTRUCTION 38.790 cubiertas/5.431 no cubiertas (87,72%); BRANCH 2.552/1.209 (67,85%); LINE 7.441/888 (89,34%); COMPLEXITY 2.921/1.394 (67,69%); METHOD 2.095/314 (86,97%); CLASS 453/22 (95,37%). No existen umbrales mínimos JaCoCo configurados; estos resultados son globales del backend, no exclusivos de observabilidad ni equivalentes al Quality Gate de SonarCloud.
- [ ] T045 Ejecutar el análisis SonarCloud únicamente después de una integración autorizada manualmente en `main`, revisar vulnerabilidades/issues y dejar explícito que esta tarea no autoriza commits, merges, push, cambios de rama ni gates adicionales para `feature/actuator-prometheus`.
- [x] T046 Revisar el resultado final contra `specs/007-actuator-prometheus/spec.md`, `specs/007-actuator-prometheus/plan.md` y `specs/007-actuator-prometheus/contracts/observability.md`, sin modificar esos documentos durante la implementación.

> **Cierre T046 (2026-11-24)**: La revisión final contrastó `spec.md`, `plan.md`, `contracts/observability.md`, `quickstart.md`, `research.md`, `data-model.md`, checklist y `tasks.md`. Las 44 tareas cerradas mantienen trazabilidad suficiente desde requisitos y escenarios de health, métricas, aislamiento, superficie mínima y no bypass hasta pruebas de contexto, Testcontainers, runners Docker, Playwright, regresión y JaCoCo. Se preservan los límites de evidencia: JWT positivo Docker opcional como SKIP, smoke de infraestructura no equivalente a benchmark, listener administrativo E2E no capturado directamente, limpieza revalidada focalmente tras corregir el runner negativo y cobertura JaCoCo global no equivalente al Quality Gate de SonarCloud. No se identificaron contradicciones bloqueantes. Quedan como mejoras documentales opcionales formulaciones históricas de plan/research sobre valores pendientes ya resueltos por la implementación y sus cierres. T045 sigue pendiente de análisis SonarCloud tras una integración autorizada en `main`; la feature no se declara cerrada al 100%.

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
- T006 y T007 pueden prepararse en paralelo. T008 debe crear primero la prueba fundacional; T009 y T010 dependen de ella y se aplican sobre `SecurityConfig.java`, sin una configuración administrativa independiente. T008 debe pasar antes de las historias.
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
