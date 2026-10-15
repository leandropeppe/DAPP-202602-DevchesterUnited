# Plan de implementación: Sincronización completa de estadísticas de jugadores

**Branch**: `002-player-statistics-sync` | **Fecha**: 2026-10-15 | **Spec**: [spec.md](spec.md)

**Input**: Especificación de funcionalidad en `specs/002-player-statistics-sync/spec.md`

## Resumen

La ampliación extiende la sincronización existente de WhoScored desde tres
familias a cuatro: `summary`, `offensive`, `defensive` y `passing`. El Adapter
continuará adquiriendo y validando las respuestas, pero devolverá un snapshot
por competición consolidado por jugador. El Service seguirá sin conocer el
formato externo, Selenium ni sus parámetros.

El dominio se reorganiza mediante composición en cuatro records independientes
dentro de `MetricasEstadisticasJugador`. La persistencia continúa siendo una
única fila por jugador en `jugador_estadisticas`; V15 no se modifica y V16
agrega las columnas necesarias, junto con la evolución equivalente del staging.

La lectura local devolverá cuatro familias en español `camelCase`, y
`PlayerDetailPage` habilitará Resumen, Ataque, Defensa y Pases. Las ausencias
legítimas se almacenarán como `null`; en una sincronización válida reemplazan
el valor anterior de esa métrica.

## Contexto técnico

**Lenguaje/Versión**: Java 21, Spring Boot 3.5.x; frontend React con JavaScript/JSX.

**Dependencias principales**: Spring Web, Spring Data JPA, Flyway, Selenium,
Springdoc OpenAPI; Vitest, Testing Library y Playwright en frontend.

**Storage**: PostgreSQL. Migraciones en `backend/src/main/resources/db/migration`.

**Testing**: JUnit 5, Spring Boot Test, Testcontainers PostgreSQL, tests de
Adapter con fixtures/mocks, Vitest/Testing Library y Playwright.

**Plataforma objetivo**: aplicación web con backend Spring Boot y frontend React.

**Tipo de proyecto**: aplicación web full-stack con integración externa y
persistencia local.

**Objetivos de rendimiento**: conservar la adquisición actual de una llamada
por familia y competición mientras cada respuesta completa quepa en una página;
no introducir llamadas WhoScored durante consultas de detalle.

**Restricciones**: una sincronización lógica es atómica; no se publican
actualizaciones parciales; no se modifica V15; no se crean tablas por familia;
no se usan Internet ni credenciales reales en tests; no se modifica ni elimina
ningún test existente sin autorización.

**Escala/alcance**: cinco competiciones, hasta el límite vigente de 500
jugadores por respuesta externa, una fila consolidada por jugador y 30 métricas
distribuidas 8/9/8/5.

## Constitution Check

*GATE: aprobado antes de Phase 0 y debe mantenerse aprobado tras Phase 1.*

- **Capas**: Controller solo invoca Service; Service coordina dominio,
  persistencia y el puerto de Adapter; Modelo no conoce Spring ni infraestructura.
- **Adapter externo**: URLs, Selenium, JSON, categorías, parámetros e IDs de
  WhoScored quedan dentro de `adapter`; los DTO externos no cruzan al dominio.
- **Persistencia local**: la lectura del detalle consulta solo repositories y no
  depende de WhoScored.
- **Sincronización**: se conserva idempotencia, staging, promoción atómica,
  rollback y el último estado válido ante fallos.
- **Migraciones**: V15 queda intacta; V16 evoluciona la tabla consolidada y el
  staging sin introducir tablas por familia.
- **Testing**: se agregan tests unitarios, integración, Adapter, API y frontend
  sin red ni credenciales reales; los tests existentes permanecen intactos.
- **API**: el detalle y sus cuatro familias quedan documentados mediante
  OpenAPI; el frontend consume únicamente ese contrato.
- **Frontend**: se conservan 2 espacios, comillas simples, ausencia de punto y
  coma, JSX semántico y manejo explícito de estados asíncronos.
- **Fuera de alcance**: no se agregan normalización `[0,1]`, score, cotización,
  rankings, histórico ni mercado.

No se identifica ninguna violación que requiera una justificación de complejidad.

## Estructura del proyecto

### Documentación de la feature

```text
specs/002-player-statistics-sync/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── player-statistics-sync.md
└── tasks.md                         # generado por $speckit-tasks
```

### Código backend

```text
backend/src/main/java/com/devchesterunited/devchesterunitedv2/
├── adapter/
│   ├── dto/WhoScoredStatisticsFeedResponseDTO.java
│   └── impl/WhoScoredStatisticsAdapter.java
├── modelo/
│   ├── MetricasEstadisticasJugador.java
│   ├── MetricasGenerales.java
│   ├── MetricasOfensivas.java
│   ├── MetricasDefensivas.java
│   ├── MetricasPases.java
│   ├── RegistroEstadisticasJugador.java
│   └── SnapshotEstadisticasJugador.java
├── persistence/
│   ├── dto/EstadisticasJugadorEntity.java
│   ├── dto/EstadisticasSyncStagingEntity.java
│   ├── EstadisticasJugadorRepository.java
│   └── EstadisticasSyncStagingRepository.java
├── service/impl/
│   ├── ConsolidadorEstadisticasJugador.java
│   ├── EstadisticasStagingService.java
│   └── SincronizacionEstadisticasJugadorServiceImpl.java
└── controller/dto/
    ├── EstadisticasJugadorResponseDTO.java
    └── JugadorDetalleResponseDTO.java
```

