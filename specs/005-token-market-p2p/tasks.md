---

description: "Task list for Token Market P2P"
---

# Tasks: Token Market P2P

**Input**: Design documents from `specs/005-token-market-p2p/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Scope guard**: No modificar ni eliminar tests existentes sin aprobación explícita. Player Valuation, Player Statistics Sync y WhoScored permanecen fuera de alcance.

## Phase 1: Setup y compatibilidad

**Purpose**: Confirmar las convenciones existentes y preparar el trabajo sin cambiar todavía el comportamiento.

- [x] T001 Verificar en `backend/build.gradle` el toolchain Java 21, las tareas de test y las dependencias Spring/JPA/Flyway/Testcontainers definidas por el proyecto
- [x] T002 [P] Inventariar contratos, entidades, repositories, services y tests actuales de Token Market en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/` y `backend/src/test/java/com/devchesterunited/devchesterunitedv2/`
- [x] T003 [P] Verificar que las migraciones `backend/src/main/resources/db/migration/V19__create_token_market.sql` y `V20__add_market_quote_header_validity.sql` son las últimas migraciones Token Market y reservar `V21__create_token_market_p2p.sql`
- [x] T004 [P] Revisar `frontend/src/api/http.js`, `frontend/src/views/MarketPortfolioPage.jsx`, `frontend/src/views/MarketTransactionsPage.jsx`, `frontend/src/components/MarketOperationPanel.jsx` y `frontend/src/styles/market.css` para mantener las convenciones de integración
- [x] T005 Ejecutar únicamente la suite existente de Token Market bajo `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/market/`, `integration/market/`, `integration/persistence/` y `e2e/market/` como línea base, sin modificar sus archivos

**Checkpoint**: La línea base del mercado primario está identificada y los tests existentes permanecen intactos.

---

## Phase 2: Foundation - persistencia, contratos internos y concurrencia

**Purpose**: Crear los prerrequisitos comunes que bloquean todas las historias P2P.

**⚠️ CRITICAL**: Ninguna historia P2P puede comenzar hasta completar esta fase.

- [x] T006 Diseñar y crear `backend/src/main/resources/db/migration/V21__create_token_market_p2p.sql` con `market_holdings.reserved_quantity` inicializado en cero, checks `0 <= reserved_quantity <= quantity`, tablas de ofertas, trades y auditoría de cancelación, sin tabla de reservas separada
- [x] T007 [P] Crear `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/market/entity/OfertaVentaP2PEntity.java` con cantidades original/restante/vendida, estado y referencia al vendedor/jugador
- [x] T008 [P] Crear `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/market/entity/TradeP2PEntity.java` y `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/market/entity/AsientoP2PEntity.java` como registros inmutables de liquidación y participación histórica
- [x] T009 [P] Crear `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/market/entity/AuditoriaCancelacionOfertaP2PEntity.java` con original, vendida, liberada, actor y timestamp
- [x] T010 [P] Extender `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/market/entity/TenenciaMercadoEntity.java` y `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/market/repository/TenenciaMercadoRepository.java` para `reserved_quantity`, disponible y lock de escritura
- [x] T011 [P] Crear repositories P2P en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/market/repository/` para ofertas, trades, asientos y auditoría con consultas paginadas y locks requeridos
- [x] T012 Crear DTOs/commands internos en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/dto/market/` para oferta, cancelación, compra P2P, quote observada, paginación y errores
- [x] T013 Extender `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/market/MarketIdempotencyClaimService.java` y sus DTOs/repositorios para aceptar operaciones P2P y conservar hash, replay y conflicto de parámetros
- [x] T014 Extender `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/market/MarketOperationExecutionLock.java` o su coordinación asociada para reutilizar el advisory lock por usuario/key sin introducir un lock global
- [x] T015 Documentar e implementar en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/market/` el orden de locks `claim/advisory → market_state → offer cuando existe → player → quote_header → accounts UUID ascendente → holdings UUID/player ascendente → movimientos/auditoría`
- [x] T016 Crear `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/exception/` o extender el manejo existente para `MANUAL_PRICE_NOT_ALLOWED`, `QUOTE_CHANGED`, `OFFER_ALREADY_CANCELLED`, `OFFER_NOT_PURCHASABLE` y demás errores P2P sin alterar el envelope vigente
- [x] T017 Crear fixtures P2P reutilizables en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/support/fixtures/market/MarketP2PFixtureBuilder.java`, sin alterar fixtures existentes
- [x] T018 [P] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/persistence/MarketP2PSchemaIntegrationTest.java` para checks, FKs, índices, `reserved_quantity = 0` y ausencia de tabla de reservas separada
- [x] T019 [P] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/market/MarketP2PQuantityInvariantTest.java` para cantidad original, restante, vendida, liberada y disponible

