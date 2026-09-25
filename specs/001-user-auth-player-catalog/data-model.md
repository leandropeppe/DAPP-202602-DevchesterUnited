# Modelo de datos: autenticación, equipos y catálogo sincronizado

**Revisión**: 2026-09-15. Diseño objetivo para US4/US5; V4 y las entidades nuevas todavía no están implementadas.

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

Token: `dev_key_` + Base64URL sin padding de 32 bytes aleatorios (43 caracteres); 51 caracteres totales. En backend solo vive transitoriamente en Service/respuesta. El frontend lo conserva durante la pestaña para consumir catálogo, sin volver a mostrarlo completo tras la generación. El prefijo es orientativo, no permite autenticación. Los tokens ilustrativos anteriores de la spec no son credenciales utilizables ni definen su longitud.

Transición: creación → activa; rotación → anterior revocada + nueva activa, atómicamente. No reactivar claves. Modelo aplica revocación y coherencia de estado; Service orquesta lock NOWAIT sobre usuario y persistencia. SQLSTATE55P03 →409 sin cambios; rollback de inserción conserva la anterior. Respuesta HTTP solo tras commit. Pérdida de respuesta no permite recuperar la clave; una rotación posterior genera otra.

## Equipo → equipos

| Dominio | Persistencia | Tipo y regla |
|---------|--------------|--------------|
| id | id | UUID PK, estable aun al inactivar/reactivar |
| idExterno | external_id | bigint positivo, UNIQUE; null solo en datos locales previos |
| nombre | name | varchar(100), no vacío |
| nombreCorto | short_name | varchar(100), nullable |
| sigla | tla | varchar(10), nullable |
| liga | league | varchar(100), nombre canónico; equipos externos limitados a las cinco ligas |
| escudoUrl | crest_url | text nullable, URI HTTPS validada; nunca se descarga en el backend ni se muestra en este MVP |
| activo | is_active | boolean no nulo |
| temporadaExternaId | provider_season_id | bigint positivo en equipo importado; null solo en local |
| inicioTemporada / finTemporada | season_start / season_end | date del proveedor, orden coherente; null solo en local |
| creadoEn / actualizadoEn | created_at / updated_at | timestamptz; actualizadoEn >= creadoEn |

No inferir identidad externa por nombre; equipos locales migrados mantienen `external_id=null`. Su liga puede contener valores de fixtures previos. Cada equipo importado pertenece a una de las cinco ligas y su identidad no depende de nombre ni temporada. Al desaparecer de un snapshot completo queda inactivo, sin eliminarse; al reaparecer se actualiza/reactiva sobre el mismo UUID. Temporada recibida se registra para trazabilidad, sin tablas ni endpoints de historia deportiva.

## Jugador → jugadores

| Dominio | Persistencia/API | Tipo y regla |
|---------|------------------|--------------|
| id | id | UUID PK; API `ply_<uuid>` |
| idExterno | external_id | bigint positivo UNIQUE; null solo en datos locales previos |
| nombre | name | varchar(200), no vacío; representación vigente de dominio, persistencia y API |
| campos históricos | first_name / last_name | Compatibilidad interna con el esquema anterior; no forman parte de la representación REST vigente |
| equipoId | team_id | UUID FK equipos.id no nulo, sin eliminación en cascada |
| posicion | position | varchar(30) nullable; Forward/Midfielder/Defender/Goalkeeper cuando está informada |
| dorsal | jersey_number | Integer 1–99 o null; nunca 0 como sustituto |
| nacionalidad | nationality | varchar(100), no vacío |
| activo | is_active | boolean no nulo |
| creadoEn / actualizadoEn | created_at / updated_at | timestamptz; actualizadoEn >= creadoEn |

El DTO público expone `name` como nombre completo, junto con los campos públicos vigentes; `league` y `team` se proyectan desde Equipo y no se duplican como columnas de Jugador. No expone external_id, team_id, actividad ni metadatos de temporadas. `jersey_number` se serializa explícitamente aunque sea null. Los campos históricos `first_name`/`last_name` pueden conservarse para compatibilidad interna, pero no se publican ni sustituyen a `name`. Índices: UNIQUE external_id y B-tree team_id; no agregar índices de subcadena sin medir.

Mantener las validaciones de los constructores legados para sus entradas actuales. La nueva fábrica explícita de importación recibe y conserva `name` como nombre completo, exige ID externo/equipo y admite dorsal y posición null; comparte todas las otras invariantes. No asignar apellidos inventados. Si se mantienen `first_name`/`last_name` por compatibilidad interna, pueden proyectar datos históricos sin formar parte del DTO REST ni alterar `name`. Nacionalidad ausente o una posición informada pero no soportada invalidan el snapshot; una posición null representa que el proveedor no clasificó al jugador.

Un jugador pertenece a un único Equipo y tiene una posición principal; no hay historial de afiliaciones. Si reaparece en otro equipo en un snapshot inequívoco, cambia team_id sin cambiar UUID. Jugadores ausentes se inactivan; presentes se reactivan. Un jugador activo nunca se publica con equipo inactivo. No inferir unicidad por nombre o dorsal. Duplicados externos idénticos en el mismo equipo se colapsan; duplicados conflictivos entre equipos impiden publicar.

