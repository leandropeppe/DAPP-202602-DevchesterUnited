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

La decision productiva es `isMinApp=false` y `numberOfPlayersToPick=500`. El
diagnostico real verifico un maximo actual de 472 jugadores por competicion,
por lo que se realiza una llamada por familia/competicion y no se implementa
paginacion. Si `totalPages > 1` o la respuesta es incompleta, el
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
`stageId`, `tournamentOptions`, `sortBy=Rating`, `field=Overall`, `isMinApp=false`
y `numberOfPlayersToPick=500`.

El diagnostico real de las cinco competiciones verifico `currentPage=1`,
`totalPages=1`, `players.size == totalResults` y `totalResults <= resultsPerPage`
en una unica llamada por familia/competicion. El maximo actual fue 472
jugadores por competicion, dentro de `resultsPerPage=500`.

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
## Decisiones de cierre frontend

### Navegacion

Decision: reutilizar la navegacion manual existente con la ruta
`/catalogo/jugadores/{id}` y el evento `popstate`.

Rationale: `App.jsx` ya resuelve rutas y publica navegaciones sin una libreria
de routing. Agregar una dependencia para una unica vista ampliaria el cambio
sin aportar valor y podria alterar el comportamiento existente.

Alternatives considered: mantener el detalle dentro de la misma vista del
catalogo o agregar una libreria de routing. La primera no cumple claramente la
separacion de pagina solicitada; la segunda no es necesaria para las
convenciones actuales.

### Control navegable del catalogo

Decision: conservar la tabla responsive y hacer navegable cada fila mediante un
enlace semantico con nombre accesible e identificador opaco.

Rationale: en escritorio se mantiene el diseno visual actual y en movil las
filas ya se comportan como cards. Un enlace permite teclado, historial y
lectores de pantalla sin depender de un click no semantico.

Alternatives considered: reemplazar toda la tabla por cards nuevas o usar
`onClick` sobre `tr`. Ambas opciones aumentan el alcance y la segunda degrada
accesibilidad.

### Contrato de detalle

Decision: agregar solo un lector frontend para `GET /api/v1/players/{id}` y
usar `data` como fuente del detalle, distinguiendo `statistics === null` de un
error HTTP.

Rationale: el endpoint ya existe, esta documentado y tiene pruebas E2E de
backend para las metricas requeridas, por lo que el frontend no debe duplicar
la logica ni modificar el contrato.

Alternatives considered: ampliar `GET /api/v1/players` con estadisticas o
consultar una fuente externa desde React. Ambas alternativas rompen el alcance
y las reglas de arquitectura.

### Estados y recuperacion

Decision: la vista mantiene estados explicitos de carga, exito con estadisticas,
exito sin estadisticas y error; el error muestra mensaje existente y enlace de
regreso al catalogo.

Rationale: diferencia ausencia valida de datos de un fallo de consulta y evita
una pantalla vacia o un valor estadistico inventado.

### Nacionalidad

Decision: sustituir la columna Dorsal por Nacionalidad y mostrar el helper de
ausencia ya usado por el catalogo cuando el dato no este disponible.

Rationale: nacionalidad ya forma parte del dato del jugador y no requiere
scraping, matching o persistencia adicional. El dorsal no es confiable y no se
debe convertir su ausencia en `0`.

### Estado final validado

El flujo vigente no conserva el contrato HTTP ficticio ni sus DTOs, paginacion
o parametros `competition/family/page/pageSize`. El Adapter devuelve snapshots
completos adquiridos por Selenium con `isMinApp=false` y
`numberOfPlayersToPick=500`; actualmente no se requiere paginacion porque las
cinco competiciones fueron recuperadas completas con una llamada por familia.
El mapping aprobado de equipos debe importarse desde
`backend/config/whoscored-team-enrollment-approved.csv` antes de la primera
sincronizacion. Los jugadores individualmente no resolubles se omiten sin
abortar la publicacion. La evidencia productiva final fue 2175 adquiridos y
1968 publicados.
