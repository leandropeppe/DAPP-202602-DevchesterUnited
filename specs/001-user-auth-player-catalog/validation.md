# Evidencia de validación — preparación compartida

> **Estado vigente (2026-09-16):** T001–T069 están cerradas; US1–US4 están implementadas y validadas. T070–T088 permanecen pendientes. Las secciones anteriores conservan evidencia histórica de cada corte.

Fecha: 2026-09-06. Alcance: T005, estado posterior a T001–T004 y anterior a T006. Esta evidencia no declara terminado el DoD de la entrega funcional.

## Estado inicial registrado

**Nota histórica**: esta sección conserva el estado registrado en el corte inicial. El estado vigente se encuentra en la nota al inicio del documento y en las últimas secciones de cierre.

Directorio: `C:\Users\Leandro\IdeaProjects\DevchesterUnitedV2`.
Rama real: `master`. HEAD: `d721a6d54fad1773915e703593a61411ea951e01`.
La evidencia corresponde al árbol de trabajo con cambios previos, no solamente a ese commit. No se cambió de rama ni se creó un commit.

Comandos ejecutados desde la raíz:

```powershell
git status --short
git branch --show-current
git rev-parse HEAD
```

Estado antes de editar documentación en T005:

```text
 M build.gradle
 M specs/001-user-auth-player-catalog/tasks.md
 M src/main/resources/application.properties
?? src/test/resources/
```

Git rechazó inicialmente la consulta desde el usuario del sandbox por propiedad del repositorio; se repitió fuera del sandbox, sin modificar `safe.directory` ni configuración de Git. Gradle también se ejecutó fuera del sandbox para acceder a su caché.

Se conservan `rootProject.name = 'DevchesterUnitedV2'`, `group = 'com.devchesterUnited'`, versión `0.0.1-SNAPSHOT` y el paquete raíz `com.devchesterunited.devchesterunitedv2`. La clase de arranque sigue siendo `DevchesterUnitedV2Application`, con `@SpringBootApplication` y su llamada a `SpringApplication.run`. No se renombraron ni movieron archivos.

## Archivos que deben preservarse

Se inventarió `src/test` completo: contiene un único archivo de test Java y `resources/application.properties`. SHA-256 calculados sobre los bytes de los archivos actuales:

| Archivo relativo a la raíz | SHA-256 |
| --- | --- |
| `src/test/java/com/devchesterunited/devchesterunitedv2/DevchesterUnitedV2ApplicationTests.java` | `61C45718D8B33C238EF584E5D72D36263B79F3FAD247AC02D30A2621B5A82367` |
| `src/main/java/com/devchesterunited/devchesterunitedv2/DevchesterUnitedV2Application.java` | `CD4F342B0BC3EEE24A973A5A85811F10071C6D8B4438AE95251C84E371733701` |
| `settings.gradle` | `A5C25E62E7A7AB3B48349D160AA5045E3F841EEC4130DD8F38F14BAB26D224E4` |
| `build.gradle` | `5DC23839F2F0A3F85C0B60DFEBD8C0E4AF9F992AA5843BD8FBAC7C2691A38CCF` |
| `src/main/resources/application.properties` | `7778467CABA461090838CEC4C5D391BC02CFB7E371AA983E2F7A9EC3DE5F69D4` |
| `src/test/resources/application.properties` | `1B6FEBF14BC95D692078BD9B86A29401C5BED9FE5F5C0237B2C0F38038241D3C` |

El hash del test coincide con el registrado antes y después de T004. Ningún test se modificó en T005. Según la Constitución, modificar o borrar un test existente requiere un «SÍ» explícito del usuario.

Comandos de inventario y reproducción de hashes:

```powershell
Get-ChildItem -LiteralPath 'src/test' -Recurse -File
Get-ChildItem -LiteralPath 'src/test' -Recurse -File -Filter '*.java' | Get-FileHash -Algorithm SHA256 | Format-List Path,Hash
Get-ChildItem -LiteralPath 'src/main/java' -Recurse -File -Filter '*Application.java' | Get-FileHash -Algorithm SHA256 | Format-List Path,Hash
Get-FileHash -LiteralPath 'build.gradle','settings.gradle','src/main/resources/application.properties','src/test/resources/application.properties' -Algorithm SHA256 | Format-List Path,Hash
```

## Entorno y dependencias resueltas

Comandos ejecutados en T005, con `JAVA_HOME` ajustado solo en el proceso de validación (en otro equipo, utilizar la ruta de su JDK 21):

```powershell
$env:JAVA_HOME = 'C:\Users\Leandro\.jdks\corretto-21.0.12.1'
.\gradlew.bat --version
.\gradlew.bat dependencies --configuration testRuntimeClasspath --console=plain
```

Resultados: Windows 11 amd64, Gradle **8.14.4**, Launcher JVM **21.0.12.1** Amazon Corretto y Daemon JVM en el mismo JDK 21. El build declara toolchain Java 21. El informe de dependencias terminó con **BUILD SUCCESSFUL**, sin dependencias `FAILED`.

Versiones seleccionadas en `testRuntimeClasspath`, incluyendo las dependencias productivas transitivas:

| Componente | Versión resuelta |
| --- | --- |
| Spring Boot y starters Web, Validation, Security, OAuth2 Resource Server, JPA y Test | 3.5.16 |
| Spring Framework: aop, aspects, beans, context, core, expression, jcl, jdbc, orm, test, tx, web y webmvc | 6.2.19 |
| JUnit Jupiter: agregado, API, engine y params; JUnit BOM | 5.12.2 |
| JUnit Platform: launcher, engine y commons | 1.12.2 |
| Spring Security, Resource Server, JOSE y Security Test | 6.5.11 |
| Testcontainers: core, junit-jupiter, jdbc, postgresql y database-commons | 1.21.4 |
| AssertJ core | 3.27.7 |
| PostgreSQL JDBC | 42.7.11 |
| Flyway core y database-postgresql | 11.7.2 |
| Springdoc WebMVC UI | 2.8.17 |
| Hibernate ORM core | 6.6.53.Final |

La inspección del árbol completo confirma **JUnit Jupiter 5** y **ausencia de Spring Framework 7** en esta configuración. JUnit 4.13.2 aparece como dependencia transitiva de Testcontainers; esto no reemplaza Jupiter 5 ni implica usar su engine. `useJUnitPlatform()` está configurado. Las versiones sin especificar se gestionan mediante el plugin de dependency management y el BOM de Spring Boot; el árbol también muestra el BOM de JUnit 5.12.2.