## Snapshot y resultado de sincronización

El modelo vigente de `Jugador` utiliza únicamente `name`, persistido en `jugadores.name` (`varchar(200)`, no vacío). `position` y `jersey_number` pueden ser null cuando el proveedor no informa esos datos. Las referencias a `first_name`/`last_name` describen compatibilidad con el esquema anterior y no representan el contrato REST vigente.

`CatalogoSincronizado` es un objeto de dominio puro en memoria, no una entidad JPA: contiene cinco ligas con temporada, 96 equipos y jugadores deduplicados. Valida el reparto 20/20/18/20/18, IDs, pertenencia y referencias. Service resuelve las entidades persistidas y aplica las transiciones antes de guardarlas; Repository nunca invoca reglas del modelo.

Orden de aplicación dentro de la transacción: equipos presentes → jugadores presentes y transferencias → jugadores ausentes inactivos → equipos ausentes inactivos. No realizar borrados físicos. Solo al primer snapshot válido, desactivar todos los registros locales sin external_id para que la semilla no se mezcle con datos reales; conservarlos como historial técnico. Los fallos revierten todo, incluida esa desactivación.

El lock usa `pg_try_advisory_xact_lock` con clave fija `(114644, 1)`, reservada para catálogo, sin tabla de jobs ni fila por usuario. Operación Repository devuelve booleano; Service decide 409. Se libera al commit/rollback. No hay acumulación de locks de sesión en conexiones reutilizadas.

Contadores de respuesta: `teams_processed=96`; `players_processed` es el número de jugadores externos únicos del snapshot. `*_created` cuenta IDs externos sin registro previo; `*_updated` cuenta IDs ya existentes procesados, aunque no hayan cambiado, incluyendo reactivados. Por tanto created + updated = processed. Los ausentes inactivados no cuentan como procesados. UUID y created_at se conservan; updated_at cambia solo ante modificación del estado/datos. Metadatos de temporada pueden cambiar sin duplicar equipos.

## Consulta del catálogo

DTO de consulta con league/team hasta 100 caracteres y position hasta 30, tras trim; vacíos o controles →400. Escape de comodines SQL; tildes significativas. AND entre filtros presentes; filtros ausentes no restringen. Unicode admitido para nombres, sin concatenación de SQL.

page: entero 1–2147483647, default 1; limit: entero 1–100, default 20. Offset `(page-1)*limit` calculado con tipo amplio y control de rango del motor; overflow no soportado →400. JOIN jugadores→equipos con ambos activos, filtros parametrizados sobre campos canónicos y orden por UUID de jugador. `total_records` cuenta solo coincidencias consultables y `total_pages=ceil(total_records/limit)`; cero resultados implica total_pages=0. Página fuera de rango conserva current_page solicitado y totales reales con players vacío. Conteo y página conservan transacción REPEATABLE_READ existente. No exponer directamente el JSON interno de Spring Page.

## Errores y controles

400 para JSON/validación/parámetros inválidos, 401 para credenciales ausentes/no válidas, 409 para duplicados, competencia de rotación o sincronización global ocupada, 503 para BD/proveedor inaccesible, configuración externa ausente, cuota/timeout o snapshot no utilizable y 500 para fallo inesperado. Todos usan el esquema Error en OpenAPI, sin stack traces, SQL, cuerpos del proveedor o secretos. No revelar existencia del usuario en login.

## Migraciones y validación

V1–V3 se conservan byte a byte. V4 debe ejecutarse tanto tras V3 existente como en una base vacía que recorre todo el historial:

1. Crear equipos locales distintos por `(league, team)` exactos y copiar sin normalizar nombres/ligas. Asignar UUID internos nuevos a equipos y external_id null.
2. Agregar team_id temporalmente nullable, enlazar cada jugador a su equipo y verificar cobertura completa antes de exigir FK/NOT NULL.
3. Agregar external_id, is_active (inicialmente true), timestamps con fecha de migración y columnas de temporada en equipos; índices/checks descritos. UUID de jugadores y datos previos se conservan.
4. Quitar NOT NULL de jersey_number, conservar CHECK de rango para valores presentes; ajustar CHECK de apellido para el caso importado documentado.
5. Eliminar columnas redundantes league/team de jugadores solo después del backfill verificado; despliegue coordinado de V4 y backend nuevo, sin convivencia con versión antigua.

La migración no adivina IDs del proveedor ni borra datos. La implementación debe ofrecer respaldo/rollback operacional mediante restauración de la BD y versión compatibles; no editar ni revertir el historial aplicado. Pruebas contra PostgreSQL: datos/UUID preservados, constraints, fixtures normalizados y fallo antes de completar V4 sin esquema parcial.

Los inserts SQL de `JugadorE2ETest`, `JugadorRepositoryIntegrationTest` y `JugadorServiceIntegrationTest` requieren fixtures con equipos y team_id. Preparar adaptación conservando el contenido canónico y solicitar autorización antes de editar tests existentes. Añadir tests nuevos para migración, sync y null. Fixture de rendimiento separado en BD descartable: conserva volumen/distribución históricos de 10.000 jugadores, 20 ligas, 400 equipos y 1.000 usuarios, con IDs externos null; es carga sintética de consulta, no un snapshot admitido por sincronización.
