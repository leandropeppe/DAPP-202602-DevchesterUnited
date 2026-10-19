cd ..
description: "Lista de tareas para la implementación de Token Market"
---

# Tareas: Token Market

**Entrada**: Documentos de diseño de `specs/003-token-market/`

**Prerrequisitos**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `quickstart.md`, `contracts/market-api.md` y `contracts/quote-contract.md`.

**Alcance**: Implementar únicamente Token Market. No modificar `specs/001-user-auth-player-catalog`, `specs/002-player-statistics-sync` ni archivos pertenecientes a la rama remota de estadísticas. Player Statistics Sync y Player Valuation son dependencias de integración, no objetivos de implementación de esta feature.

**Convención de rutas**: La raíz de paquetes existente del backend es `backend/src/main/java/com/devchesterunited/devchesterunitedv2/` con `configuration`, `modelo`, `persistence`, `service`, `controller` y `adapter`. Los directorios existentes del frontend son `frontend/src/api`, `components`, `session`, `styles` y `views`. La configuración de la aplicación está en `backend/src/main/resources/application.properties`.

**Pruebas**: Se incluyen pruebas porque Specify y el Plan aprobado requieren verificación unitaria, MockMvc, PostgreSQL/Testcontainers, frontend, concurrencia, idempotencia y demostración.

## Fase 1: Preparación

**Propósito**: Establecer la superficie de implementación de Token Market sin cambiar features aprobadas ni convenciones de configuración.

- [ ] T001 Verificar que la rama activa sea `feature/token-market` y leer/confirmar las entradas aprobadas en `specs/003-token-market/spec.md`, `plan.md`, `research.md`, `data-model.md`, `quickstart.md` y `contracts/`; no escribir, registrar ni modificar ningún documento aprobado.
- [ ] T002 [P] Inventariar las convenciones existentes de autenticación, jugadores, repositorios, servicios, controladores, API del frontend y pruebas en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/` y `frontend/src/`, sin modificar la responsabilidad de sus features.
- [ ] T003 [P] Definir el mapa de paquetes/archivos de Token Market bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/`, `persistence/`, `service/`, `controller/`, `adapter/` y `configuration/`, preservando las convenciones verificadas del repositorio.
- [ ] T004 [P] Definir el mapa de archivos de Token Market del frontend bajo `frontend/src/api/`, `frontend/src/views/` y `frontend/src/components/`, reutilizando las convenciones existentes de sesión y HTTP.

## Fase 2: Persistencia y contratos fundacionales

**Propósito**: Crear las bases de datos y de dominio requeridas por todas las historias del mercado.

**Puerta de coordinación de migraciones**: V16 es provisional. No crear la migración hasta reconciliar la V15 final de Player Statistics Sync con esta rama. No modificar la rama de estadísticas.

