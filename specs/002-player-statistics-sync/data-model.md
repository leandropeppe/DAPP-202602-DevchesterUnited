# Modelo de datos: estadísticas completas de jugadores

## 1. Composición de dominio

El dominio no representa las 30 métricas como un record plano ni utiliza DTOs
de WhoScored.

```java
public record MetricasEstadisticasJugador(
        MetricasGenerales generales,
        MetricasOfensivas ofensivas,
        MetricasDefensivas defensivas,
        MetricasPases pases
) {}
```

Cada familia es un record de dominio independiente, inmutable y con campos
numéricos anulables según la ausencia legítima de información.

## 2. Familias y campos

### MetricasGenerales — 8 campos

| Campo de dominio | Campo WhoScored | Tipo conceptual |
|---|---|---|
| `apariciones` | `apps` | entero anulable |
| `ingresosSuplente` | `subOn` | entero anulable |
| `minutosJugados` | `minsPlayed` | entero anulable |
| `vecesJugadorDelPartido` | `manOfTheMatch` | entero anulable |
| `duelosAereosGanadosPorPartido` | `aerialWonPerGame` | decimal anulable |
| `valoracion` | `rating` | decimal anulable |
| `tarjetasAmarillas` | `yellowCard` | entero anulable |
| `tarjetasRojas` | `redCard` | entero anulable |

### MetricasOfensivas — 9 campos

| Campo de dominio | Campo WhoScored | Tipo conceptual |
|---|---|---|
| `goles` | `goal` | entero anulable |
| `asistencias` | `assistTotal` | entero anulable |
| `tirosPorPartido` | `shotsPerGame` | decimal anulable |
| `pasesClavePorPartido` | `keyPassPerGame` | decimal anulable |
| `regatesGanadosPorPartido` | `dribbleWonPerGame` | decimal anulable |
| `faltasRecibidasPorPartido` | `foulGivenPerGame` | decimal anulable |
| `fuerasDeJuegoPorPartido` | `offsideGivenPerGame` | decimal anulable |
| `desposesionesPorPartido` | `dispossessedPerGame` | decimal anulable |
| `perdidasPorPartido` | `turnoverPerGame` | decimal anulable |

### MetricasDefensivas — 8 campos

| Campo de dominio | Campo WhoScored | Tipo conceptual |
|---|---|---|
| `tacklesPorPartido` | `tacklePerGame` | decimal anulable |
| `intercepcionesPorPartido` | `interceptionPerGame` | decimal anulable |
| `faltasCometidasPorPartido` | `foulsPerGame` | decimal anulable |
| `offsidesProvocadosPorPartido` | `offsideWonPerGame` | decimal anulable |
| `despejesPorPartido` | `clearancePerGame` | decimal anulable |
| `regatesRecibidosPorPartido` | `wasDribbledPerGame` | decimal anulable |
| `bloqueosPorPartido` | `outfielderBlockPerGame` | decimal anulable |
| `golesEnContra` | `goalOwn` | entero anulable |

### MetricasPases — 5 campos

| Campo de dominio | Campo WhoScored | Tipo conceptual |
|---|---|---|
| `pasesPorPartido` | `totalPassesPerGame` | decimal anulable |
| `precisionPases` | `passSuccess` | decimal anulable, porcentaje 0..100 |
| `centrosPrecisosPorPartido` | `accurateCrossesPerGame` | decimal anulable |
| `pasesLargosPrecisosPorPartido` | `accurateLongPassPerGame` | decimal anulable |
| `pasesFiltradosPrecisosPorPartido` | `accurateThroughBallPerGame` | decimal anulable |

Los valores decimales conservan la escala decimal vigente mediante
estandarización de escala. Esto no es normalización a `[0,1]`. `passSuccess`
mantiene semántica de porcentaje: 86.44 se representa como 86.44.

## 3. Métricas repetidas y propiedad canónica

Las métricas repetidas no se agregan a más de una familia:

| Campo externo repetido | Propiedad canónica |
|---|---|
| `minsPlayed` | `generales.minutosJugados` |
| `rating` | `generales.valoracion` |
| `assistTotal` | `ofensivas.asistencias` |
| `keyPassPerGame` | `ofensivas.pasesClavePorPartido` |
| `passSuccess` | `pases.precisionPases` |

La consolidación ignora la copia secundaria para construir dominio, staging,
persistencia y API. Si la fuente canónica omite el valor, el resultado es
`null`; no se recupera desde una copia secundaria salvo que el contrato de la
fuente defina explícitamente esa copia como equivalente futura.

## 4. Registro persistente consolidado

`EstadisticasJugadorEntity` representa una fila de `jugador_estadisticas`:

- `jugador_id`: FK a `jugadores`, único y no nulo.
- 30 columnas de métricas, una por campo de dominio, anulables según el modelo.
- Referencia a la ejecución de sincronización y timestamps de auditoría
  existentes, sin exponerlos en la API.

La tabla no se divide por familia. La promoción realiza upsert por jugador y
reemplaza por `null` los campos ausentes de una sincronización válida.

La migración `V16__Expand_player_statistics_sync.sql` agrega las columnas que
no existen en V15 y conserva las columnas vigentes equivalentes. V15 no se
modifica.

## 5. Snapshot y staging

`SnapshotEstadisticasJugador` continúa siendo el contrato interno completo por
competición. Cada `RegistroEstadisticasJugador` contiene identidad externa,
identidad local resuelta y `MetricasEstadisticasJugador` consolidado.

`estadisticas_sync_staging` conserva una fila técnica por ejecución,
competición y jugador, con las 30 métricas o su ausencia, las marcas de las
cuatro familias y los identificadores necesarios para promoción. Staging no es
visible para consultas de negocio.

La ejecución conserva estados `INICIADA`, `RECIBIENDO`, `VALIDADA`, `PUBLICADA`
y `FALLIDA`. Si falla una familia o la promoción, no se publica staging parcial
y permanece la fila consolidada anterior.

## 6. Relaciones e identidad

```text
Jugador 1 ─── 0..1 EstadisticasJugadorEntity
Jugador 1 ─── N RegistroEstadisticasJugador (solo durante snapshot/staging)
EjecucionSync 1 ─── N EstadisticasSyncStagingEntity
```

La identidad externa `WHOSCORED/playerId` continúa resolviéndose dentro del
flujo vigente. Una identidad individual ambigua se omite y se informa; los
errores técnicos, estructurales, de equipo, staging o promoción abortan la
sincronización lógica.

## 7. Lectura pública y frontend

El detalle local mantiene `statistics: null` cuando no existe fila. Con fila,
serializa:

```text
statistics.generales
statistics.ofensivas
statistics.defensivas
statistics.pases
```

Todos los nombres públicos son español `camelCase`. No se exponen IDs técnicos,
metadatos de sincronización ni nombres de DTOs externos.

La vista distribuye el contenido en Resumen, Ataque, Defensa y Pases. Resumen
puede mostrar solo un subconjunto; las otras familias muestran sus campos
correspondientes. Una ausencia de un campo se presenta como no disponible,
manteniendo los estados de carga, error y `statistics: null`.
