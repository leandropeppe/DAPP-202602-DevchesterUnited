# Implementation Plan: Token Market P2P

**Branch**: `feature/token-market-p2p` | **Date**: 2026-10-28 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/005-token-market-p2p/spec.md`

## Summary

Agregar un mercado secundario P2P de ofertas de venta a precio derivado exclusivamente de la cotización oficial vigente, sin alterar el mercado primario. La solución reutiliza cuentas, tenencias, validación de cotizaciones, idempotencia, movimientos y controles transaccionales existentes; incorpora ofertas y reservas explícitas, liquidación usuario-a-usuario, asientos de historial P2P y el desglose total/reservado/disponible del portfolio. El endpoint histórico de venta al superusuario queda retirado con HTTP 410, sin borrar ni transformar transacciones anteriores.

## Technical Context

**Language/Version**: Java 21 mediante `backend/build.gradle` (`java.toolchain.languageVersion = JavaLanguageVersion.of(21)`); JavaScript/React existente

**Primary Dependencies**: Spring Web, Spring Data JPA, Bean Validation, PostgreSQL, Flyway, JWT/Spring Security, React y herramientas de test ya instaladas

**Storage**: PostgreSQL. La siguiente migración disponible verificada es V21, posterior a `V20__add_market_quote_header_validity.sql`; el archivo SQL se planificará pero no se crea en esta etapa.

**Testing**: JUnit 5, Mockito/unit tests, MockMvc/E2E y Testcontainers PostgreSQL; tests frontend existentes del proyecto

**Target Platform**: API Spring Boot y aplicación web React

**Project Type**: Web application con backend REST y frontend React

**Performance Goals**: Mantener el comportamiento actual del mercado primario y resolver una consulta paginada de ofertas sin cargar el historial completo; la liquidación debe usar locks acotados a la oferta, cotización, cuentas y tenencias involucradas.

**Constraints**: No modificar Player Valuation, Player Statistics Sync ni WhoScored; no introducir precio manual, subastas ni order book; no permitir estados contables parciales; toda operación reintentable debe ser idempotente.

**Scale/Scope**: Un jugador puede tener múltiples ofertas; cada oferta pertenece a un vendedor y una posición. Las compras pueden ser parciales y concurrentes. El cambio se limita a Token Market, su API, portfolio, historial y frontend de mercado.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Result | Evidence |
|---|---|---|
| División Controller/Service/Modelo/Persistence | PASS | Los controladores delegan en servicios; ofertas, reservas y liquidación se modelan dentro de los paquetes `modelo`, `service` y `persistence` existentes. |
| Cotizaciones e integraciones externas | PASS | P2P consume el contrato/versionado existente; no llama a Player Valuation, estadísticas ni WhoScored ni duplica su lógica. |
| Seguridad e identidad | PASS | El vendedor, comprador y dueño de portfolio provienen del principal JWT; el cuerpo no selecciona otra cuenta. |
| Integridad transaccional | PASS | Reserva, transferencia, movimientos, estado de oferta e historial se confirman en una única transacción; restricciones de base de datos respaldan invariantes. |
| Concurrencia | PASS | Orden de locks determinista por recurso y por identificador; no se propone lock global para operaciones normales. |
| Compatibilidad | PASS WITH CONTROL | Se conserva el flujo de compra primaria y el historial legado; `POST /api/v1/market/sales` se mantiene como endpoint retirado que devuelve 410. |
| OpenAPI y frontend | PASS | Los endpoints nuevos y la retirada se documentan; React consume únicamente contratos backend y maneja 409/410 explícitamente. |
| Testing y DoD | PASS | Se agregan unitarios, integración PostgreSQL/Testcontainers, MockMvc/E2E, concurrencia, idempotencia y regresión frontend sin alterar tests existentes. |

## Project Structure

### Documentation (this feature)

```text
specs/005-token-market-p2p/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    ├── market-api.md
    └── quote-contract.md
```

`tasks.md` queda reservado para `/speckit.tasks` y no forma parte de esta etapa.

### Source Code (repository root)

```text
backend/src/main/java/com/devchesterunited/devchesterunitedv2/
├── controller/market/
│   ├── MarketController.java
│   └── dto/
├── modelo/market/
├── persistence/market/
│   ├── entity/
│   ├── repository/
│   ├── dto/
│   └── mapper/
└── service/
    ├── dto/market/
    ├── interfaces/market/
    └── impl/market/

backend/src/main/resources/db/migration/
└── V21__create_token_market_p2p.sql  # futuro; no se crea en Plan

backend/src/test/java/com/devchesterunited/devchesterunitedv2/
├── unit/market/
├── integration/market/
├── integration/persistence/
├── e2e/market/
└── contract/market/