## Evidencia previa de compilación y ejecución

No se repitieron pruebas en T005: se leyó el informe existente de la ejecución exitosa de T004, realizada en esta sesión. Comando utilizado entonces, con el mismo JDK 21:

```powershell
.\gradlew.bat test --tests 'com.devchesterunited.devchesterunitedv2.DevchesterUnitedV2ApplicationTests.contextLoads' --console=plain
```

Resultado: **BUILD SUCCESSFUL**, `test` y `jacocoTestReport` ejecutados. Informe local: `build/test-results/test/TEST-com.devchesterunited.devchesterunitedv2.DevchesterUnitedV2ApplicationTests.xml`. Fecha del test: `2026-09-06T17:02:07.288Z`; **1 test, 0 omitidos, 0 fallos, 0 errores**. Perfil activo: `test`. Testcontainers inició `postgres:16` y Flyway confirmó conexión a **PostgreSQL 16.15**. La URL efectiva informada fue `jdbc:postgresql://localhost:64523/test?loggerLevel=OFF`; el puerto es efímero. La URL declarada de Testcontainers sigue siendo `jdbc:tc:postgresql:16:///devchester_test`.

Lectura de evidencia ejecutada en T005:

```powershell
Select-String -LiteralPath 'build/test-results/test/TEST-com.devchesterunited.devchesterunitedv2.DevchesterUnitedV2ApplicationTests.xml' -Pattern '<testsuite |Container postgres:16 started|Database:|profile is active' | ForEach-Object { $_.Line }
```

La primera ejecución de T004 falló porque PostgreSQL rechazaba `America/Buenos_Aires`; T004 configuró `user.timezone=UTC` exclusivamente en la JVM de tests y la repetición pasó. No se alteró el test para lograrlo. El informe de `build/` es regenerable y puede ser reemplazado por futuras ejecuciones.

En T003 se ejecutó `.\gradlew.bat assemble --console=plain` con Java 21: **BUILD SUCCESSFUL**, incluyendo `bootJar` y `jar`. Este resultado previo acredita compilación/empaquetado, no arranque local ni validación funcional de historias.

## Control de cierre de preparación y DoD

| Control | Estado y límite de la evidencia |
| --- | --- |
| Wrapper y dependencias compatibles con Java 21 / Boot 3.5.16 | Verificado por `--version`, resolución exitosa y compilación/ejecución previas. |
| JUnit Jupiter 5 y ausencia de Framework 7 | Verificado en `testRuntimeClasspath`. |
| Raíz, clase de arranque y test existente preservados | Inventario y hashes registrados; sin cambios en T005. |
| Contexto de pruebas con PostgreSQL real | PASSED en T004; reporte inspeccionado en T005. |
| Pruebas unitarias, integración y E2E de historias, felices y bordes | Pendientes de las fases de implementación; `contextLoads` no acredita estos controles. |
| Arranque con configuración local | No validado en esta fase. |
| Postman actualizado y flujo funcional ejecutado | No validado en esta fase. |
| GitHub Actions Build SUCCESS y SonarCloud (máximo 10 issues, sin vulnerabilidades ni smells críticos) | Sin evidencia externa en esta fase. |

T005 completa el registro del estado inicial de preparación. El DoD global permanece abierto. No se ejecutaron ni implementaron T006 o tareas posteriores. Cambios posteriores en build, configuración, código o tests requieren revisar la evidencia afectada.

## Actualización T009–T010 — 2026-09-06

Esta sección agrega evidencia posterior al registro histórico de T005. Se implementó T009 antes de T010. No se implementó T011 ni autenticación JWT/API Key real ni controllers de historias.

### Decisiones verificadas

- Seguridad stateless, repositorio de contexto nulo, sin caché de solicitudes, form login, HTTP Basic, logout ni remember-me. CSRF deshabilitado para esta base sin autenticación por cookies. Los filtros no crean sesiones ni emiten cookies.
- Solo POST `/api/v1/auth/register` y `/api/v1/auth/login` son públicos entre los endpoints de negocio. El resto utiliza `denyAll`, incluso ante un usuario autenticado simulado. No hay mecanismos reales de credenciales todavía.
- Los filtros responden JSON con `status=error` y mensajes fijos `No autorizado.` (401) o `Acceso denegado.` (403), sin redirección ni detalles de excepciones y con `Cache-Control: no-store`.
- La documentación GET se permite en `local`/`test` cuando sus propiedades Springdoc están habilitadas. Se probaron los perfiles `local`, `test`, `prod` y sin perfil, leyendo `src/main/resources/application.properties` real: Swagger UI y API docs disponibles en los dos primeros, deshabilitados y protegidos en los demás.
- `BearerAuth`: HTTP bearer, formato JWT. `ApiKeyAuth`: API Key en header `X-API-KEY`. Se declaran componentes sin requisito global de seguridad; no se exige JWT y API Key conjuntamente. Las historias posteriores asignarán seguridad a sus operaciones.
- Los controllers de prueba solo existen en `src/test`; no se agregaron endpoints productivos de registro/login.

### Comandos y resultados reales

Desde la raíz, con Java 21 y acceso a Docker/caché de Gradle fuera del sandbox:

```powershell
$env:JAVA_HOME = 'C:\Users\Leandro\.jdks\corretto-21.0.12.1'
.\gradlew.bat test --tests '*SecurityBaseE2ETest' --console=plain
.\gradlew.bat test --tests '*OpenApiProfilesE2ETest' --console=plain
.\gradlew.bat clean build --console=plain
```

Los tres comandos terminaron con **BUILD SUCCESSFUL**. Pruebas dirigidas: T009, 8 casos; T010, 4 casos. Build final: **23 s, 10 tareas ejecutadas**, con `bootJar`, `jar`, `test`, `check`, `build` y `jacocoTestReport`. La suite completa se ejecutó después de las validaciones dirigidas:

| Suite | Tests | Fallos / errores / omitidos |
| --- | ---: | --- |
| DevchesterUnitedV2ApplicationTests (`contextLoads`) | 1 | 0 / 0 / 0 |
| PostgresTestSupportTest | 2 | 0 / 0 / 0 |
| CommonResponsesE2ETest | 10 | 0 / 0 / 0 |
| SecurityBaseE2ETest | 8 | 0 / 0 / 0 |
| OpenApiProfilesE2ETest | 4 | 0 / 0 / 0 |
| ServiceConfigTest | 1 | 0 / 0 / 0 |
| **Total** | **26** | **0 / 0 / 0** |

