---
description: "Tareas de implementación de autenticación y catálogo de jugadores"
---

# Tareas: Autenticación y catálogo de jugadores

**Fecha**: 2026-09-05. **Rama**: `001-user-auth-player-catalog`.
**Entrada**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contrato OpenAPI](./contracts/openapi.json) y [quickstart.md](./quickstart.md).

## Formato y reglas

**Criterio operativo para T028, T033 y T037**: una solicitud es concurrente o competidora únicamente si intenta adquirir el `FOR UPDATE NOWAIT` del mismo usuario mientras otra transacción mantiene el lock. La ganadora continúa; las solicitudes que encuentran el lock ocupado reciben `409 Conflict` sin reintentos ni mutaciones. Una solicitud que adquiere el lock después del `commit` anterior puede rotar válidamente y recibir `200 OK`. El lock es específico por usuario y debe mantenerse como máximo una API Key activa.

- Cada tarea tiene checkbox, ID secuencial, etiqueta de historia cuando corresponde y rutas relativas a la raíz.
- `[P]` permite trabajar en archivos distintos únicamente cuando los prerrequisitos de su grupo estén completos. No habilita paralelismo entre historias dependientes.
- Las pruebas son obligatorias por la Constitución y por la corrección C1 ya autorizada. Escribir pruebas antes de implementar la historia, observar fallo esperado y ejecutarlas nuevamente al cerrar. Fallos de compilación por componentes aún ausentes no acreditan la validación funcional final.
- No modificar ni borrar tests existentes sin un «SÍ» explícito. Agregar archivos nuevos y soporte de configuración para preservar `src/test/java/com/devchesterunited/devchesterunitedv2/DevchesterUnitedV2ApplicationTests.java`.
- Mantener Java 21, JUnit 5, PostgreSQL real/Testcontainers, unitarias sin Spring, E2E MockMvc solo en controller, casos felices y borde y documentación/errores en español.
- Nota histórica de regeneración: esta versión reemplazó los IDs del borrador anterior, cuyo estado inicial era pendiente. El estado vigente se detalla en la sección final: T001–T069 están completadas y validadas.

## Fase 1: Preparación compartida

**Objetivo**: Adecuar el build existente al stack constitucional.

**Prueba independiente y control de cierre**: Wrapper y dependencias compatibles, raíz y test existentes preservados.

- [x] T001 Adecuar Spring Boot a 3.5.16 y Java 21; agregar Web, Validation, Security, OAuth2 Resource Server/JOSE, JPA, JDBC PostgreSQL, Flyway core/PostgreSQL, Springdoc 2.8.17, JUnit 5, Testcontainers JDBC/PostgreSQL, Security Test y AssertJ con BOM; configurar JaCoCo y useJUnitPlatform en `build.gradle`.
- [x] T002 Actualizar el wrapper completo a Gradle 8.14.4 conservando scripts y checksum de distribución; verificar --version con Java 21 en `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`.
- [x] T003 Configurar PostgreSQL por variables de entorno, Flyway y ddl-auto=validate; perfiles local/test con Swagger y deshabilitado en otros perfiles, sin secretos reales en `src/main/resources/application.properties`.
- [x] T004 Agregar configuración global de pruebas con jdbc:tc:postgresql:16:///devchester_test y secreto JWT exclusivo de test; mantener intacto el contextLoads existente en `src/test/resources/application.properties`.
- [x] T005 Registrar estado inicial, hashes de tests existentes y dependencias resueltas (Jupiter 5, no Framework 7); conservar clase de arranque/raíz actual y preparar evidencia del DoD en `specs/001-user-auth-player-catalog/validation.md`.

## Fase 2: Fundamentos bloqueantes

**Objetivo**: Completar soporte común antes de las historias.

**Prueba independiente y control de cierre**: Infraestructura de pruebas disponible y errores/configuración comunes definidos; el build base debe pasar antes de US1.

