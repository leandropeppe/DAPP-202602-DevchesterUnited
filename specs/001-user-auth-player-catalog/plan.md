# Plan de implementación: Autenticación, catálogo sincronizado y frontend web

**Rama**: `001-user-auth-player-catalog` | **Fecha**: 2026-09-15 | **Spec**: [spec.md](./spec.md)

## Resumen

**Estado documental vigente (2026-10-03)**: T001–T079, T081–T086, T087 y T089–T098 figuran completadas con evidencia histórica o ejecución reciente en `validation.md`. La funcionalidad US1–US5 y la corrección constitucional C1 están implementadas según el código inspeccionado y la evidencia registrada; la aceptación operativa final permanece abierta por T080 y T088. T084 fue ejecutada y verificada en CI; T086 quedó documentada como bloqueo externo.

Conservar registro, login JWT, API Keys y consultas paginadas. Agregar un Adapter Football-Data.org v4, sincronización manual global con publicación atómica en PostgreSQL, entidades Equipo/Jugador con identidad externa y actividad, y una SPA React. Usar temporada vigente por liga; 96 equipos activos; ausentes inactivos y reactivables; dorsal nullable; credenciales frontend por pestaña. Conservar Gradle, Java 21, el paquete raíz y las migraciones aplicadas.

**Límite operativo**: se diseña para una cuenta del proveedor con acceso a Squads. La documentación pública no acredita los permisos de una cuenta concreta ni una importación real exitosa. El acceso efectivo y la calidad de los datos son gates de aceptación externa; no se contratan servicios durante este flujo.

## Contexto técnico

| Aspecto | Decisión |
|---------|----------|
| Lenguaje/framework | Java 21, Spring Boot 3.5.16 y Spring Framework 6.2 administrado por su BOM |
| Build | Gradle mediante wrapper existente en `backend/`; conservar `build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat` y `gradle/`, sin crear `pom.xml` |
| Dependencias | Web, Validation, Security, OAuth2 Resource Server/JOSE, Data JPA, PostgreSQL JDBC, Flyway core y módulo PostgreSQL; versiones del BOM de Boot |
| JWT | `NimbusJwtEncoder` y `NimbusJwtDecoder` de Spring Security; no agregar `jjwt` |
| Persistencia | PostgreSQL 16, Flyway; Hibernate `ddl-auto=validate` |
| Testing | JUnit 5, Testcontainers PostgreSQL, Spring Security Test, MockMvc y AssertJ |
| Documentación | OpenAPI 3.0.3 en `contracts/openapi.json`; Springdoc 2.8.17 con Swagger UI |
| Plataforma | REST; Windows/PowerShell para desarrollo, Linux con Docker para CI y medición |
| Escala de referencia | 10.000 jugadores, 1.000 usuarios iniciales, 50 consultas concurrentes |
| Rendimiento | p95 registro+login <2 s; API Key <500 ms; catálogo <300 ms a 50 consultas concurrentes |
| Integración | RestClient de Spring 6.2 dentro de `adapter/impl`; DTOs externos en `adapter/dto`; cinco consultas agregadas, sin SDK adicional |
| Frontend | React con JavaScript (`.js`/`.jsx`), React DOM, Vite; Node.js LTS, npm y `package-lock.json`; rutas relativas y proxy de mismo origen |
| Pruebas nuevas | Adapter con servidor HTTP local, JUnit 5; Vitest/Testing Library y Playwright para frontend; PostgreSQL Testcontainers |
| Presupuesto de sync | HTTP conexión 5 s, respuesta 15 s, cuerpo 10 MiB por liga; ejecución global 120 s; transacción PostgreSQL 150 s; proxy HTTP 180 s |
| Alcance de sync | Una ejecución global; cinco ligas; máximo técnico de 10.000 jugadores por snapshot; sin cron, cola ni endpoint de trabajos |

