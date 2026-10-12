# Guia de validacion: estadisticas y enrolamiento WhoScored

## Prerrequisitos

- Java 21 y Docker.
- PostgreSQL de Testcontainers.
- Sin Internet ni credenciales reales de WhoScored para tests.
- Ejecutar desde `backend/`.
- JSON controlado, fixtures, fakes o mocks para el Adapter.
- Para una base nueva, importar previamente el mapping aprobado de equipos
  desde `config/whoscored-team-enrollment-approved.csv`.

## Comando automatizado

    .\gradlew.bat test --no-daemon --console=plain

No se modifican ni borran tests existentes. La extension agrega una consulta de
detalle del Catalogo, sin alterar el contrato de `GET /api/v1/players`.

## Validacion manual diagnostica

La navegacion real con Selenium a `/statisticsfeed/1/getplayerstatistics` es
manual y no forma parte de la suite automatizada. Una sincronizacion completa
reutiliza una unica sesion WebDriver para las cinco competiciones y debe cerrarla
ante exito o excepcion.

Para cada competicion deben verificarse las tres adquisiciones internas:

- `category=summary`, `subcategory=all`;
- `category=summary`, `subcategory=offensive`;
- `category=summary`, `subcategory=defensive`.

Tambien deben comprobarse `statsAccumulationType=0`, `isCurrent=true`,
`sortBy=Rating`, `field=Overall`, `isMinApp=false`,
`numberOfPlayersToPick=500`, y la configuracion de
`stageId`/`tournamentOptions`.

La primera implementacion no pagina. Debe aceptar solo:

- `currentPage == 1`;
- `totalPages == 1`;
- `players.size == totalResults`;
- metadata sin contradicciones.

La decision productiva es `numberOfPlayersToPick=500`: el diagnostico real
verifico un maximo actual de 472 jugadores por competicion. Por ello se hace
una llamada por familia/competicion y no se implementa paginacion. La
validacion estricta de completitud permanece activa.
Ante incompletitud debe fallar, no publicar parcial y conservar el snapshot
previo.

### ValidaciÃ³n manual real T060 (2026-10-11)

Se validaron las cinco competiciones configuradas (`PREMIER_LEAGUE`, `LA_LIGA`,
`SERIE_A`, `BUNDESLIGA` y `LIGUE_1`) con las tres familias por competiciÃ³n:
`summary/all`, `summary/offensive` y `summary/defensive`. Se realizaron 15
navegaciones headed en una Ãºnica sesiÃ³n WebDriver, con cierre correcto del
driver. Las 15 respuestas fueron JSON vÃ¡lidos y completas, con
`currentPage == 1`, `totalPages == 1`, `players.size == totalResults` y
`totalResults <= resultsPerPage`.

Se produjeron cinco snapshots consolidados, sin duplicados por `playerId`,
conservando `playerId`/`teamId` y las precedencias Summary, Offensive y
Defensive. Las mÃ©tricas decimales (`tiros`, `pasesClave`, `regates`, `tackles`
y `valoracion`) se compararon contra el valor crudo normalizado con
`setScale(2, RoundingMode.HALF_UP)` y quedaron con escala 2; los contadores
enteros conservaron su representaciÃ³n. La suite backend completa terminÃ³
verde con 521 tests.

La validaciÃ³n se ejecutÃ³ headed mediante un launcher diagnÃ³stico temporal que
no se incorporÃ³ a la suite automatizada. La integraciÃ³n productiva con
WhoScored requiere actualmente Chrome headed: `--headless=new` y su alias
oficial `--headless` recibieron una respuesta alternativa de Cloudflare en la
misma consulta, mientras headed devolviÃ³ el JSON esperado. No se implementan
mecanismos de evasiÃ³n. El driver se abre durante la sincronizaciÃ³n y se cierra
garantizadamente al finalizar o ante error.

## Escenarios obligatorios