- [x] T006 Crear soporte Testcontainers para suites con contenedor PostgreSQL explícito, conexiones/transacciones independientes, fixtures y barreras de concurrencia; sin MockMvc en soporte de Service/Repository en `src/test/java/com/devchesterunited/devchesterunitedv2/configuration/PostgresTestSupport.java`.
- [x] T007 Crear DTOs comunes de éxito/error y manejador HTTP en español para 400/401/409/500/503, sin SQL/stack traces/secretos; rechazar JSON desconocido en `src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/ErrorResponseDTO.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/SuccessResponseDTO.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/controller/exceptionHandler/GlobalExceptionHandler.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/configuration/JacksonConfig.java`.
- [x] T008 Configurar Clock UTC inyectable y BCrypt coste 12 como infraestructura de Service, fuera del modelo puro en `src/main/java/com/devchesterunited/devchesterunitedv2/service/config/ServiceConfig.java`.
- [x] T009 Configurar base stateless de Spring Security: registro/login públicos, resto cerrado hasta integrar cadenas de cada historia; sin cookies ni sesión; errores previos al controller con esquema común en `src/main/java/com/devchesterunited/devchesterunitedv2/configuration/SecurityConfig.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/configuration/SecurityErrorHandler.java`.
- [x] T010 Configurar Swagger/OpenAPI y esquemas BearerAuth/ApiKeyAuth sin exigir ambos a una operación; ejecutar build base incluido contextLoads, registrar resultado y bloquear US1 ante fallos en `src/main/java/com/devchesterunited/devchesterunitedv2/configuration/OpenApiConfig.java`, `specs/001-user-auth-player-catalog/validation.md`.

## Fase 3: User Story 1 — Registro y login (P1)

**Objetivo**: Registrar usuarios y emitir JWT de una hora (FR-001–FR-004).

**Prueba independiente y control de cierre**: Registro 201 → login 200 con JWT; duplicados 409, entradas inválidas 400, credenciales incorrectas 401; dominio e integración PASSED sin depender de US2/US3.