- [x] T005 Confirmar la integración/orden definitivos de Player Statistics Sync V15 frente a la secuencia actual bajo `backend/src/main/resources/db/migration/`; si V15 está ausente, entra en conflicto o no está resuelta, detenerse ante un bloqueo explícito y solicitar aprobación de coordinación sin fetch, checkout, merge, rebase ni modificación de esa feature.
- [x] T006 Crear la migración provisional de Token Market en `backend/src/main/resources/db/migration/V16__create_token_market.sql` únicamente después de que T005 confirme que V15 está integrada definitivamente y V16 es la siguiente versión disponible; si esa confirmación no existe, no crear V16 ni hacer merge, checkout, rebase o modificación de la feature de estadísticas.
- [x] T007 [P] Agregar entidades de persistencia para estado del mercado, superusuario/contraparte interna, cuentas de créditos, asignaciones iniciales, emisión/inventario de tokens y tenencias de usuarios bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/dto/`.
- [x] T008 [P] Agregar entidades de persistencia para versiones/cabeceras de cotizaciones, solicitudes de idempotencia, transacciones confirmadas, movimientos de créditos/tokens y auditoría de rechazos bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/dto/`.
- [x] T009 [P] Agregar repositorios con consultas de unicidad, no negatividad, ejecución única y pertenencia por jugador/usuario bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/`.
- [x] T010 Agregar mappers/DTOs de persistencia para registros de Token Market bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/mapper/` y `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/dto/`, sin duplicar la persistencia de `Jugador` ni de estadísticas.
- [x] T011 Agregar validación de valores de dominio y excepciones del mercado bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/modelo/exception/` y `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/exception/` para cantidades inválidas, jugadores inactivos, cotizaciones no disponibles, saldos insuficientes, mercado no listo, conflicto de idempotencia y solicitudes obsoletas.
- [x] T012 [P] Agregar pruebas unitarias de invariantes de base de datos/dominio y validación monetaria/de tokens bajo `backend/src/test/java/com/devchesterunited/devchesterunitedv2/modelo/` y `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/`.
- [x] T013 Configurar únicamente las convenciones existentes de pruebas PostgreSQL/Flyway necesarias para Token Market en `backend/src/test/resources/` y verificar que la aplicación continúe usando `backend/src/main/resources/application.properties`; no introducir configuración YAML.
- [x] T014 Agregar el límite compartido de publicación interna de cotizaciones y sus DTOs bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/interfaces/`, `service/dto/` y `adapter/`, incluyendo identidad/versión inmutable de cotización, vigencia, fecha de publicación, precio, identidad del jugador y referencia opcional de estrategia.
- [x] T015 Agregar pruebas a nivel de contrato para el límite de publicación/consumo de cotizaciones bajo `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/`, demostrando que las versiones iniciales y futuras usan una única fuente de verdad y que las versiones son inmutables.

**Punto de control**: Las restricciones de persistencia, el límite compartido de cotizaciones y la infraestructura de pruebas están listos; ninguna historia de usuario puede omitir estas invariantes.

## Fase 3: Historia de usuario 1 - Inicializar el mercado de forma segura (P1)

**Objetivo**: Inicializar automáticamente el superusuario protegido, la reserva finita, los créditos únicos de usuarios, las emisiones de jugadores elegibles y las cotizaciones iniciales sin intervención pública.

**Prueba independiente**: Los escenarios de inicio y activación repetidos producen un superusuario, una asignación de 1.000 créditos por usuario, exactamente 100 tokens emitidos por jugador elegible y ninguna operación mientras la inicialización esté incompleta.

### Pruebas para la historia de usuario 1

- [x] T016 [P] [US1] Agregar pruebas unitarias para transiciones del estado de inicialización, reserva configurada, asignaciones únicas, emisiones únicas, activación de jugadores inactivos y recuperación mediante reintentos en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/`.
- [x] T017 [P] [US1] Agregar pruebas PostgreSQL/Testcontainers para superusuario singleton, unicidad de asignaciones, unicidad de emisiones, exactamente 100 tokens, saldos no negativos e idempotencia tras reinicio en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/persistence/`.

### Implementación para la historia de usuario 1

- [x] T018 Crear repositorios de persistencia y consultas de locks para inicialización/estado en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/`, usando la fila singleton de estado del mercado y semántica transaccional determinista.
- [x] T019 Implementar `MarketInitializationService` bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/` para crear/verificar el superusuario protegido, aplicar el valor por defecto configurado de 1.000.000 de créditos antes de la primera inicialización y nunca restablecer una reserva inicializada.
- [x] T020 Implementar el coordinador automático de disponibilidad de la aplicación bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/` o `service/config/`, con transiciones reintentables `INITIALIZING`, `FAILED` y `READY`, y sin endpoint público.
- [x] T021 Implementar la asignación idempotente de créditos iniciales para usuarios existentes y el hook de asignación posterior al registro bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/`, usando el marcador de asignación único y evitando que la autenticación dependa del inicio exitoso del mercado.
- [x] T022 Implementar la emisión/asignación única para jugadores activos y para la primera activación bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/`, referenciando la identidad estable existente del jugador y el contrato de estado activo sin modificar Player Statistics Sync.
- [x] T023 Implementar la siembra de cotizaciones iniciales mediante el adaptador real de publicación compartida expuesto por `QuotePublicationPort.publishInitialQuote` a 1.00 crédito con vigencia no expirable bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/` y `service/interfaces/`, solo después de que T014–T015 definan/verifiquen el contrato y T039–T040 implementen/verifiquen su servicio de persistencia y publicación; no crear una tabla de precios paralela.
- [x] T024 Implementar la recuperación de inicialización para que una ejecución fallida o interrumpida reanude asignaciones, emisiones y cotizaciones iniciales faltantes, mantenga las operaciones deshabilitadas hasta completar y preserve los pasos exitosos existentes bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/`.
- [x] T025 Agregar pruebas de integración de inicialización y activación que cubran cuatro usuarios, jugadores activos/inactivos, reinicio, recuperación ante fallos y operación independiente de la autenticación en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/` y `persistence/`.

