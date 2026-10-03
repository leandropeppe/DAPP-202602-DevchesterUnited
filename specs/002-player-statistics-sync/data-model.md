# Modelo de Datos: Estadísticas de Jugadores

## Agregado de dominio: `EstadisticasJugador`

Representa la fotografía normalizada de estadísticas asociada a un `Jugador` existente. Su identidad funcional es `jugadorId`; no crea una identidad de jugador alternativa.

### Componentes

| Componente | Datos | Reglas |
|---|---|---|
| `Participacion` | `apps`, `minsPlayed` | Valores no negativos; cada dato puede estar ausente si la fuente no lo informa. |
| `EstadisticasOfensivas` | `goals`, `assists`, `shotsPerGame`, `keyPassPerGame`, `dribbleWonPerGame` | Conteos no negativos; promedios no negativos; ausencia explícita permitida. |
| `EstadisticasDefensivas` | `tacklePerGame` | Promedio no negativo; ausencia explícita permitida. |
| `Disciplina` | `yellowCard`, `redCard` | Conteos no negativos; ausencia explícita permitida. |
| `ValoracionGeneral` | `rating` | Valor decimal no negativo y dentro del rango aceptado por la fuente cuando esté informado. |

Los grupos evitan constructores de más de siete parámetros y mantienen cohesión conceptual. No se copiarán campos externos que no estén requeridos por el spec.

## Entidad persistida publicada

### `jugador_estadisticas`

| Campo conceptual | Persistencia | Regla |
|---|---|---|
| Identidad del registro | UUID local | Clave primaria propia del registro. |
| Jugador asociado | UUID `jugador_id` | FK a `jugadores.id`, NOT NULL, UNIQUE. |
| Participación | `apps`, `mins_played` | Nullable individualmente, no negativos cuando presentes. |
| Ofensiva | `goals`, `assists`, `shots_per_game`, `key_pass_per_game`, `dribble_won_per_game` | Nullable por métrica; validaciones de dominio. |
| Defensiva | `tackle_per_game` | Nullable; no negativo cuando presente. |
| Disciplina | `yellow_card`, `red_card` | Nullable; no negativos cuando presentes. |
| Valoración | `rating` | Nullable; validación de rango. |
| Contexto de edición vigente | referencia interna de edición/sincronización | Permite saber qué fotografía se publicó sin convertir el `stageId` en identidad universal de liga. |
| Auditoría | `created_at`, `updated_at` | Fechas coherentes y actualizadas en la promoción. |

La relación se resuelve por `jugador_id`; no se agrega otra fila por cada familia de estadísticas. El identificador externo del jugador permanece en la entidad `Jugador` existente.

## Entidades técnicas de staging

### `estadisticas_sync`

Representa una ejecución aislada: identificador, estado, inicio, finalización, competencias esperadas, familias esperadas y motivo de error técnico si la ejecución falla. No es visible para las consultas de negocio.

Estados: `INICIADA`, `RECIBIENDO`, `VALIDADA`, `PUBLICADA`, `FALLIDA`.

### `estadisticas_sync_staging`

Contiene una fila normalizada por `sync_id`, competición y `playerId`, con los grupos de métricas consolidados progresivamente y marcas de familia recibida.

Restricciones:

- clave única `(sync_id, competicion, player_id)`;
- `player_id` positivo;
- valores numéricos no negativos cuando estén presentes;
- marca independiente para `all`, `offensive` y `defensive`;
- no se publica mientras `sync_id` no esté `VALIDADA`;
- los reintentos de una misma página son idempotentes mediante la misma clave.

El staging permite descartar una ejecución incompleta sin tocar `jugador_estadisticas`.

## Correspondencia de identidad de fuente

El modelo no presupone que `Jugador.external_id` y `WhoScored.playerId` sean el mismo identificador: el código actual utiliza `Jugador.external_id` para el proveedor del catálogo. Si la validación de la implementación confirma namespaces distintos, se agregará una tabla técnica de correspondencias con:

- `jugador_id` como FK a `jugadores`;
- `fuente` como namespace de integración;
- `identificador_externo` como `playerId` de la fuente;
- unicidad por `(fuente, identificador_externo)` y por `(fuente, jugador_id)`.

La tabla no reemplaza ni duplica `Jugador`. Una identidad no correspondida impide la promoción de la sincronización; no se hará matching silencioso por nombre o equipo.

## Relaciones

```text
Jugador 1 ───── 0..1 EstadisticasJugador
Jugador 1 ───── N    EstadisticasSyncStaging (durante una sincronización)
EstadisticasSync 1 ─ N EstadisticasSyncStaging
```

## Reglas de consolidación

1. El `playerId` es la clave de unión entre las tres familias externas.
2. Cada familia puede aportar métricas nulas individualmente, pero debe tener una respuesta y secuencia de páginas válida para el conjunto esperado.
3. Antes de promover, todos los `playerId` deben resolverse contra un único `Jugador` local mediante `idExterno`.
4. Un jugador repetido con valores incompatibles, una familia incompleta o una referencia local inexistente invalida toda la ejecución.
5. La promoción actualiza la fila existente por `jugador_id` o crea una sola fila si todavía no existe.

## Migraciones previstas

- Nueva migración posterior a las existentes para `jugador_estadisticas`, con FK, unicidad, índices y restricciones numéricas.
- Nueva migración posterior para `estadisticas_sync` y `estadisticas_sync_staging`, con índices por ejecución, competición y jugador.
- No se modifica la tabla ni la entidad conceptual `jugadores`, salvo usar su clave externa e índice existente de `external_id`.