- [x] T011 [P] [US1] Escribir primero pruebas de contrato MockMvc para POST /api/v1/auth/register y /api/v1/auth/login: respuestas/esquemas, 201/200/400/401/409, español, campos desconocidos y Cache-Control no-store en login; observar fallo funcional antes de implementar en `src/test/java/com/devchesterunited/devchesterunitedv2/controller/AuthE2ETest.java`.
- [x] T012 [P] [US1] Escribir primero unitarias puras de Usuario: construcción válida, identidad/hash vacíos y coherencia temporal con excepciones propias; sin Spring ni BD en `src/test/java/com/devchesterunited/devchesterunitedv2/modelo/UsuarioTest.java`.
- [x] T013 [P] [US1] Escribir primero integración de Service con PostgreSQL: registro/login, hash BCrypt, normalización, credenciales erróneas y duplicados concurrentes sin doble inserción en `src/test/java/com/devchesterunited/devchesterunitedv2/service/AutenticacionIntegrationTest.java`.
- [x] T014 [P] [US1] Escribir primero integración de Repository: persistencia, búsquedas/ausencia, unicidad de username/email y restricciones temporales de BD en `src/test/java/com/devchesterunited/devchesterunitedv2/persistence/UsuarioRepositoryIntegrationTest.java`.
- [x] T015 [US1] Implementar Usuario puro con nombres españoles e invariantes, sin dependencias de infraestructura; crear excepciones de dominio en `src/main/java/com/devchesterunited/devchesterunitedv2/modelo/Usuario.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/modelo/exception/UsuarioException.java`.
- [x] T016 [US1] Crear UsuarioEntity y migración V1 de usuarios: UUID, username/email normalizados únicos, password_hash y timestamps con restricciones del modelo de datos en `src/main/java/com/devchesterunited/devchesterunitedv2/persistence/dto/UsuarioEntity.java`, `src/main/resources/db/migration/V1__Create_usuarios_table.sql`.
- [x] T017 [US1] Crear UsuarioRepository de acceso a entidades sin lógica de dominio; consultas por identidad normalizada y UUID en `src/main/java/com/devchesterunited/devchesterunitedv2/persistence/UsuarioRepository.java`.
- [x] T018 [US1] Crear DTOs registro/login/respuesta con username ASCII 3–30 y email <=254, trim/minúsculas; contraseña registro 8–72 caracteres y <=72 bytes, mayúscula/minúscula/dígito, sin controles/espacios externos ni truncamiento; login valida su contrato propio en `src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/RegistroRequestDTO.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/LoginRequestDTO.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/UsuarioResponseDTO.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/LoginResponseDTO.java`.
- [x] T019 [US1] Implementar NimbusJwtEncoder/Decoder HS256: secreto Base64 externo >=32 bytes, iss devchester-united, aud devchester-api, sub usr_UUID, iat/exp=3600 s y Clock sin tolerancia; fallar configuración insegura y no consultar proveedor externo en `src/main/java/com/devchesterunited/devchesterunitedv2/configuration/JwtConfig.java`.
- [x] T020 [US1] Agregar pruebas de firma, algoritmo, emisor, audiencia, expiración y configuración de secreto JWT mediante Clock controlable; sin modificar pruebas existentes en `src/test/java/com/devchesterunited/devchesterunitedv2/configuration/JwtConfigTest.java`.
- [x] T021 [US1] Implementar interfaz y Service de registro/login: mapear entidad/modelo, comprobar recursos, hash antes de insertar, traducir carrera de unicidad a 409 y credenciales incorrectas a 401 genérico, emitir JWT en `src/main/java/com/devchesterunited/devchesterunitedv2/service/interfaces/AutenticacionService.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/AutenticacionServiceImpl.java`.
- [x] T022 [US1] Implementar AuthController con rutas completas /api/v1/auth/register y /api/v1/auth/login, delegación exclusiva a Service y respuestas conforme OpenAPI; no-store en login en `src/main/java/com/devchesterunited/devchesterunitedv2/controller/AuthController.java`.
- [x] T023 [US1] Ejecutar unitarias, integración y E2E de US1 y suite existente; verificar resultados PASSED, ausencia de credenciales en logs/respuestas y registrar evidencia antes de US2 en `specs/001-user-auth-player-catalog/validation.md`.

## Fase 4: User Story 2 — Generación y rotación de API Key (P1)

**Objetivo**: Emitir claves sin vencimiento, proteger generación con JWT y asegurar rotación atómica (FR-005–FR-008, FR-012).

**Prueba independiente y control de cierre**: Tras US1, generar y rotar con 200, JWT inválido 401, competencia transaccional 409; una activa y rollback seguro; todas sus pruebas PASSED.