**Punto de control**: La inicialización del mercado es automática, idempotente y recuperable, y no puede duplicar identidades, créditos, cotizaciones ni emisiones de tokens.

## Fase 4: Historia de usuario 2 - Consultar el estado operativo del mercado (P1)

**Objetivo**: Exponer la disponibilidad del mercado y la información de jugadores operables sin exponer operaciones internas ni datos de otro usuario.

**Prueba independiente**: Un usuario solo puede ver jugadores activos con disponibilidad actual de cotización/tokens; la inicialización incompleta y las cotizaciones ausentes/obsoletas se representan correctamente.

- [x] T026 [P] [US2] Agregar pruebas unitarias para barreras compartidas/exclusivas del estado del mercado, épocas de estado, manejo de jugadores inactivos, cotizaciones ausentes y marcado de precios históricos en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/`.
- [x] T027 [P] [US2] Agregar pruebas de seguridad MockMvc para acceso no autenticado, listado autenticado del mercado y exclusión de operaciones del superusuario interno en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/controller/`.
- [x] T028 Implementar servicios de estado del mercado y disponibilidad de jugadores bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/`, usando locks compartidos de estado para lecturas normales y el contrato estable de estado activo del jugador.
- [x] T029 Implementar DTOs de saldo y jugador de mercado bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/` y DTOs de servicio bajo `service/dto/`, reutilizando las convenciones existentes de paginación y respuestas.
- [x] T030 Implementar los endpoints de lectura de saldo y jugadores del mercado en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/`, incluyendo cotización actual, marcador histórico, tokens disponibles y semántica de disponibilidad del mercado de `contracts/market-api.md`.
- [x] T031 Agregar documentación OpenAPI y del manejador de errores para mercado no listo, cotización ausente, jugador inactivo y errores de autorización mediante `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/exceptionhandler/` y el controlador del mercado.

**Punto de control**: La información de solo lectura del mercado está autenticada, delimitada y es consistente con la inicialización y el estado del jugador.

## Fase 5: Historia de usuario 7 - Recuperar solicitudes idempotentes y concurrentes (P1)

**Objetivo**: Garantizar que las operaciones repetidas, concurrentes, agotadas por timeout y recuperadas no dupliquen transferencias.

**Prueba independiente**: Dos instancias del backend que usan la misma clave producen un resultado; el rollback no deja movimientos; un reintento posterior recupera o vuelve a ejecutar de forma segura una solicitud `PROCESSING` durable.

- [x] T032 [P] [US7] Agregar pruebas unitarias para solicitudes idénticas con la misma clave, parámetros conflictivos, `PROCESSING`, `CONFIRMED`, `REJECTED`, rollback y manejo de respuestas perdidas en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/`.
- [x] T033 Agregar pruebas de concurrencia PostgreSQL/Testcontainers para locks advisory con alcance transaccional, propiedad de la fila de solicitud, simulación de caída del proceso, recuperación de timeout/pendiente y ausencia de movimientos duplicados en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/persistence/`.
- [x] T034 Implementar la reclamación de idempotencia, hash de parámetros normalizados, token de ejecución, transiciones de estado, resultado almacenado y consultas de recuperación bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/` y `service/impl/`.
- [x] T035 Implementar el ejecutor de locks advisory con alcance transaccional de PostgreSQL y la coordinación de locks de filas en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/`, garantizando que un segundo ejecutor devuelva pendiente mientras la primera transacción esté activa.
- [x] T036 Implementar DTOs y endpoint de estado/recuperación de operaciones en `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/` y `controller/`, devolviendo resultados almacenados solo después del commit y sin presentar solicitudes pendientes/inciertas como compraventas confirmadas.

**Punto de control**: La idempotencia y la recuperación son seguras entre instancias concurrentes del backend y ante fallos técnicos.

## Fase 6: Historia de usuario 8 - Operar con cotizaciones publicadas (P1)

**Objetivo**: Consumir una única fuente de cotizaciones inmutable/versionada y coordinar la validación de versión exacta con la futura Player Valuation.

**Prueba independiente**: Las cotizaciones iniciales operan antes de que exista Player Valuation; los reemplazos, invalidaciones, vencimientos y publicaciones concurrentes rechazan versiones aceptadas obsoletas sin sustituir precios.

- [x] T037 [P] [US8] Agregar pruebas unitarias para versiones inmutables de cotización, reemplazo de la cabecera actual, invalidación explícita, cotización inicial no expirable, política de vigencia dinámica y aceptación de versión exacta en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/`.
- [x] T038 Agregar pruebas PostgreSQL/Testcontainers para publicación/invalidación concurrente de cotizaciones frente a la validación del mercado y la restricción de fuente única de verdad en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/persistence/`.
- [x] T039 Implementar persistencia y repositorios de versiones/cabeceras de cotizaciones bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/`, distinguiendo versiones inmutables de la referencia/estado de la cotización actual por jugador.
- [x] T040 Implementar servicios internos de publicación/consumo de cotizaciones bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/` y `service/interfaces/`, compartiendo la misma fuente para la siembra inicial y la publicación dinámica futura.
- [x] T041 Implementar la validación exacta de cotizaciones en el servicio de operaciones de mercado bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/`, rechazando versiones reemplazadas, invalidadas, vencidas o ausentes sin sustitución automática.
- [x] T042 Agregar el punto de integración con Player Valuation y pruebas de contrato bajo `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/`, documentando que valoración es responsable de estrategia/cálculo/histórico mientras Token Market solo consume y registra referencias.