`contextLoads` PASSED, timestamp `2026-09-06T17:39:18.321Z`. Testcontainers inició PostgreSQL 16 y Flyway conectó correctamente. Los contextos de prueba de perfiles excluyen persistencia únicamente para aislar HTTP/OpenAPI; `contextLoads` y el soporte PostgreSQL se ejecutan con base real en la suite completa.

Reportes regenerables: `build/test-results/test/TEST-*.xml`, `build/reports/tests/test/index.html` y `build/reports/jacoco/test/html/index.html`. Gradle informó uso de una API de test deprecada en `SecurityBaseE2ETest` (comparación JSON); no hubo fallos de compilación ni ejecución.

Verificación ejecutada de resultados y preservación:

```powershell
Get-ChildItem 'build/test-results/test/TEST-*.xml' | ForEach-Object {
    [xml]$report = Get-Content $_.FullName -Raw
    $report.testsuite | Select-Object name,tests,failures,errors,skipped,timestamp
}
Get-ChildItem 'src/test/java' -Recurse -File | Get-FileHash -Algorithm SHA256 | Format-List Path,Hash
```

Todos los archivos de tests preexistentes conservaron los hashes capturados antes de T009:

| Archivo (relativo al paquete raíz de tests) | SHA-256 antes = después |
| --- | --- |
| `DevchesterUnitedV2ApplicationTests.java` | `61C45718D8B33C238EF584E5D72D36263B79F3FAD247AC02D30A2621B5A82367` |
| `configuration/PostgresTestSupport.java` | `CD1DC643964865A8379810C7F7E688EFC20DF2ABE3ABF9088754908CD08070DF` |
| `configuration/PostgresTestSupportTest.java` | `8D151B77A15BF8DDEF5655CEDB9ECA6F84ED66C83405A6BB48F85FCE4CB2FF0C` |
| `controller/CommonResponsesE2ETest.java` | `C7DAC31BF04DD9B179B443FFDFF24B39F92F1014A55B002F839A3C44854957A0` |
| `service/config/ServiceConfigTest.java` | `82B53592A673433CF41EEE84032C0EFB490E53CA5428DE43932D8779E7326186` |

**Control de cierre T010: PASSED.** No hay fallo del build base que bloquee US1 en este estado. Esto no ejecuta ni autoriza trabajo adicional: T011 permanece pendiente. El DoD global (historias, arranque local, Postman y controles externos) sigue abierto. Un fallo posterior del build base debe bloquear US1 hasta resolverse dentro de su alcance.

## Actualización T023 — 2026-09-06

Se ejecutó la validación completa de US1 después de T021–T022, sin implementar T024 ni tareas posteriores. Docker/Testcontainers estuvo disponible y las suites de integración utilizaron PostgreSQL real.

### Comandos reproducibles

```powershell
.\gradlew.bat test --tests '*UsuarioTest' --tests '*UsuarioRepositoryIntegrationTest' --tests '*AutenticacionIntegrationTest' --tests '*AuthE2ETest' --tests '*JwtConfigTest' --no-daemon --console=plain
.\gradlew.bat test --no-daemon --console=plain
Get-ChildItem build/test-results/test/TEST-*.xml | ForEach-Object {
    [xml]$report = Get-Content $_.FullName -Raw
    $report.testsuite | Select-Object name,tests,failures,errors,skipped
}
$pat = 'Segura123!|password_hash|NO_FILTRAR|RklVElDSU8|token-ficticio|JWT_SECRET'
Get-ChildItem build/test-results,build/reports/tests -Recurse -File |
    Select-String -Pattern $pat -SimpleMatch
```

### Resultados

La ejecución dirigida terminó con **BUILD SUCCESSFUL**: 86 tests, 0 fallos, 0 errores y 0 omitidos.

| Suite | Tests | Fallos / errores / omitidos |
| --- | ---: | ---: |
| `JwtConfigTest` | 31 | 0 / 0 / 0 |
| `AuthE2ETest` | 37 | 0 / 0 / 0 |
| `UsuarioTest` | 6 | 0 / 0 / 0 |
| `UsuarioRepositoryIntegrationTest` | 8 | 0 / 0 / 0 |
| `AutenticacionIntegrationTest` | 4 | 0 / 0 / 0 |

La suite completa terminó con **BUILD SUCCESSFUL**: **139 tests, 0 fallos, 0 errores y 0 omitidos**. Incluyó `contextLoads`, Testcontainers/PostgreSQL, seguridad base, OpenAPI, respuestas comunes, DTOs y configuración de servicio.

### Controles funcionales y de exposición

- Registro válido: HTTP 201, respuesta `success`, usuario normalizado y esquema `Usuario`.
- Login válido: HTTP 200, JWT Bearer y `expires_in=3600`.
- Username/email duplicados, incluidos duplicados normalizados y carreras concurrentes: HTTP 409, una sola inserción.
- Entradas inválidas, JSON desconocido y tipos JSON incorrectos: HTTP 400 con detalles en español.
- Credenciales incorrectas o usuario inexistente: HTTP 401 con mensaje genérico.
- Login: header `Cache-Control: no-store`.
- JWT: firma HS256, `iss`, `aud`, `sub`, `iat`, `exp`, duración exacta de 3600 segundos y reloj controlable sin tolerancia.
- BCrypt validado en persistencia; la contraseña en claro no se almacena.
- Las aserciones de `AuthE2ETest`, `CommonResponsesE2ETest` y `AutenticacionDTOTest` verifican que respuestas y errores no contienen contraseñas, hashes, secretos JWT, SQL ni `NO_FILTRAR`; los XML/HTML no contienen `system-out` ni `system-err` de la aplicación. Los únicos textos de `Segura123!` en los reportes son nombres de casos parametrizados que prueban espacios inválidos, no logs ni respuestas.

**Control de cierre T023: PASSED.** US1 queda validada con pruebas unitarias, integración y E2E, y la suite completa permanece en verde. T024 y las tareas posteriores siguen pendientes.

## Actualización T037 — cierre de User Story 2 (2026-09-06)

Se ejecutaron las validaciones con Java 21, PostgreSQL real mediante Testcontainers y Docker disponible. No se modificaron tests para esta validación.

