# Implementation Plan: Valorización de jugadores según estrategia

**Branch**: `004-player-valuation-strategies` | **Date**: 2026-10-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-player-valuation-strategies/spec.md`

## Summary

El feature calcula y conserva resultados de valorización independientes para todas las estrategias habilitadas a partir de estadísticas deportivas persistidas. Inicialmente se habilitarán GENERAL v1 y POSITIONAL v1. Una estrategia visible/oficial determina qué serie se devuelve por defecto, pero no limita los cálculos.

La lógica se ubicará en el dominio y será orquestada por servicios existentes de la aplicación. El cálculo no dependerá de tokenización, compra, venta o portfolio. La persistencia seguirá el patrón Repository/JPA/Flyway ya utilizado por el backend y las consultas, el ranking y el recálculo manual respetarán el contrato REST/OpenAPI existente.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.5, Spring Data JPA, Flyway, Spring Web/OpenAPI

**Storage**: PostgreSQL mediante migraciones Flyway y repositorios existentes

**Testing**: JUnit 5 para dominio; Spring Boot/Testcontainers/PostgreSQL para persistencia y servicios; MockMvc para contratos HTTP

**Target Platform**: Servicio backend ejecutado en JVM 21

**Project Type**: Web service backend con procesos periódicos, ejecución manual protegida por API key y consumo desde el frontend existente

**Performance Goals**: Completar una valorización sobre toda la población elegible disponible sin que jugadores no elegibles interrumpan el proceso; evitar consultas por jugador para rangos de normalización y persistencia parcial

**Constraints**: No umbral funcional por defecto para partidos/minutos; métricas positivas requeridas obligatorias por jugador y estrategia; métricas negativas ausentes sin penalización; históricos separados e inmutables por estrategia/version; como máximo una estrategia visible

**Scale/Scope**: Población de jugadores sincronizados de las competiciones existentes; GENERAL v1 y POSITIONAL v1 habilitadas, cuatro posiciones posicionales, cotización actual e histórico por estrategia; sin UI administrativa ni tokenización

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **PASS**: La fórmula, normalización, elegibilidad y restricciones permanecen en objetos de dominio independientes de Spring, HTTP, PostgreSQL y WhoScored.
- **PASS**: La orquestación de estadísticas y persistencia queda en `service`, `persistence` y contratos internos; no se agregan llamadas externas al dominio.
- **PASS**: La fuente de estadísticas existente se consume mediante representaciones internas normalizadas; no se incorpora scraping nuevo.
- **PASS**: Las nuevas entidades persistidas tendrán migración Flyway, repositorios separados y pruebas unitarias/integración.
- **PASS**: Las consultas de cotización, el ranking y el recálculo manual protegido se documentan mediante OpenAPI v3. No se expone cambio de estrategia visible con autorización administrativa.
- **PASS**: Se conservarán tests existentes y se agregarán casos felices y de borde; no se modificarán ni eliminarán tests existentes.
- **PASS**: La feature no crea dependencia funcional con tokenización, compra, venta, wallets, portfolios o balances.

## Project Structure

### Documentation (this feature)

```text
specs/004-player-valuation-strategies/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/valuation-api.yaml
└── tasks.md              # generado por $speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/com/devchesterunited/devchesterunitedv2/
├── modelo/              # estrategia, score, elegibilidad y cotización
├── persistence/         # entities, repositories y mappers de valorización
├── service/             # ejecución manual/semanal y consultas
├── controller/          # consultas REST y DTOs de cotización
└── configuration/       # scheduler, si la configuración actual lo requiere

backend/src/main/resources/db/migration/
└── nueva-version-disponible__Create_player_valuation.sql (número determinado al implementar)

backend/src/test/java/com/devchesterunited/devchesterunitedv2/
├── modelo/*Valorizacion*Test.java
├── service/*Valorizacion*Test.java
├── persistence/*Cotizacion*IntegrationTest.java
└── controller/*Cotizacion*E2ETest.java
```

**Structure Decision**: Se mantiene el backend existente organizado por capas. El dominio será independiente de infraestructura; el servicio coordinará jugadores, estadísticas y repositorios; las consultas REST, el ranking y el recálculo manual utilizarán DTOs y seguridad propios; las tablas de cotización e histórico se agregarán mediante Flyway. El frontend existente se adapta únicamente al endpoint canónico del histórico, sin agregar una pantalla administrativa.

### Constitution Check — Post-Design Re-evaluation

- **PASS**: `research.md` resuelve las decisiones de arquitectura y datos faltantes sin fijar un umbral funcional de partidos/minutos.
- **PASS**: `data-model.md` mantiene históricos separados por estrategia/version, múltiples estrategias habilitadas, una estrategia visible única y trazabilidad de métricas faltantes.
- **PASS**: `contracts/valuation-api.yaml` documenta consultas, ranking y recálculo manual protegido, sin agregar administración de roles o estrategias.
- **PASS**: `quickstart.md` exige pruebas unitarias, integración y contrato sin APIs reales ni modificación de tests existentes.
- **PASS**: No se requiere complejidad adicional ni excepción a la organización Package by Layer.

## Complexity Tracking

No hay violaciones justificadas de la constitución. La tabla de complejidad queda vacía.