- [x] T024 [P] [US2] Escribir primero contrato MockMvc POST /api/v1/auth/apikey: 200, no-store, secreto una vez, cuerpo no vacío 400, JWT ausente/alterado/expirado o usuario inexistente 401, 409 y esquema de errores; solo API Key no autoriza (SC-004) en `src/test/java/com/devchesterunited/devchesterunitedv2/controller/ApiKeyE2ETest.java`.
- [x] T025 [P] [US2] Escribir primero unitarias puras de APIKey: estado activo/revocado, coherencia de fechas y vigencia sin expiración aunque avance el tiempo (FR-012) en `src/test/java/com/devchesterunited/devchesterunitedv2/modelo/ApiKeyTest.java`.
- [x] T026 [P] [US2] Escribir primero integración Service: generación/rotación, hash y prefijo persistidos sin secreto, clave anterior revocada y fallo de inserción con rollback que conserva la anterior en `src/test/java/com/devchesterunited/devchesterunitedv2/service/ApiKeyIntegrationTest.java`.
- [x] T027 [P] [US2] Escribir primero integración Repository: FK, hash único, CHECK activo/revocado y restricción parcial de una activa por usuario en `src/test/java/com/devchesterunited/devchesterunitedv2/persistence/ApiKeyRepositoryIntegrationTest.java`.
- [x] T028 [P] [US2] Escribir prueba concurrente PostgreSQL con barreras: ganador mantiene lock, competidoras fallan sin mutación, luego un commit; verificar usuarios distintos, primera clave y rotación posterior válida; sin sleeps ni reintentos en `src/test/java/com/devchesterunited/devchesterunitedv2/service/ApiKeyConcurrencyIntegrationTest.java`.
- [x] T029 [US2] Implementar modelo APIKey puro y excepciones, transición irreversible activa→revocada y ausencia de expires_at en `src/main/java/com/devchesterunited/devchesterunitedv2/modelo/APIKey.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/modelo/exception/ApiKeyException.java`.
- [x] T030 [US2] Crear APIKeyEntity y migración V2 con UUID/FK, hash SHA-256, prefijo de 14, timestamps, CHECK e índice único parcial user_id WHERE is_active=true en `src/main/java/com/devchesterunited/devchesterunitedv2/persistence/dto/APIKeyEntity.java`, `src/main/resources/db/migration/V2__Create_api_keys_table.sql`.
- [x] T031 [US2] Implementar APIKeyRepository para hash/estado y operación NOWAIT sobre fila Usuario; mantener acceso de datos sin invocar lógica de dominio en `src/main/java/com/devchesterunited/devchesterunitedv2/persistence/APIKeyRepository.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/persistence/UsuarioRepository.java`.
- [x] T032 [US2] Crear respuesta de clave con token dev_key_ + 43 caracteres Base64URL, prefix/is_active/created_at y sin expiración; evitar incluir secretos en toString en `src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/ApiKeyResponseDTO.java`.
- [x] T033 [US2] Extender Service con generación de 32 bytes aleatorios, SHA-256 y rotación READ COMMITTED bajo FOR UPDATE NOWAIT; revocar/insertar atómicamente y devolver solo tras commit; mapear 55P03 a 409 fuera de transacción fallida sin reintentar en `src/main/java/com/devchesterunited/devchesterunitedv2/service/interfaces/AutenticacionService.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/AutenticacionServiceImpl.java`.
- [x] T034 [US2] Implementar autenticación por hash/estado contra BD primaria al inicio, sin caché entre solicitudes; Service confirma usuario existente, clave activa o 401 y errores de BD 503 en `src/main/java/com/devchesterunited/devchesterunitedv2/service/interfaces/ApiKeyService.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/ApiKeyServiceImpl.java`.
- [x] T035 [US2] Implementar filtro X-API-KEY que delega en Service y delimitar cadenas: JWT exclusivo para generar, API Key exclusiva para GET /api/v1/players, sin aceptar credencial de tipo equivocado; traducir errores previos al controller en `src/main/java/com/devchesterunited/devchesterunitedv2/configuration/ApiKeyAuthenticationFilter.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/configuration/SecurityConfig.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/configuration/SecurityErrorHandler.java`.
- [x] T036 [US2] Agregar POST /api/v1/auth/apikey a AuthController: sin cuerpo, JWT válido y existencia del usuario, no-store, 200/400/401/409 y errores comunes en español en `src/main/java/com/devchesterunited/devchesterunitedv2/controller/AuthController.java`.
- [x] T037 [US2] Ejecutar pruebas US1/US2, comprobar 409 HTTP con bloqueo real controlado desde pruebas controller, una clave activa y ausencia de secretos persistidos/logueados; registrar evidencia en `specs/001-user-auth-player-catalog/validation.md`.

## Fase 5: User Story 3 — Catálogo de jugadores (P2)

**Objetivo**: Consultar jugadores con API Key, filtros parciales y paginación (FR-009–FR-011).

**Prueba independiente y control de cierre**: Con US1/US2 disponibles, Madrid/madrid/MADRID devuelve jugadores de ambos equipos; AND y totales exactos, claves inválidas 401 y revocación en curso conforme a la spec.