Nota histórica de compatibilidad: el repositorio declaraba Boot 4.1.1 y Gradle 9.7.1, mientras Framework 7 exigía JUnit Jupiter 6, incompatible con JUnit 5. La adecuación ya fue realizada y validada en T001–T002: el estado implementado usa Java 21, Gradle 8.14.4, Spring Boot 3.5.16 y las dependencias definidas en este plan. Las fuentes y alternativas de la decisión están en [research.md](./research.md); la Constitución no fue modificada.

## Verificación de Constitución

**Antes de fase 0**: el stack local y las capas permiten la ampliación sin excepciones constitucionales. Se investigan acceso al proveedor, snapshot, migración y sesión web. **Después de fase 1**: las decisiones están concretadas en este plan y sus contratos; los controles siguientes pasan a nivel de diseño. El éxito de build, pruebas, integración real, CI y SonarCloud debe acreditarse al implementar.

| Regla | Aplicación | Gate de entrega |
|-------|------------|-----------------|
| Java 21, PostgreSQL, JUnit 5 | Stack compatible y PostgreSQL real en integración | Dependencias resueltas y pruebas PASSED |
| Package by Layer | Subpaquetes obligatorios bajo `com.devchesterunited.devchesterunitedv2`; el raíz constitucional es un ejemplo | Revisión de imports y dependencias |
| Controller solo habla con Service | DTO y delegación a interfaces; sin Repository ni lógica de dominio | Revisión por capas |
| Modelo puro y rico | Usuario, APIKey y Jugador sin Spring/JPA; invariantes y excepciones propias | Unitarias sin contexto ni BD |
| Persistencia aislada | Entidades en `persistence/dto`; Repository no invoca lógica del dominio | Integración de cada Repository |
| Validación por capa | DTO formato/trimming; Service existencia; Modelo invariantes | Casos felices y borde |
| JWT y API Key | JWT para generar credenciales; lecturas del catálogo públicas; API Key obligatoria solo en sincronización administrativa; registro/login públicos según spec | Matriz de autenticación contractual |
| OpenAPI y español | Contrato del endpoint propio, cinco rutas externas, dorsal nullable y errores en español | Swagger UI y comparación de contrato |
| Protección de tests | Conservar `DevchesterUnitedV2ApplicationTests.java`; agregar soporte en archivos nuevos | Cambiar/borrar tests existentes exige «SÍ» explícito |
| DoD local y Postman | Build, arranque, flujo completo y colección ejecutable | Evidencia en `validation.md` |
| GitHub Actions | Build y todas las suites antes de integrar en principal | Build SUCCESS para commit/PR exacto |
| SonarCloud | Proyecto vinculado y análisis completo | Máximo 10 issues abiertos, cero vulnerabilidades y cero smells críticos |
| Adapter exclusivo | Solo Service utiliza Adapter; no HTTP en Controller/Modelo/Repository | Pruebas de contrato y revisión de dependencias |
| Snapshot local | Todo o nada, identidad externa y actividad; lectura local durante fallos | PostgreSQL real, rollback y concurrencia determinista |
| React consumidor | Solo API documentada, credenciales del proveedor solo en backend | Build frontend, flujo real SC-010 y bloqueo de red externa en tests |
| Tests sin terceros | Fixtures sintéticos y servidor local, sin claves reales | Suites ejecutables sin Internet una vez provisionadas herramientas |

La normalización cambia fixtures SQL en `JugadorE2ETest`, `JugadorRepositoryIntegrationTest` y `JugadorServiceIntegrationTest`: insertan `league/team` directamente. Su adaptación se debe presentar como diff concreto y requiere el «SÍ» explícito constitucional antes de editar esos tests. No se reduce cobertura ni se agregan excepciones al esquema productivo para sostener fixtures antiguos. Este plan no modifica tests; esa autorización es una dependencia de la implementación, no una excepción concedida.

No se sustituyen CI/Sonar por validación local ni se marcan aprobados por configurar un workflow. Requieren evidencia real y acceso durante la implementación.

## Estructura del proyecto