**Punto de control**: Token Market tiene una única fuente de cotizaciones, ningún algoritmo de valoración y semántica de ejecución con versión exacta.

## Fase 7: Historia de usuario 3 - Comprar tokens de un jugador activo (P1)

**Objetivo**: Ejecutar una compra autenticada, todo o nada, contra el superusuario.

**Prueba independiente**: Una compra válida transfiere créditos/tokens, actualiza el costo de adquisición ponderado y registra una transacción confirmada; cada fallo de validación no realiza mutaciones parciales.

- [x] T043 [P] [US3] Agregar pruebas de servicio para validación de compra, cantidad entera, jugador activo, inventario, saldo del usuario, cotización exacta y comportamiento todo o nada en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/`.
- [x] T044 Agregar pruebas PostgreSQL/Testcontainers para compras concurrentes, agotamiento del inventario del superusuario, prevención del doble gasto del usuario, orden determinista de locks y commit atómico de movimientos/historial en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/persistence/`.
- [x] T045 Implementar registros de movimientos de créditos/tokens y servicios atómicos de actualización de cuentas/tenencias bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/` y repositorios bajo `persistence/`.
- [x] T046 Implementar la orquestación de compras con el orden de locks idempotencia → estado del mercado → estado del jugador → cabecera de cotización → saldo del usuario → saldo del superusuario → inventario → tenencia bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/`.
- [x] T047 Agregar DTOs de solicitud/respuesta de compra con `Idempotency-Key`, identidad/versión exacta de cotización, cantidad entera positiva y validación bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/`.
- [x] T048 Agregar el endpoint autenticado de compra y el mapeo de errores bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/`, obteniendo el usuario únicamente del JWT validado.
- [x] T049 Agregar pruebas MockMvc para compra exitosa, cotización ausente/obsoleta, jugador inactivo, créditos/inventario insuficientes, clave inválida e intentos de seleccionar la cuenta de otro usuario bajo `backend/src/test/java/com/devchesterunited/devchesterunitedv2/controller/`.