- [x] T038 [P] [US3] Escribir primero contrato MockMvc GET /api/v1/players: éxito y esquema, API Key ausente/inválida/revocada o solo JWT 401; 400 por filtros vacíos, controles, parámetros repetidos/desconocidos, page/limit inválidos y overflow; página vacía 200 (SC-004) en `src/test/java/com/devchesterunited/devchesterunitedv2/controller/JugadorE2ETest.java`.
- [x] T039 [P] [US3] Escribir primero unitarias puras Jugador con campos obligatorios, posición válida y dorsal 1–99, casos felices/borde y excepciones de dominio en `src/test/java/com/devchesterunited/devchesterunitedv2/modelo/JugadorTest.java`.
- [x] T040 [P] [US3] Escribir primero integración Service para filtros parciales case-insensitive, AND, sin filtros, vacíos, orden UUID y paginación; comparar contenido/totales con fixtures independientes (SC-005) en `src/test/java/com/devchesterunited/devchesterunitedv2/service/JugadorServiceIntegrationTest.java`.
- [x] T041 [P] [US3] Escribir primero integración Repository con %/_/! y comillas literales, tildes significativas, parámetros vinculados y conteo antes de paginar; no concatenar SQL de entrada en `src/test/java/com/devchesterunited/devchesterunitedv2/persistence/JugadorRepositoryIntegrationTest.java`.
- [x] T042 [P] [US3] Escribir prueba MockMvc con sincronización determinista: autorizar consulta y pausarla, rotar clave, permitir fin exitoso y exigir 401 a nuevas validaciones; BD inaccesible debe producir 503 sin acceso (FR-009, SC-004) en `src/test/java/com/devchesterunited/devchesterunitedv2/controller/RevocacionEnCursoE2ETest.java`.
- [x] T043 [US3] Implementar Jugador puro con invariantes y excepciones propias según data-model.md en `src/main/java/com/devchesterunited/devchesterunitedv2/modelo/Jugador.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/modelo/exception/JugadorException.java`.
- [x] T044 [US3] Crear JugadorEntity y migración V3 con restricciones y semilla mínima que incluya Real Madrid y Atlético de Madrid en LaLiga, todas las posiciones y datos suficientes para paginación en `src/main/java/com/devchesterunited/devchesterunitedv2/persistence/dto/JugadorEntity.java`, `src/main/resources/db/migration/V3__Create_jugadores_table.sql`.
- [x] T045 [US3] Implementar Repository con subcadenas literales ILIKE, escape de !/%/_, AND y parámetros vinculados, orden UUID ascendente y conteo consistente; no llamar al modelo en `src/main/java/com/devchesterunited/devchesterunitedv2/persistence/JugadorRepository.java`.
- [x] T046 [US3] Crear DTOs de filtros/respuesta/paginación con trimming, límites league/team=100 y position=30, tildes conservadas, validación de parámetros repetidos/desconocidos, page desde 1 y limit 1–100 (20 default), offset verificado en `src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/FiltroJugadoresDTO.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/JugadorResponseDTO.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/PaginacionDTO.java`.
- [x] T047 [US3] Implementar interfaz y JugadorService: consultas, mapeo y metadatos propios, total_pages=0 sin coincidencias y lista vacía fuera de rango conservando totales; autorización ya realizada no se repite en curso en `src/main/java/com/devchesterunited/devchesterunitedv2/service/interfaces/JugadorService.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/JugadorServiceImpl.java`.
- [x] T048 [US3] Implementar GET /api/v1/players en JugadorController delegando solo a Service, contrato OpenAPI y errores en español en `src/main/java/com/devchesterunited/devchesterunitedv2/controller/JugadorController.java`.
- [x] T049 [US3] Ejecutar suites de las tres historias incluido test existente; verificar Madrid en ambos equipos, contenido exacto, seguridad y autorización en curso; registrar PASSED antes de validación final en `specs/001-user-auth-player-catalog/validation.md`.

