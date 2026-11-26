# Contract: Structured Audit Event

## Purpose

Definir el contrato mínimo de los registros de auditoría emitidos por las operaciones seleccionadas.

## Required fields

```text
eventName = AUDIT_OPERATION
operation = closed logical operation name
stage = START | END
result = STARTED | SUCCESS | ERROR
timestamp = UTC instant
contextType = HTTP | BACKGROUND
requestCorrelationId = required internal request ID when contextType=HTTP
executionCorrelationId = required per-execution ID when contextType=BACKGROUND
durationMs = absent/null on START; non-negative integer on END
errorCode = required only when result=ERROR
externalCorrelationId = optional validated client label; omitted for BACKGROUND
```

## Allowed operation names

- `catalog.sync`
- `statistics.sync`
- `valuation.manual`
- `valuation.weekly`
- `market.purchase`
- `market.sale`
- `market.p2p.offer.publish`
- `market.p2p.purchase`
- `market.p2p.offer.cancel`
- `market.initialize`

## Security rules

El evento MUST NOT contener argumentos, valores de retorno, contraseñas, JWT,
API keys, headers de autorización, secretos, datos personales sensibles,
mensajes arbitrarios de excepción, cadena de causas ni stack trace. El writer
MUST construir campos desde una allowlist y no serializar objetos arbitrarios.

## Failure behavior

El fallo al construir o emitir un evento es best effort. MUST NOT cambiar el
resultado de negocio ni la excepción original. La pérdida de eventos durante
una falla grave de logging es una limitación aceptada y no se representa como
fallo del negocio.
