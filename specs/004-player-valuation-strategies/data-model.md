# Data Model: Valorización de jugadores según estrategia

## EstrategiaValorizacion

Definición versionada disponible para producir scores: `id`, `nombre`, `version`, `tipo` (general/posicional), `habilitadaParaCalculo`, `seleccionadaComoVisible`, `metricasPositivas`, `penalizaciones`, `posicionesSoportadas`, `esProvisoria`, `valorBase` y `factorEscala`.

Reglas:

- Deben existir inicialmente GENERAL v1 y POSITIONAL v1 habilitadas para cálculo.
- Los pesos positivos suman exactamente 1.0.
- Una estrategia/version no se modifica después de generar cotizaciones.
- Puede haber varias estrategias habilitadas para cálculo.
- Existe como máximo una estrategia visible; cuando hay configuración operativa, no puede dejarse ninguna.
- `habilitadaParaCalculo` y `seleccionadaComoVisible` son estados distintos.

## ConfiguracionElegibilidad

Reglas por ejecución: `minimoPartidos` opcional, `minimoMinutos` opcional, `modoCumplimiento`, métricas positivas requeridas derivadas de cada estrategia y métricas negativas trazables. No se define un umbral funcional por defecto; la ejecución recibe la configuración explícita.

## SnapshotEstadisticoEjecucion

Conjunto lógico inmutable de estadísticas persistidas capturado al inicio de una ejecución. Todas las estrategias de la misma ejecución leen este snapshot y no se refrescan entre estrategias.

- `id` o referencia de snapshot.
- fecha/hora de captura.
- estadísticas por jugador.
- identificador de la sincronización o versión de datos usada, cuando exista.

## ResultadoNormalizacion

`estrategiaId`, `metrica`, `valorBruto`, `minimo`, `maximo`, `valorNormalizado` en [0,1], `alcance` (global/posición), posición de referencia y estado (calculado/constante/ausente). Si `minimo == maximo`, el valor normalizado es 0 y el estado explica la decisión.

## ResultadoElegibilidad

Decisión por jugador y estrategia: `jugadorId`, `estrategiaId`, `version`, `elegible`, `posicion`, partidos/minutos observados, causas de exclusión y métricas negativas ausentes. Una exclusión para una estrategia no bloquea otras combinaciones.

## EjecucionValorizacion

Agrupa una corrida manual o semanal: `id`, inicio/fin, tipo, snapshot estadístico, conjunto de estrategias habilitadas y versiones capturadas al inicio, configuración de elegibilidad, estado, combinaciones procesadas, exclusiones y ausencias negativas.

## CotizacionActualEstrategia

Último resultado vigente por combinación `jugadorId + estrategiaId + version`: `valorActual`, `scoreActual`, histórico más reciente, fecha de actualización, valorBase, factorEscala y trazabilidad resumida. Debe existir como máximo una cotización actual para cada combinación.

La consulta oficial/predeterminada selecciona la cotización actual asociada a la estrategia visible; no existe una única cotización actual independiente de estrategia.

## HistoricoCotizacionEstrategia

Serie inmutable por jugador y estrategia/version: `id`, `jugadorId`, `ejecucionId`, fecha/hora, `estrategiaId`, `version`, `score`, `cotizacion`, `valorBase`, `factorEscala`, snapshot estadístico y datos de trazabilidad de insumos, normalizaciones, penalizaciones, ausencias y decisiones de elegibilidad.

Una nueva ejecución agrega como máximo un registro por combinación jugador + estrategia + versión que pueda valorizarse correctamente. No actualiza registros anteriores.

## SeleccionEstrategiaVisible

Configuración única que identifica la estrategia/version visible y su fecha de cambio. Su operación de cambio es atómica, no ejecuta valorización y no modifica históricos ni cotizaciones actuales. La invariante se protege en el caso de uso además de las garantías de persistencia.

## Relaciones y transiciones

```text
Jugador 1 ── N CotizacionActualEstrategia (una por estrategia/version)
Jugador 1 ── N HistoricoCotizacionEstrategia (series separadas)
Estrategia 1 ── N CotizacionActualEstrategia
Estrategia 1 ── N HistoricoCotizacionEstrategia
Ejecucion 1 ── N HistoricoCotizacionEstrategia
Ejecucion 1 ── N ResultadoElegibilidad
Estrategia 0..N ── 0..1 SeleccionEstrategiaVisible
```

```text
Estrategia registrada → habilitada para cálculo → capturada por ejecución
Estrategia registrada → seleccionada como visible → serie elegida por consultas por defecto
Ejecución iniciada → snapshot y estrategias/versiones capturados → resultados por estrategia → históricos agregados
Histórico creado → inmutable
```

## Persistencia propuesta

La migración debe usar la próxima versión Flyway realmente disponible al momento de implementar, no un número fijo asumido. Debe crear tablas para estrategias/configuraciones, habilitación, selección visible, ejecuciones/snapshot, cotización actual por estrategia/version, histórico y trazabilidad suficiente. Las claves referenciarán `jugadores(id)` y las reglas protegerán score, versión, valores no negativos, pesos y la unicidad de estrategia visible. El cambio visible debe ser transaccional y validado por el caso de uso, no exclusivamente por una constraint SQL.
