# Contrato de cotización usado por P2P

Token Market no calcula cotizaciones. Consume la representación versionada ya expuesta por `QuoteVersion`/`MarketQuoteValidationService`.

## Datos requeridos

- identidad estable de jugador;
- `quote_id` o identidad equivalente;
- versión monotónica/inmutable;
- precio unitario con la precisión interna existente;
- estado de validez y vigencia;
- referencia temporal de publicación/invalidación.

## Regla de confirmación

La UI envía la identidad y versión que mostró. El servicio vuelve a cargar la cabecera y la versión bajo lock, comprueba jugador activo, estado válido y coincidencia exacta. El importe se recalcula en backend. Una diferencia numérica, de versión, identidad, vigencia o estado se trata como cambio de cotización; no se usa un precio histórico como fallback.

`QUOTE_CHANGED` no cambia oferta, reserva, saldo, tenencia ni historial. La respuesta incluye `current_quote` cuando existe una cotización válida; si no existe, incluye `current_quote: null` y el estado operativo `SUSPENDED`. La UI debe mostrar la nueva cotización, o la suspensión sin acción de confirmar, y obtener una nueva confirmación/key solo cuando exista una cotización válida nuevamente.

## Límites

Player Valuation conserva cálculo/publicación; Player Statistics Sync y WhoScored conservan estadísticas/scraping. P2P solo consume el contrato interno y guarda la referencia exacta usada para auditoría.