```text
backend/
├── build.gradle
├── settings.gradle
├── gradlew
├── gradlew.bat
├── gradle/
├── src/main/java/com/devchesterunited/devchesterunitedv2/
│   ├── DevchesterUnitedV2Application.java
│   ├── configuration/       # Seguridad, JWT, Swagger, filtro API Key
│   ├── controller/          # dto/ y exceptionHandler/
│   ├── modelo/              # Usuario, APIKey, Jugador, Equipo, CatalogoSincronizado
│   ├── persistence/         # Repositories y dto/ JPA
│   ├── adapter/             # dto/ externo e impl/ FootballDataAdapter
│   └── service/             # config/, impl/, interfaces/
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/        # V1–V3 intactas; V4 normalización y sincronización
└── src/test/
    ├── java/com/devchesterunited/devchesterunitedv2/
    │   ├── DevchesterUnitedV2ApplicationTests.java
    │   ├── configuration/
    │   ├── modelo/
    │   ├── service/
    │   ├── persistence/
    │   ├── adapter/
    │   └── controller/
    └── resources/application.properties

frontend/
├── package.json             # React + JavaScript, Vite y scripts
├── package-lock.json
├── vite.config.js
├── src/                     # archivos .js/.jsx: vistas, componentes, api/, session/
├── tests/                   # Vitest/Testing Library
└── e2e/                     # Playwright, compose.yml y fixture de navegador

.specify/                    # proyecto SDD en la raíz
specs/                       # artefactos SDD en la raíz
.github/                     # CI en la raíz; Gradle usa backend/, npm usa frontend/
performance/                 # k6 y semilla reproducible en la raíz
postman/                     # colección en la raíz
```

## Fase 0: investigación

Consolidada en [research.md](./research.md): se conservan decisiones de autenticación y se agregan cobertura externa, mapeo, publicación atómica, migración y frontend. No quedan elecciones técnicas sin resolver. La disponibilidad comercial de Squads y la validación de una cuenta real quedan identificadas como prerrequisitos operativos. La implementación moverá el Gradle existente a `backend/` conservando su historial/configuración; no se recrea el proyecto. El frontend será React + JavaScript en `frontend/`, con archivos `.js`/`.jsx`, sin TypeScript, `tsconfig` ni `.ts`/`.tsx`.

## Fase 1: diseño y contratos

### Seguridad y capas

- Seguridad por ruta: registro/login públicos, generación de API Key solo con Bearer JWT, lecturas del catálogo públicas y sincronización administrativa solo con `X-API-KEY`. Las API Keys son alternativas para consumidores externos o machine-to-machine en endpoints explícitamente autorizados. Sin sesiones/cookies de backend. Swagger público en local/test; deshabilitado en otros perfiles.
- JWT HS256 con secreto Base64 externo de al menos 32 bytes (`JWT_SECRET`, propiedad existente `jwt.secret`), `iss=devchester-united`, `aud=devchester-api`, `sub=usr_<uuid>`, `iat` y `exp=iat+3600s`. Validar algoritmo, firma, emisor, audiencia, tiempo y existencia del usuario. Clock controlable; tolerancia temporal cero. Sin refresh ni endpoint de logout; cerrar sesión web borra únicamente el estado local.
- BCrypt coste 12; contraseña de 8–72 caracteres y máximo 72 bytes UTF-8, con mayúscula, minúscula y número. No truncar ni aplicar trimming: rechazar espacios externos, conservar interiores. PasswordEncoder en infraestructura de Service, nunca en modelo puro.
- API Key `dev_key_` más 32 bytes aleatorios en Base64URL sin padding; guardar SHA-256 hexadecimal y prefijo de 14 caracteres. Respuesta solo después del commit y `Cache-Control: no-store`, igual que login. Nunca registrar credenciales, tokens o hashes en logs.
- El filtro API Key delega en Service y se aplica únicamente a endpoints explícitamente protegidos, actualmente la sincronización administrativa. Las lecturas públicas no deben validar API Key. Error de BD durante la autorización →503, nunca autorización ni falso 401.
- Service mapea entidades a modelo, invoca comportamiento y persiste. Repository solo lee/escribe entidades. Excepciones en `modelo/exception`; traducción HTTP centralizada y adaptadores para errores anteriores al controller.

