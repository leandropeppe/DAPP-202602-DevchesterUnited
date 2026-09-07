---
description: "Tareas de implementación de autenticación y catálogo de jugadores"
---

# Tareas: Autenticación y catálogo de jugadores

**Fecha**: 2026-09-05. **Rama**: `001-user-auth-player-catalog`.
**Entrada**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contrato OpenAPI](./contracts/openapi.json) y [quickstart.md](./quickstart.md).

## Formato y reglas

- Cada tarea tiene checkbox, ID secuencial, etiqueta de historia cuando corresponde y rutas relativas a la raíz.
- `[P]` permite trabajar en archivos distintos únicamente cuando los prerrequisitos de su grupo estén completos. No habilita paralelismo entre historias dependientes.
- Las pruebas son obligatorias por la Constitución y por la corrección C1 ya autorizada. Escribir pruebas antes de implementar la historia, observar fallo esperado y ejecutarlas nuevamente al cerrar. Fallos de compilación por componentes aún ausentes no acreditan la validación funcional final.
- No modificar ni borrar tests existentes sin un «SÍ» explícito. Agregar archivos nuevos y soporte de configuración para preservar `src/test/java/com/devchesterunited/devchesterunitedv2/DevchesterUnitedV2ApplicationTests.java`.
- Mantener Java 21, JUnit 5, PostgreSQL real/Testcontainers, unitarias sin Spring, E2E MockMvc solo en controller, casos felices y borde y documentación/errores en español.
- Esta regeneración reemplaza los IDs del borrador anterior; todos estaban pendientes. No representa ejecución de implementación ni de gates.

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

- [ ] T038 [P] [US3] Escribir primero contrato MockMvc GET /api/v1/players: éxito y esquema, API Key ausente/inválida/revocada o solo JWT 401; 400 por filtros vacíos, controles, parámetros repetidos/desconocidos, page/limit inválidos y overflow; página vacía 200 (SC-004) en `src/test/java/com/devchesterunited/devchesterunitedv2/controller/JugadorE2ETest.java`.
- [ ] T039 [P] [US3] Escribir primero unitarias puras Jugador con campos obligatorios, posición válida y dorsal 1–99, casos felices/borde y excepciones de dominio en `src/test/java/com/devchesterunited/devchesterunitedv2/modelo/JugadorTest.java`.
- [ ] T040 [P] [US3] Escribir primero integración Service para filtros parciales case-insensitive, AND, sin filtros, vacíos, orden UUID y paginación; comparar contenido/totales con fixtures independientes (SC-005) en `src/test/java/com/devchesterunited/devchesterunitedv2/service/JugadorIntegrationTest.java`.
- [ ] T041 [P] [US3] Escribir primero integración Repository con %/_/! y comillas literales, tildes significativas, parámetros vinculados y conteo antes de paginar; no concatenar SQL de entrada en `src/test/java/com/devchesterunited/devchesterunitedv2/persistence/JugadorRepositoryIntegrationTest.java`.
- [ ] T042 [P] [US3] Escribir prueba MockMvc con sincronización determinista: autorizar consulta y pausarla, rotar clave, permitir fin exitoso y exigir 401 a nuevas validaciones; BD inaccesible debe producir 503 sin acceso (FR-009, SC-004) en `src/test/java/com/devchesterunited/devchesterunitedv2/controller/RevocacionEnCursoE2ETest.java`.
- [ ] T043 [US3] Implementar Jugador puro con invariantes y excepciones propias según data-model.md en `src/main/java/com/devchesterunited/devchesterunitedv2/modelo/Jugador.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/modelo/exception/JugadorException.java`.
- [ ] T044 [US3] Crear JugadorEntity y migración V3 con restricciones y semilla mínima que incluya Real Madrid y Atlético de Madrid en LaLiga, todas las posiciones y datos suficientes para paginación en `src/main/java/com/devchesterunited/devchesterunitedv2/persistence/dto/JugadorEntity.java`, `src/main/resources/db/migration/V3__Create_jugadores_table.sql`.
- [ ] T045 [US3] Implementar Repository con subcadenas literales ILIKE, escape de !/%/_, AND y parámetros vinculados, orden UUID ascendente y conteo consistente; no llamar al modelo en `src/main/java/com/devchesterunited/devchesterunitedv2/persistence/JugadorRepository.java`.
- [ ] T046 [US3] Crear DTOs de filtros/respuesta/paginación con trimming, límites league/team=100 y position=30, tildes conservadas, validación de parámetros repetidos/desconocidos, page desde 1 y limit 1–100 (20 default), offset verificado en `src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/FiltroJugadoresDTO.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/JugadorResponseDTO.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/PaginacionDTO.java`.
- [ ] T047 [US3] Implementar interfaz y JugadorService: consultas, mapeo y metadatos propios, total_pages=0 sin coincidencias y lista vacía fuera de rango conservando totales; autorización ya realizada no se repite en curso en `src/main/java/com/devchesterunited/devchesterunitedv2/service/interfaces/JugadorService.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/JugadorServiceImpl.java`.
- [ ] T048 [US3] Implementar GET /api/v1/players en JugadorController delegando solo a Service, contrato OpenAPI y errores en español en `src/main/java/com/devchesterunited/devchesterunitedv2/controller/JugadorController.java`.
- [ ] T049 [US3] Ejecutar suites de las tres historias incluido test existente; verificar Madrid en ambos equipos, contenido exacto, seguridad y autorización en curso; registrar PASSED antes de validación final en `specs/001-user-auth-player-catalog/validation.md`.

