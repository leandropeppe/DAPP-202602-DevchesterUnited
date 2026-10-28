# Plan de implementación: simulador de cotización futura de jugadores

**Branch**: `005-player-valuation-simulator` | **Date**: 2026-10-28 | **Spec**: [spec.md](./spec.md)

## Summary

Se incorporará un caso de uso de simulación exclusivamente en memoria. El backend cargará el jugador, sus estadísticas normalizadas, la cotización visible y la estrategia/version vigente; devolverá la configuración editable y aceptará un horizonte de 3, 5, 8 o 10 partidos junto con únicamente los cambios del usuario. Un proyector de dominio construirá una copia temporal de las métricas, sumará automáticamente apariciones y 90 minutos por partido, y distinguirá incrementos acumulativos de valores finales promedio/porcentuales. El resultado se calculará delegando en el mismo `MotorValorizacion` y la misma estrategia de 004, sin invocar repositorios de cotizaciones, históricos ni publicación de mercado.

La UI agregará el simulador al detalle del jugador mediante un modal accesible, con campos derivados del contrato de estrategia, recalculo/restablecimiento, comparación monetaria y loader de Maradona con mínimo de dos segundos solo en éxitos rápidos.

## Technical Context

**Language/Version**: Java 21; JavaScript/React existente con Vite.

**Primary Dependencies**: Spring Boot, Spring MVC/Validation, Jackson, JPA para lectura, PostgreSQL/Flyway, JUnit 5, Testcontainers; React, Vitest, Testing Library y Playwright.

**Storage**: PostgreSQL existente. No se agregan tablas ni migraciones: el escenario, proyección y resultado viven en la request/respuesta y en el estado local del modal.

**Testing**: Unit tests de dominio y servicio sin Spring; contract/E2E con MockMvc; integración de lectura contra PostgreSQL mediante Testcontainers; Vitest/Testing Library y Playwright para el frontend.

**Target Platform**: API Spring Boot y SPA React/Vite ejecutada en navegador moderno.

**Project Type**: Web application monorepo con backend REST y frontend SPA.

**Performance Goals**: cálculo síncrono y acotado al jugador solicitado; sin esperar artificialmente en backend. La UI garantiza 2 s mínimos solo cuando la respuesta exitosa llega antes de ese umbral.

**Constraints**: horizontes exactos `{3,5,8,10}`; posiciones y estrategia deben resolverse con las reglas de 004; estadísticas oficiales, cotizaciones e históricos son inmutables; no se inventan métricas ausentes; mensajes y documentación en español; no exponer DTOs de WhoScored.

**Scale/Scope**: un jugador por simulación, una estrategia visible/versionada por request, sin almacenamiento de escenarios ni comparación histórica.

## Constitution Check

*GATE: PASS antes de research.*

- Se conserva Java 21, Spring Boot, PostgreSQL, React/Vite y la separación Controller → Service → Modelo/Persistence.
- El escenario se modela como dominio inmutable/transitorio; no requiere persistencia nueva ni modifica entidades oficiales.
- El Controller solo recibe DTOs y delega al Service. El Service lee repositorios existentes y llama al motor de dominio; no accede a APIs externas.
- La lógica de proyección, clasificación y validación de invariantes queda en Modelo; Bean Validation cubre forma y Service existencia/estrategia.
- Se reutiliza `MotorValorizacion`; no se duplican normalizaciones, ponderaciones, penalizaciones o elegibilidad.
- Se documentan contratos consumidos por frontend y se agregan pruebas de éxito y borde sin modificar tests existentes.
- No hay violaciones que requieran Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/005-player-valuation-simulator/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    └── valuation-simulator-api.yaml
```

### Source Code (repository root)

```text
backend/src/main/java/com/devchesterunited/devchesterunitedv2/
├── controller/jugador/                 # endpoints REST y DTOs públicos
├── modelo/simulacion/                  # escenario, proyección y resultado temporales
├── service/impl/simulacion/             # orquestación de lectura + motor
├── service/dto/simulacion/              # DTOs internos del caso de uso
└── service/interfaces/simulacion/       # puerto del caso de uso

backend/src/test/java/com/devchesterunited/devchesterunitedv2/
├── unit/simulacion/
├── contract/simulacion/
├── integration/simulacion/
└── support/fixtures/valorizacion/

frontend/src/
├── api/http.js
├── components/PlayerValuationSimulator.jsx
├── components/MaradonaLoader.jsx
├── views/PlayerDetailPage.jsx
└── styles/player-valuation-simulator.css
```

**Structure Decision**: se mantiene la estructura actual de aplicación única con paquetes por capa en backend y componentes/vistas en frontend. La simulación tendrá un subpaquete propio para no mezclar DTOs temporales con valorización oficial; el motor y las estrategias de 004 permanecen en `modelo/valorizacion`.

## Phase 0: Research

Ver [research.md](./research.md). Las decisiones resueltas cubren representación y semántica de métricas, estrategia/motor 004, posiciones múltiples, datos reales de arqueros, ausencia de persistencia y patrón de contratos.

## Phase 1: Design

Ver [data-model.md](./data-model.md), [contracts/valuation-simulator-api.yaml](./contracts/valuation-simulator-api.yaml) y [quickstart.md](./quickstart.md). El diseño define un endpoint de preparación y uno de ejecución, ambos sin efectos de escritura.

## Constitution Check (post-design)

*GATE: PASS.* El diseño no agrega tablas, no crea un segundo motor y mantiene las validaciones distribuidas entre DTO, Service y Modelo. La API pública expone únicamente representaciones internas controladas, y frontend consume exclusivamente estos contratos documentados. Las pruebas cubren equivalencia con 004, ausencia de persistencia, posiciones, datos faltantes, porcentajes/rangos, loader y restablecimiento.

## Complexity Tracking

Sin violaciones constitucionales justificadas.