```text
backend/src/main/resources/db/migration/
└── V16__Expand_player_statistics_sync.sql
```

### Código frontend y pruebas

```text
frontend/src/
├── api/http.js
├── views/PlayerDetailPage.jsx
├── components/PlayerCard.jsx
└── styles/player-detail.css

backend/src/test/...
frontend/src/**/*.test.*
frontend/tests/...
```

**Decisión de estructura**: se mantiene Package by Layer en backend y la
organización React existente. La ampliación modifica clases existentes cuando
representan el contrato vigente y agrega solo records, DTOs, fixture, migración
y tests necesarios.

## Diseño por fases

### Phase 0: investigación consolidada

Las decisiones y evidencias están en [research.md](research.md). En particular:

1. El Adapter adquiere cuatro categorías dentro de una única operación por
   competición y valida cada respuesta antes de consolidar.
2. La consolidación se hace por `playerId`, con una familia propietaria por
   métrica repetida.
3. `passSuccess` conserva escala 0..100 y la escala decimal existente solo se
   estandariza; no hay normalización estadística.
4. El puerto Service sigue devolviendo un snapshot completo por competición.

### Phase 1: diseño y contratos

1. Actualizar [data-model.md](data-model.md) con records de dominio, entidad
   consolidada, staging, tipos, nulabilidad, relaciones y reglas de precedencia.
2. Actualizar [contracts/player-statistics-sync.md](contracts/player-statistics-sync.md)
   con el puerto interno, la API de detalle y el contrato de navegación frontend.
3. Actualizar [quickstart.md](quickstart.md) con validaciones reproducibles para
   las cuatro familias, V16, API, frontend e idempotencia.

### Phase 2: implementación posterior

La descomposición ejecutable queda para `$speckit-tasks`. Deberá cubrir, en
orden, dominio y mapeos, DTO/Adapter, consolidación, migración y repositories,
staging/promoción, API, frontend, tests y validación completa. No se deben
modificar ni eliminar tests existentes.

## Flujo arquitectónico objetivo

1. El Service inicia la sincronización y adquiere el lock vigente.
2. El Adapter abre una sesión WebDriver y, por competición, solicita `summary`,
   `offensive`, `defensive` y `passing`.
3. Cada respuesta se valida con las reglas actuales de completitud de una
   página; un fallo aborta la sincronización lógica.
4. El Adapter traduce DTOs externos a estructuras internas y consolida por
   `playerId`, aplicando la propiedad canónica de cada métrica.
5. El Service resuelve identidades locales sin conocer familias ni transporte.
6. El staging recibe las 30 métricas y marcas de las cuatro familias.
7. La promoción transaccional actualiza la única fila de
   `jugador_estadisticas`; los valores ausentes válidos se escriben como
   `null`, y los fallos hacen rollback.
8. El Adapter cierra WebDriver aun ante excepciones y el Service libera el lock.

## Decisiones de mapeo y precedencia

| Familia propietaria | Campos WhoScored incluidos |
|---|---|
| Generales | `apps`, `subOn`, `minsPlayed`, `manOfTheMatch`, `aerialWonPerGame`, `rating`, `yellowCard`, `redCard` |
| Ofensivas | `goal`, `assistTotal`, `shotsPerGame`, `keyPassPerGame`, `dribbleWonPerGame`, `foulGivenPerGame`, `offsideGivenPerGame`, `dispossessedPerGame`, `turnoverPerGame` |
| Defensivas | `tacklePerGame`, `interceptionPerGame`, `foulsPerGame`, `offsideWonPerGame`, `clearancePerGame`, `wasDribbledPerGame`, `outfielderBlockPerGame`, `goalOwn` |
| Pases | `totalPassesPerGame`, `passSuccess`, `accurateCrossesPerGame`, `accurateLongPassPerGame`, `accurateThroughBallPerGame` |

Las repeticiones conocidas se resuelven siempre hacia su propietaria: `minsPlayed`
y `rating` en generales; `assistTotal` y `keyPassPerGame` en ofensivas;
`passSuccess` en pases. Ninguna familia secundaria duplica ni sobrescribe la
representación propietaria.

## Constitución: reevaluación posterior al diseño

- El diseño mantiene las dependencias permitidas y separa DTO externo, dominio,
  persistence y API pública.
- La única persistencia sigue siendo `jugador_estadisticas`; el staging es
  técnico y no visible para negocio.
- El fallo de cualquier familia requerida invalida la sincronización completa,
  por lo que no quedan estados parciales.
- La API pública usa español `camelCase`, `null` legítimo y porcentaje 0..100.
- El frontend tiene cuatro pestañas accesibles y no consume fuentes externas.
- Los tests requeridos pueden ejecutarse con fixtures, mocks y Testcontainers,
  sin Internet ni credenciales reales.

**Resultado del gate**: aprobado; no quedan incógnitas bloqueantes.

## Complexity Tracking

No aplica: no hay violaciones de la Constitution que justificar.