## Fase 6: Validación transversal y entrega

**Objetivo**: Medir rendimiento y completar documentación, CI y calidad.

**Prueba independiente y control de cierre**: Todas las historias PASSED más mediciones, arranque, Postman, CI y SonarCloud con evidencia real.

- [ ] T050 Revisar separación de capas, DTOs/errores españoles y ausencia de secretos en logs; corregir producción si corresponde y repetir suites afectadas antes de medir en `src/main/java/com/devchesterunited/devchesterunitedv2/controller/AuthController.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/controller/JugadorController.java`, `src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/AutenticacionServiceImpl.java`, `specs/001-user-auth-player-catalog/validation.md`.
- [ ] T051 Preparar fixture y entorno descartable de rendimiento: 10.000 jugadores (20 ligas×20 equipos×25), cuatro posiciones y 1.000 usuarios, Madrid en ambos equipos, checksum y recursos Linux/app/BD/generador según plan; restaurar solo esta BD en `performance/seed.sql`, `performance/compose.yml`, `performance/README.md`.
- [ ] T052 [P] Implementar escenario k6 de registro+login: 1 VU, identificadores únicos, 60 s de calentamiento excluidos y 5 min medidos, Trend conjunto HTTP completo p95<2000 ms, cero errores/checks 100 % (SC-001) en `performance/registro-login.js`.
- [ ] T053 [P] Implementar escenario k6 API Key: 1 VU y rotaciones secuenciales con setup/login fuera de medición, calentamiento y duración del plan, Trend HTTP completo p95<500 ms, cero errores/checks 100 % (SC-002) en `performance/api-key.js`.
- [ ] T054 [P] Implementar escenario k6 catálogo: 50 VU constantes sin pausas, mezcla 20 % de cada tipo definido en plan, page=1/limit=20, calentamiento excluido y Trend HTTP completo p95<300 ms, cero errores/checks 100 % (SC-003) en `performance/catalogo.js`.
- [ ] T055 Ejecutar tres repeticiones por escenario con restauración del fixture, conservar resúmenes/recursos/commit/checksum y comprobar cada umbral; si falla corregir causa y repetir mediciones afectadas, sin reducir carga o relajar metas en `specs/001-user-auth-player-catalog/validation.md`, `performance/results/README.md`.
- [ ] T056 Comparar Swagger UI y /v3/api-docs contra los cuatro endpoints del contrato: rutas, esquemas, parámetros, seguridad, errores y perfiles; corregir anotaciones/configuración, no debilitar contrato para ocultar discrepancias en `specs/001-user-auth-player-catalog/contracts/openapi.json`, `src/main/java/com/devchesterunited/devchesterunitedv2/configuration/OpenApiConfig.java`, `specs/001-user-auth-player-catalog/validation.md`.
- [ ] T057 Crear o actualizar colección Postman con cuatro endpoints, variables baseUrl/JWT/API Key, ejemplos felices y errores en español; importar y ejecutar flujo sin guardar secretos reales en `postman/DevchesterUnited.postman_collection.json`.
- [ ] T058 Configurar GitHub Actions para Java 21, wrapper, Docker/Testcontainers, build y todas las pruebas sin omisiones, informe JaCoCo y artefactos; exigir Build SUCCESS antes de integrar en principal en `.github/workflows/ci.yml`, `build.gradle`.
- [ ] T059 Configurar vinculación/análisis SonarCloud con secretos de CI y esperar Quality Gate; agregar comprobación explícita de máximo 10 issues abiertos, cero vulnerabilidades y cero smells críticos sobre el análisis del commit en `.github/workflows/ci.yml`, `build.gradle`, `scripts/verify-sonar-gate.ps1`.
- [ ] T060 Ejecutar quickstart completo con configuración local: clean build, arranque, migraciones, flujo HTTP y Postman; registrar comandos/resultados, hashes de tests preservados y actualizar instrucciones solo si cambiaron comandos reales en `specs/001-user-auth-player-catalog/quickstart.md`, `specs/001-user-auth-player-catalog/validation.md`.
- [ ] T061 Cerrar DoD solo con pruebas por capa PASSED, build/arranque, Postman, mediciones y URLs de GitHub Actions/SonarCloud exitosos del commit final; controles pendientes o sin acceso siguen abiertos, no declarar entrega completa en `specs/001-user-auth-player-catalog/validation.md`.

