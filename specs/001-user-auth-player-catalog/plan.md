# Plan de implementación: Autenticación y catálogo de jugadores

**Rama**: `001-user-auth-player-catalog` | **Fecha**: 2026-09-05 | **Spec**: [spec.md](./spec.md)

## Resumen

Registro, login con JWT, generación y rotación de API Key sin vencimiento y catálogo paginado con filtros parciales sin distinguir mayúsculas. Conservar Gradle, Java 21, la clase de arranque y el paquete raíz existentes. Ajustar las versiones de Boot y del wrapper durante la implementación para cumplir JUnit 5. Este documento describe el diseño; no acredita ejecución del build ni de controles externos.

## Contexto técnico

| Aspecto | Decisión |
|---------|----------|
| Lenguaje/framework | Java 21, Spring Boot 3.5.16 y Spring Framework 6.2 administrado por su BOM |
| Build | Gradle 8.14.4 mediante wrapper; conservar `build.gradle` y `settings.gradle`, sin crear `pom.xml` |
| Dependencias | Web, Validation, Security, OAuth2 Resource Server/JOSE, Data JPA, PostgreSQL JDBC, Flyway core y módulo PostgreSQL; versiones del BOM de Boot |
| JWT | `NimbusJwtEncoder` y `NimbusJwtDecoder` de Spring Security; no agregar `jjwt` |
| Persistencia | PostgreSQL 16, Flyway; Hibernate `ddl-auto=validate` |
| Testing | JUnit 5, Testcontainers PostgreSQL, Spring Security Test, MockMvc y AssertJ |
| Documentación | OpenAPI 3.0.3 en `contracts/openapi.json`; Springdoc 2.8.17 con Swagger UI |
| Plataforma | REST; Windows/PowerShell para desarrollo, Linux con Docker para CI y medición |
| Escala de referencia | 10.000 jugadores, 1.000 usuarios iniciales, 50 consultas concurrentes |
| Rendimiento | p95 registro+login <2 s; API Key <500 ms; catálogo <300 ms a 50 consultas concurrentes |

El repositorio declara Boot 4.1.1 y Gradle 9.7.1. Framework 7 exige JUnit Jupiter 6 para integración, incompatible con JUnit 5. La decisión de adecuar versiones, sus fuentes y alternativas están en [research.md](./research.md). Es trabajo futuro; no se modifica la Constitución.

## Verificación de Constitución

Evaluación previa: el diseño anterior tenía pendientes de dependencias y no coincidía con el build existente. Se resolvieron en fase 0. Evaluación posterior a fase 1: **PASS de diseño**, con todos los controles de ejecución pendientes.

| Regla | Aplicación | Gate de entrega |
|-------|------------|-----------------|
| Java 21, PostgreSQL, JUnit 5 | Stack compatible y PostgreSQL real en integración | Dependencias resueltas y pruebas PASSED |
| Package by Layer | Subpaquetes obligatorios bajo `com.devchesterunited.devchesterunitedv2`; el raíz constitucional es un ejemplo | Revisión de imports y dependencias |
| Controller solo habla con Service | DTO y delegación a interfaces; sin Repository ni lógica de dominio | Revisión por capas |
| Modelo puro y rico | Usuario, APIKey y Jugador sin Spring/JPA; invariantes y excepciones propias | Unitarias sin contexto ni BD |
| Persistencia aislada | Entidades en `persistence/dto`; Repository no invoca lógica del dominio | Integración de cada Repository |
| Validación por capa | DTO formato/trimming; Service existencia; Modelo invariantes | Casos felices y borde |
| JWT y API Key | JWT para generar credenciales; API Key obligatoria en catálogo; registro/login públicos según spec | Matriz de autenticación contractual |
| OpenAPI y español | Cuatro endpoints y errores en español, incluidos filtros de seguridad | Swagger UI y comparación de contrato |
| Protección de tests | Conservar `DevchesterUnitedV2ApplicationTests.java`; agregar soporte en archivos nuevos | Cambiar/borrar tests existentes exige «SÍ» explícito |
| DoD local y Postman | Build, arranque, flujo completo y colección ejecutable | Evidencia en `validation.md` |
| GitHub Actions | Build y todas las suites antes de integrar en principal | Build SUCCESS para commit/PR exacto |
| SonarCloud | Proyecto vinculado y análisis completo | Máximo 10 issues abiertos, cero vulnerabilidades y cero smells críticos |