1. Adapter: tres familias reales controladas se consolidan en un snapshot completo por competicion.
2. Completitud: metadata valida, `totalPages > 1`, conteos incompletos y contradicciones.
3. WebDriver: una sesion para cinco competiciones y cierre ante excepcion.
4. Equipo y jugador: matching conservador, identidades persistidas y namespaces.
5. Idempotencia: repetir datos no crea identidades ni filas duplicadas.
6. Atomicidad: fallos de adquisicion, staging y promocion no dejan parciales.
7. Mapeo: solo las metricas existentes se traducen; `posicion` queda pendiente si no aparece en JSON.
8. Errores: detalles de JSON, navegacion y proveedor se sanitizan.
9. Detalle con estadisticas: `GET /api/v1/players/{id}` devuelve HTTP 200 y el
   objeto `statistics` con los valores persistidos.
10. Detalle sin estadisticas: el mismo endpoint devuelve HTTP 200 con
    `statistics: null`.
11. Detalle inexistente o inactivo: devuelve HTTP 404 para jugador inexistente,
    jugador inactivo o equipo inactivo.
12. Aislamiento de lectura: la consulta de detalle no invoca WhoScored, Selenium,
    `WhoScoredStatisticsAdapter` ni sincronizaciones.

Los tests automatizados no consumen WhoScored real ni implementan mecanismos de
evasiÃ³n, stealth o bypass.



La evidencia productiva final registro 2175 jugadores adquiridos y 1968 publicados. Los jugadores individualmente no resolubles se omiten sin abortar la publicacion; los errores tecnicos, estructurales, de equipo, staging o promocion siguen siendo fatales.

## Validacion del cierre frontend

Desde `frontend/`, ejecutar:

    npm test -- --run
    npm run build

La validacion de vistas debe cubrir:

1. El listado muestra Nacionalidad y no muestra Dorsal ni `0` como sustituto.
2. Seleccionar una fila/card navega a `/catalogo/jugadores/{id}` y solicita
   `/api/v1/players/{id}`.
3. Un detalle con estadisticas muestra datos basicos y las diez metricas del
   contrato.
4. `statistics: null` muestra el estado sin estadisticas y conserva los datos
   basicos.
5. HTTP 404, errores de red y carga muestran estados comprensibles y permiten
   regresar a `/catalogo`.

La prueba E2E debe ejecutar el recorrido catalogo -> detalle -> volver con
respuestas controladas y verificar que no se realizan solicitudes a WhoScored,
Football-Data u otros hosts externos. Las pruebas existentes no deben
modificarse ni eliminarse.

## Flujo reproducible desde una base nueva

1. Levantar PostgreSQL y ejecutar el arranque normal de la aplicacion para que Flyway aplique las migraciones.
2. Configurar `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `FOOTBALL_DATA_API_KEY`, `WHOSCORED_BASE_URL` y las variables de configuracion WhoScored documentadas en `application.properties`.
3. Ejecutar la sincronizacion inicial de Football-Data mediante el flujo operativo existente del proyecto.
4. Ejecutar `.\gradlew.bat importWhoScoredTeamIdentities` desde `backend/`, con el mapping versionado ya disponible.
5. Instalar Chrome y mantener Selenium headed disponible.
6. Ejecutar la sincronizacion WhoScored mediante `.\gradlew.bat manualStatisticsSyncTest` solamente cuando se quiera realizar una sincronizacion real.
7. Verificar en PostgreSQL las identidades WhoScored y las filas publicadas de `jugador_estadisticas`.

El reporte candidato se genera opcionalmente con `.\gradlew.bat generateWhoScoredTeamEnrollmentReport`.
El diagnostico excepcional de jugadores se genera con `.\gradlew.bat generateWhoScoredPlayerIdentityDiagnostic`.
El mapping aprobado no se regenera automaticamente ni forma parte de esos pasos opcionales.

El cierre actual de la suite backend es de 518 tests verdes; la evidencia productiva permanece en 2175 adquiridos, 1968 publicados y 207 omitidos.
