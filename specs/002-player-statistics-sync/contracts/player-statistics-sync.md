# Contratos internos de sincronizacion

No se agrega endpoint HTTP. El contrato define el puerto entre Service y
Adapter y las consultas de persistencia necesarias para enrolamiento.

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

1. Resolver identidades WHOSCORED persistidas.
2. Ejecutar enrolamiento conservador para identidades ausentes.
3. Persistir identidades nuevas solo junto con la promocion.
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