### Transacciones y concurrencia

La unidad de concurrencia es la adquisición del lock, no la duración completa de la solicitud HTTP. Para cada usuario, la rotación usa `FOR UPDATE NOWAIT`: una solicitud que intenta entrar mientras otra transacción conserva el lock es competidora; si encuentra el lock ocupado, debe salir con SQLSTATE `55P03` traducido a `409 Conflict`, después del rollback, sin reintentos ni mutaciones. La solicitud que adquiere el lock después del `commit` anterior inicia una rotación independiente y válida con `200 OK`. El lock es por usuario, por lo que las rotaciones de usuarios distintos no se bloquean mutuamente. El índice único parcial mantiene como invariante un máximo de una API Key activa por usuario.

Registro: unicidad en BD y 409 ante duplicados; hashing antes de insertar. Rotación: transacción READ COMMITTED con fila del usuario `FOR UPDATE NOWAIT`, revocación e inserción atómicas. SQLSTATE 55P03 →409 después del rollback, sin reintentos. Índice único parcial por usuario `WHERE is_active=true` y CHECK de coherencia con `revoked_at`.

La competencia se define en la adquisición del bloqueo durante la rotación: quien llega después del commit realiza una nueva rotación válida. No se promete deduplicación de toda ventana HTTP. Prueba determinista: detener ganador tras el lock, ejecutar competidoras, observar 409 y liberar ganador. Probar rollback conservando clave anterior y operaciones de usuarios distintos.

La generación, rotación/reemplazo explícito, revocación, unicidad y concurrencia de API Keys son requisitos vigentes de esta especificación. La implementación y las pruebas históricas cubren el flujo descrito; la evidencia de cierre final debe conservarse junto con el resto de los gates. El frontend nunca rota claves automáticamente.

### Consulta y validación

Filtros parciales literales ILIKE con escape de `%`, `_` y `!`, parámetros vinculados y AND entre filtros presentes; trimming de extremos, vacío/caracteres de control →400. No eliminar tildes. `page` desde 1, `limit` 1–100 (20 por defecto), orden estable por UUID, página fuera de rango →200 con lista vacía y totales reales. Validar overflow de offset y enteros de 32 bits.

Username alfanumérico ASCII de 3–30 caracteres, normalizado a minúsculas para unicidad; corregir ejemplos anteriores con `_` para respetar la regla normativa. Email máximo 254, trim y minúsculas, único. Rechazar campos JSON desconocidos y parámetros repetidos/desconocidos con 400. [Modelo de datos](./data-model.md) y [contrato](./contracts/openapi.json) detallan las restricciones.

### Sincronización externa y atomicidad

`CatalogoController` delega en `SincronizacionCatalogoService`; el Service coordina Adapter, Modelo y Repository. DTOs externos nunca salen al Controller ni se convierten implícitamente en dominio. `CatalogoSincronizado` verifica pertenencia, cantidad por liga, unicidad y coherencia del snapshot como reglas puras; Service resuelve UUID existentes y ejecuta transiciones de actividad sobre los modelos.

