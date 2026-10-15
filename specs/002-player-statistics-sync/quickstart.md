# Guía de validación: estadísticas completas de jugadores

Esta guía valida el diseño descrito en [plan.md](plan.md),
[data-model.md](data-model.md) y
[contracts/player-statistics-sync.md](contracts/player-statistics-sync.md).
No ejecuta WhoScored real ni requiere credenciales externas.

## Prerrequisitos

- Java 21 disponible para el backend.
- Docker disponible para Testcontainers PostgreSQL.
- Node.js y dependencias ya instaladas en `frontend`.
- Repositorio ubicado en la raíz del proyecto.

## 1. Validar el esquema

Ejecutar desde `backend`:

```powershell
.\gradlew.bat test --tests '*EstadisticasSchemaRepositoryIntegrationTest' --no-daemon --console=plain
```

Resultado esperado: Flyway aplica V15 y V16 sin modificar V15; la tabla
`jugador_estadisticas` mantiene una fila por jugador y contiene las columnas de
las 30 métricas. El staging puede almacenar las cuatro familias.

## 2. Validar parsing y adquisición controlada

Ejecutar desde `backend`:

```powershell
.\gradlew.bat test --tests '*WhoScoredStatisticsFeedResponseDTOTest' --tests '*WhoScoredStatisticsAdapterTest' --tests '*WhoScoredStatisticsAdapterCompletenessTest' --tests '*WhoScoredStatisticsAdapterFailureTest' --no-daemon --console=plain
```

Las fixtures deben cubrir `summary`, `offensive`, `defensive` y `passing`,
metadata incompleta, JSON inválido y cierre/control de errores. No se abre una
conexión a Internet.

## 3. Validar consolidación y repetición

```powershell
.\gradlew.bat test --tests '*ConsolidadorEstadisticasJugadorIdempotenciaTest' --tests '*SincronizacionEstadisticasAtomicidadIntegrationTest' --tests '*SincronizacionEstadisticasConcurrenciaIntegrationTest' --no-daemon --console=plain
```

Comprobar:

- Un jugador produce una sola composición con 8/9/8/5 métricas.
- `minsPlayed` y `rating` se toman de generales.
- `assistTotal` y `keyPassPerGame` se toman de ofensivas.
- `passSuccess` se toma de pases.
- Una ausencia legítima reemplaza el valor anterior por `null`.
- Un fallo de una familia conserva el snapshot anterior y no publica staging.
- Una segunda sincronización actualiza sin duplicar.

## 4. Validar persistencia y API

```powershell
.\gradlew.bat test --tests '*EstadisticasJugadorUpsertIntegrationTest' --tests '*JugadorDetalleE2ETest' --no-daemon --console=plain
```

Consultar un jugador activo con datos persistidos y verificar que
`GET /api/v1/players/{id}` devuelve `statistics.generales`, `ofensivas`,
`defensivas` y `pases`, con nombres en español `camelCase`. Confirmar que
`precisionPases: 86.44` conserva el porcentaje y que un jugador sin fila
devuelve `statistics: null`.

## 5. Validar frontend

Desde `frontend`:

```powershell
npm.cmd run test
npm.cmd run build
npm.cmd run verify:frontend
```

Verificar manualmente o con Playwright:

1. Abrir el catálogo y seleccionar un jugador.
2. Confirmar la navegación a `/catalogo/jugadores/{id}`.
3. Confirmar carga local del detalle y activación de Resumen, Ataque, Defensa y
   Pases.
4. Confirmar que cada pestaña muestra la familia correcta y que `precisionPases`
   se presenta como porcentaje.
5. Simular `statistics: null`, 404 y error de red; comprobar mensajes y regreso.
6. Confirmar que no se solicita WhoScored desde el navegador.

## 6. Validación completa antes del cierre

```powershell
cd backend
.\gradlew.bat test --no-daemon --console=plain
cd ..\frontend
npm.cmd run build
npm.cmd run test -- --run
```

El cierre requiere que las suites existentes y nuevas pasen, que no se hayan
eliminado o modificado tests sin autorización y que la revisión de calidad
incluya backend y frontend. SonarCloud debe ejecutarse según el pipeline del
proyecto antes de cerrar la feature.

## Fuera de esta validación

No se validan normalización `[0,1]`, score, cotización, rankings, histórico,
tokenización, compra, venta ni gráficos.
