# Investigación técnica: sincronización completa de estadísticas

**Feature**: `002-player-statistics-sync`  
**Fecha**: 2026-10-15

## Decisión: conservar el puerto como snapshot completo por competición

**Decisión**: `ProveedorEstadisticasJugador` continúa exponiendo una operación completa por competición. El Adapter adquiere las cuatro familias, valida cada respuesta y devuelve un snapshot normalizado consolidado por `playerId`.

**Rationale**: evita filtrar el formato de WhoScored al Service y conserva la arquitectura vigente. El Service no necesita coordinar cuatro respuestas ni conocer categorías, URLs, Selenium o paginación.

**Alternativas consideradas**: exponer cuatro llamadas al Service o entregar DTOs externos. Se rechazan porque aumentan el acoplamiento, duplican reglas de error y contradicen la separación Adapter/Modelo/Service.

## Decisión: cuatro adquisiciones por competición dentro del Adapter

**Decisión**: además de `summary/all`, `summary/offensive` y `summary/defensive`, el Adapter solicita `summary/passing` usando el parámetro `category=passing` según el contrato del proveedor ya validado.

**Rationale**: la familia de pases debe participar del mismo staging, consolidación, control de completitud y rollback. Una falla de una familia requerida invalida la sincronización lógica completa.

**Alternativas consideradas**: hacer `passing` opcional o sincronizarlo en una segunda ejecución. Se rechazan porque producirían datasets parciales y romperían la atomicidad exigida.

## Decisión: mantener adquisición de una página y una sesión WebDriver

**Decisión**: conservar `isMinApp=false`, `numberOfPlayersToPick=500`, una llamada por familia y competición y una única sesión WebDriver por ejecución. Aceptar solo respuestas con `currentPage == 1`, `totalPages == 1`, `players.size == totalResults`, metadata coherente y límite dentro de la página.

**Rationale**: es el comportamiento productivo validado; el máximo observado fue 472 jugadores por competición. La futura paginación, si fuera necesaria, queda encapsulada en el Adapter.

**Alternativas consideradas**: paginación en Service o aceptar páginas parciales. Se rechazan por acoplamiento y por riesgo de publicar snapshots incompletos.

## Decisión: composición de dominio por familia

**Decisión**: `MetricasEstadisticasJugador` contiene cuatro records `MetricasGenerales`, `MetricasOfensivas`, `MetricasDefensivas` y `MetricasPases`.

**Rationale**: evita un record plano ambiguo, mantiene una única propiedad por métrica y prepara la futura valorización para seleccionar familias sin depender de WhoScored.

**Alternativas consideradas**: un único record de 30 campos o un mapa de atributos. Se rechazan porque pierden límites semánticos, tipado y claridad de contrato.

## Decisión: propiedad canónica de métricas repetidas

**Decisión**: cada métrica repetida se toma únicamente de su familia propietaria:

| Métrica repetida | Familia propietaria |
|---|---|
| `minsPlayed` | Generales |
| `rating` | Generales |
| `assistTotal` | Ofensivas |
| `keyPassPerGame` | Ofensivas |
| `passSuccess` | Pases |

Una familia secundaria no crea otro atributo, columna o campo de respuesta ni puede sobrescribir el valor canónico.

**Rationale**: satisface la unicidad conceptual y hace determinista la consolidación aun cuando WhoScored repita el mismo campo.

## Decisión: escala decimal y porcentaje de pases

**Decisión**: conservar la escala decimal vigente del dominio mediante estandarización de escala, sin llamarla normalización. `passSuccess` se conserva en 0..100; 86.44 permanece 86.44.

**Rationale**: la escala porcentual es semántica del proveedor y la normalización a `[0,1]` pertenece a una feature posterior.

## Decisión: nulabilidad y actualización

**Decisión**: una métrica ausente legítimamente se representa como `null`. En una sincronización válida, ese `null` reemplaza el valor persistido anterior. Un fallo de adquisición, validación o promoción conserva el snapshot anterior completo.

**Rationale**: diferencia ausencia actual confirmada de fallo de sincronización y evita mantener valores obsoletos.

## Decisión: persistencia consolidada y V16

**Decisión**: mantener una fila por jugador en `jugador_estadisticas`, agregar las nuevas columnas en `V16__Expand_player_statistics_sync.sql` y evolucionar las columnas equivalentes del staging. No se modifica V15 ni se crea una tabla por familia.

**Rationale**: conserva la unicidad existente, simplifica upsert y permite leer un snapshot local completo sin joins entre familias.

**Alternativas consideradas**: tablas separadas por familia o modificar V15. Se rechazan por la restricción explícita de migraciones y por duplicación conceptual en persistencia.

## Decisión: contrato público y frontend

**Decisión**: el detalle mantiene `GET /api/v1/players/{id}`, el envoltorio actual y `statistics: null` cuando no existe fila. Cuando existe, `statistics` contiene `generales`, `ofensivas`, `defensivas` y `pases`, con nombres en español `camelCase`. `PlayerDetailPage` habilita cuatro pestañas y consume únicamente ese endpoint local.

**Rationale**: evita romper el acceso actual, mantiene DTOs externos fuera de la API y permite distribuir las métricas sin sobrecargar Resumen.

**Alternativas consideradas**: ampliar el listado, crear endpoint nuevo o consultar WhoScored desde React. Se rechazan por regresión, duplicación y violación de arquitectura.

## Decisión: estrategia de pruebas

**Decisión**: agregar fixtures JSON para cada familia y tests unitarios de parsing/consolidación; tests de persistencia y staging con Testcontainers; tests de API con MockMvc; tests frontend con Vitest/Testing Library y recorrido E2E con Playwright. Ninguno consume Internet ni credenciales reales.

**Rationale**: cubre las capas donde cambia el comportamiento y respeta la Constitution, incluida la regla de no modificar ni eliminar tests existentes.