1. Autenticar la API Key administrativa; cuerpo o query no admitidos →400 una vez autenticada. Verificar configuración del proveedor sin mostrar su valor.
2. Abrir una transacción y adquirir `pg_try_advisory_xact_lock` con clave fija de la aplicación, vía Repository. Si está ocupado →409 inmediato sin HTTP externo. El lock transaccional se conserva hasta commit/rollback y coordina instancias que comparten PostgreSQL; no usar un mutex JVM.
3. Antes de toda mutación de catálogo, obtener secuencialmente `/competitions/{PL,PD,BL1,SA,FL1}/teams`, sin `season`, validando cada respuesta. Conservar `season.id/startDate/endDate` por liga. No consultar personas/equipos individualmente como fallback silencioso.
4. Validar snapshot completo: 20/20/18/20/18 equipos únicos, cinco competiciones correctas, squads presentes y no vacíos, IDs positivos, campos obligatorios mapeables y un equipo por jugador. Los duplicados idénticos del mismo jugador/equipo se colapsan; asociaciones conflictivas invalidan todo. `players_processed` no es una constante: 2473 es solo un ejemplo.
5. Resolver UUID por ID externo; crear o actualizar equipos/jugadores, transferir asociación cuando el snapshot sea inequívoco, reactivar presentes e inactivar ausentes. En la primera publicación válida desactivar también la semilla demostrativa, sin borrarla ni asignarle IDs externos ficticios. Publicar todos los cambios en el único commit.
6. Devolver 200 después del commit con contadores; ante fallo HTTP, cuota, timeout, payload inválido, pérdida de BD o validación externa →503 y rollback. Error interno inesperado →500 y rollback. Liberar recursos al terminar. Si se pierde la respuesta después del commit, el catálogo ya quedó publicado: una nueva acción manual idempotente reconcilia el estado.

Tradeoff: se mantiene una conexión/transacción abierta durante la obtención externa, hasta 120 s, pero no se bloquean filas del catálogo durante HTTP. Es la opción acotada que evita locks de sesión filtrados al pool y leases vencidos mientras una ejecución aún escribe. Mantener pool de al menos 10 conexiones, deadline de pared comprobado antes de cada paso y antes del commit, timeouts HTTP/SQL y rollback al vencer. Las consultas conservan `REPEATABLE_READ`: cada petición ve datos y totales del mismo snapshot; peticiones distintas pueden ver publicaciones diferentes.

Cadenciar consultas de una ejecución con intervalo mínimo de 6 s, sin reintentos automáticos; si headers indican cuota insuficiente para continuar, abortar con 503. La cuota externa sigue siendo autoridad, incluso entre ejecuciones consecutivas o si otra aplicación comparte la credencial: 429 externo →503. No se promete que una nueva sincronización admitida tenga cuota disponible. La política y sus tests están en [proveedor.md](./contracts/proveedor.md).

La composición 96 es una condición de esta entrega: una temporada futura que no respete ese reparto no se publica ni se rellena artificialmente. La documentación del proveedor describe rosters de temporada, no prueba la pertenencia contractual exacta a la fecha; aceptar datos reales requiere verificar esa correspondencia. Ante ambigüedad el sistema conserva el snapshot anterior.

### Evolución de datos y compatibilidad

V4 crea `equipos`, normaliza `jugadores.team_id`, agrega IDs externos únicos y estados de actividad, permite dorsal null y conserva los UUID de V3. Se migran los equipos locales por combinación exacta liga/nombre, sin atribuirles identidad del proveedor. El DTO público mantiene `league/team` como proyección del equipo y usa `name` como nombre completo del jugador. La fuente externa se conserva exactamente desde `player.name`, sin separar ni inferir apellido. Si dominio o persistencia conservan temporalmente `first_name`/`last_name` por compatibilidad histórica, esos campos son internos y no forman parte del contrato REST. [data-model.md](./data-model.md) detalla esquema, estados y validación.

### Frontend y experiencia

El contrato vigente de jugadores usa `name` como nombre completo; no expone `first_name` ni `last_name`. La sincronización conserva exactamente el `name` recibido por Football-Data.

SPA bajo `frontend/`, implementada con React + JavaScript (`.js`/`.jsx`), `fetch` y estado/contexto local, sin SSR ni librerías adicionales de estado global salvo necesidad justificada. Cuatro vistas: registro, login, API Key y catálogo. No se agrega una pantalla de sincronización al alcance US5. Vite proxy `/api/v1` hacia el backend en desarrollo/preview; distribución posterior bajo mismo origen mediante reverse proxy. No habilitar CORS global ni exponer secretos en `VITE_*`. No agregar TypeScript, `tsconfig` ni archivos `.ts`/`.tsx`.

