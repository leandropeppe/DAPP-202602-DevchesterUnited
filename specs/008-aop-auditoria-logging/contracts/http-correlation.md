# Contract: HTTP Correlation

## Request

- Header: `X-Correlation-ID` opcional como etiqueta externa.
- Valores válidos: 1–128 caracteres ASCII de `A-Z`, `a-z`, `0-9`, `.`, `_`, `-`.
- Valores inválidos, vacíos o demasiado largos se ignoran; la solicitud no se
  rechaza por ese motivo.
- El valor recibido nunca define la identidad interna de la solicitud.

## Response

- Header: `X-Correlation-ID`.
- Valor: UUID interno nuevo generado por el backend.
- El valor externo no se devuelve automáticamente.

## Logging context

- `requestCorrelationId`: siempre presente en eventos HTTP, generado internamente y devuelto en `X-Correlation-ID`.
- `executionCorrelationId`: siempre presente en eventos BACKGROUND, generado por la ejecución y nunca enviado como header HTTP.
- `externalCorrelationId`: presente solo cuando el header pasó validación.
- Los valores de correlación presentes se eliminan de MDC y del contexto al finalizar.

## Security and compatibility

El filtro no inspecciona ni registra `Authorization`, `X-API-KEY` ni otros
headers de autenticación. No cambia autorización, management port, health,
Prometheus ni contratos de error.

## Non-HTTP execution

Schedulers y startup usan `contextType=BACKGROUND`, generan un `executionCorrelationId` por ejecución y omiten `requestCorrelationId`. No se crea un identificador HTTP sintético ni se atribuye un `externalCorrelationId`.