## Fase 6: Validación transversal y entrega

**Objetivo**: Medir rendimiento y completar documentación, CI y calidad.

**Prueba independiente y control de cierre**: Todas las historias PASSED más mediciones, arranque, Postman, CI y SonarCloud con evidencia real.

- [x] T050 Revisar separación de capas, DTOs/errores españoles y ausencia de secretos en logs; corregir producción si corresponde y repetir suites afectadas antes de medir en `src/main/java/com/devchesterunited/devchesterunitedv2/controller/AuthController.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/controller/JugadorController.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/AutenticacionServiceImpl.java`, `specs/001-user-auth-player-catalog/validation.md`.
---
## Continuación pendiente después de T050

T001–T050 son historial completado y validado según specs/001-user-auth-player-catalog/validation.md. No se vuelven a generar tareas de US1, US2 ni US3. Las tareas siguientes son nuevas, permanecen pendientes y usan la estructura futura backend/ y frontend/.

## Fase 7: Reorganización estructural del monorepo

**Objetivo**: mover el Gradle existente sin recrearlo, preparar el frontend y demostrar que el estado validado no se degrada.

- [x] T051 Trasladar el proyecto Gradle existente a backend/ (build.gradle, settings.gradle, wrappers, gradle/, src/) preservando historial y configuración; no recrear el proyecto ni cambiar el paquete com.devchesterunited.devchesterunitedv2.
- [x] T052 [P] Preparar frontend/package.json, frontend/package-lock.json, frontend/vite.config.js y directorios frontend/src/, frontend/tests/, frontend/e2e/ para React + JavaScript; no crear TypeScript, tsconfig, .ts ni .tsx.
- [x] T053 [P] Verificar que .specify/, specs/, .github/, performance/ y postman/ permanezcan en la raíz y documentar la estructura en specs/001-user-auth-player-catalog/plan.md.
- [x] T054 Validar compilación y empaquetado desde backend/ con el wrapper existente, ajustando solo rutas relativas necesarias en backend/build.gradle, backend/settings.gradle y recursos de configuración.
- [x] T055 Ejecutar la regresión completa histórica desde backend/ y confirmar los 363 tests previos sin modificar tests existentes en backend/src/test/.
- [x] T056 Registrar la reorganización, hashes de tests y resultado de regresión en specs/001-user-auth-player-catalog/validation.md; si una adaptación de test fuera imprescindible, detenerse y solicitar «SÍ» explícito antes de editarlo.

## Fase 8: User Story 4 — Sincronización externa del catálogo (P1)

**Objetivo**: sincronizar cinco ligas mediante Adapter y publicar un snapshot local atómico (FR-013–FR-022, FR-030–FR-035, SC-006–SC-009).

**Prueba independiente**: fixtures HTTP locales representan las cinco respuestas; un snapshot válido publica equipos/jugadores, un fallo devuelve 503 y conserva el snapshot anterior, y una ejecución concurrente devuelve 409.

