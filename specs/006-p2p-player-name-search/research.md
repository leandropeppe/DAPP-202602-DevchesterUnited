# Research: Búsqueda P2P por nombre de jugador

## Decisión 1: Reutilizar la relación persistida `jugadores`

- **Decision**: El nombre se obtiene en la consulta existente mediante el `JOIN jugadores j` y se proyecta como `player_name`.
- **Rationale**: El repositorio ya relaciona cada oferta con `jugadores` para evaluar actividad, por lo que no hace falta un acceso adicional ni una consulta por oferta.
- **Alternatives considered**: Resolver nombres en el frontend habría requerido otra API y no permitiría filtrar correctamente antes de paginar; un lookup por servicio produciría riesgo de N+1.

## Decisión 2: Aplicar `playerName` en la consulta paginada

- **Decision**: Agregar una condición opcional equivalente a `LOWER(j.name) LIKE LOWER('%texto%')` tanto en la consulta principal como en `countQuery`.
- **Rationale**: Mantiene el total de páginas correcto y evita cargar o filtrar sólo los resultados visibles.
- **Alternatives considered**: Filtrado posterior en Java/React se descarta porque rompe el contrato semántico de paginación.

## Decisión 3: Mantener `playerId` temporalmente

- **Decision**: Conservar `playerId` en controller, query, repository y cliente HTTP; el filtro visual principal pasa a ser `playerName`.
- **Rationale**: El requisito pide revisar compatibilidad antes de eliminarlo y ya existen tests/consumidores del contrato.
- **Alternatives considered**: Eliminarlo ahora introduciría una ruptura innecesaria y no aporta valor a la mejora UX.

## Decisión 4: Aplicación explícita de filtros

- **Decision**: Separar los valores editables del formulario de los filtros aplicados y consultar sólo con `Buscar`, Enter o `Limpiar`.
- **Rationale**: Evita requests durante la edición y mantiene alineados los filtros visibles con la consulta ejecutada.
- **Alternatives considered**: El debounce automático se descarta porque sigue generando consultas mientras el usuario escribe y no coincide con la UX del catálogo.