**Checkpoint**: El esquema y las abstracciones comunes pueden soportar ofertas, reservas embebidas, trades, auditoría, idempotencia y locks.

---

## Phase 3: User Story 1 - Compra primaria y retiro de venta al superusuario (Priority: P1) 🎯 MVP de compatibilidad

**Goal**: Mantener las compras al superusuario y retirar nuevas ventas al superusuario sin perder historial.

**Traceability**: HU1/HU7; FR-002 a FR-005, FR-028, FR-030; aceptación HU1-1..HU1-3 y HU7-5.

**Independent Test**: Una compra primaria existente continúa transfiriendo correctamente; `POST /api/v1/market/sales` devuelve 410 y no cambia ningún estado.

### Tests for User Story 1

- [x] T020 [P] [US1] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/market/controller/MarketSaleGoneMockMvcTest.java` para HTTP 410 sin movimientos, saldos, tenencias ni transacción nueva
- [x] T021 [P] [US1] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/market/PrimaryPurchaseRegressionIntegrationTest.java` para saldo, inventario, tenencia, quote exacta e idempotencia de compra primaria sin modificar los tests existentes

### Implementation for User Story 1

- [x] T022 [US1] Cambiar `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/market/MarketController.java` para responder HTTP 410 en `POST /api/v1/market/sales` antes de invocar settlement
- [x] T023 [US1] Ajustar la documentación OpenAPI de `MarketController.java` y DTOs asociados para describir 410 como funcionalidad retirada, manteniendo la lectura de ventas históricas
- [x] T024 [US1] Verificar que `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/market/MarketPurchaseExecutionServiceImpl.java`, `MarketPurchasePersistenceServiceImpl.java` y `MarketQuoteValidationServiceImpl.java` conservan sus validaciones y no consumen reservas P2P

**Checkpoint**: El mercado primario sigue operativo y la dirección usuario → superusuario está bloqueada.

---

## Phase 4: User Story 2 - Publicar ofertas P2P (Priority: P1)

**Goal**: Permitir publicar ofertas sin precio manual, con validación de jugador/quote y reserva inmediata.

**Traceability**: HU2; FR-006 a FR-010, FR-026, FR-027, FR-034; aceptación HU2-1..HU2-6.

**Independent Test**: Un usuario publica una cantidad disponible, su `reserved_quantity` aumenta y la oferta queda abierta; una cantidad no disponible, jugador inactivo, quote inválida o precio manual se rechaza sin mutaciones.

### Tests for User Story 2

- [x] T025 [P] [US2] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/market/MarketP2POfferValidationTest.java` para cantidad entera positiva, precio manual rechazado, identidad JWT y jugador/quote no operables
- [x] T026 [P] [US2] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/persistence/MarketP2POfferReservationIntegrationTest.java` para reserva inmediata, suma de remanentes y no exceder disponibilidad
- [x] T027 [P] [US2] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/market/controller/MarketP2POfferControllerTest.java` para request/response, autenticación y errores de publicación

### Implementation for User Story 2

- [x] T028 [P] [US2] Crear `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/interfaces/market/MarketP2POfferService.java` y DTOs de comando/resultado
- [x] T029 [US2] Crear `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/market/MarketP2POfferServiceImpl.java` con claim idempotente, validación de jugador activo/quote válida y rechazo explícito de `price`/`unit_price`
- [x] T030 [US2] Implementar en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/market/MarketP2POfferPersistenceServiceImpl.java` la actualización atómica de `reserved_quantity` y la inserción de oferta
- [x] T031 [US2] Agregar DTOs HTTP de publicación en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/market/dto/` sin campo de precio aceptado
- [x] T032 [US2] Agregar endpoint `POST /api/v1/market/p2p/offers` en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/market/MarketController.java` con `Idempotency-Key` y documentación OpenAPI

