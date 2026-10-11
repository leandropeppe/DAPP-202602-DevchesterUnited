# Guia de validacion: estadisticas y enrolamiento WhoScored

## Prerrequisitos

- Java 21 y Docker.
- PostgreSQL de Testcontainers.
- Sin Internet ni credenciales reales de WhoScored para tests.
- Ejecutar desde `backend/`.
- JSON controlado, fixtures, fakes o mocks para el Adapter.

## Comando automatizado

    .\gradlew.bat test --no-daemon --console=plain

No se modifican ni borran tests existentes. No se agrega endpoint HTTP.

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
`sortBy=Rating`, `field=Overall`, `isMinApp=true`, y la configuracion de
`stageId`/`tournamentOptions`.

La primera implementacion no pagina. Debe aceptar solo:

- `currentPage == 1`;
- `totalPages == 1`;
- `players.size == totalResults`;
- metadata sin contradicciones.

`numberOfPlayersToPick=350` es un valor experimental, no una garantia universal.
Ante incompletitud debe fallar, no publicar parcial y conservar el snapshot
previo.

### Validación manual real T060 (2026-10-11)

Se validaron las cinco competiciones configuradas (`PREMIER_LEAGUE`, `LA_LIGA`,
`SERIE_A`, `BUNDESLIGA` y `LIGUE_1`) con las tres familias por competición:
`summary/all`, `summary/offensive` y `summary/defensive`. Se realizaron 15
navegaciones headed en una única sesión WebDriver, con cierre correcto del
driver. Las 15 respuestas fueron JSON válidos y completas, con
`currentPage == 1`, `totalPages == 1`, `players.size == totalResults` y
`totalResults <= resultsPerPage`.

Se produjeron cinco snapshots consolidados, sin duplicados por `playerId`,
conservando `playerId`/`teamId` y las precedencias Summary, Offensive y
Defensive. Las métricas decimales (`tiros`, `pasesClave`, `regates`, `tackles`
y `valoracion`) se compararon contra el valor crudo normalizado con
`setScale(2, RoundingMode.HALF_UP)` y quedaron con escala 2; los contadores
enteros conservaron su representación. La suite backend completa terminó
verde con 473 tests.

La validación se ejecutó headed mediante un launcher diagnóstico temporal que
no se incorporó a la suite automatizada. La integración productiva con
WhoScored requiere actualmente Chrome headed: `--headless=new` y su alias
oficial `--headless` recibieron una respuesta alternativa de Cloudflare en la
misma consulta, mientras headed devolvió el JSON esperado. No se implementan
mecanismos de evasión. El driver se abre durante la sincronización y se cierra
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

Los tests automatizados no consumen WhoScored real ni implementan mecanismos de
evasión, stealth o bypass.
