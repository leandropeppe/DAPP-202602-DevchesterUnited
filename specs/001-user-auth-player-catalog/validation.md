# Evidencia de validación — preparación compartida

Fecha: 2026-09-06. Alcance: T005, estado posterior a T001–T004 y anterior a T006. Esta evidencia no declara terminado el DoD de la entrega funcional.

## Estado inicial registrado

**Nota de estado actual**: Las notas históricas de esta sección reflejan cortes anteriores. El estado vigente es T001–T049 completadas y validadas, con T050–T061 pendientes. Los cierres posteriores de T023, T037 y T049 son la evidencia vigente para las historias de usuario.

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
