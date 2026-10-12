# Modelo de datos: estadisticas y vinculacion de identidades

## EstadisticasJugador

Fotografia vigente asociada por `jugador_id` al Jugador local. Los campos
existentes son: posicion, minutos jugados, goles, asistencias, tiros, pases
clave, regates, tackles, valoracion, tarjetas amarillas y tarjetas rojas.

Las metricas individuales ausentes permanecen `NULL`. No se crean Jugador ni
Equipo alternativos y no se agregan metricas solo porque WhoScored las devuelva.
Las métricas estadísticas decimales normalizadas por Devchester se representan
con escala exacta de 2 decimales y redondeo `RoundingMode.HALF_UP`; la
precisión adicional entregada por los proveedores externos no forma parte de
la representación normalizada del dominio. Actualmente son decimales
`tiros`, `pasesClave`, `regates`, `tackles` y `valoracion`; los minutos, goles,
asistencias y tarjetas conservan su representación entera.

## Snapshot normalizado por competicion

El puerto entrega un snapshot completo de una competicion, no lotes separados
por familia. El snapshot contiene registros consolidados por `playerId` y las
metricas internas disponibles. Las familias `summary/all`,
`summary/offensive` y `summary/defensive` son internas al Adapter.

El snapshot solo puede considerarse completo si el Adapter valida cada una de
las tres respuestas y la metadata de cada respuesta representa una pagina
completa:

- `currentPage == 1`;
- `totalPages == 1`;
- `players.size == totalResults`;
- metadata sin contradicciones.

Si no se cumple, la ejecucion falla y el snapshot no entra en staging validado.

## Mapeo de metricas existente

| Metrica interna | Campo real WhoScored | Familia de origen |
|---|---|---|
| `minutosJugados` | `minsPlayed` | Summary, Offensive o Defensive |
| `goles` | `goal` | Summary u Offensive |
| `asistencias` | `assistTotal` | Summary u Offensive |
| `tiros` | `shotsPerGame` | Summary u Offensive |
| `pasesClave` | `keyPassPerGame` | Offensive |
| `regates` | `dribbleWonPerGame` | Offensive |
| `tackles` | `tacklePerGame` | Defensive |
| `valoracion` | `rating` | Summary |
| `tarjetasAmarillas` | `yellowCard` | Summary |
| `tarjetasRojas` | `redCard` | Summary |
| `posicion` | No documentado en el JSON disponible | Pendiente |

No se mapean al dominio `aerialWonPerGame`, `manOfTheMatch`,
`foulGivenPerGame`, `offsideGivenPerGame`, `dispossessedPerGame`,
`turnoverPerGame`, `interceptionPerGame`, `foulsPerGame`, `offsideWonPerGame`,
`clearancePerGame`, `wasDribbledPerGame`, `outfielderBlockPerGame` ni
`goalOwn`. `goalOwn` no sustituye `goles`.

La precedencia de consolidacion es fija: Summary es canonica para minutos,
goles, asistencias, tiros, valoracion y tarjetas; Offensive es canonica para
pases clave y regates; Defensive es canonica para tackles. Una familia
secundaria no sobrescribe el valor canonico. La coherencia entre valores
redundantes puede validarse sin agregar metricas de dominio. `posicion` es
opcional y su ausencia no bloquea la adquisicion.

## Persistencia publicada

`jugador_estadisticas` mantiene una fila por `jugador_id`, FK a jugadores,
unicidad, metricas normalizadas, referencia de ejecucion y auditoria. Se
actualiza por upsert dentro de la promocion.

## Ejecucion y staging

`estadisticas_sync` mantiene los estados `INICIADA`, `RECIBIENDO`, `VALIDADA`,
`PUBLICADA` y `FALLIDA`, timestamps y error sanitizado.

`estadisticas_sync_staging` mantiene ejecucion, competicion, IDs externos,
identidades locales, hints normalizados, metricas consolidadas y marcas de
familias. El staging no es visible para negocio.

## Identidades y detalles fuera del modelo

`IdentidadExternaJugador(WHOSCORED, playerId)` referencia Jugador local y
`IdentidadExternaEquipo(WHOSCORED, teamId)` referencia Equipo local.

`stageId`, `tournamentOptions`, `category`, `subcategory`, Selenium, WebDriver,
rutas, cookies, headers, limites y tamanos de adquisicion son detalles del
Adapter. No forman parte del dominio ni del contrato del Service.

## Atomicidad

La adquisicion se ejecuta fuera de la transaccion de promocion. Una respuesta
invalida, incompleta o bloqueada no publica staging parcial. Si falla la
promocion, las identidades nuevas y las estadisticas revierten y el snapshot
anterior permanece visible.

## Estado operativo validado

El enrollment de equipos usa identidades persistidas (WHOSCORED, teamId) y el
mapping aprobado revisado humanamente en
backend/config/whoscored-team-enrollment-approved.csv. El importer es
transaccional e idempotente. El resultado productivo validado fue de 2175
adquiridos, 1968 publicados y 207 omitidos.