## Dependencias y orden de ejecución

```text
Preparación (T001–T005)
  → Fundamentos (T006–T010)
    → US1 (T011–T023)
      → US2 (T024–T037)
        → US3 (T038–T049)
          → Validación y entrega (T050–T061)
```

- Orden secuencial por defecto. US2 requiere usuario/JWT de US1; US3 requiere autenticación API Key de US2. Cada cierre vuelve a ejecutar las historias anteriores.
- Pruebas US1 T011–T014 pueden escribirse juntas después de T010; US2 T024–T028 después de T023; US3 T038–T042 después de T037. Se ejecutan contra implementación completa antes del cierre de su fase.
- Modelos preceden a Services; entidades/migraciones preceden a Repository; DTO/Service preceden a controllers. Los tests escritos primero no deben marcar la historia terminada mientras fallen.
- T033 extiende archivos creados en US1; T035 completa SecurityConfig y depende de T034. No editar estos archivos concurrentemente.
- T050 debe terminar antes de medir; T051 prepara datos/entorno. T052–T054 pueden implementarse en paralelo después de T051. T055 depende de los tres scripts y de las suites PASSED.
- T058 precede a T059 porque comparten build/workflow. T060 usa la colección T057 y se realiza después de toda corrección de código. T061 depende de T050–T060 y del resultado externo para el commit final. Cambios posteriores invalidan la evidencia afectada.

## Ejemplos de ejecución paralela por historia

| Historia | Grupo independiente | Prerrequisito | Integración final |
|----------|---------------------|---------------|-------------------|
| US1 | T011 contrato, T012 dominio, T013 Service, T014 Repository | T010 completo | T023 |
| US2 | T024 contrato, T025 dominio, T026 Service, T027 Repository, T028 concurrencia | T023 completo | T037 |
| US3 | T038 contrato, T039 dominio, T040 Service, T041 Repository, T042 revocación en curso | T037 completo | T049 |

Las tareas de escritura de pruebas trabajan sobre archivos diferentes; comparten el contrato y no deben modificar los componentes productivos desde ese grupo. No lanzar suites que comparten fixtures mutables en paralelo sin aislamiento de BD.

## Cobertura de requisitos

| Requisito | Tareas principales |
|-----------|--------------------|
| FR-001 | T011–T018, T021–T023 |
| FR-002 | T011, T018, T022 |
| FR-003 | T013, T008, T021 |
| FR-004 | T019–T023 |
| FR-005 | T024, T019–T020, T035–T036 |
| FR-006 | T026, T032–T033 |
| FR-007 | T026–T027, T030, T033–T034 |
| FR-008 | T025–T031, T033, T037 |
| FR-009 | T034–T035, T038, T042, T048–T049 |
| FR-010 | T038, T040–T041, T045–T048 |
| FR-011 | T038, T040, T045–T048 |
| FR-012 | T025–T026, T029–T030, T033 |
| SC-001 | T051–T052, T055 |
| SC-002 | T051, T053, T055 |
| SC-003 | T051, T054–T055 |
| SC-004 | T020, T024, T035, T038, T042, T049 |
| SC-005 | T040–T041, T045–T049 |

## Estrategia de implementación

1. Completar preparación/fundamentos y US1 como primer incremento verificable: registro y login.
2. Completar US2 para el MVP de credenciales indicado en el plan: usuario, JWT y API Key rotatoria.
3. Completar US3 para cubrir toda la funcionalidad de la Entrega 1.
4. Ejecutar validación transversal y cerrar únicamente con evidencia real de todos los controles; un MVP funcional no equivale al DoD completo.
