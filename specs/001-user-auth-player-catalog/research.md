# Investigación: autenticación, catálogo sincronizado y frontend

**Fecha**: 2026-09-15. Las secciones 1–5 conservan las decisiones base de 2026-09-05; 6–10 incorporan la ampliación. Investigación documental y revisión del repositorio, sin llamadas autenticadas al proveedor, instalaciones ni ejecución de suites.

## 1. Stack y compatibilidad constitucional

- **Decisión**: Java 21, Boot 3.5.16, Gradle 8.14.4, Springdoc 2.8.17. Conservar Gradle y el raíz `com.devchesterunited.devchesterunitedv2` existentes.
- **Motivo**: Spring Framework 7 exige Jupiter 6 para su extensión de pruebas; la Constitución exige JUnit 5. Boot 3.5 soporta Java 21 y Gradle 8. La línea Springdoc 2.8 corresponde a Boot 3.5.
- **Alternativas**: Boot 4 exigiría actualizar explícitamente la Constitución; forzar JUnit 5 bajo Framework 7 no resuelve la incompatibilidad. Maven o renombrar el raíz no aportan valor y complican conservar el test existente.
- **Fuentes**: [Spring TestContext](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/support-classes.html), [Boot 3.5 requisitos](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [Gradle 8.14.4](https://docs.gradle.org/8.14.4/release-notes.html), [Springdoc compatibilidad](https://springdoc.org/v2/).

Usar el BOM de Boot para JUnit 5, Testcontainers, Flyway y JDBC, sin fijar versiones independientes contradictorias. Añadir `flyway-core` y `flyway-database-postgresql`; migraciones versionadas y validación del esquema. [Coordenadas administradas por Boot](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html).

## 2. JWT y contraseñas

- **Decisión**: Spring Security Resource Server/JOSE y Nimbus para emitir/validar JWT localmente; HS256, secreto externo de 256 bits o más, duración 3600 s, emisor y audiencia fijos.
- **Motivo**: Integración con la cadena de seguridad y versiones administradas. No hay proveedor externo ni descubrimiento remoto requerido.
- **Alternativas**: JJWT agregaría una biblioteca y validación manual; un proveedor OAuth externo o refresh tokens amplían el alcance.
- **Fuente**: [Spring Security 6.5 JWT](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html).

- **Decisión**: BCrypt coste 12 mediante PasswordEncoder; entradas limitadas a 72 bytes UTF-8, nunca truncadas. Rechazar espacios externos en contraseña, no transformarla.
- **Motivo**: Hash adaptativo soportado por Spring Security. El coste es una decisión del proyecto a comprobar en el benchmark, no una garantía de rendimiento.
- **Alternativas**: Argon2 es válido pero agrega configuración/dependencias; SHA-256 solo no es adecuado para contraseñas humanas.
- **Fuente**: [Password Storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).

## 3. API Keys y concurrencia

- **Decisión**: 32 bytes aleatorios generados en aplicación, token Base64URL con `dev_key_`, SHA-256 indexado y prefijo visible; sin expiración y una activa por usuario.
- **Motivo**: Claves aleatorias de alta entropía permiten búsqueda por hash sin almacenar el secreto. El hash rápido se limita a estas claves; la contraseña usa BCrypt.
- **Alternativas**: Texto plano permitiría recuperar credenciales de la BD; cifrado reversible agrega gestión innecesaria. [Referencia de hashes y hashing de contraseñas en PostgreSQL](https://www.postgresql.org/docs/current/pgcrypto.html).

- **Decisión**: Bloquear fila de Usuario con `FOR UPDATE NOWAIT` dentro de la transacción; 55P03 →409, rollback sin reintento. Revocar e insertar atómicamente, índice único parcial donde `is_active=true` y CHECK de estado.
- **Motivo**: La fila existe incluso para la primera clave; el bloqueo funciona entre instancias de aplicación que comparten PostgreSQL.
- **Alternativas**: Lock Java no coordina instancias; lock bloqueante serializaría solicitudes con varios éxitos; una restricción única sola no expresa la rotación.
- **Límite explícito**: Compiten las operaciones que intentan adquirir el lock mientras una rotación lo mantiene. Una llegada posterior al commit es una rotación nueva. No se promete detectar cualquier solapamiento HTTP ni recuperar un secreto si se pierde la respuesta.
- **Fuentes**: [SELECT y NOWAIT](https://www.postgresql.org/docs/current/sql-select.html), [SQLSTATE](https://www.postgresql.org/docs/current/errcodes-appendix.html), [Índices parciales](https://www.postgresql.org/docs/current/indexes-partial.html).

Probar con barreras sobre transacciones reales: detener ganadora después del lock, comprobar rechazo de competidoras, liberar y verificar exactamente una clave activa. Probar rollback y usuarios diferentes. La autorización consulta el estado confirmado al inicio de cada request sin caché de credenciales; no vuelve a validarse durante la búsqueda.

## 4. Filtros y contrato

- **Decisión**: Subcadena literal case-insensitive mediante ILIKE parametrizado, escapando `!`, `%` y `_`; AND entre filtros, tildes significativas, orden UUID estable y paginación page/limit.
- **Motivo**: Cumple Madrid → Real Madrid y Atlético de Madrid sin convertir entrada en comodines SQL.
- **Alternativas**: Igualdad contradice la aclaración; búsqueda sin acentos o full-text agrega semántica no solicitada. No agregar índices trigram inicialmente: medir la línea base de 10.000 filas antes de optimizar.
- **Fuente**: [PostgreSQL pattern matching](https://www.postgresql.org/docs/16/functions-matching.html).

Concretar el formato alfanumérico normativo de username como ASCII 3–30 y corregir los ejemplos con `_`; elegir minúsculas para identidad única, también en email. Estos son detalles de validación del contrato, documentados en el modelo. OpenAPI 3.0.3 permite declarar mecanismos distintos por operación; no declarar JWT y API Key juntos en una misma exigencia. [OpenAPI 3.0.3](https://spec.openapis.org/oas/v3.0.3).

## 5. Validación y entrega

- **Decisión**: Unitarias puras, integración real Service/Repository, E2E MockMvc en controller, y k6 separado para p95. El test existente conserva su contenido; configuración test global nueva usa PostgreSQL mediante driver JDBC de Testcontainers.
- **Motivo**: Cada nivel verifica una obligación constitucional distinta; E2E no reemplaza pruebas del dominio o repositorios.
- **Alternativas**: H2 no reproduce PostgreSQL; sleeps en concurrencia no aseguran el solapamiento; medir solo en una laptop sin recursos/datos fijados no es reproducible.
- **Fuentes**: [Testcontainers JDBC](https://java.testcontainers.org/modules/databases/jdbc/), [k6 thresholds](https://grafana.com/docs/k6/latest/using-k6/thresholds/), [k6 Trend](https://grafana.com/docs/k6/latest/javascript-api/k6-metrics/trend/).

Recursos, datos, mezcla de consultas y duración se fijan en `plan.md`. El flujo registro+login usa una medición conjunta; no se suman percentiles. Las tres repeticiones deben pasar y los errores no cuentan como éxitos rápidos. CI, Postman, SonarCloud y evidencia de arranque permanecen obligatorios. No se introducen nuevas metas de disponibilidad ni servicios externos en esta entrega.

Todas las decisiones técnicas abiertas del plan anterior quedan resueltas. Credenciales de infraestructura y resultados de gates se obtendrán al implementar; no son decisiones de diseño pendientes.

## 6. Proveedor, cobertura y temporada vigente

- **Decisión**: Football-Data.org v4, `/competitions/{code}/teams` para PL, PD, BL1, SA y FL1, sin filtro `season`. Requerir credencial con acceso efectivo a plantillas; usar `FOOTBALL_DATA_API_KEY` solo en backend, cabecera `X-Auth-Token`.
- **Motivo**: los endpoints agregados permiten diseñar cinco solicitudes por snapshot. La temporada vigente la decide el proveedor. Se guarda su identificador por liga para trazabilidad.
- **Alternativas**: 96 consultas individuales adicionales aumentan cuota y duración; se rechazan como fallback automático. No se cambia de proveedor ni se compra una suscripción en planificación.
- **Evidencia**: [Competition](https://docs.football-data.org/general/v4/competition.html#_teams), [códigos y headers](https://docs.football-data.org/general/v4/lookup_tables.html), [cobertura](https://www.football-data.org/coverage). El [quickstart oficial](https://www.football-data.org/documentation/quickstart) contiene respuestas con `squad` vacío; documentar un campo no acredita su disponibilidad en una cuenta.

Al 2026-09-15, [pricing oficial](https://www.football-data.org/pricing) anuncia Squads en «Free + Deep Data» (29 EUR/mes, 30 llamadas/minuto); el nivel Free enumera 10 llamadas/minuto y no Squads. Esto es un requisito comercial identificado, no una contratación autorizada ni un costo inmutable. Verificar plan y permisos antes de aceptación real.

Las [políticas del proveedor](https://docs.football-data.org/general/v4/policies.html) definen temporada actual por inicio más reciente y roster de temporada, que puede incluir jugadores que ya pasaron por un equipo. **Límite**: esos rosters no garantizan plantillas contractuales exactas de hoy. El diseño importa el snapshot vigente publicado y rechaza asociaciones contradictorias; no resuelve transferencias eligiendo arbitrariamente la última respuesta. El recurso [Person](https://docs.football-data.org/general/v4/person.html) expone `currentTeam`, pero incorporarlo masivamente cambia cuota y duración y queda fuera de la ruta elegida. Si la aceptación exige una precisión que el snapshot real no da, se debe revisar fuente/alcance antes de declarar US4 satisfecha.

## 7. Mapeo y validación del snapshot

- **Decisión**: traducir contratos externos a DTOs internos explícitos; exigir IDs, equipo inequívoco, nombre utilizable, nacionalidad y posición mapeable. Dorsal ausente/null se conserva null. Nombre sin apellido separado usa `name` completo en nombre y apellido vacío, sin inferir apellidos.
- **Motivo**: el [recurso Team](https://docs.football-data.org/general/v4/team.html) documenta variantes de nombre y posiciones propias. La identidad siempre depende del ID externo, nunca del nombre. Diccionario de posiciones y reglas exactas en [contrato de proveedor](./contracts/proveedor.md).
- **Alternativas**: inventar dorsal, nacionalidad o posición; inferir identidad por nombre; omitir silenciosamente jugadores inválidos. Todas permitirían publicar datos no verificables y se rechazan.

La cantidad 96 y reparto 20/20/18/20/18 son exigencias de la spec, no datos confirmados por esta investigación. No se fija cantidad de jugadores. Snapshot incompleto, desconocido o conflictivo falla entero. Tests usan datos sintéticos; confirmar que la cuenta real entrega los cinco squads completos es un gate posterior. No hay garantía de detectar una omisión silenciosa que el proveedor no señale.

- **Decisión**: RestClient de Spring 6.2 y HTTP síncrono con conexión 5 s, respuesta 15 s, máximo 10 MiB por liga, deadline global 120 s y separación de 6 s entre consultas; sin reintentos automáticos.
- **Motivo**: cinco consultas nominales caben en un presupuesto acotado. El Adapter traduce 403/404/429/5xx, timeouts y respuestas inválidas a fallo propio; el Service/Controller responde 503 genérico sin propagar secretos.
- **Alternativas**: WebFlux/SDK/colas no aportan al endpoint síncrono requerido. No se ignora cuota ni se reintenta un 429 en bucle. Los números son decisiones locales que deben verificarse, no SLA del proveedor.
- **Fuentes**: [Spring REST clients](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html), [errores del proveedor](https://docs.football-data.org/general/v4/errors.html). Respetar `X-RequestsAvailable` y `X-RequestCounter-Reset`; sus valores pueden ser afectados por otros consumidores de la cuenta.

## 8. Atomicidad, migración y preservación de pruebas

- **Decisión**: lock advisory transaccional global no bloqueante, snapshot en memoria antes de escribir y un único commit. La transacción conserva una conexión durante HTTP con límites explícitos, sin locks de filas de catálogo hasta aplicar datos.
- **Motivo**: [PostgreSQL 16](https://www.postgresql.org/docs/16/explicit-locking.html#ADVISORY-LOCKS) libera el lock al finalizar la transacción y coordina instancias. Evita persistencia parcial y permite lectura MVCC del catálogo anterior.
- **Alternativas**: mutex JVM no coordina instancias; locks de sesión requieren limpieza estricta del pool; un lease necesita fencing ante caducidad; transacciones por liga contradicen la conservación integral en fallos.

- **Decisión**: V4 aditiva respecto del historial Flyway, con normalización de equipos, conservación de UUID, índices únicos de IDs externos, actividad y dorsal nullable. Desactivar semilla demostrativa solo al primer snapshot externo válido.
- **Motivo**: V3 contiene 24 jugadores ficticios en dos equipos y no puede reinterpretarse como datos del proveedor. Relacionar por nombre con datos externos atribuiría identidades sin evidencia.
- **Alternativas**: modificar V3 aplicada, borrar datos, fabricar IDs externos o dejar demos mezcladas con plantillas reales. Se rechazan.

Los tests de Repository, Service y E2E de jugadores insertan el esquema antiguo con SQL directo. Se identifica una adaptación real de fixtures al esquema normalizado, manteniendo valores y aserciones del oráculo. Se requiere autorización explícita antes de editar esos archivos conforme a la constitución. No se propone falsear el modelo ni agregar triggers de compatibilidad solo para evitar esa revisión. Pruebas nuevas del Adapter usarán servidor HTTP local; [Spring testing clients](https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-client.html) distingue simulación de transporte y pruebas HTTP reales locales.

## 9. Frontend y sesión por pestaña

- **Decisión**: React + React DOM, JavaScript en archivos `.js`/`.jsx`, Vite, Node.js LTS, npm y `package-lock.json`; `fetch`, CSS y estado React bastan para las cuatro vistas del MVP. No se agrega TypeScript, `tsconfig` ni archivos `.ts`/`.tsx`; no se fija una versión específica de React/Vite en esta fase.
- **Motivo**: una SPA consume la API existente. Las líneas verificadas son compatibles; no se migra el backend ni se incorpora SSR.
- **Alternativas**: Next.js/SSR, Redux y un BFF amplían componentes sin un requerimiento que los necesite.
- **Fuentes**: [versiones React](https://react.dev/versions), [requisitos Vite](https://vite.dev/guide/), [Node LTS](https://nodejs.org/en/about/previous-releases).

- **Decisión**: `sessionStorage` para JWT, expiración y API Key, con estado de visualización del secreto nuevo solo en memoria. Proxy mismo origen `/api/v1`; la UI envía únicamente la credencial requerida por el endpoint. No introduce API de logout.
- **Motivo**: cumple la sesión ordinaria de pestaña y recarga. Logout elimina explícitamente almacenamiento y estado. La API Key no depende de la expiración JWT.
- **Alternativas**: `localStorage` persiste entre visitas; solo memoria pierde credenciales al recargar; cookies HttpOnly exigirían otro contrato. `beforeunload` no distingue cierre de recarga y no sirve para borrar credenciales.
- **Límite documentado**: la restauración de sesión/pestaña por el navegador puede conservar los datos. No se promete borrado irrecuperable. Ventanas abiertas por la aplicación usan `noopener`; si almacenamiento está bloqueado, informar funcionamiento solo en memoria.
- **Fuentes**: [HTML Web Storage](https://html.spec.whatwg.org/multipage/webstorage.html#the-sessionstorage-attribute), [sessionStorage](https://developer.mozilla.org/en-US/docs/Web/API/Window/sessionStorage), [proxy Vite](https://vite.dev/config/server-options#server-proxy).

## 10. Verificación del diseño y aceptación posterior

- **Decisión**: Vitest y Testing Library para componentes JavaScript; Playwright para pestañas/recargas y flujo SC-010 con backend y PostgreSQL locales. Instalar herramientas antes de correr suites desconectadas.
- **Motivo**: una API mockeada no acredita integración completa; un test unitario no reproduce el ciclo de vida real de una pestaña.
- **Alternativas**: solo capturas/manual o llamar al proveedor desde tests contradicen cobertura reproducible y restricciones de cuota.
- **Fuentes**: [Vitest](https://vitest.dev/guide/), [Testing Library](https://testing-library.com/docs/react-testing-library/intro/), [Playwright](https://playwright.dev/docs/mock).

Diseño resuelto sin cambiar requisitos de autenticación ni relajar fallos externos. El Gradle existente se planifica bajo `backend/` y el frontend React + JavaScript bajo `frontend/`; la organización física se aplicará durante la implementación, sin recrear Gradle. Condiciones de aceptación aún sin ejecutar: acceso real a Squads, snapshot utilizable, adaptación autorizada de fixtures, pruebas/build/arranque, rendimiento, Postman y CI/Sonar. La planificación no acredita esas condiciones.