**Checkpoint**: La publicación es idempotente, reserva tokens inmediatamente y no permite precio manual.

---

## Phase 5: User Story 3 - Comprar ofertas P2P (Priority: P1)

**Goal**: Liquidar compras parciales y completas entre usuarios al precio oficial vigente, de forma atómica.

**Traceability**: HU3; FR-012 a FR-025, FR-027, FR-034; aceptación HU3-1..HU3-6.

**Independent Test**: Dos usuarios pueden comprar una oferta parcial o totalmente; créditos, tenencias, restante, reserva, movimientos y asientos quedan consistentes.

### Tests for User Story 3

- [x] T033 [P] [US3] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/market/MarketP2PPurchaseServiceTest.java` para cálculo oficial, compra parcial/completa, propia oferta, cantidad restante y saldo insuficiente
- [x] T034 [P] [US3] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/persistence/MarketP2PTradeAtomicityIntegrationTest.java` para cuentas, tenencias, reserva, movimientos, trade y asientos en un único commit/rollback
- [x] T035 [P] [US3] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/market/controller/MarketP2PPurchaseControllerTest.java` para request, quote observada, respuestas parciales/completas y errores

### Implementation for User Story 3

- [x] T036 [P] [US3] Crear `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/interfaces/market/MarketP2PPurchaseService.java` y commands de compra
- [x] T037 [US3] Crear `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/market/MarketP2PPurchaseExecutionServiceImpl.java` con revalidación de jugador, quote identity/version y orden de locks P2P
- [x] T038 [US3] Crear `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/market/MarketP2PPurchasePersistenceServiceImpl.java` para débito/crédito, transferencia de tokens, reducción de `remaining_quantity` y `reserved_quantity`, movimientos y asientos atómicos
- [x] T039 [US3] Implementar la actualización de costo de adquisición/PnL en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/market/entity/TenenciaMercadoEntity.java` y los servicios de tenencia existentes sin alterar las reglas de valorización
- [x] T040 [US3] Agregar endpoint `POST /api/v1/market/p2p/offers/{offerId}/purchases` en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/market/MarketController.java`

**Checkpoint**: Una compra P2P parcial o total es atómica, idempotente y usa exclusivamente la quote oficial confirmada.

---

## Phase 6: User Story 4 - Cancelar ofertas P2P (Priority: P1)

**Goal**: Cancelar una oferta propia y liberar únicamente el remanente no vendido, con auditoría inequívoca.

**Traceability**: HU4; FR-011, FR-026, FR-028, FR-034; aceptación HU4-1..HU4-4.

**Independent Test**: Después de una compra parcial, el vendedor cancela; original, vendido y liberado quedan auditados, la reserva se reduce solo por el remanente y los trades no cambian.

### Tests for User Story 4

- [x] T041 [P] [US4] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/market/MarketP2PCancellationServiceTest.java` para ownership, estados terminales, original/vendido/liberado y no revertir trades
- [x] T042 [P] [US4] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/persistence/MarketP2PCancellationIntegrationTest.java` para liberación atómica y auditoría append-only
- [x] T043 [P] [US4] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/market/controller/MarketP2PCancellationControllerTest.java` para autorización e idempotencia de cancelación

### Implementation for User Story 4