### Comandos reproducibles

```powershell
.\gradlew.bat test --tests '*ApiKeyE2ETest' --tests '*ApiKeyIntegrationTest' --tests '*ApiKeyConcurrencyIntegrationTest' --tests '*ApiKeyTest' --tests '*ApiKeyRepositoryIntegrationTest' --tests '*UsuarioTest' --tests '*UsuarioRepositoryIntegrationTest' --tests '*AutenticacionIntegrationTest' --tests '*AuthE2ETest' --tests '*JwtConfigTest' --no-daemon --console=plain
.\gradlew.bat test --tests '*SecurityBaseE2ETest' --no-daemon --console=plain
.\gradlew.bat test --no-daemon --console=plain
```

### Resultados

La validación dirigida US1/US2 terminó en `BUILD SUCCESSFUL`: **103 tests, 0 fallos, 0 errores y 0 omitidos**. `SecurityBaseE2ETest` terminó en `BUILD SUCCESSFUL`: **8 tests, 0 fallos, 0 errores y 0 omitidos**. La suite completa terminó en `BUILD SUCCESSFUL`: **156 tests, 0 fallos, 0 errores y 0 omitidos**.

| Suite US2 | Tests | Fallos / errores / omitidos |
| --- | ---: | --- |
| `ApiKeyE2ETest` | 4 | 0 / 0 / 0 |
| `ApiKeyTest` | 4 | 0 / 0 / 0 |
| `ApiKeyIntegrationTest` | 3 | 0 / 0 / 0 |
| `ApiKeyRepositoryIntegrationTest` | 4 | 0 / 0 / 0 |
| `ApiKeyConcurrencyIntegrationTest` | 2 | 0 / 0 / 0 |

### Controles de US2

- La generación inicial devuelve una clave válida; la rotación revoca la anterior y mantiene exactamente una activa por usuario.
- La prueba de inserción fallida confirma rollback: la clave anterior continúa activa y sin `revoked_at`.
- La prueba concurrente de controller confirma un resultado HTTP `200` y otro `409`; la prueba de Service usa barrera y PostgreSQL real, sin `sleep` ni reintentos, y confirma una sola clave activa tras el commit.
- La generación requiere JWT válido. Una API Key sola no autoriza `/api/v1/auth/apikey`; `/players` exige autoridad `API_KEY` y no acepta JWT como sustituto.
- Las aserciones E2E verifican `Cache-Control: no-store` y que respuestas/errores no contienen la clave completa, contraseñas, hashes, secretos JWT, SQL ni credenciales sensibles.
- Las consultas de persistencia confirman que solo se almacenan `key_hash`, `key_prefix`, `is_active`, `created_at` y `revoked_at`; el secreto completo no tiene columna ni se persiste.
- Los errores se traducen al esquema común en español: `401` para credenciales inválidas, `409` para competencia y `503` para fallas de acceso a BD durante validación.

**Control de cierre T037: PASSED.** US2 queda validada. T038 y las tareas posteriores permanecen pendientes.

## Actualización T049 — cierre de User Story 3

Se ejecutaron las suites de US3 y la suite completa del proyecto con PostgreSQL real mediante Testcontainers. La ejecución utilizó temporalmente:

```powershell
$env:JAVA_TOOL_OPTIONS='-Duser.timezone=UTC -Dapi.version=1.44'
```

### Resultados de US3

| Suite | Tests | Fallos / errores / omitidos |
| --- | ---: | --- |
| `JugadorTest` | 54 | 0 / 0 / 0 |
| `JugadorRepositoryIntegrationTest` | 28 | 0 / 0 / 0 |
| `JugadorServiceIntegrationTest` | 59 | 0 / 0 / 0 |
| `JugadorE2ETest` | 64 | 0 / 0 / 0 |
| `RevocacionEnCursoE2ETest` | 2 | 0 / 0 / 0 |
| **Total suites US3** | **207** | **0 / 0 / 0** |

La suite completa del proyecto terminó con **BUILD SUCCESSFUL in 4m 17s**: **363 tests, 0 fallos, 0 errores y 0 omitidos**.

### Controles funcionales de US3

- `JugadorTest` validó las invariantes del dominio, posiciones permitidas, campos obligatorios y dorsal 1–99.
- `JugadorRepositoryIntegrationTest` validó filtros literales, parámetros enlazados, acentos, combinación AND, orden UUID y paginación.
- `JugadorServiceIntegrationTest` validó filtros, trimming, metadatos, mapeo público e identificadores `ply_<uuid>`.
- `JugadorE2ETest` validó Madrid case-insensitive devolviendo jugadores de Real Madrid y Atlético de Madrid; contenido y totales; contrato MockMvc; autenticación API Key; errores 400/401; paginación; y ausencia de filtraciones.
- `RevocacionEnCursoE2ETest` validó la consulta autorizada durante una revocación y la respuesta 503 ante una base de datos inaccesible.

**Control de cierre T049: PASSED.** US3 queda validada con pruebas de dominio, Repository, Service y E2E. La suite completa permanece en verde.

## Actualización T050 — revisión transversal y cierre (2026-09-14)

Se revisó la implementación completa de T001–T049 contra la Constitución, `spec.md`, `plan.md` y `tasks.md`. No se detectaron incumplimientos reales que requirieran modificar código productivo ni tests.

### Controles revisados

- Separación estricta por capas: los Controllers dependen de interfaces de Service y no acceden directamente a Repository.
- DTOs en los límites HTTP; validaciones de formato en DTO, existencia y operaciones en Service e invariantes en Modelo.
- Respuestas uniformes de error JSON en español para 400, 401, 403, 409, 500 y 503, sin stack traces ni detalles internos.
- Ausencia de logging de contraseñas, secretos JWT, API Keys completas o hashes; DTOs sensibles no los exponen en `toString` ni en respuestas indebidas.
- BCrypt con coste 12, JWT HS256 con secreto externo, API Keys hasheadas y rotación atómica según el plan.
- Seguridad stateless, `Clock.systemUTC()` y consistencia de packages, nombres y arquitectura.

### Validación ejecutada

Comando reproducible:

```powershell
.\gradlew.bat test --no-daemon --console=plain
```

Resultado: **BUILD SUCCESSFUL** en 1m 16s. Suite completa: **363 tests, 0 fallos, 0 errores y 0 omitidos**. También se generó correctamente `build/reports/jacoco/test/jacocoTestReport.xml`.

