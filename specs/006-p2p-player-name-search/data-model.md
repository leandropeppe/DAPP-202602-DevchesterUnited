# Data Model: Búsqueda P2P por nombre de jugador

## Oferta P2P proyectada

- `offer_id`: UUID técnico de la oferta.
- `player_id`: UUID técnico del jugador, conservado para compra, cancelación y publicación.
- `player_name`: nombre visible obtenido de `jugadores.name`.
- `seller_alias`, `mine`: identidad pública y pertenencia al usuario autenticado.
- `remaining_quantity`, `status`, `purchasable`, `quote`: datos actuales de disponibilidad y cotización.

## Filtro de consulta

- `playerName`: texto opcional, recortado; vacío equivale a ausencia de filtro.
- `playerId`: UUID opcional existente, combinado con `playerName` si ambos llegan.
- `status`, `mine`, `page`, `limit`: filtros existentes sin cambios semánticos.

## Reglas

1. `playerName` se compara contra el nombre persistido sin distinguir mayúsculas/minúsculas y por fragmento.
2. La condición de nombre se aplica antes de ordenar y paginar.
3. El conteo usa exactamente las mismas condiciones que la consulta de contenido.
4. Si falta el nombre por una inconsistencia excepcional, el UUID permanece disponible internamente; la UI usa fallback sólo como último recurso.
