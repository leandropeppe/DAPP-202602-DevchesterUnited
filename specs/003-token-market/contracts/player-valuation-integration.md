# Contrato de integración Player Valuation → Token Market

## Responsabilidades

- Player Statistics Sync obtiene y persiste estadísticas de jugadores.
- Player Valuation calcula externamente el precio, la estrategia aplicada y la
  ventana de vigencia.
- Token Market recibe el resultado calculado mediante `QuotePublicationPort`.
- Token Market valida la forma del request, persiste una versión inmutable,
  actualiza la cabecera vigente por jugador y permite consultar la versión
  exacta.
- Token Market no realiza scraping, no calcula precios y no mantiene una
  segunda fuente de cotizaciones.

## Publicación externa

Player Valuation debe invocar `QuotePublicationPort.publish` con:

- el jugador estable;
- el precio calculado externamente;
- `publishedAt` y `validFrom` coherentes;
- `validUntil` cuando la cotización sea temporal;
- una referencia opaca de la estrategia aplicada.

El precio recibido se conserva con la normalización monetaria definida por el
SDD. La publicación crea una nueva versión bajo el mismo `quoteId` lógico del
jugador y mueve la cabecera vigente a esa versión. Las versiones históricas no
se modifican ni se promocionan automáticamente.

## Invalidación

Player Valuation decide cuándo una cotización deja de ser confiable y solicita
la invalidación mediante `QuotePublicationPort.invalidate` con `playerId`,
`quoteId`, `version` y un motivo acotado. Token Market valida la coincidencia
exacta, serializa la operación con la publicación del mismo jugador y persiste
el estado operativo de la cabecera. No calcula precios ni modifica la versión
histórica.

El resultado devuelve únicamente la referencia pública, `INVALIDATED`,
`invalidatedAt` y el motivo normalizado. Los reintentos exactos recuperan esos
mismos datos persistidos. Una solicitud contra una versión reemplazada,
inexistente o vencida es rechazada y nunca puede afectar una versión nueva.
Una publicación posterior vuelve a marcar como `VALID` la nueva cabecera.
