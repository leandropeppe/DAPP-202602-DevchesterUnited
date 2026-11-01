# Tasks: Búsqueda P2P por nombre de jugador

**Input**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

## Completed implementation

- [x] T001 Confirmar contrato y rutas en `specs/006-p2p-player-name-search/contracts/market-api.md`
- [x] T002 Revisar scripts de pruebas frontend en `frontend/package.json`
- [x] T003 Revisar build y tests P2P backend en `backend/build.gradle`
- [x] T004 Añadir `nombreJugador` compatible en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/dto/market/MarketP2POfferQuery.java`
- [x] T005 Propagar nombre en `MarketP2POfferProjection` y `MarketP2POfferView`
- [x] T006 Cubrir búsqueda exacta, parcial, case-insensitive y vacía en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/persistence/MarketP2POfferPlayerNameQueryIntegrationTest.java`
- [x] T007 Cubrir parámetro y respuesta `player_name` en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/integration/market/controller/MarketP2POfferPlayerNameControllerTest.java`
- [x] T008 Cubrir nombre visible sin UUID en `frontend/src/components/MarketP2POfferPlayerName.test.jsx`
- [x] T009 Extender SQL y `countQuery` con `j.name AS player_name` y `LIKE` en `OfertaVentaP2PRepository.java`
- [x] T010 Propagar `playerName` desde service a repository sin filtrado en memoria
- [x] T011 Propagar `player_name` por mapper, vista y DTO de respuesta
- [x] T012 Aceptar/documentar `playerName` en `MarketP2POfferQueryController.java` conservando `playerId`
- [x] T013 Actualizar contrato en `specs/006-p2p-player-name-search/contracts/market-api.md`
- [x] T014 Cubrir paginación, `status` y `mine` en la integración de nombre
- [x] T015 Cubrir cliente HTTP en `frontend/src/api/http.player-name-search.test.js`
- [x] T016 Cubrir edición sin consulta, Buscar, Enter, Limpiar, reset de página y paginación en `frontend/src/views/MarketP2POfferPlayerNameSearch.test.jsx`
- [x] T017 Permitir `playerName` en `frontend/src/api/http.js`
- [x] T018 Cambiar filtro visible a `Buscar por nombre` en `frontend/src/views/MarketP2POffersPage.jsx`
- [x] T019 Separar filtros editables/aplicados y aplicar Buscar, Enter, Limpiar y página 1
- [x] T020 Mantener UUIDs en publicación, compra, cancelación y posiciones
- [x] T021 Cubrir compatibilidad de `playerId` en el controller test nuevo
- [x] T022 Verificar regresión de operaciones UUID con la suite P2P frontend existente
- [x] T023 Mantener constructores/mappers compatibles con la identidad técnica
- [x] T024 Verificar que operaciones frontend no dependan del nombre visible
- [x] T025 Verificar build frontend y convenciones de archivos modificados
- [x] T026 Ejecutar validación de contrato OpenAPI; el test fue bloqueado por `PortInUseException` del entorno
- [x] T027 Ejecutar suite frontend focalizada: 5 archivos, 25 tests aprobados
- [x] T028 Validar quickstart y registrar resultados en esta feature

## Dependencies and validation

- Backend: `compileJava` aprobado; tests existentes de query/controller aprobados; tests nuevos de nombre/paginación/controller aprobados.
- Frontend: tests P2P existentes y nuevos actualizados para filtros explícitos; `npm run build` aprobado.
- Contrato OpenAPI: anotación del nuevo parámetro implementada; la prueba automática requiere un puerto libre y falló dos veces por conflicto de puerto.
- No se modificaron ni eliminaron tests existentes.