**Punto de control**: La compra es inmediata, atómica, versionada por cotización, idempotente y limitada a la contraparte superusuario.

## Fase 8: Historia de usuario 4 - Vender tokens de un jugador activo (P1)

**Objetivo**: Ejecutar una venta autenticada, todo o nada, contra la reserva finita del superusuario.

**Prueba independiente**: Una venta válida transfiere tokens al superusuario y créditos al usuario, actualiza el costo ponderado restante y la ganancia/pérdida realizada, y nunca se ejecuta parcialmente.

- [x] T050 [P] [US4] Agregar pruebas de servicio para validación de venta, tenencias del usuario, suficiencia de reserva, asignación de costo de venta parcial, cierre de posición final, cotización exacta y comportamiento todo o nada en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/`.
- [x] T051 Agregar pruebas PostgreSQL/Testcontainers para ventas concurrentes que compiten por una tenencia y la reserva finita del superusuario, demostrando ausencia de saldo negativo, sobreventa o movimiento parcial en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/persistence/`.
- [x] T052 Implementar la orquestación de ventas usando el mismo orden determinista de locks y ejecutor de idempotencia que la compra bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/`.
- [x] T053 Implementar la reducción de costo mediante promedio ponderado, ganancia/pérdida realizada, puesta en cero de la posición final y persistencia de movimientos/historial bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/`.
- [x] T054 Agregar DTOs de solicitud/respuesta de venta y endpoint autenticado de venta bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/` y `controller/`, reutilizando las reglas exactas de cotización y pertenencia JWT.
- [x] T055 Agregar pruebas MockMvc para venta parcial, venta final, tenencia/reserva insuficientes, jugador inactivo, cotización obsoleta, reintento idempotente y rechazo de acceso entre usuarios bajo `backend/src/test/java/com/devchesterunited/devchesterunitedv2/controller/`.

**Punto de control**: La venta es atómica, está limitada por la reserva finita, contempla el costo ponderado y es auditable.

## Fase 9: Historia de usuario 5 - Consultar el portfolio y la rentabilidad (P2)

**Objetivo**: Mostrar únicamente posiciones abiertas del usuario autenticado con semántica contable completa de resultados realizados/no realizados.

**Prueba independiente**: El portfolio muestra promedio ponderado y valor actual de las posiciones abiertas, conserva P/L realizado después del cierre y marca cotizaciones actuales ausentes sin presentar totales incompletos como actuales.

- [x] T056 [P] [US5] Agregar pruebas unitarias para valores de portfolio con promedio ponderado, P/L realizado tras cerrar posiciones, P/L no realizado, presentación de cotización histórica, manejo de cotización ausente y completitud del resultado combinado en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/`.
- [x] T057 Implementar servicios de agregación y valoración del portfolio bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/service/impl/`, usando cotizaciones vigentes actuales solo para el valor actual y conservando agregados realizados independientemente de las tenencias abiertas.
- [x] T058 Agregar DTOs de portfolio bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/` y DTOs de servicio bajo `service/dto/`, incluyendo posiciones abiertas, costos, estado de cotización, P/L realizado/no realizado e indicadores de completitud.
- [x] T059 Agregar el endpoint autenticado de portfolio bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/` y pruebas de aislamiento MockMvc bajo `backend/src/test/java/com/devchesterunited/devchesterunitedv2/controller/`.

**Punto de control**: El portfolio es personal, preciso después de ventas parciales/completas y explícito respecto de precios históricos o no disponibles.

## Fase 10: Historia de usuario 6 - Consultar transacciones confirmadas y auditoría (P2)

**Objetivo**: Proporcionar un historial personal confirmado e inmutable y una auditoría separada y restringida de rechazos.

**Prueba independiente**: Los usuarios solo ven sus propias operaciones confirmadas, de la más reciente a la más antigua, con filtros/paginación; los intentos rechazados y las solicitudes pendientes nunca aparecen como transacciones confirmadas.

- [x] T060 [P] [US6] Agregar pruebas de repositorio/servicio para snapshots inmutables de transacciones, orden descendente, filtros por jugador/tipo/fecha, paginación, separación de rechazos y aislamiento de usuarios en `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/` y `persistence/`.
- [x] T061 Implementar persistencia inmutable y servicios de consulta de transacciones confirmadas bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/` y `service/impl/`, incluyendo snapshot del nombre del jugador, versión de cotización, contraparte, referencia de idempotencia y P/L realizado.
- [x] T062 Implementar persistencia separada de auditoría interna de rechazos y servicio restringido bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/persistence/` y `service/impl/`, excluyendo credenciales, JWT y payloads sensibles.
- [x] T063 Agregar DTOs del historial de transacciones, validación de filtros/paginación y endpoint autenticado del historial personal bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/controller/dto/` y `controller/`.
- [x] T064 Agregar pruebas MockMvc de historial solo confirmado, filtros, paginación, orden más reciente primero, exclusión de rechazos, exclusión de pendientes y denegación de acceso entre usuarios bajo `backend/src/test/java/com/devchesterunited/devchesterunitedv2/controller/`.

