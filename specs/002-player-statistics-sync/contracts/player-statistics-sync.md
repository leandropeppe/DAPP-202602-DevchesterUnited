# Contratos internos de sincronizacion

El contrato define el puerto entre Service y Adapter, las consultas de
persistencia necesarias para enrolamiento y el extremo de lectura local del
Catalogo. El extremo de lectura no consulta el Adapter.

## GET /api/v1/players/{id}

`id` recibe exclusivamente el identificador opaco `ply_<uuid>` expuesto por
`GET /api/v1/players`. No se acepta UUID desnudo como contrato publico.

Para un jugador activo de un equipo activo, la respuesta exitosa mantiene el
envoltorio `status`/`data` y devuelve los campos actuales del jugador junto con
`statistics`. `statistics` es un objeto cuando existe una fila persistida en
`jugador_estadisticas` y `null` cuando no existe.

El objeto de estadisticas expone `position`, `minutes_played`, `goals`,
`assists`, `shots`, `key_passes`, `dribbles`, `tackles`, `rating`,
`yellow_cards` y `red_cards`. No expone el id tecnico de la fila, `syncId`,
`createdAt` ni `updatedAt`.

Respuestas: HTTP 200 para jugador activo con o sin estadisticas; HTTP 404 para
identificador inexistente, jugador inactivo o equipo inactivo.

La consulta usa exclusivamente `JugadorRepository` y
`EstadisticasJugadorRepository.findByJugadorId(UUID)`. No invoca
`WhoScoredStatisticsAdapter`, Selenium, sincronizaciones ni proveedores
externos.

## Puerto ProveedorEstadisticasJugador

El puerto expone conceptualmente una operacion de adquisicion completa por
`CompeticionEstadistica`. Devuelve un snapshot completo y normalizado de esa
competicion, con registros consolidados por `playerId`.

El Service no consume lotes separados por familia y no conoce
`summary/all`, `summary/offensive` ni `summary/defensive`. Tampoco recibe
`page`, `pageSize`, `stageId`, `tournamentOptions`, URLs, Selenium o parametros
HTTP.

El snapshot contiene:

- competicion interna;
- registros `RegistroEstadisticasJugador` consolidados;
- metricas internas existentes;
- metadata interna de completitud, si es necesaria, sin modelar el transporte.

## Responsabilidad del Adapter

Para cada competicion, el Adapter:

1. Reutiliza la unica sesion WebDriver de la sincronizacion completa.
2. Adquiere `summary/all`, `summary/offensive` y `summary/defensive`.
3. Valida cada respuesta y traduce el JSON real.
4. Exige `currentPage == 1`, `totalPages == 1`, `players.size == totalResults` y metadata coherente.
5. Consolida las tres familias por `playerId`.
6. Devuelve el snapshot completo o un error de proveedor.

La consolidacion aplica precedencia fija: Summary para minutos, goles,
asistencias, tiros, valoracion y tarjetas; Offensive para pases clave y
regates; Defensive para tackles. Una familia secundaria no sobrescribe la
fuente canonica. Puede validarse coherencia de valores redundantes sin agregar
metricas al dominio. `posicion` es opcional y no bloquea por ausencia.

La primera implementacion no pagina. Si `totalPages > 1`, la respuesta es
incompleta o la metadata contradice el contenido, falla de forma controlada.
Una futura paginacion debe quedar dentro del Adapter sin modificar este
contrato.

El Adapter crea y cierra WebDriver, con cierre garantizado ante excepciones.
No realiza matching local ni escribe repositories.

## Responsabilidad del Service

1. Resolver equipos exclusivamente mediante identidades WHOSCORED persistidas
   previamente por el importer del mapping aprobado.
2. Resolver jugadores de forma conservadora dentro del equipo local ya resuelto.
3. Persistir identidades nuevas de jugadores solo junto con la promocion.
4. Consolidar o validar el flujo interno sin conocer familias del proveedor.
5. Escribir staging y promover estadisticas atomicamente.

Ante error de proveedor, JSON invalido, metadata incoherente, navegacion fallida,
bloqueo o adquisicion incompleta, aborta la sincronizacion y conserva el ultimo
snapshot valido.

## Repositorios y pruebas

Se reutilizan repositories de identidades, jugadores y equipos, y los
repositories de estadisticas, ejecuciones y staging. No se agrega tabla de
matching.

Los tests automatizados usan JSON controlado, fixtures, fakes o mocks. No se
consume WhoScored real. No se implementan stealth, evasiones ni bypass.

## Estado final validado

La sincronizacion real usa Selenium headed, cinco competiciones, tres familias
internas, isMinApp=false, numberOfPlayersToPick=500 y page=1. No existe
paginacion productiva actualmente. Los equipos se resuelven exclusivamente
mediante identidad WhoScored persistida; no hay matching nominal o fuzzy de
equipos durante la sincronizacion normal. Una identidad individual de jugador
no resoluble se omite y se reporta, sin crear identidad ni abortar la
publicacion. Los errores tecnicos, estructurales, de equipo o persistencia son
fatales. La evidencia validada fue 2175 adquiridos, 1968 publicados y 207
omitidos.