- [x] T044 [P] [US4] Crear `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/interfaces/market/MarketP2PCancellationService.java`
- [x] T045 [US4] Crear `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/market/MarketP2PCancellationServiceImpl.java` con lock de oferta/tenencia, claim idempotente y rechazo de una nueva cancelación sobre oferta cancelada
- [x] T046 [US4] Crear endpoint `POST /api/v1/market/p2p/offers/{offerId}/cancel` y DTOs OpenAPI en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/market/MarketController.java` y `controller/market/dto/`

**Checkpoint**: Cancelar libera solo tokens no vendidos, es replay-safe y conserva auditoría.

---

## Phase 7: User Story 5 - Consultar ofertas y cotizaciones (Priority: P1)

**Goal**: Listar ofertas con paginación/filtros y exponer únicamente información pública segura.

**Traceability**: HU5; FR-014, FR-017 a FR-020, FR-034 a FR-040; aceptación HU5-1..HU5-4.

**Independent Test**: El usuario consulta páginas y filtros, ve ofertas comprables o suspendidas, cotización/versiones y alias público sin datos sensibles.

### Tests for User Story 5

- [x] T047 [P] [US5] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/market/MarketP2POfferQueryTest.java` para filtros, estados derivados, orden estable y alias público
- [x] T048 [P] [US5] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/market/controller/MarketP2POfferListMockMvcTest.java` para `page`, `limit`, `playerId`, `status`, `mine` y metadatos

### Implementation for User Story 5

- [x] T049 [US5] Implementar consultas paginadas en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/market/repository/OfertaVentaP2PRepository.java` con orden `created_at DESC, id DESC`
- [x] T050 [US5] Crear `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/market/MarketP2POfferQueryServiceImpl.java` para calcular `PURCHASABLE`/`SUSPENDED` según jugador y quote actuales
- [x] T051 [US5] Crear endpoint `GET /api/v1/market/p2p/offers` en `MarketController.java`, respetando `page` desde 1, `limit` 1..100 y el envelope de paginación existente
- [x] T052 [US5] Implementar mapeo de `seller_alias` en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/market/mapper/` sin exponer correo, cuenta, saldo, hash, JWT o identificadores sensibles

**Checkpoint**: El listado es paginado, filtrable, estable y no filtra información privada.

---

## Phase 8: User Story 7 - Seguridad, concurrencia e idempotencia (Priority: P1)

**Goal**: Probar invariantes contables y ausencia de duplicados/deadlocks en operaciones concurrentes.

**Traceability**: HU7; FR-024 a FR-028, FR-034, FR-040; aceptación HU7-1..HU7-5.

**Independent Test**: Las carreras sobre oferta, tenencia, cuentas, quote y keys dejan un estado válido y reproducible.

### Tests for User Story 7

- [ ] T053 [P] [US7] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/persistence/MarketP2PPurchaseConcurrencyIntegrationTest.java` para compradores concurrentes sobre el mismo remanente
- [ ] T054 [P] [US7] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/persistence/MarketP2PPublicationConcurrencyIntegrationTest.java` para publicaciones concurrentes sobre una misma tenencia
- [ ] T055 [P] [US7] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/persistence/MarketP2PCancelPurchaseConcurrencyIntegrationTest.java` para compra/cancelación concurrentes
- [ ] T056 [P] [US7] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/persistence/MarketP2PCrossUserLockConcurrencyIntegrationTest.java` para compras cruzadas y compatibilidad con compra primaria sin deadlocks
- [ ] T057 [P] [US7] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/persistence/MarketP2PIdempotencyConcurrencyIntegrationTest.java` para replay idéntico, key con parámetros distintos, pérdida de respuesta y recuperación
- [ ] T058 [P] [US7] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/market/controller/MarketP2PQuoteChangedMockMvcTest.java` para 409, `current_quote`, suspensión y obligación de nueva key

### Implementation for User Story 7

- [ ] T059 [US7] Revisar los servicios P2P y `MarketIdempotencyClaimService.java` para que todas las operaciones usen la misma semántica de claim, ownership y resultado confirmado
- [ ] T060 [US7] Añadir comprobaciones SQL/domain para no negatividad de balances, tenencias, reservas, inventario y remanentes en `persistence/market` y `modelo/market`
- [ ] T061 [US7] Verificar con dos conexiones PostgreSQL que el orden de locks documentado en `specs/005-token-market-p2p/research.md` se mantiene durante publication, purchase, cancel y primary purchase

**Checkpoint**: Las invariantes sobreviven concurrencia, reintentos, cambios de quote y errores de transporte.

---

## Phase 9: User Story 6 - Portfolio e historial unificado (Priority: P2)

**Goal**: Mostrar total/reservado/disponible y un historial que diferencie operaciones primarias, compras P2P y ventas P2P sin perder datos legacy.

**Traceability**: HU6; FR-029 a FR-033, FR-041; aceptación HU6-1..HU6-7.

**Independent Test**: Comprador y vendedor ven sus propios asientos; el portfolio refleja reservas y las ventas históricas al superusuario siguen visibles.

### Tests for User Story 6

- [x] T062 [P] [US6] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/unit/market/MarketP2PPortfolioHistoryMappingTest.java` para total/reservado/disponible y tipos históricos
- [x] T063 [P] [US6] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/market/MarketP2PUnifiedHistoryIntegrationTest.java` para aislamiento por usuario, orden, legacy sales y asientos P2P
- [x] T064 [P] [US6] Crear `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/market/controller/MarketP2PPortfolioHistoryMockMvcTest.java` para DTOs extendidos y filtros de historial

### Implementation for User Story 6

- [x] T065 [US6] Extender `MarketPortfolioServiceImpl.java`, `MarketPortfolioPosition.java` y DTOs `controller/market/dto/` con `total_quantity`, `reserved_quantity` y `available_quantity`
- [x] T066 [US6] Extender `MarketTransactionServiceImpl.java`, `MarketTransactionFilter.java`, repositories y mappers para unir registros legacy y asientos P2P sin reclasificar ventas históricas
- [x] T067 [US6] Actualizar OpenAPI de `/portfolio` y `/transactions` en `MarketController.java` para los nuevos campos y tipos `PRIMARY_PURCHASE`, `P2P_PURCHASE`, `P2P_SALE`
- [x] T068 [P] [US6] Extender `frontend/src/api/http.js` para consumir portfolio, ofertas, compras/cancelaciones y transacciones con los nuevos contratos
- [ ] T069 [P] [US6] Actualizar `frontend/src/views/MarketPortfolioPage.jsx` y componentes asociados para mostrar total/reservado/disponible y eliminar la acción de venta al superusuario
- [x] T070 [P] [US6] Crear `frontend/src/views/MarketP2POffersPage.jsx` y `frontend/src/components/MarketP2POfferCard.jsx` para listado, alias, cotización, estados y acciones permitidas
- [x] T071 [P] [US6] Crear/actualizar `frontend/src/components/MarketP2PConfirmationModal.jsx` y `frontend/src/styles/market.css` para confirmación sin precio manual, 409 `QUOTE_CHANGED`, nueva key y suspensión
- [ ] T072 [P] [US6] Actualizar `frontend/src/views/MarketTransactionsPage.jsx` para diferenciar tipos de historial y mantener ventas legacy visibles

**Checkpoint**: Backend y frontend muestran saldos/reservas/historial coherentes y no habilitan venta al superusuario.

---

## Phase 10: Polish y validación transversal

**Purpose**: Completar contratos, documentación y validación sin ampliar el alcance.

- [ ] T073 [P] Crear/actualizar tests de contrato OpenAPI en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/contract/market/MarketP2PApiContractTest.java` para endpoints, errores, autenticación, paginación y HTTP 410
- [ ] T074 [P] Crear tests frontend nuevos en `frontend/src/components/MarketP2POfferCard.test.jsx`, `frontend/src/components/MarketP2PConfirmationModal.test.jsx` y `frontend/src/views/MarketPortfolioPage.test.jsx` sin modificar ni borrar tests existentes
- [ ] T075 Ejecutar los escenarios de `specs/005-token-market-p2p/quickstart.md` contra entorno de pruebas, sin usar cuotas reales ni WhoScored
- [ ] T076 Revisar `specs/005-token-market-p2p/contracts/market-api.md`, `contracts/quote-contract.md` y anotaciones OpenAPI para que no haya divergencias
- [ ] T077 Ejecutar desde `backend/build.gradle` la compilación backend, desde `frontend/package.json` la compilación frontend, la suite completa, tests Testcontainers, revisión de seguridad y SonarCloud según la Constitution
- [ ] T078 Verificar en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/`, `backend/src/main/java/com/devchesterunited/devchesterunitedv2/scraping/` y `backend/src/test/` que no se modificaron Player Valuation, Player Statistics Sync, WhoScored ni los tests existentes no autorizados

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: Sin dependencias; solo inspección, línea base y preparación.
- **Phase 2**: Depende de Phase 1 y bloquea todas las historias; crea V21 y fundamentos compartidos.
- **Phase 3 / US1**: Depende de Phase 2; preserva compra primaria y retira venta legacy.
- **Phase 4 / US2**: Depende de Phase 2; puede ejecutarse en paralelo con US1 si no se editan los mismos archivos simultáneamente.
- **Phase 5 / US3**: Depende de US2 y Phase 2.
- **Phase 6 / US4**: Depende de US2 y Phase 2; puede avanzar en paralelo con US3 después de que existan entidades/repositories.
- **Phase 7 / US5**: Depende de US2 y del contrato de ofertas; puede avanzar en paralelo con US3/US4.
- **Phase 8 / US7**: Depende de US2, US3 y US4; valida la integración de locks/idempotencia.
- **Phase 9 / US6**: Depende de US3, US4, US5 y US7 para reflejar estados finales e historial real.
- **Phase 10**: Depende de todas las fases funcionales relevantes.

### Critical Dependency Chain

`T001-T005 → T006-T019 → T028-T032 (US2) → T036-T040 (US3) → T044-T046 (US4) → T053-T061 (US7) → T065-T072 (US6) → T073-T078`

US1 (`T020-T024`) puede ejecutarse inmediatamente después de Foundation y debe validarse antes de integrar el frontend.

### Parallel Opportunities

- En Phase 1: T002, T003 y T004 pueden ejecutarse en paralelo.
- En Phase 2: T007-T011, T018 y T019 pueden ejecutarse en paralelo respetando T006 cuando necesiten el esquema final.
- En US1: T020 y T021 son pruebas independientes; T022-T024 deben seguirlas.
- En US2: T025-T027 son pruebas independientes; T028 y T031 pueden prepararse en paralelo antes de T029-T032.
- En US3: T033-T035 son pruebas independientes; T036 y DTOs pueden avanzar en paralelo, luego T037-T040.
- En US4: T041-T043 son pruebas independientes; T044 puede avanzar junto con DTOs antes de T045-T046.
- En US5: T047-T048 y la documentación de contrato pueden avanzar en paralelo con la implementación de US3/US4.
- En US7: T053-T058 son suites independientes y pueden ejecutarse en paralelo una vez exista el settlement completo.
- En US6: T062-T064, T068-T072 pueden dividirse por backend/frontend después de fijar contratos.

## Independent Test Criteria by Story

- **US1**: Compra primaria confirmada sin regresión; venta legacy devuelve 410 sin mutaciones.
- **US2**: Publicación válida reserva inmediatamente; precio manual y disponibilidad insuficiente se rechazan.
- **US3**: Compra parcial/total transfiere créditos y tokens atómicamente con quote exacta.
- **US4**: Cancelación libera solo remanente, registra auditoría y es idempotente.
- **US5**: Listado paginado/filtros muestra estados y solo alias público.
- **US7**: Carreras y reintentos conservan invariantes y no generan deadlocks/duplicados.
- **US6**: Portfolio e historial muestran reservas y tipos correctos, incluyendo legacy.

## Implementation Strategy

### Minimum Safe Increment

1. Completar Phase 1 y Phase 2.
2. Implementar US1 y validar que la compra primaria funciona y la venta al superusuario queda en 410.
3. Implementar US2 y validar reserva/idempotencia de publicación.
4. Implementar US3 y validar una compra P2P parcial/total.
5. Detenerse en el checkpoint de US3 para validar contabilidad antes de frontend e historial.

### Incremental Delivery

1. Foundation + US1: compatibilidad y retiro seguro.
2. US2: ofertas/reservas.
3. US3 + US4: liquidación y cancelación.
4. US5 + US7: consulta, quote changed, concurrencia e idempotencia.
5. US6: portfolio, historial y frontend.
6. Polish: contratos, quickstart, build, suite completa y SonarCloud.

## Notes

- `[P]` indica que la tarea puede ejecutarse en paralelo sin depender de archivos incompletos de otra tarea.
- Cada tarea tiene un identificador secuencial y al menos una ruta concreta.
- Las tareas crean tests nuevos; los tests existentes se ejecutan como regresión y no se modifican/eliminan sin aprobación.
- La migración V21 se implementará en la fase correspondiente, pero no se ejecuta durante la generación de este archivo.
- La implementación no debe agregar precio manual, subastas, order book, scraping ni cambios al motor de valorización.