`sessionStorage` conserva el JWT al recargar; React mantiene separada la presentación transitoria del secreto recién generado. Logout borra el estado local. El catálogo público no requiere ni conserva una API Key para navegar. La generación de API Key, si se expone en la vista de credenciales, es una acción explícita con JWT y nunca ocurre durante login, navegación o recarga. Si el almacenamiento está bloqueado, informar persistencia limitada a memoria.

Se verifica cierre ordinario de pestaña; los mecanismos del navegador para restaurar sesiones pueden recuperar `sessionStorage`. No prometer borrado irrecuperable ni usar `beforeunload` para borrar, porque también se ejecuta al recargar. Usar `noopener` al abrir ventanas propias. Validar este límite con el contrato [frontend.md](./contracts/frontend.md).

### Pruebas por historia y regresión

SC-005 usa como fuente normativa el fixture funcional determinista de seis jugadores definido en `spec.md`, con Real Madrid, Atlético de Madrid, LaLiga y las cuatro posiciones del modelo; los fixtures de Repository agregan casos literales de `%`, `_`, `!`, comillas y tildes. La validación compara contenido completo y metadatos exactos contra el oráculo del fixture: filtros parciales case-insensitive, `Madrid`/`madrid`/`MADRID` equivalentes e incluyendo ambos equipos, AND entre filtros, escape literal, tildes significativas, orden por UUID ascendente, conteo antes de paginar, página fuera de rango con `200` y lista vacía conservando totales, y cero coincidencias con total `0` y `total_pages=0`.

SC-004 se valida contra la matriz normativa de seguridad definida en `spec.md`: registro y login son públicos; la generación de API Key acepta exclusivamente JWT válido; `/api/v1/players` y las lecturas de catálogo son públicas; `/api/v1/catalog/sync` acepta exclusivamente una API Key válida; y los errores de BD durante la validación de esa clave responden `503`. Las credenciales no requeridas no convierten un endpoint público en protegido.

| Historia | Dominio puro | Service/Repository con PostgreSQL | E2E MockMvc |
|----------|--------------|----------------------------------|-------------|
| US1 / FR-001–004 | Invariantes Usuario | Registro, hashing, duplicados y búsquedas | 201, login 200, 400, 401, 409 |
| US2 / FR-005–008,012 | Estado APIKey | Hash, una activa, rotación, rollback, concurrencia | JWT válido/ausente/inválido/expirado, secreto una vez, 409 |
| US3 / FR-009–011 | Invariantes Jugador | Filtros literales, AND, orden, paginación, totales | Acceso sin credenciales, Madrid en ambos equipos, mayúsculas, 400 y lista pública |
| US4 / FR-013–022,030–032,035 | Equipo, snapshot, identidad, actividad y dorsal opcional | V3→V4, upsert, rollback, inactivación, reactivación, transferencia y lock entre conexiones | 200 con contadores, API Key ausente/inválida 401, JWT solo insuficiente 401, competencia 409, incompletitud/timeout/cuota 503 |
| US5 / FR-023–029,033–035 | Estado/validación de interfaz | Componentes con API simulada; backend real para flujo completo | Playwright registro→login→clave→catálogo, recarga/logout, filtros/paginación, null y errores |

Adapter: servidor HTTP local y fixtures sintéticos versionados, incluyendo cinco ligas/96 equipos, error HTTP, cuerpo inválido/truncado, squad ausente/vacío, nombres de una palabra, posiciones detalladas, dorsal null, duplicados y timeouts. No se ejecuta la API real desde ninguna suite. SC-006–009 se prueban con fixtures exactos y PostgreSQL; además la aceptación de operación real requiere verificar credencial/cobertura del proveedor. Barreras deterministas mantienen sync antes del commit para verificar 409, lectura del snapshot anterior y liberación tras error. Añadir pruebas de contenido/paginación con entidades inactivas y dorsal null, sin debilitar el oráculo previo.

