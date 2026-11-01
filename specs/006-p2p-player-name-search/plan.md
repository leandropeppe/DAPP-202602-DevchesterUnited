# Implementation Plan: Búsqueda P2P por nombre de jugador

**Branch**: `006-p2p-player-name-search` | **Date**: 2026-11-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/006-p2p-player-name-search/spec.md`

**Note**: This template is filled in by the `$speckit-plan` command; its definition describes the execution workflow.

## Summary

El mercado P2P debe presentar el nombre visible del jugador y permitir buscar ofertas por coincidencia parcial e insensible a mayúsculas, sin filtrar sólo la página actual. La solución extiende la consulta paginada existente con un filtro opcional por nombre, proyecta el nombre desde `jugadores`, lo expone como `player_name`, y usa un formulario explícito con `Buscar`, Enter y `Limpiar`. El UUID continúa siendo el identificador técnico para las operaciones.

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Java 21; JavaScript/React existente en frontend

**Primary Dependencies**: Spring Boot, Spring Data JPA, PostgreSQL, Vite, Vitest, Testing Library

**Storage**: PostgreSQL; consulta nativa paginada sobre `market_p2p_offers`, `jugadores`, usuarios y cotizaciones

**Testing**: JUnit 5 + Mockito + Testcontainers/MockMvc; Vitest + Testing Library

**Target Platform**: Aplicación web con backend Spring Boot y frontend React

**Project Type**: Web application monorepo con API REST

**Performance Goals**: Buscar sobre el conjunto persistido con el mismo tamaño de página y conteo correcto; editar filtros no debe generar consultas hasta una acción explícita.

**Constraints**: No filtrar resultados en React; no cambiar operaciones P2P; mantener `playerId`; no agregar dependencias; preservar paginación y arquitectura por capas.

**Scale/Scope**: Un endpoint y una pantalla del mercado P2P, una proyección/DTO de listado, cobertura de filtros, nombre, paginación y aplicación explícita.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Arquitectura por capas**: PASS. El controller sólo construye el query y delega al service; el repository mantiene la consulta SQL.
- **Contrato público/OpenAPI**: PASS. Se documentará `playerName` como parámetro opcional y `player_name` en la respuesta.
- **Persistencia y paginación**: PASS. El filtro se aplica en SQL y el `countQuery` replica la condición antes de paginar.
- **Frontend**: PASS. React sólo gestiona presentación, estado editable/aplicado y acciones explícitas; no agrega lógica de negocio ni filtrado local.
- **Testing**: PASS con una restricción operativa. Se agregarán tests nuevos sin modificar ni borrar los existentes, conforme a la regla constitucional.
- **Dependencias**: PASS. No se requieren librerías nuevas.

## Project Structure

### Documentation (this feature)

```text
specs/006-p2p-player-name-search/
├── plan.md              # This file ($speckit-plan command output)
├── research.md          # Phase 0 output ($speckit-plan command)
├── data-model.md        # Phase 1 output ($speckit-plan command)
├── quickstart.md        # Phase 1 output ($speckit-plan command)
├── contracts/           # Phase 1 output ($speckit-plan command)
└── tasks.md             # Phase 2 output ($speckit-tasks command - NOT created by $speckit-plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
backend/
└── src/
    ├── main/java/com/devchesterunited/devchesterunitedv2/controller/market/
    ├── main/java/com/devchesterunited/devchesterunitedv2/service/dto/market/
    ├── main/java/com/devchesterunited/devchesterunitedv2/service/impl/market/
    ├── main/java/com/devchesterunited/devchesterunitedv2/persistence/market/
    └── test/java/com/devchesterunited/devchesterunitedv2/

frontend/
└── src/
    ├── api/
    ├── views/
    └── components/
```

**Structure Decision**: Se mantiene la estructura web existente. El backend conserva la cadena controller → service → repository/projection/mapper/DTO y el frontend modifica únicamente la pantalla, el cliente HTTP y las pruebas nuevas asociadas.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
