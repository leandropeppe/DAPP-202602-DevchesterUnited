# Modelo de datos: autenticación y catálogo

## Convenciones

Dominio puro bajo `com.devchesterunited.devchesterunitedv2.modelo`; nombres de campos Java en español sin acentos. Entidades JPA separadas bajo `persistence/dto`. Tablas en PostgreSQL y timestamps `timestamptz`/Instant UTC. UUID como PK interno; API representa usuarios con `usr_` y jugadores con `ply_` más UUID. Los identificadores API son opacos: los ejemplos de la spec no fijan UUID concretos.

Service realiza las conversiones entre DTOs, dominio y persistencia. Repository no invoca invariantes o transiciones de dominio. Flyway controla esquema y semillas; Hibernate valida, no crea tablas.

## Usuario → usuarios

| Dominio | Persistencia | Tipo y regla |
|---------|--------------|--------------|
| id | id | UUID PK no nulo |
| nombreUsuario | username | varchar(30), 3–30 ASCII alfanuméricos; trim y minúsculas; UNIQUE |
| correo | email | varchar(254), formato email, trim y minúsculas; UNIQUE |
| hashContrasena | password_hash | varchar(255), hash BCrypt, nunca texto plano |
| creadoEn | created_at | timestamptz no nulo |
| actualizadoEn | updated_at | timestamptz no nulo, >= created_at |

DTO de registro: todos los campos obligatorios; contraseña 8–72 caracteres y <=72 bytes UTF-8, al menos una mayúscula ASCII, minúscula ASCII y dígito; sin espacios externos ni caracteres de control. No guardar contraseña ni incluirla en toString/logs. El modelo garantiza identidad y hash no vacíos, coherencia temporal; el Service verifica disponibilidad y hashea mediante PasswordEncoder. Las restricciones UNIQUE resuelven carreras como 409. Login usa la misma normalización de username, no transforma contraseña; formato inválido →400, credenciales no coincidentes →401 con mensaje genérico.

Relación 1:N histórica con APIKey, máximo una activa. Sin roles administrativos operativos, reset de contraseña ni CRUD de usuarios en esta entrega.

## APIKey → api_keys

| Dominio | Persistencia | Tipo y regla |
|---------|--------------|--------------|
| id | id | UUID PK |
| usuarioId | user_id | UUID FK usuarios.id, no nulo, sin borrado en cascada |
| hashClave | key_hash | char(64), SHA-256 hexadecimal minúsculo, UNIQUE |
| prefijo | key_prefix | varchar(14), primeros 14 caracteres del token |
| activa | is_active | boolean no nulo |
| creadaEn | created_at | timestamptz no nulo |
| revocadaEn | revoked_at | timestamptz nulo solo mientras está activa |

Índice único parcial `user_id WHERE is_active=true`; índice UNIQUE del hash para autenticar. CHECK: activa implica revoked_at NULL; revocada implica fecha >= created_at. Sin `expires_at`, sin vencimiento por tiempo (FR-012).

Token: `dev_key_` + Base64URL sin padding de 32 bytes aleatorios (43 caracteres); 51 caracteres totales. Solo vive transitoriamente en Service/respuesta. El prefijo es orientativo, no permite autenticación. Los tokens ilustrativos anteriores de la spec no son credenciales utilizables ni definen su longitud.

Transición: creación → activa; rotación → anterior revocada + nueva activa, atómicamente. No reactivar claves. Modelo aplica revocación y coherencia de estado; Service orquesta lock NOWAIT sobre usuario y persistencia. SQLSTATE55P03 →409 sin cambios; rollback de inserción conserva la anterior. Respuesta HTTP solo tras commit. Pérdida de respuesta no permite recuperar la clave; una rotación posterior genera otra.

## Jugador → jugadores

| Dominio | Persistencia/API | Tipo y regla |
|---------|------------------|--------------|
| id | id | UUID PK; API `ply_<uuid>` |
| nombre | first_name | varchar(100), no vacío |
| apellido | last_name | varchar(100), no vacío |
| liga | league | varchar(100), no vacío |
| equipo | team | varchar(100), no vacío |
| posicion | position | varchar(30), Forward/Midfielder/Defender/Goalkeeper |
| dorsal | jersey_number | integer 1–99 |
| nacionalidad | nationality | varchar(100), no vacío |

Las restricciones anteriores son decisiones de diseño para la semilla y el modelo. No inferir unicidad por nombre o dorsal entre equipos. Un jugador tiene una liga, equipo y posición principal. No tablas de asociaciones complejas ni endpoints de escritura. La semilla local incluye ambos equipos de Madrid para comprobar la búsqueda parcial.

## Consulta del catálogo

DTO de consulta con league/team hasta 100 caracteres y position hasta 30, tras trim; vacíos o controles →400. Escape de comodines SQL; tildes significativas. AND entre filtros presentes; filtros ausentes no restringen. Unicode admitido para nombres, sin concatenación de SQL.

page: entero 1–2147483647, default 1; limit: entero 1–100, default 20. Offset `(page-1)*limit` calculado con tipo amplio y control de rango del motor; overflow no soportado →400. Orden por UUID ascendente antes de paginar. `total_records` cuenta todas las coincidencias y `total_pages=ceil(total_records/limit)`; cero resultados implica total_pages=0. Página fuera de rango conserva current_page solicitado y totales reales con players vacío. No exponer directamente el JSON interno de Spring Page.

## Errores y controles

400 para JSON/validación/parámetros inválidos, 401 para credenciales ausentes/no válidas, 409 para duplicados y competencia de rotación, 503 para dependencia de BD inaccesible y 500 para fallo inesperado. Todos usan el esquema Error en OpenAPI, sin stack traces, SQL o secretos. No revelar existencia del usuario en login.

## Migraciones y validación

V1 usuarios y restricciones; V2 api_keys e índice parcial; V3 jugadores y semilla local mínima. Fixture de rendimiento separado, determinista y solo en BD descartable según `plan.md`. Probar migración desde vacío, validación del esquema, unicidad, FK, estados y rollback contra PostgreSQL real. No editar migraciones ya aplicadas; cambios futuros usan nueva versión.
