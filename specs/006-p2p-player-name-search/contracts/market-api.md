# Contrato de API: listado P2P por nombre

## `GET /api/v1/market/p2p/offers`

Parámetros opcionales:

- `playerName`: texto parcial del nombre visible del jugador.
- `playerId`: UUID existente, conservado por compatibilidad.
- `status`, `mine`, `page`, `limit`: filtros existentes.

El filtrado por `playerName` es parcial e insensible a mayúsculas/minúsculas y se ejecuta sobre el listado persistido antes de la paginación.

Cada elemento de `data.offers` incluye al menos:

```json
{
  "player_id": "uuid",
  "player_name": "Federico Valverde"
}
```

El resto del contrato de oferta, cotización y paginación no cambia.
