# Investigacion tecnica: contrato y adquisicion de estadisticas

**Feature**: 002-player-statistics-sync | **Fecha**: 2026-10-10

## Decisiones cerradas

### Contrato del puerto

`ProveedorEstadisticasJugador` representa una adquisicion completa por
competicion. El Service no recibe familias separadas ni conoce
`summary/all`, `summary/offensive` o `summary/defensive`.

El Adapter realiza las tres adquisiciones, valida cada respuesta, traduce y
consolida por `playerId`. Luego devuelve un snapshot completo, normalizado y
asociado a una sola competicion. Las familias de WhoScored son un detalle
interno del Adapter.

### Paginacion

La primera implementacion no pagina `statisticsfeed`. El Adapter solicita un
limite suficientemente alto y solo acepta una respuesta completa en una pagina
cuando se cumple:

- `currentPage == 1`;
- `totalPages == 1`;
- `players.size == totalResults`;
- no existen contradicciones en la metadata.

`numberOfPlayersToPick=350` fue validado experimentalmente, pero no es una
garantia universal. Si `totalPages > 1` o la respuesta es incompleta, el
Adapter falla de forma controlada. No se valida ni publica el snapshot parcial
y el Service conserva el snapshot anterior. Una paginacion futura debera
permanecer encapsulada en el Adapter y no cambiar el contrato del Service.

### Ciclo de vida Selenium

Una sincronizacion completa reutiliza una unica sesion WebDriver para las cinco
competiciones. El Adapter o su infraestructura crea y cierra la sesion. El
cierre se garantiza ante excepciones. Selenium no cruza al Service ni al
dominio.

### Configuracion de competiciones y temporada

`stageId` y `tournamentOptions` son configuracion de infraestructura exclusiva
de WhoScored:

| Competicion | tournamentOptions | stageId |
|---|---:|---:|
| Premier League | 2 | 25544 |
| La Liga | 4 | 25662 |
| Serie A | 5 | 25518 |
| Bundesliga | 3 | 25666 |
| Ligue 1 | 22 | 25554 |

`isCurrent=true` puede enviarse al proveedor, pero no identifica por si solo la
temporada. Los IDs no forman parte del dominio.

### Fallos del proveedor

JSON invalido, metadata incoherente, estructura inesperada, navegacion fallida,
bloqueo del proveedor o adquisicion incompleta se traducen a error de
proveedor, con detalles sanitizados. La sincronizacion se aborta, no promueve
staging parcial y conserva el ultimo snapshot valido. No se implementan
mecanismos de evasión, stealth ni bypass.

## Adquisicion validada

El endpoint real navegado mediante Selenium es
`/statisticsfeed/1/getplayerstatistics`; la navegacion devuelve JSON en el body
del navegador.

Para cada competicion el Adapter solicita:

| Familia interna | category | subcategory |
|---|---|---|
| GENERAL | summary | all |
| OFENSIVA | summary | offensive |
| DEFENSIVA | summary | defensive |

Parametros comunes relevantes: `statsAccumulationType=0`, `isCurrent=true`,
`stageId`, `tournamentOptions`, `sortBy=Rating`, `field=Overall`, `isMinApp=true`
y `numberOfPlayersToPick`.

La prueba manual de Premier League 2026/2027 uso `tournamentOptions=2`,
`stageId=25544` y `numberOfPlayersToPick=350`. Las tres respuestas devolvieron
`currentPage=1`, `totalPages=1`, `totalResults=315`, `resultsPerPage=350` y
315 jugadores.

## Mapeo conceptual de metricas

La inspeccion de `MetricasEstadisticasJugador`, `RegistroEstadisticasJugador`,
`EstadisticasJugadorEntity` y `V15__Create_player_statistics_sync.sql` confirma
que el dominio ya tiene estos campos persistidos:

| Metrica interna existente | Campo real WhoScored | Familia de origen | Estado |
|---|---|---|---|
| `minutosJugados` | `minsPlayed` | Summary, Offensive o Defensive | Mapeo inequívoco; consolidar el valor disponible |
| `goles` | `goal` | Summary u Offensive | Mapeo inequívoco |
| `asistencias` | `assistTotal` | Summary u Offensive | Mapeo inequívoco |
| `tiros` | `shotsPerGame` | Summary u Offensive | Mapeo inequívoco |
| `pasesClave` | `keyPassPerGame` | Offensive | Mapeo inequívoco |
| `regates` | `dribbleWonPerGame` | Offensive | Mapeo inequívoco |
| `tackles` | `tacklePerGame` | Defensive | Mapeo inequívoco |
| `valoracion` | `rating` | Summary | Mapeo inequívoco |
| `tarjetasAmarillas` | `yellowCard` | Summary | Mapeo inequívoco |
| `tarjetasRojas` | `redCard` | Summary | Mapeo inequívoco |
| `posicion` | No documentado en el JSON disponible | No determinada | Pendiente |

### Precedencia canónica

Cuando un campo aparezca en más de una familia, el Adapter utiliza siempre la
fuente canónica siguiente y una familia secundaria no puede sobrescribirla:

| Familia canónica | Métricas |
|---|---|
| Summary | `minutosJugados <- minsPlayed`, `goles <- goal`, `asistencias <- assistTotal`, `tiros <- shotsPerGame`, `valoracion <- rating`, `tarjetasAmarillas <- yellowCard`, `tarjetasRojas <- redCard` |
| Offensive | `pasesClave <- keyPassPerGame`, `regates <- dribbleWonPerGame` |
| Defensive | `tackles <- tacklePerGame` |

El Adapter puede validar coherencia entre valores redundantes para detectar
cambios del proveedor, pero esa validación no agrega métricas ni altera la
fuente canónica. `posicion` continúa siendo opcional y no bloquea la adquisición
por su ausencia.

Las métricas `aerialWonPerGame`, `manOfTheMatch`, `foulGivenPerGame`,
`offsideGivenPerGame`, `dispossessedPerGame`, `turnoverPerGame`,
`interceptionPerGame`, `foulsPerGame`, `offsideWonPerGame`, `clearancePerGame`,
`wasDribbledPerGame`, `outfielderBlockPerGame` y `goalOwn` no se agregan al
dominio. `goalOwn` no se usa como sustituto de `goles`.

El manejo exacto de una métrica repetida entre familias y la fuente definitiva
de `posicion` siguen siendo decisiones de implementación pendientes si el JSON
real no entrega una regla inequívoca.

## Pruebas

Los tests automatizados usan JSON controlado, fixtures, fakes o mocks y no
consumen WhoScored real. Selenium real es únicamente diagnóstico/manual.
