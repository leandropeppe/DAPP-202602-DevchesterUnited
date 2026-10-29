# Research: Token Market P2P

## Evidencia del repositorio

- La entrada HTTP actual está en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/market/MarketController.java`.
- Las compras y ventas existentes se separan en `service/impl/market/MarketPurchase*` y `MarketSale*`; portfolio e historial usan `MarketPortfolioServiceImpl` y `MarketTransactionServiceImpl`.
- Persistencia relevante: `CuentaCreditosMercado*`, `TenenciaMercado*`, `MovimientoMercado*`, `TransaccionConfirmadaMercado*`, `SolicitudIdempotenciaMercado*`, `VersionCotizacionMercado*` y sus repositorios bajo `persistence/market`.
- La cotización versionada se valida mediante `MarketQuoteValidationService` y `QuoteVersion`; no se debe crear otra fuente de precio.
- `MarketIdempotencyClaimService` adquiere el advisory transaction lock y reclama la fila idempotente; la compra primaria continúa luego por estado de mercado, jugador, cabecera de cotización y persistence que bloquea cuenta de usuario, cuenta superusuario, emisión y tenencia.
- `TenenciaMercadoRepository.findByUsuarioIdAndJugadorIdForUpdate` usa `PESSIMISTIC_WRITE`; `CuentaCreditosMercadoRepository` ofrece locks equivalentes para cuentas. El modelo actual no tiene `reserved_quantity`, por lo que V21 deberá inicializarlo a cero y la entidad/repository deberán exponerlo explícitamente.
- El frontend centraliza HTTP en `frontend/src/api/http.js`, portfolio en `MarketPortfolioPage.jsx`, operaciones en `MarketOperationPanel.jsx` e historial en `MarketTransactionsPage.jsx`.
- Flyway termina actualmente en `backend/src/main/resources/db/migration/V20__add_market_quote_header_validity.sql`, luego de `V19__create_token_market.sql`. Se reserva V21 para P2P; no se crea el archivo durante Plan.
- `backend/build.gradle` fija el toolchain efectivo en Java 21 mediante `JavaLanguageVersion.of(21)`; esto es la referencia del proyecto y no el JDK instalado localmente.

## Decisiones

### D1. Extender Token Market sin alterar Player Valuation

P2P consume la identidad, precio, vigencia y versión que ya publica el motor de valorización. La regla de negocio se implementa en el servicio de mercado: publicación exige jugador activo/cotización válida y compra revalida la versión vigente. No se agrega scraping ni cálculo de precio.

### D2. La reserva es un estado contable explícito

Se adopta `reserved_quantity` dentro de `market_holdings`, inicializado a cero, con `0 <= reserved_quantity <= quantity` y `available = quantity - reserved_quantity`. Cada oferta activa conserva `remaining_quantity`; la suma de esos remanentes para `(seller, player)` debe coincidir con `reserved_quantity`. La comprobación se realiza bajo lock en publicación, compra y cancelación. No habrá tabla de reserva separada.

### D3. La suspensión de compras es derivada

Las ofertas no vencen ni se eliminan cuando el jugador se desactiva o pierde la cotización. El listado puede calcular `PURCHASABLE`/`SUSPENDED` con estado actual del jugador y cabecera de cotización; cancelar siempre sigue permitido. Así una reactivación o nueva cotización no requiere reabrir manualmente una oferta histórica.

### D4. Historial unificado compatible

No se modifican ni borran ventas históricas al superusuario. P2P usa un registro de trade y asientos de participación para que comprador y vendedor consulten su propio tipo de operación. El servicio de historial combina los registros legacy y P2P, ordena por fecha y filtra por principal autenticado.

### D5. Precio observado no es precio acordado

El listado y la UI muestran una cotización informativa con `quote_id`, `version`, estado y total calculado. El backend vuelve a validar identidad, versión, vigencia, jugador activo y valor al confirmar. Un cambio devuelve 409 `QUOTE_CHANGED` con la oferta intacta y, si existe, un bloque `current_quote` con identidad, versión, precio y vigencia actuales; si no existe una cotización válida, devuelve `current_quote: null`, estado `SUSPENDED` y no permite confirmar. La UI refresca/representa ese resultado y solicita una nueva confirmación con una nueva key cuando la cotización vuelve a estar disponible.

### D6. Orden de locks compatible

La compra primaria existente establece el prefijo `claim/advisory → market_state → player → quote_header`; su persistence bloquea después `buyer_account → superuser_account → issuance → buyer_holding`. P2P debe respetar ese prefijo y no bloquear una tenencia antes de una cuenta.

Para dos usuarios, P2P bloquea ambas cuentas por UUID ascendente y ambas tenencias por `(user_id, player_id)` ascendente, aplicando luego los roles comprador/vendedor. Publicación y cancelación no adquieren cuentas, y su acceso a la tenencia ocurre después de los locks comunes. La compra P2P bloquea la oferta existente antes del jugador/cotización; publicación bloquea jugador/cotización/tenencia y recién después inserta la oferta; cancelación bloquea oferta y luego tenencia. Así no se crea el ciclo compra primaria/P2P ni el deadlock de compras cruzadas; no se agrega lock global.

### D7. Auditoría de cancelación e idempotencia

La oferta conserva `original_quantity` inmutable, `remaining_quantity` y un acumulado inmutable `sold_quantity` o equivalente calculable desde trades confirmados. La cancelación registra un evento/auditoría append-only con `released_quantity = remaining_quantity` al momento del lock, `original_quantity`, `sold_quantity`, actor y timestamp. Luego deja la oferta en estado `CANCELLED` con restante cero y reduce `reserved_quantity` exactamente por lo liberado. Nunca modifica ni revierte trades confirmados; una cantidad vendida permanece vendida.

Repetir la misma cancelación con la misma `Idempotency-Key` recupera el resultado original. Una key nueva sobre una oferta ya cancelada se rechaza sin liberar tokens nuevamente.

### D8. Paginación, filtros y privacidad

El listado de ofertas usará la convención existente de historial: `page` desde 1, `limit` entre 1 y 100, respuesta con registros y metadatos `pagina`, `limite`, `totalPaginas`, `totalElementos`. Filtros iniciales: `playerId`, `status` (`OPEN`, `PARTIALLY_FILLED`, `SUSPENDED`, `FILLED`, `CANCELLED`) y `mine` para incluir solo ofertas propias; el orden será estable por `created_at DESC, id DESC`.

El vendedor expone únicamente un alias/display name no sensible, derivado por backend. No se exponen correo, hash, saldo, identificador de cuenta, JWT ni datos privados.

## Alternativas descartadas

- **Precio guardado por oferta**: contradice la cotización oficial vigente y permitiría liquidar un precio manual o desactualizado.
- **Transferir contra el superusuario usando `MarketSaleService`**: viola la nueva dirección permitida y mezcla reserva primaria con reservas P2P.
- **Order book/subastas**: fuera de alcance y agrega matching, prioridad y estados no requeridos.
- **Borrar o reescribir transacciones antiguas**: rompe auditoría y la compatibilidad exigida.
- **Lock global de todo el mercado**: evita carreras a costa de serializar operaciones independientes y contradice el diseño existente de locks acotados.
- **Tabla de reservas separada**: duplica el estado contable de la tenencia y abre una divergencia entre oferta, portfolio y disponibilidad.

## Riesgos abiertos para implementación

1. Confirmar los nombres de constraints y el tipo exacto de columnas de `TenenciaMercadoEntity` antes de escribir V21.
2. Verificar si el repositorio actual de idempotencia acepta nuevos tipos de operación o requiere ampliar un check/enum.
3. Resolver si el contrato de error 410 usa el exception handler estándar o una respuesta específica del controller.
4. Confirmar si el alias público debe ser el `username` actual o un alias de mercado separado; la regla de no exponer información sensible no está abierta.
5. Preservar todos los tests existentes: la constitución prohíbe modificar o borrar tests sin aprobación explícita.
