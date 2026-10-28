# Research: simulador de cotización futura

## Alcance investigado

Se revisaron la especificación 005, la constitución, `MetricasEstadisticasJugador`, `EstadisticasJugador`, `MetricaValorizacion`, `EstrategiaValorizacion`, `MotorValorizacion`, `ValorizacionJugadorServiceImpl`, las entidades de estadísticas, el contrato de valorización de 004, el detalle React y los fixtures/migraciones de estadísticas.

## Decisiones

### D1. Usar el modelo estadístico normalizado existente

- **Decisión**: el simulador trabajará con `MetricasEstadisticasJugador` y construirá un `JugadorEstadistico` temporal. No leerá DTOs ni entidades de WhoScored directamente.
- **Racional**: 004 ya consume esa representación y la sincronización existente traduce y persiste allí todos los campos disponibles.
- **Alternativas consideradas**: duplicar un DTO de simulación o usar el feed externo; se descartan por duplicación y por violar las capas.

### D2. Clasificar métricas por semántica de origen

- **Acumulativas**: `apps`, `minsPlayed`, `goal`, `assistTotal`, `manOfTheMatch`, `yellowCard`, `redCard` y `goalOwn` cuando sean editables por la estrategia. En el escenario, el request entrega incrementos no negativos; apariciones/minutos no se aceptan en el request y se calculan como `actual + horizonte` y `actual + horizonte * 90`.
- **Promedio por partido**: métricas con sufijo `PerGame` (`shots`, `keyPass`, `tackle`, `interception`, `clearance`, `aerialWon`, pases, faltas, etc.).
- **Porcentual**: `passSuccess`, tratado como valor final dentro de `[0,100]`.
- **Promedio/escala**: `rating`, tratado como valor final dentro de la escala validada por la estrategia/datos existentes.
- **Racional**: coincide con los nombres y tipos de la entidad de estadísticas y evita convertir un valor por partido en un total ficticio.
- **Regla de implementación**: la clasificación vivirá en un catálogo de dominio compartido por simulación, no en el frontend ni en `MetricaValorizacion`; si una métrica no tiene clasificación/rango definido, no será editable.

### D3. Derivar campos de la estrategia vigente

- **Decisión**: la configuración del simulador se obtiene de la estrategia visible/versionada de 004. Los campos editables son la intersección entre métricas que la estrategia usa, métricas presentes en el jugador y el catálogo de simulación; se informa la posición resuelta y la estrategia/version.
- **Racional**: evita mantener listas de delanteros/mediocampistas/defensores/arqueros duplicadas que se desincronicen con pesos y penalizaciones.
- **Alternativas consideradas**: hardcodear exactamente seis campos por posición; se descarta porque no contempla cambios de estrategia ni la disponibilidad real de arqueros.

### D4. Reutilizar exactamente el motor 004

- **Decisión**: agregar un flujo de cálculo en memoria que invoque `MotorValorizacion.calcular` con la estrategia existente y una población temporal adecuada, y reutilizar `evaluarElegibilidad`/`ConfiguracionElegibilidad` vigentes. No se llamará a `ValorizacionJugadorServiceImpl.ejecutar`, porque ese caso de uso persiste ejecución, cotización, histórico y publicación.
- **Racional**: satisface la equivalencia de reglas sin efectos secundarios.
- **Nota de diseño**: como el motor normaliza contra una población, el servicio debe cargar la población elegible que 004 usa para esa estrategia y reemplazar solo el jugador solicitado por su escenario. La estrategia/version y la configuración se capturan una vez por request.
- **Alternativas consideradas**: copiar las fórmulas al simulador o invocar el servicio oficial; ambas violan, respectivamente, no duplicación o no persistencia.

### D5. Posiciones múltiples y arqueros

- **Decisión**: aplicar el mismo normalizador/resolutor de posición de la estrategia vigente. Si la posición múltiple produce una única posición compatible, se devuelve esa selección; si es ambigua o no soportada, se rechaza.
- **Datos de arqueros encontrados**: el catálogo permite `Goalkeeper`; la estrategia POSITIONAL v1 histórica de 004 define PO como provisoria y usa `rating` 30%, `passSuccess` 30%, `accurateLongPassPerGame` 25% y `manOfTheMatch` 15%. Las migraciones almacenan esos campos generales/de pase y no existe un conjunto sincronizado de atajadas/clean sheets/paradas en el modelo actual. Por tanto, el formulario de arquero solo mostrará esos campos cuando estén presentes; si falta una métrica positiva requerida, no simulará.
- **Alternativas consideradas**: inventar atajadas o agregar columnas/sincronización; fuera de alcance y no demostrado por la fuente actual.

### D6. No persistir escenarios

- **Decisión**: no crear tablas ni migraciones. La preparación lee jugador/estadísticas/cotización/estrategia y la ejecución devuelve resultado transitorio.
- **Racional**: la especificación prohíbe convertir simulaciones en oficiales y no hay necesidad de auditoría histórica.
- **Alternativas consideradas**: tabla de escenarios o histórico temporal; se descartan por alcance y riesgo de contaminar cotizaciones.

### D7. Contrato separado de preparación y ejecución

- **Decisión**: `GET /api/v1/players/{playerId}/valuation-simulator` carga estado actual y campos editables; `POST` al mismo recurso recibe `horizonMatches` y `modifications`.
- **Racional**: permite pintar el modal sin duplicar reglas en React y hace explícita la diferencia entre carga y cálculo.
- **Alternativas consideradas**: enviar todas las estadísticas actuales desde frontend; se descarta porque el backend debe ser la fuente de verdad y evitar manipulación de datos oficiales.

### D8. Loader contextual en frontend

- **Decisión**: medir el tiempo desde el inicio del POST; en éxito esperar `max(0, 2000ms - elapsed)` antes de mostrar resultado. Errores se muestran inmediatamente. El componente tendrá `role="status"`, texto accesible y recurso Maradona reutilizable.
- **Racional**: implementa exactamente los escenarios 005 sin añadir latencia a errores ni a respuestas lentas.

## Resultado

No quedan aclaraciones técnicas bloqueantes. La única limitación deliberada es que un arquero no será simulable si sus métricas positivas configuradas no están presentes; se informa la causa y no se agregan datos ficticios.