frontend/src/
├── api/http.js
├── components/
├── views/MarketPortfolioPage.jsx
├── views/MarketTransactionsPage.jsx
└── styles/market.css
```

**Structure Decision**: Se conserva el package-by-layer existente. Las clases P2P se agregan a los subpaquetes `market` actuales; no se crea un bounded context paralelo ni se toca el código de valoración, estadísticas o scraping. El frontend extiende `api`, `components`, `views` y estilos de mercado existentes.

## Implementation Design

1. **Retirada compatible del sale primario**: `MarketController` responderá 410 desde `POST /api/v1/market/sales` antes de invocar liquidación. Los servicios/repositorios históricos se conservan para lectura y para que los registros anteriores sigan visibles; no se ejecuta la antigua transferencia inversa.
2. **Oferta y reserva**: una nueva entidad de oferta mantiene vendedor, jugador, cantidad original, cantidad restante, cantidad vendida derivada, cantidad liberada al cancelar, estado, timestamps y versión optimista. `market_holdings` incorpora `reserved_quantity` con valor inicial cero y la restricción `0 <= reserved_quantity <= quantity`; la suma de `remaining_quantity` de ofertas activas del vendedor/jugador debe coincidir con la reserva de la tenencia. No se utilizará un registro de reserva separado. La publicación bloquea la posición y reserva inmediatamente.
3. **Precio oficial**: la oferta no tiene precio editable. La consulta muestra la cotización actual disponible. La compra recibe identidad/versión observada, vuelve a cargar y valida la cabecera y la versión exactas bajo lock; si cambió responde `409 QUOTE_CHANGED` sin mutar la oferta y devuelve `current_quote` cuando existe. Si no hay quote válida, devuelve `current_quote: null` y la oferta queda suspendida, sin confirmación posible. La confirmación posterior usa una nueva Idempotency-Key.
4. **Liquidación P2P**: compra parcial o total, crédito comprador → vendedor, tokens vendedor → comprador, reducción de reserva y creación de movimientos/asientos se ejecutan en un único límite transaccional. Se reutilizan `MarketQuoteValidationService`, `MarketIdempotencyService`, `MarketBalanceService`, tenencias, movimientos y patrones de settlement existentes.
5. **Historial**: para no alterar restricciones ni semántica de los registros primarios, se propone una representación P2P append-only vinculada a la oferta y dos asientos de participación (compra P2P y venta P2P). `MarketTransactionService` unifica ambos orígenes en el DTO existente/extendido, preservando las ventas históricas al superusuario y el aislamiento por usuario.
6. **Locks**: se conserva el prefijo actual de compra primaria: claim idempotente/advisory lock → estado de mercado → jugador → cabecera de cotización → cuentas → emisión → tenencia. P2P usa claim idempotente/advisory lock → estado de mercado → oferta existente (o creación posterior) → jugador → cabecera de cotización → cuentas de comprador/vendedor en UUID ascendente → tenencias involucradas en `(user_id, player_id)` ascendente → oferta/tenencia/movimientos. Publicación usa claim → estado → jugador → cabecera → tenencia vendedora → inserción de oferta; cancelación usa claim → estado → oferta → tenencia vendedora. No se toma una cuenta después de una tenencia. La implementación debe preservar este orden y tratar deadlock/transient serialization failure con la política transaccional existente.
7. **Migración**: después de revisar V19 y V20, se reserva V21 para tablas/columnas P2P, índices, checks de cantidades y claves foráneas. La migración deberá ser forward-only y compatible con datos históricos; no se ejecuta ni se genera en Plan.
8. **Frontend**: `http.js` agrega clientes para listar/publicar/comprar/cancelar ofertas. `MarketPortfolioPage` muestra total/reservado/disponible y ofrece publicar solo con cantidad; una vista/componente de ofertas muestra cotización, estado y compra. La confirmación ante 409 refresca la cotización y exige una nueva confirmación/key; sin quote válida muestra suspensión. La cancelación repetida con la misma key recupera el resultado original y una key nueva sobre una oferta cancelada no vuelve a liberar tokens. El listado usa paginación/filtros del contrato y solo un alias público del vendedor. El antiguo botón de venta al superusuario desaparece y 410 se trata como retirada.

## Complexity Tracking

| Decision | Why needed | Simpler alternative rejected because |
|---|---|---|
| Persistir ofertas/reservas P2P separadas del inventario del superusuario | Permite parciales, cancelación y concurrencia sin confundir emisión primaria | Reutilizar la venta al superusuario permitiría transferir en dirección prohibida y no representa reservas de usuarios |
| Mantener asientos P2P junto a un lector histórico unificado | Conserva el esquema/contratos de transacciones primarias y evita migrar destructivamente ventas históricas | Sobrecargar la fila primaria con dos participantes y dos idempotencias rompe constraints y complica reintentos |
| Lock canónico de dos cuentas/tenencias | Dos usuarios pueden intercambiar recursos en órdenes opuestos | Bloquear siempre comprador primero puede generar deadlocks cuando otra operación tiene al vendedor como comprador |
| `reserved_quantity` en `market_holdings` | La disponibilidad debe ser una cifra contable bloqueada y consultable junto con la tenencia | Una tabla de reservas separada permite divergencia entre ofertas y portfolio y agrega otra fila crítica a bloquear |