No se sustituyen CI/Sonar por validación local ni se marcan aprobados por configurar un workflow. Requieren evidencia real y acceso durante la implementación.

## Estructura del proyecto

```text
specs/001-user-auth-player-catalog/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── contracts/openapi.json
├── quickstart.md
└── tasks.md                  # Regenerar con speckit-tasks
build.gradle
gradle/wrapper/gradle-wrapper.properties
src/main/java/com/devchesterunited/devchesterunitedv2/
├── DevchesterUnitedV2Application.java
├── configuration/           # Seguridad, JWT, Swagger, filtro API Key
├── controller/
│   ├── dto/
│   └── exceptionHandler/
├── modelo/                  # Usuario, APIKey, Jugador
│   ├── exception/
│   └── utils/
├── persistence/             # UsuarioRepository, APIKeyRepository, JugadorRepository
│   └── dto/                 # Entidades JPA
└── service/
    ├── config/              # Clock y PasswordEncoder
    ├── impl/
    └── interfaces/
src/main/resources/
├── application.properties
└── db/migration/            # V1 usuarios, V2 api_keys, V3 jugadores y semilla
src/test/java/com/devchesterunited/devchesterunitedv2/
├── DevchesterUnitedV2ApplicationTests.java  # Conservar
├── configuration/           # Soporte nuevo de Testcontainers
├── modelo/                  # Unitarias puras
├── service/                 # Integración PostgreSQL
├── persistence/             # Integración PostgreSQL
└── controller/              # E2E MockMvc exclusivamente
src/test/resources/application.properties
performance/                # Scripts k6 y semilla reproducible a implementar
postman/DevchesterUnited.postman_collection.json
.github/workflows/ci.yml
```

## Fase 0: investigación

Completada en [research.md](./research.md): stack, JWT, hashing, migraciones, concurrencia, filtros y medición. El raíz actual permite que el test existente descubra la misma clase de arranque. La configuración global nueva de test usará el driver JDBC de Testcontainers (`jdbc:tc:postgresql:16:///devchester_test`, con su módulo JDBC) para ejecutar también `contextLoads` sin editarlo; las suites concurrentes usarán contenedor explícito compartido y conexiones independientes. Configurar secreto JWT exclusivo de test en recursos de test.

## Fase 1: diseño y contratos

### Seguridad y capas

- Cadenas de seguridad por ruta: registro/login públicos, generación solo Bearer JWT, catálogo solo `X-API-KEY`; no aceptar credenciales del tipo equivocado. Sin sesiones/cookies. Swagger público en local/test; deshabilitado en otros perfiles.
- JWT HS256 con secreto Base64 externo de al menos 32 bytes, `iss=devchester-united`, `aud=devchester-api`, `sub=usr_<uuid>`, `iat` y `exp=iat+3600s`. Validar algoritmo, firma, emisor, audiencia, tiempo y existencia del usuario. Clock controlable; tolerancia temporal cero. Sin refresh ni logout.
- BCrypt coste 12; contraseña de 8–72 caracteres y máximo 72 bytes UTF-8, con mayúscula, minúscula y número. No truncar ni aplicar trimming: rechazar espacios externos, conservar interiores. PasswordEncoder en infraestructura de Service, nunca en modelo puro.
- API Key `dev_key_` más 32 bytes aleatorios en Base64URL sin padding; guardar SHA-256 hexadecimal y prefijo de 14 caracteres. Respuesta solo después del commit y `Cache-Control: no-store`, igual que login. Nunca registrar credenciales, tokens o hashes en logs.
- El filtro API Key delega en Service; consulta primaria al inicio de cada solicitud, sin caché entre solicitudes. Consulta ya autorizada puede terminar tras revocación. Error de BD →503, nunca autorización ni falso 401.
- Service mapea entidades a modelo, invoca comportamiento y persiste. Repository solo lee/escribe entidades. Excepciones en `modelo/exception`; traducción HTTP centralizada y adaptadores para errores anteriores al controller.