**Punto de control**: El historial confirmado es inmutable y personal; la auditoría de rechazos es separada e interna.

## Fase 11: Historia de usuario 9 - Utilizar la interfaz mínima del mercado (P2)

**Objetivo**: Proporcionar la experiencia mínima de mercado, saldo, operaciones, portfolio, historial y recuperación de operaciones sin trasladar lógica de negocio a React.

**Prueba independiente**: Un usuario autenticado puede completar el flujo del mercado y distinguir estados de carga, rechazo, cotización ausente/histórica, pendiente e incierto.

- [x] T065 [P] [US9] Agregar funciones tipadas del cliente API del mercado bajo `frontend/src/api/` para saldo, jugadores/cotizaciones, compra, venta, recuperación de operaciones, portfolio e historial de transacciones.
- [ ] T066 [P] [US9] Agregar componentes reutilizables de carga, error, cotización obsoleta, pendiente y operación incierta bajo `frontend/src/components/`.
- [ ] T067 [P] [US9] Agregar vistas de listado de mercado/jugadores y saldo bajo `frontend/src/views/`, reutilizando el estado de autenticación de `frontend/src/session/` y los estilos existentes.
- [ ] T068 [P] [US9] Agregar vistas de confirmación de compra/venta bajo `frontend/src/views/` y `frontend/src/components/`, mostrando versión de cotización aceptada, cantidad, total y estado de rechazo.
- [ ] T069 [P] [US9] Agregar vistas de portfolio e historial con filtros/paginación bajo `frontend/src/views/` y `frontend/src/components/`, mostrando precios históricos y resultados realizados después del cierre de posiciones.
- [ ] T070 [US9] Agregar pruebas unitarias frontend para mapeo de API/cliente y todos los estados del mercado en `frontend/tests/market-api.test.js` y `frontend/tests/market-views.test.jsx`, usando la configuración Vitest existente en `frontend/vite.config.js`; Vitest ya está declarado en `frontend/package.json`, por lo que no instalar ni modificar dependencias.
- [ ] T071 [US9] Agregar cobertura del flujo Playwright en `frontend/e2e/token-market.spec.js` para saldo, listado del mercado, compra, venta, portfolio, historial, recuperación de pendientes y aislamiento de usuarios, usando `testDir: './e2e'` de `frontend/playwright.config.js`; Playwright ya está declarado en `frontend/package.json`, por lo que no instalar ni modificar dependencias.

**Punto de control**: La UI mínima es funcional y delega todas las decisiones de pertenencia, precios y validación al backend.

## Fase 12: Historia de usuario 10 - Verificar el escenario reproducible de demostración (P3)

**Objetivo**: Reproducir la demostración controlada de cuatro usuarios/cinco jugadores y verificar por separado las futuras integraciones reales.

**Prueba independiente**: Los fixtures controlados producen portfolios comparables, cambios posteriores de cotización, ventas, resultados realizados/no realizados e historiales personales sin mezclarse con datos reales.