**Control de cierre T050: PASSED.** T001–T049 continúan validadas; no se modificaron requisitos, decisiones técnicas, código productivo ni tests. T051–T061 permanecen pendientes.
## Actualizacion T054-T056 - reorganizacion monorepo y regresion (2026-09-16)

Se valido exclusivamente el cierre de la reorganizacion estructural. No se implemento T057, US4, integracion Football-Data ni funcionalidad frontend de US5.

### Estructura verificada

- `backend/` contiene el proyecto Gradle completo: `build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat`, `gradle/` y `src/`, conservando el paquete `com.devchesterunited.devchesterunitedv2`.
- `frontend/` contiene unicamente el esqueleto planificado: `package.json`, `package-lock.json`, `vite.config.js`, `src/.gitkeep`, `tests/.gitkeep` y `e2e/.gitkeep`.
- `.specify/` y `specs/` permanecen en la raiz. `.github/`, `performance/` y `postman/` no existen en el arbol actual y permanecen como areas planificadas en raiz; no se creo contenido fuera del alcance T054-T056.
- No existen archivos `.ts`, `.tsx` ni `tsconfig` en `frontend/`.

### T054 - compilacion y empaquetado

Comando desde `backend/`:

```powershell
$env:JAVA_HOME = 'C:\Users\Leandro\.jdks\corretto-21.0.12.1'
.\gradlew.bat clean assemble --no-daemon --console=plain
```

Resultado real: **BUILD SUCCESSFUL in 12s**, 6 tareas ejecutadas. Se generaron `backend/build/libs/DevchesterUnitedV2-0.0.1-SNAPSHOT.jar` y `-plain.jar`.

### T055 - regresion historica

Comando desde `backend/`:

```powershell
$env:JAVA_HOME = 'C:\Users\Leandro\.jdks\corretto-21.0.12.1'
.\gradlew.bat test --no-daemon --console=plain
```

Resultado exacto de los XML: **363 tests, 0 fallos, 0 errores y 0 omitidos**, en 22 suites. Gradle termino con **BUILD SUCCESSFUL in 1m 16s**. No se modifico ningun archivo bajo `backend/src/test/`.

La cifra verificable de esta ejecucion es 363/363, coincidente con el criterio textual de T055; no se alteraron tests para ajustar el conteo.

### Arranque local y Swagger

Se ejecuto temporalmente el bootJar con `--spring.profiles.active=local` contra `jdbc:postgresql://localhost:5433/devchester`, PostgreSQL **16.15**, Flyway y secreto JWT sintetico de validacion. El proceso arranco en el puerto 8080; Flyway valido el esquema y se registro `Started DevchesterUnitedV2Application`.

Se uso `-Duser.timezone=UTC` unicamente en el proceso de validacion, porque la instancia PostgreSQL local rechaza `America/Buenos_Aires`; no se cambio codigo ni configuracion versionada.

| Recurso | Resultado |
| --- | --- |
| `GET http://localhost:8080/swagger-ui/index.html` | **HTTP 200** |
| `GET http://localhost:8080/v3/api-docs` | **HTTP 200** |

El proceso de validacion fue detenido despues de las comprobaciones.

### Hashes SHA-256 de tests

Se calcularon sobre los 24 archivos actuales de `backend/src/test/` despues de la regresion; no hubo ediciones durante T054-T056.

```text
DevchesterUnitedV2ApplicationTests.java 61C45718D8B33C238EF584E5D72D36263B79F3FAD247AC02D30A2621B5A82367
configuration/JwtConfigTest.java D5CA60358629922E159A94D5F1E1E638525261ABF46A393892A56F4037FA7BEC
configuration/PostgresTestSupport.java CD1DC643964865A8379810C7F7E688EFC20DF2ABE3ABF9088754908CD08070DF
configuration/PostgresTestSupportTest.java 8D151B77A15BF8DDEF5655CEDB9ECA6F84ED66C83405A6BB48F85FCE4CB2FF0C
controller/ApiKeyE2ETest.java 4686D2CE7FD09A181A0962FE88562B905CE454FC0AAF7F50A85BBC8FB1A15DEE
controller/AuthE2ETest.java 2758433FF809F629029F9D5362D77802F44FD76E935719F94791909FEF017131
controller/CommonResponsesE2ETest.java C7DAC31BF04DD9B179B443FFDFF24B39F92F1014A55B002F839A3C44854957A0
controller/JugadorE2ETest.java 3ED54DF29A5D9566EE7B679C743746EB368D5970D9537DD8E3787F11783BEAD1
controller/OpenApiProfilesE2ETest.java 0C080227CAE6C4D592E5E06D418932C8DB6E5D0978D8A7763FB361B31265CD7A
controller/RevocacionEnCursoE2ETest.java 40F3BC1DB359B59232257DFE3C532F2E6B818AE518B3642C179801BDFECCF291
controller/SecurityBaseE2ETest.java 51AFD421B51934C068AA08C3AB87F551D8E9A36209AFC52BFBEB298FC31AF070
controller/dto/AutenticacionDTOTest.java EE056ACF4EC5D0790028839CAE619D1730031FCDD34EC0733F765E635C3D0890
modelo/ApiKeyTest.java A266FC4407E9CA3A16585F140B75E35F5E7CECC38B71DB301570C120C5B2C70C
modelo/JugadorTest.java 8164076ECCFDC327CF6B09716077FABB45E95E1C1F006A4AA2B41372EF1D42A0
modelo/UsuarioTest.java 1ACF31CE0684AB35D5491C3D55192E63ADEC3C85DE60FD30BC676EF93B98F9CD
persistence/ApiKeyRepositoryIntegrationTest.java 9B1AC4710B4570D7FD1855D11EC2F08FA0EE4DE885C257A2B324AC6CF2A06AE7
persistence/JugadorRepositoryIntegrationTest.java 2DB628F527AA54DBB6AE96A0E4D540EE2487334775FAD077320504FBD211F079
persistence/UsuarioRepositoryIntegrationTest.java BD0C6291D24A3D5002C5A90FE6D0DFA8ED1B7F886CDD1E91F26743B63C1916D7
service/ApiKeyConcurrencyIntegrationTest.java 938D23833BFA7BAD30B59CCC645BBA8093975A33436C62CC92FD2F6F16F7A124
service/ApiKeyIntegrationTest.java 51C3308D5F15A3EEA700ACF99B2062C36BF5A31A243845C3D7F032F2836C7AC9
service/AutenticacionIntegrationTest.java 2714953ABF52173E72F962AD25DF43D7A314C3B207BC0A0078588B237C9EE442
service/JugadorServiceIntegrationTest.java A2AC4C924C21053EF2B06446D9FD66C5AAF468BBED241506AA6AEC3EE490FD0B
service/config/ServiceConfigTest.java 82B53592A673433CF41EEE84032C0EFB490E53CA5428DE43932D8779E7326186
resources/application.properties 1B6FEBF14BC95D692078BD9B86A29401C5BED9FE5F5C0237B2C0F38038241D3C
```

