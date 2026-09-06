# Investigación: autenticación y catálogo

**Fecha**: 2026-09-05. Decisiones de diseño; no constituyen resultados de ejecución.

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