- [x] T057 Obtener autorización constitucional «SÍ» explícita antes de modificar cualquier test existente que dependa del esquema V3; registrar el diff propuesto y la decisión en specs/001-user-auth-player-catalog/validation.md.
- [x] T058 [P] [US4] Crear unitarias puras para Equipo, jugador importado y CatalogoSincronizado, incluyendo identidad externa, estados, pertenencia, duplicados, dorsal nullable y temporada vigente, en backend/src/test/java/com/devchesterunited/devchesterunitedv2/modelo/.
- [x] T059 [P] [US4] Crear pruebas del Adapter con servidor HTTP local para cinco ligas, respuestas inválidas, 429/5xx, timeout, cuota y mapeo de DTO externo en backend/src/test/java/com/devchesterunited/devchesterunitedv2/adapter/FootballDataAdapterTest.java.
- [x] T060 [P] [US4] Crear integración de repositories para V4, IDs externos únicos, asociación equipo-jugador, inactivación/reactivación, dorsal null y preservación de UUID V3 en backend/src/test/java/com/devchesterunited/devchesterunitedv2/persistence/SincronizacionRepositoryIntegrationTest.java.
- [x] T061 [P] [US4] Crear integración de Service con PostgreSQL/Testcontainers para advisory lock global, competidor 409, publicación atómica, rollback y snapshot anterior en backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/SincronizacionCatalogoIntegrationTest.java.
- [x] T062 [P] [US4] Crear E2E MockMvc del endpoint de sincronización para JWT válido, cuerpo/query rechazados, 200, 400, 409, 503 y 500 en backend/src/test/java/com/devchesterunited/devchesterunitedv2/controller/CatalogoSyncE2ETest.java.
- [x] T063 [US4] Implementar modelos puros Equipo, CatalogoSincronizado, estados y reglas de snapshot sin dependencias de Spring/JPA en backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/.
- [x] T064 [US4] Implementar entidades/repositories y migración V4 para equipos, team_id, IDs externos, actividad y dorsal nullable, conservando UUID V3, en backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/ y backend/src/main/resources/db/migration/V4__Normalize_catalog_sync.sql.
- [x] T065 [US4] Implementar DTOs Football-Data en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/dto/ y FootballDataAdapter con RestClient, temporada vigente, cinco rutas, timeouts y traducción de errores en backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/impl/.
- [x] T066 [US4] Implementar SincronizacionCatalogoService como único orquestador Adapter-Modelo-Persistence: lock global PostgreSQL, consultas secuenciales, validación 96 equipos, inactivación/reactivación, reintento manual idempotente, commit único y rollback en backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/.
- [x] T067 [US4] Implementar POST /api/v1/catalog/sync en backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/CatalogoController.java, con JWT, errores 409/503 y el contrato del endpoint propio junto con las cinco rutas externas en specs/001-user-auth-player-catalog/contracts/openapi.json.
- [x] T068 [US4] Ejecutar unitarias, Repository, Service, Adapter y E2E de US4 sin Internet ni credenciales reales; verificar rollback, 503, lock 409, la consulta local de jugadores activos con equipo activo y registrar resultados en specs/001-user-auth-player-catalog/validation.md.
- [x] T069 [US4] Confirmar que los tests históricos permanecen intactos y ejecutar la regresión backend completa después de US4; registrar cualquier cambio autorizado y su evidencia en specs/001-user-auth-player-catalog/validation.md.

## Fase 9: User Story 5 — Frontend web MVP (P2)

**Objetivo**: SPA React + JavaScript que consuma exclusivamente la API documentada (FR-023–FR-029, SC-010).

**Prueba independiente**: Playwright ejecuta registro → login → API Key → catálogo/paginación contra backend local; recarga, logout y pestañas respetan el aislamiento de sesión.

- [ ] T070 [P] [US5] Crear frontend/src/api/http.js con fetch, rutas relativas, cabeceras por operación, manejo de 400/401/409/503 y sin secretos externos.
- [ ] T071 [P] [US5] Crear almacenamiento/contexto de JWT y API Key por pestaña en frontend/src/session/storage.js y frontend/src/session/SessionContext.jsx usando sessionStorage, expiración y logout local.
- [ ] T072 [P] [US5] Crear vistas/componentes .jsx de registro, login, credenciales/API Key y catálogo en frontend/src/, con filtros, paginación, dorsal null, accesibilidad y estados de error.
- [ ] T073 [US5] Configurar rutas /registro, /login, /credenciales, /catalogo, fallback SPA y proxy Vite en frontend/src/App.jsx, frontend/src/main.jsx y frontend/vite.config.js.
- [ ] T074 [P] [US5] Crear pruebas Vitest/Testing Library de formularios, cliente fetch, almacenamiento por pestaña, expiración JWT, rotación, logout y errores en frontend/tests/.
- [ ] T075 [P] [US5] Crear compose/fixture de backend local y pruebas Playwright del flujo SC-010, recarga, pestaña independiente, secreto de una sola visualización, filtros, paginación y bloqueo de hosts externos en frontend/e2e/.
- [ ] T076 [US5] Verificar que el frontend contiene exclusivamente React + JavaScript (.js/.jsx), sin TypeScript, tsconfig, .ts ni .tsx, y que no expone secretos en frontend/package.json ni frontend/src/.
- [ ] T077 [US5] Ejecutar npm install, Vitest, build Vite, preview y Playwright desde frontend/; registrar SC-010 y resultados en specs/001-user-auth-player-catalog/validation.md.
- [ ] T078 [US5] Ejecutar regresión backend y frontend conjuntamente, sin modificar tests históricos, y documentar integración en specs/001-user-auth-player-catalog/validation.md.

