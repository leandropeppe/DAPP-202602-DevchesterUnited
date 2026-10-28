# Data model: escenario de simulación

La feature no agrega entidades persistentes. Los siguientes son objetos de dominio temporales, creados por request y descartados al finalizar la respuesta.

## ConfiguracionSimulador

- `jugadorId`, `posicionOriginal`, `posicionResuelta`.
- `estrategiaId`, `estrategiaVersion`, `tipoEstrategia`, `esProvisoria`.
- `cotizacionActual` nullable.
- `estadisticasActuales` y `camposEditables`.
- `horizontesPermitidos`: constante `{3, 5, 8, 10}`.
- `bloqueos`: causas accionables diferenciadas: jugador inexistente, estadísticas ausentes, cotización ausente, posición no soportada, posición ambigua, estrategia inexistente o métrica positiva faltante.

## EscenarioSimulacion

- `jugadorId` obligatorio.
- `horizontePartidos` obligatorio y exactamente uno de 3/5/8/10.
- `modificaciones`: mapa de `MetricaValorizacion` a valor de entrada; no admite `apps` ni `minsPlayed`.
- La modificación de una métrica acumulativa es un incremento no negativo.
- La modificación de una métrica promedio/porcentual/rating es el valor final proyectado.
- Las métricas no enviadas conservan el valor actual.

## EstadisticasProyectadas

Copia inmutable de `MetricasEstadisticasJugador` con estas transformaciones:

```text
apps proyectadas  = apps actuales + horizonte
mins proyectados  = mins actuales + (horizonte * 90)
acumulativa       = actual + incremento enviado
promedio/ratio    = valor final enviado
sin modificación  = actual
```

Si `apps` o `minsPlayed` actuales son nulos, el escenario se rechaza porque no se puede aplicar el incremento automático. Los nulos de métricas no editadas permanecen nulos y una métrica positiva requerida ausente produce error de elegibilidad.

## ResultadoSimulacion

- `cotizacionActual` nullable.
- `cotizacionSimulada`, `score`, estrategia/version y posición aplicada.
- `diferenciaMonetaria`: simulada menos actual, nullable si no existe cotización actual.
- `diferenciaPorcentual`: diferencia sobre actual por 100; `null` con causa `COTIZACION_ACTUAL_CERO` o sin cotización.
- `estadisticasProyectadas` y resumen de cambios para que UI muestre escenario.
- `traza`: resultado del motor 004 (normalizaciones, elegibilidad, penalizaciones y ausencias).

## Validaciones y estados

La request valida forma y tipos con Bean Validation. El Service valida existencia, cotización/estrategia y población. El Modelo valida horizonte, clasificación, no negatividad, rango de rating/porcentajes, coherencia y prohibición de editar apariciones/minutos. Ningún error escribe en repositorios.

## Relaciones transitorias

```text
Jugador persistido ──1──1 ConfiguracionSimulador
ConfiguracionSimulador ──1──1 EscenarioSimulacion
EscenarioSimulacion ──1──1 EstadisticasProyectadas
EstadisticasProyectadas ──1──1 ResultadoSimulacion
ResultadoSimulacion ──0──1 CotizacionActual persistida (solo lectura)
```
