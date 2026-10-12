# Plan de implementacion: Sincronizacion de estadisticas de jugadores

**Branch**: 002-player-statistics-sync | **Fecha**: 2026-10-10 | **Spec**: spec.md

## Resumen

La feature sincroniza una fotografia vigente de estadisticas hacia Jugador
local existente. WhoScored se consume mediante `ProveedorEstadisticasJugador`;
`ProveedorCatalogo` continua siendo exclusivamente el puerto del catalogo
Football-Data.

El puerto representa una adquisicion completa por competicion. El Adapter
realiza las tres adquisiciones internas de WhoScored, las valida, traduce y
consolida por `playerId`. El Service recibe un unico snapshot normalizado por
competicion y no conoce familias del proveedor, Selenium ni paginacion.

El contrato ficticio anterior `GET /statistics` con `competition`, `family`,
`page`, `pageSize` y Bearer API key queda **superseded**.

La implementacion vigente utiliza exclusivamente snapshots completos adquiridos
por Selenium desde `statisticsfeed`. No existe un flujo HTTP legacy de
WhoScored en el adapter. La configuracion validada es `isMinApp=false` y
`numberOfPlayersToPick=500`; el limite real observado permite una llamada por
familia y competicion, sin paginacion.

## Flujo arquitectonico

1. El Service adquiere exclusion y crea una ejecucion `INICIADA`.
2. El Adapter crea una unica sesion WebDriver para la sincronizacion completa.
3. Para cada competicion el Adapter adquiere `summary/all`, `summary/offensive` y `summary/defensive`.
4. El Adapter valida cada JSON y exige respuesta completa en una pagina.
5. El Adapter consolida las tres familias por `playerId` y devuelve un snapshot completo.
6. El Service resuelve identidades persistidas o ejecuta enrolamiento conservador.
7. El Service consolida/escribe staging mediante transacciones cortas.
8. Se validan las cinco competiciones antes de marcar la ejecucion como `VALIDADA`.
9. Una transaccion promueve estadisticas, identidades nuevas y `PUBLICADA`.
10. El Adapter cierra WebDriver incluso ante excepciones y el Service libera la exclusion.

El cierre de WebDriver, la navegacion y cualquier futura paginacion son
responsabilidad del Adapter/infraestructura. El Service no modifica su contrato
si la paginacion futura se encapsula dentro del Adapter.

## Adquisicion y completitud

Endpoint real: `/statisticsfeed/1/getplayerstatistics`.

Parametros comunes: `statsAccumulationType=0`, `isCurrent=true`, `sortBy=Rating`,
`field=Overall`, `isMinApp=false` y `numberOfPlayersToPick=500`. El diagnostico
real verifico un maximo actual de 472 jugadores por competicion, por lo que se
realiza una llamada por familia/competicion sin paginacion.

La primera implementacion no pagina. Se acepta solo metadata coherente con una
respuesta completa en una pagina:

- `currentPage == 1`;
- `totalPages == 1`;
- `players.size == totalResults`;
- sin contradicciones entre metadata y contenido.

Si se incumple, el Adapter traduce el problema a error de proveedor. No se
publica staging parcial y se conserva el snapshot anterior. Una futura
paginacion permanece dentro del Adapter.

## Configuracion de infraestructura

Los pares no pertenecen al dominio:

- Premier League: `tournamentOptions=2`, `stageId=25544`.
- La Liga: `tournamentOptions=4`, `stageId=25662`.
- Serie A: `tournamentOptions=5`, `stageId=25518`.
- Bundesliga: `tournamentOptions=3`, `stageId=25666`.
- Ligue 1: `tournamentOptions=22`, `stageId=25554`.

`isCurrent=true` puede utilizarse como filtro del proveedor, pero no es la
unica fuente para identificar temporada.

## Resolucion, staging y atomicidad

Se conservan la resolucion de equipos y jugadores, las identidades namespaced,
la consolidacion, el staging, el upsert, la idempotencia, el rollback, la
conservacion del snapshot anterior y la exclusion concurrente.

JSON invalido, metadata incoherente, estructura inesperada, navegacion fallida,
bloqueo del proveedor o adquisicion incompleta abortan la sincronizacion. Los
detalles se sanitizan y no se implementan stealth, evasiones ni bypass.

## Mapeo de metricas

Se persisten solamente las metricas ya existentes en el dominio. La precedencia
canonica es Summary para minutos, goles, asistencias, tiros, valoracion y
tarjetas; Offensive para pases clave y regates; Defensive para tackles. Una
familia secundaria no sobrescribe la fuente canonica. Los mapeos detallados se
encuentran en `research.md`; `posicion` queda pendiente porque no aparece en el
JSON real documentado y su ausencia no bloquea la adquisicion.

## Constitution Check

- Selenium, JSON, IDs de infraestructura y errores del proveedor quedan en Adapter.
- Service depende de un puerto y recibe un snapshot completo por competicion.
- Modelo permanece independiente de infraestructura.
- Una unica sesion WebDriver se cierra ante exito o excepcion.
- Tests no consumen WhoScored real ni requieren credenciales reales.
- Adquisicion y promocion permanecen separadas.
- Fallos no dejan actualizaciones parciales visibles.

No se detecta incompatibilidad con la Constitution.

## Decisiones superseded

- `GET /statistics` como endpoint de adquisicion.
- `competition`, `family`, `page` y `pageSize` como contrato externo.
- Bearer API key como mecanismo documentado.
- Familias separadas consumidas por el Service.
- Paginacion controlada por el Service.

## Estado validado para reproduccion

El enrollment inicial requiere importar el mapping aprobado de equipos
WhoScored/Football-Data versionado en
`backend/config/whoscored-team-enrollment-approved.csv`. Una identidad
individual de jugador no resoluble de forma segura se omite sin abortar la
publicacion; los errores tecnicos, estructurales, de equipo, staging o
promocion siguen siendo fatales. La validacion productiva registro 2175
jugadores adquiridos y 1968 publicados.
