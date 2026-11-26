# Data Model: Auditoría transversal y logging seguro

La feature no persiste datos de auditoría. Las siguientes estructuras son representaciones efímeras para construir eventos de log y contexto de ejecución.

## AuditoriaEvento

| Campo | Tipo conceptual | Obligatorio | Regla |
|---|---|---:|---|
| `eventName` | texto fijo | Sí | `AUDIT_OPERATION` |
| `operation` | texto enumerado | Sí | Una de las operaciones cerradas del catálogo |
| `stage` | `START` o `END` | Sí | START antes; END después |
| `result` | `STARTED`, `SUCCESS` o `ERROR` | Sí | START usa `STARTED`; END refleja resultado |
| `durationMs` | entero no negativo | END | `null`/ausente en START; obligatorio en END |
| `timestamp` | instante UTC | Sí | Momento de emisión |
| `requestCorrelationId` | texto | HTTP | Obligatorio en HTTP; generado internamente y nunca sustituido por el cliente |
| `executionCorrelationId` | texto | BACKGROUND | Obligatorio en BACKGROUND; generado por cada ejecución y no es un ID HTTP |
| `externalCorrelationId` | texto | No | Solo header externo válido |
| `contextType` | `HTTP` o `BACKGROUND` | Sí | Determina cuál de los dos identificadores de ejecución es obligatorio |
| `errorCode` | texto enumerado | END con error | Código estable; nunca mensaje |

## CorrelacionContext

Contexto temporal asociado al hilo de una solicitud:

- `requestCorrelationId`: UUID interno único, obligatorio únicamente para `HTTP`.
- `executionCorrelationId`: UUID generado por ejecución, obligatorio únicamente para `BACKGROUND`.
- `externalCorrelationId`: valor externo validado, opcional.
- `contextType`: HTTP mientras dura la solicitud; fuera de HTTP no se crea un contexto HTTP.

El contexto se instala antes de seguridad y se limpia en `finally`. No es una entidad de negocio, no se serializa como request/response y no se guarda en PostgreSQL.

## AuditoriaOperationCatalog

Catálogo cerrado de diez valores: `catalog.sync`, `statistics.sync`, `valuation.manual`, `valuation.weekly`, `market.purchase`, `market.sale`, `market.p2p.offer.publish`, `market.p2p.purchase`, `market.p2p.offer.cancel` y `market.initialize`.

El catálogo relaciona cada valor con una firma pública de servicio. La misma operación no debe tener una segunda entrada para el método interno delegado.

## AuditoriaErrorCode

Códigos controlados iniciales: `VALIDATION_ERROR`, `AUTHENTICATION_ERROR`, `AUTHORIZATION_ERROR`, `NOT_FOUND`, `CONFLICT`, `CONCURRENCY_ERROR`, `EXTERNAL_PROVIDER_ERROR`, `PERSISTENCE_ERROR` y `UNEXPECTED_ERROR`.

El resolver puede mapear excepciones conocidas del proyecto a estos códigos, pero no registra `getMessage()`, `getCause()`, stack trace ni argumentos.

## Relaciones y ciclo de vida

```text
HTTP request
  └── CorrelacionContext (temporal)
        └── AuditoriaEvento START
        └── operación de servicio seleccionada
        └── AuditoriaEvento END
        └── limpieza del contexto
```

Para scheduler o startup, el flujo es el mismo sin contexto HTTP: `contextType=BACKGROUND`, `executionCorrelationId` generado al comenzar la ejecución y `requestCorrelationId` ausente. Nunca se inventa un identificador HTTP para procesos internos.