SC-010 exige navegador→proxy→backend real→PostgreSQL; mocks de frontend por sí solos no lo acreditan. Definir `frontend/e2e/compose.yml` con proyecto `devchester-e2e`, PostgreSQL dedicado en tmpfs y el bootJar construido desde `backend/` en puerto loopback 18080; esperar readiness y cargar fixture SQL normalizado de forma controlada después de Flyway. Playwright inicia preview en 4173 con proxy hacia 18080 mediante `vite.config.js` y `DEV_API_TARGET`. CI prepara imágenes, dependencias y navegador antes de ejecutar sin Internet. Credenciales exclusivamente sintéticas; nunca reutilizar DB_URL del entorno personal. La evidencia de builds y suites ejecutadas se mantiene en `validation.md`.

En el alcance T064–T069 se conservan las columnas legacy `league`/`team` para compatibilidad con el DTO público y los datos existentes; su eliminación queda fuera de US4 y requiere una migración coordinada posterior.

Agregar pruebas JOSE de algoritmo/firma/emisor/audiencia. SC-004 exige matriz de rechazos al 100 %; SC-005 compara contenido y totales contra fixtures. Casos felices y borde en cada nivel, sin H2 ni MockMvc dentro de tests de Service. Mantener todas las pruebas existentes.

### Protocolo reproducible de rendimiento

Linux, Java 21, aplicación a 2 CPU/2 GiB (heap 1 GiB), PostgreSQL 16 a 2 CPU/2 GiB con SSD y generador k6 separado a 2 CPU/1 GiB. Registrar CPU real, SO, commit, configuración y digest de imágenes. Red local sin proxy/TLS para esta línea base, conexiones persistentes; no extrapolar a Internet.

Semilla determinista: 10.000 jugadores, 20 ligas, 20 equipos por liga y 25 jugadores por equipo, cuatro posiciones cíclicas y 1.000 usuarios iniciales. Incluir Real Madrid y Atlético de Madrid en LaLiga. Registrar checksum. Sin mutaciones del catálogo durante medición; nuevos registros con identificadores únicos.

Tres escenarios separados, 60 s de calentamiento excluidos y 5 min de medición, tres repeticiones con semilla restaurada en entorno descartable:

1. SC-001: 1 usuario virtual, registro seguido de login; Trend del tiempo de pared de ambos HTTP, sin pausas, p95 <2000 ms.
2. SC-002: 1 usuario virtual autenticado previamente, rotación secuencial; login/setup excluidos, p95 <500 ms.
3. SC-003: 50 usuarios virtuales constantes, una petición síncrona por usuario, sin pausas; 20 % sin filtros, 20 % liga, 20 % equipo, 20 % posición, 20 % combinados AND, todos parciales; page=1, limit=20, p95 <300 ms.

Cada repetición debe cumplir umbrales, cero errores HTTP inesperados y checks funcionales al 100 %. Medir HTTP completo desde cliente con Trend; no sumar percentiles de registro y login. Exportar métricas/resumen k6 sin secretos a evidencia. La prueba de conflictos 409 es independiente del benchmark exitoso.

### Entrega y evidencia

[quickstart.md](./quickstart.md) define validación local. Postman debe incluir cinco endpoints, variables y errores. CI usa `backend/` como working directory para Gradle (`./gradlew`/`gradlew.bat`) y `frontend/` como working directory para npm/Vite (`npm ci`, `npm run build`, tests y Playwright). `.github/`, `performance/`, `postman/`, `.specify/` y `specs/` permanecen en la raíz. Los jobs deben invocar explícitamente `working-directory: backend` para pasos Gradle y `working-directory: frontend` para pasos npm/Vite; no asumir que el checkout raíz contiene `build.gradle` o `package.json`. SonarCloud exige los conteos constitucionales además del Quality Gate. Agregar evidencia a `validation.md` sin reemplazar resultados históricos. Registrar duración y resultado de sync, liga/temporada y contadores, con ID de correlación; no loguear headers, secretos ni payloads completos. No se agregan servicios de telemetría. Gates pendientes bloquean entrega, no se acreditan por existir este diseño.