- [ ] T072 [P] [US10] Definir builders de fixtures aislados y exclusivos de pruebas para cuatro usuarios y cinco jugadores bajo `backend/src/test/java/com/devchesterunited/devchesterunitedv2/` y/o `backend/src/test/resources/`, marcando claramente los datos como exclusivos de demostración.
- [ ] T073 [US10] Agregar la prueba reproducible de demostración controlada para cotizaciones iniciales, compras, versiones posteriores diferentes, comparación de portfolios, ventas, resultados realizados/no realizados, posiciones cerradas e historiales bajo `backend/src/test/java/com/devchesterunited/devchesterunitedv2/`.
- [ ] T074 [P] [US10] Agregar cobertura de datos/carga de demostración frontend bajo `frontend/tests/market-demo.test.jsx` sin insertar fixtures en APIs de producción ni datos del catálogo real.
- [ ] T075 [P] [US10] Agregar un escenario posterior y separado de verificación de integración bajo `backend/src/test/java/com/devchesterunited/devchesterunitedv2/service/` que se ejecute solo cuando estén disponibles Player Statistics Sync y Player Valuation reales, consuma sus salidas y nunca implemente scraping, persistencia de estadísticas ni cálculo de precios en Token Market; esta tarea no debe bloquear T073 ni la validación independiente de Token Market.

**Punto de control**: La demostración controlada y la verificación real entre features están explícitamente separadas.

## Fase 13: Pulido y verificación transversal

**Propósito**: Validar todos los criterios de aceptación y límites de integración antes de entregar la implementación.