## Fase 10: Gates finales actualizados

**Objetivo**: completar gates todavía no ejecutados para el alcance ampliado; ningún gate se considera aprobado por configuración solamente.

- [ ] T079 [P] Preparar fixture/entorno k6 de 10.000 jugadores y 1.000 usuarios en performance/seed.sql, performance/compose.yml y performance/README.md.
- [ ] T080 [P] Ejecutar escenarios de rendimiento registro/login, API Key y catálogo para SC-001–SC-003 con tres repeticiones, restauración y recursos reproducibles en performance/registro-login.js, performance/api-key.js, performance/catalogo.js y performance/results/.
- [ ] T081 Comparar Swagger UI y /v3/api-docs con los cinco endpoints, esquemas, autenticación y errores; registrar discrepancias y correcciones en specs/001-user-auth-player-catalog/contracts/openapi.json y validation.md.
- [ ] T082 [P] Actualizar colección Postman con register, login, API Key, sync y players, variables y ejemplos sin secretos en postman/DevchesterUnited.postman_collection.json.
- [ ] T083 Configurar GitHub Actions con Gradle usando backend/ como working directory y npm/Vite usando frontend/, ejecutando suites backend, Vitest, build y Playwright en .github/workflows/ci.yml.
- [ ] T084 Configurar y ejecutar SonarCloud/Quality Gate para el monorepo, incluyendo backend y frontend, en .github/workflows/ci.yml, backend/build.gradle y scripts/verify-sonar-gate.ps1.
- [ ] T085 Ejecutar quickstart completo desde backend/ y frontend/, con migraciones, arranque, flujo HTTP y frontend, y actualizar specs/001-user-auth-player-catalog/quickstart.md solo si cambian comandos reales.
- [ ] T086 Verificar aceptación de integración externa: cuenta/proveedor habilitado para Squads, temporada vigente y snapshot utilizable; documentar evidencia o bloqueo externo sin afirmar éxito en specs/001-user-auth-player-catalog/validation.md.
- [ ] T087 Ejecutar el flujo final de Postman y SC-010 contra backend local, verificando que el frontend no llama al proveedor externo y que los secretos no aparecen en logs, bundles ni capturas.
- [ ] T088 Cerrar Definition of Done solo con pruebas por capa, build, arranque, performance, OpenAPI, Postman, CI, SonarCloud y aceptación externa evidenciados para el commit final en specs/001-user-auth-player-catalog/validation.md.

## Dependencias y orden pendiente

T050 completada → Monorepo T051–T056 → US4 T057–T069 → US5 T070–T078 → Gates finales T079–T088.

## Estado y cobertura

- T001–T050: [x] historial completado, respaldado por validation.md; US1, US2 y US3 no son trabajo pendiente.
- T051–T069: [x] reorganización y US4 completadas, respaldadas por validation.md.
- T070–T078: [ ] US5 pendiente.
- T079–T088: [ ] gates finales pendientes.
- La suite histórica de 363 tests se conserva como regresión; no se afirma que las pruebas nuevas, CI, SonarCloud, performance, frontend o aceptación externa estén ejecutadas.