### Decisiones vigentes, deuda y backlog

- **Seguridad vigente**: registro/login públicos; `POST /api/v1/auth/apikey` requiere JWT; `GET /api/v1/players` es público y no valida credenciales; `POST /api/v1/catalog/sync` requiere exclusivamente API Key administrativa. La API Key sigue formando parte de los requisitos obligatorios de generación, almacenamiento seguro, rotación/reemplazo, revocación, unicidad y concurrencia, pero no del acceso al catálogo público.
- **Sincronización**: pertenece al alcance documentado de esta feature y de la Entrega Nro 1 constitucional, específicamente US4 y FR-013–FR-022/FR-030–FR-035. No se inventa una asignación a Entrega 2 o 3. La aceptación real del proveedor continúa siendo T086.
- **Separación de contratos de aplicación (C1 aprobado)**: las interfaces e implementaciones de `Service` no dependen de paquetes `controller` ni de DTOs HTTP. Los resultados y consultas internos viven bajo `service/dto`; cada Controller traduce entre sus DTOs HTTP y esos modelos internos. Esta separación no cambia rutas, JSON, códigos HTTP, seguridad, validaciones ni transacciones.
 - **SonarCloud**: el workflow, el plugin y el verificador están configurados; el Quality Gate y los conteos constitucionales fueron ejecutados y aprobados en CI para el commit validado.

| Categoría | Estado documentado | Referencias |
|---|---|---|
| Implementado y con evidencia histórica | US1–US5, JWT, API Key, catálogo público, sync local con Adapter, frontend, CI y corrección constitucional C1 | T001–T078, T083, T089–T098 |
 | Implementado, pendiente de validación final | Mediciones de performance | T080 y T088 |
| Requisito vigente de esta entrega | Rotación/concurrencia de API Key y seguridad por endpoint | FR-005–FR-012, SC-002/SC-004 |
| Deuda técnica pendiente | Constructores extensos y acoplamientos menores a revisar | No relacionada con C1 ni con el contrato |
| Entrega futura | No se asignan funcionalidades concretas sin decisión aprobada | Pendiente de definición del equipo |

### Orden de ejecución de gates

1. **En paralelo**: T079 prepara el entorno de performance; T082 prepara Postman; T084 ejecuta y verifica SonarCloud; T081 prepara la matriz de comparación OpenAPI.
2. **Después de T079**: T080 ejecuta las mediciones reproducibles de rendimiento.
3. **Después de T081 y T082**: T085 ejecuta el quickstart y T087 ejecuta el flujo final Postman + SC-010.
4. **En paralelo cuando exista acceso suficiente al proveedor**: T086 documenta la aceptación o bloqueo externo del proveedor; no modifica sus criterios de aceptación. T088 cierra el DoD únicamente después de reunir todas las evidencias de T079–T087.
5. **Trabajo paralelizable para dos personas**: Persona A: T079→T080 y T084. Persona B: T081→T082→T085→T087. T086 puede ejecutarse en paralelo cuando exista una cuenta/proveedor disponible; T088 queda necesariamente al final.

## Seguimiento de complejidad

La dependencia Service → DTOs de Controller queda eliminada por la corrección C1 aprobada. Boot 4/JUnit 5 se resuelve con versiones compatibles; el raíz existente mantiene la estructura por capas.

## Traspaso a tareas

Ejecutar `$speckit-tasks` sobre este diseño conservando T001–T050 y la evidencia histórica. Agregar tareas US4/US5 y actualizar el alcance de T051–T061: fixture de rendimiento normalizado, cinco endpoints, frontend y aceptación externa. Incluir la revisión/autorización de cambios concretos de fixtures antes de editar tests existentes. Este comando termina en fase 1: no genera tareas, código, migraciones ni cambios de tests.
## Cierre de ejecución US5 (2026-09-17)

 T075–T078 están implementadas y validadas según la evidencia registrada. T079 y T081–T085, T087 también están completadas. Permanecen pendientes T080, T086 y T088.