**Control de cierre T054-T056: PASSED.** US1-US3 no tuvieron cambios funcionales ni se modificaron tests existentes. T057 continua pendiente y no se avanzo a US4.
## Actualizacion T057 - autorizacion para tests de US4 (2026-09-16)

Se revisaron constitution.md, spec.md, plan.md, research.md, data-model.md, contracts/proveedor.md, contracts/openapi.json y tasks.md antes de actuar.

T057 queda resuelta sin modificar tests existentes: no se requiere autorizacion "SI" porque T058-T059 solo permitirian agregar archivos nuevos, y no se propone ningun cambio en backend/src/test existente. No se inventaron external IDs ni se modificaron migraciones, modelos legados, contratos publicos o configuracion.

T058 y T059 permanecen pendientes. Sus clases de produccion (Equipo, CatalogoSincronizado y FootballDataAdapter) estan planificadas respectivamente en T063 y T065, aun no existen en el backend, y los artefactos actuales no fijan una API Java concreta que permita crear tests compilables sin anticipar esas tareas. Implementarlas ahora implicaria avanzar fuera del alcance solicitado.

No se ejecutaron tests nuevos porque no se agregaron tests. La regresion historica no se repitio: este cambio no afecta codigo productivo ni tests existentes. T060 y posteriores continuan pendientes.
## Actualizacion T063-T065 - prerrequisitos productivos de US4 (2026-09-16)

### Grafo de dependencias

T063 modelo puro -> T060 repositories, T064 entidades/V4/repositories y T065 Adapter/DTOs.
T064 -> T060 y T061. T065 -> T059 y T062. T063+T064+T065 -> T061/T062.
T060+T061+T062+T063+T064+T065 -> T066.

Orden aplicado: T063 -> T064 -> T065. No se siguio el orden numerico T058/T059 porque eran pruebas first de APIs productivas inexistentes. T060-T062 requieren ademas Service/Controller, que pertenecen a T066-T067.

### Cambios productivos

- T063: se agregaron Equipo y CatalogoSincronizado como modelos puros; Jugador conserva su constructor legacy y suma identidad externa, equipo, actividad, dorsal nullable y fabrica explicita de importacion.
- T064: se agregaron EquipoEntity, EquipoRepository y campos normalizados a JugadorEntity/JugadorRepository.
- T064: se agrego V4__Normalize_catalog_sync.sql. Crea equipos, conserva UUID V3, agrega team_id/external_id/actividad/timestamps, permite dorsal null, FK e indices. Las columnas legacy league/team se conservan transitoriamente para no romper US3.
- T064: V4 incluye un trigger de compatibilidad para fixtures legacy; crea/resuelve equipos locales por liga y nombre exactos sin inventar external_id.
- T065: se agregaron DTOs externos exclusivamente en adapter/dto, FootballDataException y FootballDataAdapter con RestClient, cinco rutas secuenciales, HTTPS sin redirects, X-Auth-Token desde FOOTBALL_DATA_API_KEY, validacion de temporadas/cantidades/squads/IDs/posiciones, control de cuota, limite de 10 MiB, timeout de conexion 5 s, respuesta 15 s y deadline 120 s.
- Se agrego solo la propiedad versionada vacia football-data.api-key desde FOOTBALL_DATA_API_KEY; no se versiono ningun secreto.

### Validacion ejecutada

Desde backend:

    .\gradlew.bat compileJava --no-daemon --console=plain
    .\gradlew.bat test --no-daemon --console=plain
    .\gradlew.bat clean assemble --no-daemon --console=plain

Resultados:

- Compilacion productiva: BUILD SUCCESSFUL in 9s.
- Regresion final: 363 tests, 0 fallos, 0 errores, 0 omitidos; BUILD SUCCESSFUL in 1m 14s.
- Empaquetado limpio: BUILD SUCCESSFUL in 10s.
- Las ejecuciones intermedias fallidas se corrigieron antes del resultado final: separador NUL invalido en SQL, mapeo JPA de EquipoEntity, constructor de inyeccion del Adapter y compatibilidad de inserts legacy.

Arranque local final:

- PostgreSQL 16.15 en localhost:5433/devchester.
- Flyway aplico V4 y quedo en version v4.
- Tomcat inicio en puerto 8080.
- GET /swagger-ui/index.html: HTTP 200.
- GET /v3/api-docs: HTTP 200.

T058 y T059 estan ahora desbloqueadas porque existen las APIs productivas correspondientes, pero siguen pendientes y sin marcar. No se agregaron tests nuevos en este tramo. T060, T061 y T062 siguen pendientes porque requieren completar sus respectivos niveles de prueba y, para T061/T062, el Service/Controller posterior.

No se modificaron tests historicos. US1-US3 conservaron su regresion verde. La desviacion respecto de data-model.md es conservar temporalmente league/team y el trigger legacy para compatibilidad de despliegue; se documenta como transicion coordinada y no asigna external IDs a datos existentes.

**Cierre productivo validado: T063-T065 [x]. T058-T062 [ ], T066 y posteriores [ ].**

## Validacion T058-T060 (2026-09-16)

- T058: se agregaron unitarias puras para Equipo, Jugador importado y CatalogoSincronizado. Cubren identidad externa, temporada, estados, dorsal nullable, pertenencia, snapshot valido, composicion por liga y duplicados.
- T059: se agrego FootballDataAdapterTest con HttpServer local en 127.0.0.1. Cubre las cinco rutas, DTOs y posiciones mapeadas, payload invalido, 429, 5xx, cuota agotada y timeout. No realiza llamadas a football-data.org ni usa credenciales reales.
- T060: se agrego SincronizacionRepositoryIntegrationTest con PostgreSQL 16/Testcontainers. Cubre V4, unicidad de external_id, asociacion team_id, dorsal NULL, estados activo/inactivo y preservacion del UUID historico V3.
- No se modifico ningun test existente.

