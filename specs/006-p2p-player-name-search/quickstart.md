# Quickstart de validación

## Prerrequisitos

- Java 21 y el entorno PostgreSQL/Testcontainers del proyecto.
- Node y dependencias frontend ya instaladas.

## Validación backend

Desde `backend/`, ejecutar la suite focalizada del listado P2P y las pruebas nuevas de repositorio/controlador/contrato.

Validar:

1. Nombre exacto, parcial y case-insensitive.
2. Sin coincidencias.
3. Combinación con `status` y `mine`.
4. Conteo y contenido de páginas sucesivas.
5. Presencia de `player_name` en la respuesta.
6. Compatibilidad de `playerId` y validación de sus errores actuales.

## Validación frontend

Desde `frontend/`, ejecutar Vitest para la pantalla y los tests nuevos.

Validar:

1. Placeholder `Buscar por nombre` y nombre visible en la card.
2. Envío de `playerName`, no filtrado local.
3. Ninguna consulta al editar nombre, estado o “mis ofertas”.
4. `Buscar` y Enter aplican filtros y reinician la página a 1.
5. `Limpiar` restablece todos los campos y hace una única consulta sin filtros.
6. La paginación reutiliza sólo los filtros aplicados.
7. Las acciones de comprar, cancelar y publicar siguen recibiendo UUID.