- [ ] T076 [P] Ejecutar las suites completas de pruebas unitarias backend, MockMvc, PostgreSQL/Testcontainers y frontend mediante los comandos Gradle/npm existentes del repositorio; no ejecutar migraciones de base de datos contra bases no destinadas a pruebas.
- [ ] T077 [P] Verificar los 52 requisitos funcionales y los 11 criterios de éxito de `specs/003-token-market/spec.md` frente a las pruebas de implementación y registrar cualquier criterio incumplido en el issue tracker de implementación.
- [ ] T078 [P] Verificar el comportamiento OpenAPI/de seguridad y confirmar que todas las consultas privadas obtienen la identidad del JWT en lugar de identificadores proporcionados por el cliente bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2/configuration/`, `controller/` y el uso de la API del frontend.
- [ ] T079 Verificar que ningún archivo de Token Market implemente scraping de WhoScored, sincronización de estadísticas, estrategias de valoración, rankings ni una segunda fuente de precios; revisar `backend/src/main/java/com/devchesterunited/devchesterunitedv2/adapter/` y los límites de persistencia de cotizaciones.
- [ ] T080 Verificar el orden de Flyway bajo `backend/src/main/resources/db/migration/` y la coordinación de V16 después de resolver explícitamente la integración de Player Statistics Sync; actualizar únicamente la migración de Token Market si V16 está confirmada, nunca modificar la rama de estadísticas.
- [ ] T081 Ejecutar la secuencia de validación de `quickstart.md` usando PostgreSQL/Testcontainers aislado y registrar evidencia de atomicidad, concurrencia, idempotencia, emisión, portfolio, historial y demostración.

## Dependencias y orden de ejecución

### Dependencias entre fases

- La Fase 1 es independiente y debe preceder a la implementación.
- La Fase 2 bloquea todas las historias de usuario. T005 es una puerta de coordinación externa antes de T006; T006 no debe implementarse suponiendo que no existe conflicto con V16.
- US1 depende de la Fase 2 y establece la inicialización del mercado, las asignaciones, la emisión y las cotizaciones iniciales. Su tarea de siembra de cotizaciones T023 está además condicionada por la cadena compartida T014 → T015 → T039 → T040; el resto de la inicialización puede avanzar de forma independiente.
- US2 depende de la semántica de estado de US1, pero puede desarrollar sus DTOs/pruebas de lectura en paralelo una vez que exista el modelo fundacional.
- US7 depende de la Fase 2. US8 debe ejecutar sus tareas de contrato T014–T015 antes de las tareas de persistencia/publicación T039–T040. T023 consume luego el adaptador real de publicación verificado; el trabajo de publicación dinámica de US8 puede continuar de forma independiente después.
- US3 depende de US1, US2, US7 y US8.
- US4 depende de la infraestructura de movimientos/contabilidad de US3 y de US7/US8.
- US5 depende de la contabilidad de transacciones/tenencias de US3 y US4.
- US6 depende de los registros de transacciones confirmadas y rechazos de US3/US4.
- US9 depende de los contratos backend de US2–US7 y puede iniciar el trabajo del cliente API en paralelo cuando las formas de los DTO estén estables.
- US10 depende de US1–US8 para la demostración controlada; su verificación de integración real depende además de la disponibilidad de Player Statistics Sync y Player Valuation.
- La Fase 13 depende de todas las historias seleccionadas y de la decisión de coordinación V15/V16.

### Camino crítico

`T005 → T006 → T007–T015 → T039–T040 → T018–T024 (US1 quote seeding) → T034–T042 (US7/US8) → T045–T055 (US3/US4) → T057–T064 (US5/US6) → T073 → T076–T081`.

Las puertas externas del camino crítico son la coordinación final de V15, el contrato estable de estado activo del jugador y el límite compartido de publicación de cotizaciones con Player Valuation.

### Oportunidades de ejecución en paralelo

- T002–T004 pueden ejecutarse en paralelo durante la Preparación.
- T007–T009, T012 y la preparación de T014 pueden ejecutarse en paralelo después de conocer la forma de coordinación de migraciones, siempre que no editen el mismo archivo. T015 sigue a T014.
- T039–T040 pueden avanzar después de T014–T015 y deben completarse antes de T023; T023 no puede ejecutarse en paralelo con esa cadena de cotizaciones.
- Las pruebas unitarias/de base de datos de US1 y el diseño del servicio de inicialización pueden dividirse por archivo una vez que exista el modelo fundacional.
- Los contratos de lectura de US2, la idempotencia de US7 y el trabajo de versiones de cotización de US8 pueden avanzar en paralelo después de la Fase 2; los puntos de control de integración mantienen su orden.
- Las pruebas de compra y venta pueden prepararse en paralelo, mientras la implementación compartida de movimientos/contabilidad se coordina antes de integrarse.
- El trabajo de portfolio, historial, frontend y fixtures controlados puede avanzar en paralelo después de estabilizar los contratos backend correspondientes.
- T073/T074 y toda validación independiente de Token Market pueden completarse sin T075. La integración entre features T075 puede ejecutarse en paralelo únicamente después de que Player Statistics Sync y Player Valuation estén realmente disponibles, y permanece bloqueada externamente hasta entonces.

## Estrategia de implementación

### MVP

1. Completar la Fase 1 y la Fase 2, incluida la puerta de coordinación V15/V16.
2. Completar la inicialización automática de US1 y el estado/listado del mercado de US2.
3. Completar la idempotencia de US7 y el consumo de cotizaciones de US8.
4. Completar la compra de US3 y sus pruebas atómicas.
5. Validar el incremento del mercado limitado a compras antes de continuar con las ventas.

### Entrega incremental

1. Agregar las ventas de US4 y la contabilidad de reserva finita.
2. Agregar el portfolio de US5 y el historial confirmado/auditoría de rechazos de US6.
3. Agregar los flujos frontend de US9.
4. Agregar la demostración controlada de US10.
5. Ejecutar la verificación posterior de integración real con Player Statistics Sync/Player Valuation cuando esas features estén disponibles.

## Notas

- Cada tarea usa el formato de checklist requerido e incluye una ruta concreta del repositorio.
- `[P]` significa que la tarea puede avanzar en paralelo sin editar una dependencia incompleta ni el mismo archivo.
- Las etiquetas de historia corresponden a las diez historias de usuario de `spec.md`.
- Ninguna tarea modifica archivos de Player Statistics Sync, documentos aprobados de Specify/Plan ni ejecuta migraciones contra una base de datos durante la planificación.
- Ninguna tarea implementa scraping, sincronización de estadísticas, estrategias de valoración, rankings ni una segunda fuente de cotizaciones.