Resultados desde backend:

    .\gradlew.bat test --tests '*EquipoTest' --tests '*JugadorImportadoTest' --tests '*CatalogoSincronizadoTest' --tests '*FootballDataAdapterTest' --tests '*SincronizacionRepositoryIntegrationTest' --no-daemon --console=plain
    .\gradlew.bat test --no-daemon --console=plain
    .\gradlew.bat clean assemble --no-daemon --console=plain

- Tests focalizados: 16 tests, 0 fallos, 0 errores, 0 omitidos; BUILD SUCCESSFUL in 25s.
- Regresion completa: 379 tests, 0 fallos, 0 errores, 0 omitidos; BUILD SUCCESSFUL in 1m 11s. Incluye los 363 historicos sin cambios.
- Empaquetado limpio: BUILD SUCCESSFUL in 10s.

T061 y T062 permanecen pendientes: sus pruebas requieren SincronizacionCatalogoService y CatalogoController, que son prerrequisitos productivos de T066 y T067 y quedan fuera de este alcance. T066 y posteriores continúan pendientes.

**Cierre validado: T058-T060 [x]. T061-T062 [ ]. T066 y posteriores [ ].**

## Validacion T066-T067 (2026-09-16)

- T066: se implemento SincronizacionCatalogoService con transaccion READ COMMITTED de 150 s, lock global no bloqueante `pg_try_advisory_xact_lock(114644, 1)`, consulta secuencial al Adapter y publicacion atomica.
- El snapshot se valida antes de persistir y exige exactamente 96 equipos distribuidos 20/20/18/20/18; el dominio tambien valida IDs, pertenencias, posiciones, dorsales nullable y duplicados.
- La publicacion hace upsert por external_id, conserva UUID/created_at, actualiza y reactiva presentes, inactiva ausentes tras snapshot valido y desactiva los datos demo sin external_id solo en el primer snapshot. No elimina filas ni inventa IDs externos.
- T067: se implemento `POST /api/v1/catalog/sync`, protegido exclusivamente por JWT y con validacion de usuario existente. Rechaza cuerpos no vacios con 400.
- Errores de lock se traducen a 409; proveedor, timeout, cuota, snapshot invalido y errores de BD a 503; excepciones inesperadas permanecen como 500 generico. No se exponen cuerpos, secretos ni detalles internos.
- No se modificaron tests historicos ni se realizan llamadas reales al proveedor durante tests.

Tests nuevos:

    .\gradlew.bat test --tests '*SincronizacionCatalogoServiceTest' --tests '*CatalogoControllerTest' --no-daemon --console=plain

- Focalizados Service/Controller: 6 tests, 0 fallos, 0 errores, 0 omitidos; BUILD SUCCESSFUL in 15s.
- Regresion completa: 385 tests, 0 fallos, 0 errores, 0 omitidos; BUILD SUCCESSFUL in 1m 17s. Los 363 tests historicos permanecen verdes.
- `clean assemble`: BUILD SUCCESSFUL in 10s.

Arranque local:

- PostgreSQL 16.15 y Flyway V4 operativos; la aplicacion inicio correctamente en puerto 8080.
- `/v3/api-docs`: HTTP 200; `/swagger-ui/index.html`: HTTP 200.
- OpenAPI expone `/api/v1/catalog/sync` con operationId `sincronizarCatalogo`.

T061 y T062 quedan desbloqueadas técnicamente porque ya existen Service y Controller, pero permanecen sin marcar: sus pruebas de integración/E2E completas pertenecen a esas tareas y no se implementaron en este alcance. T068 y posteriores continúan pendientes.

**Cierre validado: T066-T067 [x]. T061-T062 [ ]. T068 y posteriores [ ].**

## Validacion T061-T062 (2026-09-16)

- T061: `SincronizacionCatalogoIntegrationTest` usa PostgreSQL 16 mediante Testcontainers y el Service real, con `FootballDataAdapter` simulado. Verifica publicacion completa, exactamente 96 equipos con reparto 20/20/18/20/18, upsert por `external_id`, preservacion de UUID, reactivacion, inactivacion, primer snapshot sobre datos demo, rollback ante proveedor y lock advisory concurrente.
- T062: `CatalogoSyncE2ETest` usa MockMvc contra el contexto real de Spring, PostgreSQL/Testcontainers y adapter simulado. Verifica 200, JWT ausente/invalido 401, cuerpo no vacio 400, proveedor 503, error inesperado 500 y que GET `/api/v1/players` no invoca el adapter.
- Los fixtures externos son sintéticos; no se realizaron llamadas a football-data.org ni se usaron credenciales reales.
- No se modificaron tests históricos.

Comandos ejecutados desde backend:

    .\gradlew.bat test --tests '*SincronizacionCatalogoIntegrationTest' --tests '*CatalogoSyncE2ETest' --no-daemon --console=plain
    .\gradlew.bat test --no-daemon --console=plain
    .\gradlew.bat clean assemble --no-daemon --console=plain

- Tests focalizados T061-T062: **9 tests, 0 fallos, 0 errores y 0 omitidos**; BUILD SUCCESSFUL in 29s.
- Regresión completa: **394 tests, 0 fallos, 0 errores y 0 omitidos**; BUILD SUCCESSFUL in 1m 20s. Los 363 históricos permanecen aprobados.
- Empaquetado limpio: BUILD SUCCESSFUL in 10s.

T061 y T062 quedan cerradas con evidencia. T068 y posteriores continúan pendientes.

**Cierre validado: T061-T062 [x]. T068 y posteriores [ ].**

## Validacion T068-T069 y cierre US4 (2026-09-16)

### Alcance y dependencia

T068 es el gate de pruebas completo de US4: unitarias, adapter sin Internet, repositories, Service y E2E; exige verificar snapshot, rollback, 503, lock 409 y registrar evidencia. T069 depende de T068 y exige confirmar que los tests históricos permanecen intactos y ejecutar la regresión final de backend. Se ejecutaron en ese orden.

### T068