### Transacciones y concurrencia

Registro: unicidad en BD y 409 ante duplicados; hashing antes de insertar. Rotación: transacción READ COMMITTED con fila del usuario `FOR UPDATE NOWAIT`, revocación e inserción atómicas. SQLSTATE 55P03 →409 después del rollback, sin reintentos. Índice único parcial por usuario `WHERE is_active=true` y CHECK de coherencia con `revoked_at`.

La competencia se define en la adquisición del bloqueo durante la rotación: quien llega después del commit realiza una nueva rotación válida. No se promete deduplicación de toda ventana HTTP. Prueba determinista: detener ganador tras el lock, ejecutar competidoras, observar 409 y liberar ganador. Probar rollback conservando clave anterior y operaciones de usuarios distintos.

### Consulta y validación

Filtros parciales literales ILIKE con escape de `%`, `_` y `!`, parámetros vinculados y AND entre filtros presentes; trimming de extremos, vacío/caracteres de control →400. No eliminar tildes. `page` desde 1, `limit` 1–100 (20 por defecto), orden estable por UUID, página fuera de rango →200 con lista vacía y totales reales. Validar overflow de offset y enteros de 32 bits.

Username alfanumérico ASCII de 3–30 caracteres, normalizado a minúsculas para unicidad; corregir ejemplos anteriores con `_` para respetar la regla normativa. Email máximo 254, trim y minúsculas, único. Rechazar campos JSON desconocidos y parámetros repetidos/desconocidos con 400. [Modelo de datos](./data-model.md) y [contrato](./contracts/openapi.json) detallan las restricciones.

### Pruebas

| Historia | Dominio puro | Service/Repository con PostgreSQL | E2E MockMvc |
|----------|--------------|----------------------------------|-------------|
| US1 / FR-001–004 | Invariantes Usuario | Registro, hashing, duplicados y búsquedas | 201, login 200, 400, 401, 409 |
| US2 / FR-005–008,012 | Estado APIKey | Hash, una activa, rotación, rollback, concurrencia | JWT válido/ausente/inválido/expirado, secreto una vez, 409 |
| US3 / FR-009–011 | Invariantes Jugador | Filtros literales, AND, orden, paginación, totales | Madrid en ambos equipos, mayúsculas, 400, 401 y revocación en curso |

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

[quickstart.md](./quickstart.md) define validación local. Postman debe incluir cuatro endpoints, variables y errores; verificar importación/ejecución. CI ejecuta build, todas las pruebas y JaCoCo; análisis SonarCloud del commit y comprobación explícita de conteos constitucionales además del Quality Gate estándar. Guardar comandos, resultados y enlaces en `validation.md`, a crear al implementar. Los gates pendientes bloquean entrega.

## Seguimiento de complejidad

Sin excepciones constitucionales en el diseño. Boot 4/JUnit 5 se resuelve con versiones compatibles; el raíz existente mantiene la estructura por capas.

## Traspaso a tareas

Regenerar `tasks.md` con `$speckit-tasks`: reemplazar Maven, raíz `com/devchester/united` y `jjwt`; agregar FR-012, concurrencia, autorización en curso, k6/semilla, configuración global de test y contratos. Conservar controles C1–C3 y estado pendiente. La planificación termina en fase 1; la aplicación aún no se implementa.