Se ejecutó desde `backend/`:

    .\gradlew.bat test --tests '*EquipoTest' --tests '*JugadorImportadoTest' --tests '*CatalogoSincronizadoTest' --tests '*FootballDataAdapterTest' --tests '*SincronizacionRepositoryIntegrationTest' --tests '*SincronizacionCatalogoServiceTest' --tests '*SincronizacionCatalogoIntegrationTest' --tests '*CatalogoControllerTest' --tests '*CatalogoSyncE2ETest' --no-daemon --console=plain

Resultado verificable: **31 tests, 0 fallos, 0 errores y 0 omitidos; BUILD SUCCESSFUL in 34s**. Las pruebas del proveedor usan fixtures loopback/mock; no hubo Internet ni credenciales reales.

La cobertura incluye las cinco ligas, 96 equipos con reparto 20/20/18/20/18, idempotencia/upsert, reactivación, ausentes, datos demo sin external_id, atomicidad, rollback de proveedor, lock advisory concurrente, 409, 503, 500, JWT 401, body 400 y ausencia de llamadas del GET local al adapter.

También se verificó runtime local con PostgreSQL 16.15 en `localhost:5433/devchester`, perfil `local` y Flyway V4. `/v3/api-docs` respondió HTTP 200, `/swagger-ui/index.html` respondió HTTP 200 y OpenAPI expuso `/api/v1/catalog/sync` con operationId `sincronizarCatalogo`. El proceso de validación fue detenido posteriormente.

### T069

Se ejecutó la regresión completa desde `backend/`:

    .\gradlew.bat test --no-daemon --console=plain

Resultado verificable: **394 tests, 0 fallos, 0 errores y 0 omitidos; BUILD SUCCESSFUL in 1m 20s**. Los **363 tests históricos** continúan pasando y no se modificó ningún test histórico.

Empaquetado final:

    .\gradlew.bat clean assemble --no-daemon --console=plain

Resultado: **BUILD SUCCESSFUL in 10s**.

No se requirieron cambios productivos ni refactors para T068-T069. US1-US3 mantienen su comportamiento validado. T068 y T069 quedan cerradas; T070 y posteriores continúan pendientes.

**Cierre US4 validado: T057-T069 [x]. T070 y posteriores [ ].**

## Corrección de posición nula del proveedor (2026-09-17)

- Football-Data puede devolver `position: null` para un jugador válido. El Adapter ahora conserva ese valor como `null`; las posiciones informadas continúan mapeándose a `Forward`, `Midfielder`, `Defender` o `Goalkeeper`.
- La fábrica de jugadores importados y `JugadorEntity` admiten posición nullable. La migración `V5__Allow_null_player_position.sql` elimina únicamente el `NOT NULL`; el `CHECK` existente sigue validando los valores no nulos y se preservan los datos anteriores.
- La publicación continúa siendo atómica: un error estructural del snapshot mantiene el rollback completo. La posición ausente ya no se considera un error estructural.
- Tests nuevos: `JugadorImportadoNullPositionTest` y `FootballDataAdapterNullPositionTest`; el segundo usa un servidor HTTP loopback con un snapshot sintético de 96 equipos.
- Tests focalizados: **2 tests, 0 fallos, 0 errores y 0 omitidos; BUILD SUCCESSFUL in 18s**.
- Regresión completa desde `backend/`: **394 tests, 0 fallos, 0 errores y 0 omitidos; BUILD SUCCESSFUL in 1m 23s**.
- `clean assemble`: **BUILD SUCCESSFUL in 11s**.
- No se realizaron llamadas a football-data.org ni se modificaron tests existentes.

## Replanteo de nombre completo de Jugador (2026-09-17)

- Se informó antes de adaptar los tests que `JugadorTest`, `JugadorImportadoTest`, `FootballDataAdapterTest`, `JugadorE2ETest`, `JugadorServiceIntegrationTest`, `SincronizacionCatalogoServiceTest`, `SincronizacionCatalogoIntegrationTest`, `JugadorRepositoryIntegrationTest` y `SincronizacionRepositoryIntegrationTest` dependían del contrato dividido anterior.
- La nueva migración V6 concatena los datos históricos con `trim(concat_ws(' ', first_name, last_name))`, conserva UUIDs y elimina las columnas antiguas tras el backfill.
- Football-Data conserva exactamente `player.name`; se verificaron `Kepa Arrizabalaga`, `Cristian Cásseres Jr.` y `Pierre Emile Højbjerg` sin separación ni transformación.
- La persistencia del sync encapsula los errores de acceso/constraint como indisponibilidad y devuelve HTTP 503; el HTTP 409 queda reservado para `SincronizacionEnCursoException`.
- Tests focalizados de nombres, posición nula y error de persistencia: **3 tests, 0 fallos, 0 errores y 0 omitidos; BUILD SUCCESSFUL in 15s**.
- Regresión completa posterior: **391 tests, 0 fallos, 0 errores y 0 omitidos; BUILD SUCCESSFUL in 1m 26s**.
- `clean assemble`: **BUILD SUCCESSFUL in 12s**.

## Exclusión de semilla ficticia del catálogo consultable (2026-09-17)

- No se modificaron las migraciones históricas V3–V6 ni se eliminaron físicamente registros.
- La migración `V7__Deactivate_demo_player_seed.sql` conserva las filas demo como historial técnico y las marca inactivas.
- `JugadorRepository.buscar()` y `contar()` ahora requieren jugador activo y equipo activo, manteniendo filtros y totales coherentes.
- Se adaptaron los fixtures de Repository/Service para declarar explícitamente sus equipos activos.
- Regresión completa: **391 tests, 0 fallos, 0 errores y 0 omitidos; BUILD SUCCESSFUL in 1m 24s**.
- `clean assemble`: **BUILD SUCCESSFUL in 10s**.

## Remediación de consistencia documental (2026-09-16)

- Se alinearon los estados vigentes de `spec.md`, `plan.md`, `tasks.md`, `contracts/openapi.json` y este documento: T001–T069 cerradas; T070–T088 pendientes.
- T068 explicita la cobertura de consulta local de jugadores activos con equipo activo, correspondiente a FR-031.
- Se aclaró que el actor de US4 es cualquier usuario existente con JWT válido, sin RBAC adicional en este alcance.
- Se distinguió el endpoint propio de sincronización de las cinco rutas externas del proveedor.
- Se documentó que las columnas legacy `league`/`team` se conservan en V4 para compatibilidad y que su eliminación queda fuera del alcance actual.
- No se modificó código ni tests; no se requirieron nuevas ejecuciones de Gradle para esta remediación documental.
