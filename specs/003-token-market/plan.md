# Plan de implementación: Token Market

**Rama**: `feature/token-market` | **Fecha**: 2026-10-10 | **Especificación**: [spec.md](spec.md)

**Entrada**: Especificación aprobada en `specs/003-token-market/spec.md`.

## Resumen

Token Market incorpora el ciclo de vida interno del mercado, las cuentas de créditos virtuales, la emisión única de tokens de jugadores, las operaciones inmediatas de compra/venta contra el superusuario protegido, las tenencias personales y la consulta del portfolio, las transacciones confirmadas inmutables, la auditoría de rechazos y la recuperación persistente de solicitudes idempotentes. Consume un contrato de cotizaciones versionado cuya autoridad funcional corresponde a Player Valuation; la cotización inicial es de 1.00 crédito por token y utiliza la misma fuente de verdad.

El diseño utiliza servicios de aplicación Spring Boot sobre transacciones PostgreSQL. Las operaciones normales bloquean únicamente los recursos involucrados, en un orden determinista único, mientras que la idempotencia persistente y las escrituras atómicas de movimientos/transacciones evitan transferencias duplicadas. Token Market no calcula precios, no realiza scraping de estadísticas ni modifica la responsabilidad de estadísticas de jugadores.

## Contexto técnico

**Lenguaje/versión**: Java 21; TypeScript/React para el frontend existente.

**Dependencias principales**: Spring Boot 3.5.16, Spring Web, Spring Security OAuth2 Resource Server/JWT, Spring Data JPA, Bean Validation, springdoc OpenAPI, React, Vite.

**Almacenamiento**: PostgreSQL 16 con Flyway; la convención existente es la validación del esquema mediante Hibernate.

**Pruebas**: JUnit 5, Spring Boot Test, MockMvc, Testcontainers/PostgreSQL, Vitest y Playwright, tal como ya utiliza el repositorio.

**Plataforma objetivo**: Backend Spring Boot y cliente web React/Vite.

**Tipo de proyecto**: Aplicación web con API backend y cliente frontend.

**Objetivos de rendimiento**: Mantener las transacciones normales del mercado breves y limitadas a filas; no serializar todo el mercado. La inicialización puede serializarse de forma independiente.

**Restricciones**: propiedad derivada del JWT; cantidades de tokens enteras y positivas; créditos/precios con dos decimales y mayor precisión interna para costos; sin fuente de precios paralela; sin transferencias parciales; sin transacción confirmada antes del commit; sin cambios a Specify aprobado ni a Player Statistics Sync.

**Escala/alcance**: Catálogo actual de jugadores y modelo de usuarios autenticados, con una demostración reproducible de cuatro usuarios y cinco jugadores.

## Verificación de la Constitución

*PUERTA DE CONTROL: Debe aprobarse antes de la investigación de la Fase 0 y después del diseño de la Fase 1.*

| Principio / puerta | Resultado | Evidencia del plan |
|---|---|---|
| Backend por capas y responsabilidades claras | PASS | Las responsabilidades de dominio/aplicación/API del mercado se planifican según las convenciones de paquetes existentes; no se agrega lógica de valoración ni estadísticas. |
| Identidad y autorización seguras | PASS | Los recursos privados derivan el usuario del JWT validado; el superusuario interno no posee credenciales públicas ni ruta de login. |
| PostgreSQL como fuente persistente de verdad de negocio | PASS | Saldos, emisiones, tenencias, idempotencia, cotizaciones e historial inmutable se persisten transaccionalmente. |
| Atomicidad y consistencia | PASS | Locks de filas PostgreSQL acotados a recursos, orden determinista, restricciones y escrituras atómicas de movimientos/historial. |
| Convenciones de API y documentación | PASS | Los contratos propuestos reutilizan las convenciones existentes de respuestas, validación, errores, JWT y OpenAPI sin fijar prematuramente detalles de implementación. |
| Capacidad de prueba | PASS | Las pruebas unitarias, MockMvc, Testcontainers y las demostraciones de UI cubren los criterios de aceptación aprobados. |
| Coordinación entre features | PASS WITH COORDINATION | V16 es provisional hasta reconciliar V15 de Player Statistics Sync; la responsabilidad de publicación de cotizaciones se coordina con Player Valuation. |

## Estructura del proyecto

### Documentación (esta feature)

```text
specs/003-token-market/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── market-api.md
│   └── quote-contract.md
└── checklists/requirements.md
```

`tasks.md` is intentionally not created by this Plan execution.

### Source Code (repository root)

```text
backend/src/main/java/com/devchesterunited/devchesterunitedv2/
├── adapter/
│   ├── dto/
│   └── impl/
├── configuration/
├── controller/
│   ├── dto/
│   ├── dto/validation/
│   └── exceptionhandler/
├── modelo/
│   └── exception/
├── persistence/
│   ├── dto/
│   └── mapper/
└── service/
    ├── config/
    ├── dto/
    ├── exception/
    ├── impl/
    └── interfaces/

backend/src/main/resources/
├── db/migration/
└── application*.yml

frontend/src/
├── api/
├── components/
├── session/
├── styles/
└── views/
```

**Decisión estructural**: Extender los paquetes backend verificados `configuration`, `modelo`, `persistence`, `service` y `controller`, utilizando `adapter` cuando un límite interno lo requiera. Extender las convenciones frontend verificadas `api`, `views` y `components`. La feature introduce conceptos de dominio del mercado sin cambiar la responsabilidad sobre jugadores o estadísticas. El contrato de cotizaciones se comparte conceptualmente con Player Valuation; no debe crear una segunda estructura para el precio inicial. Los nombres exactos de clases/tablas siguen siendo tareas de implementación, no compromisos definitivos de Specify/Plan.

## Fase 0: Investigación

Las decisiones de investigación y la evidencia del repositorio están registradas en [research.md](research.md). Las decisiones principales son:

1. Utilizar las convenciones existentes de Spring Boot/JPA/PostgreSQL/Flyway/JWT.
2. Representar el superusuario como una única contraparte interna protegida del mercado, separada de la autenticación pública de `Usuario`.
3. Usar un único orden determinista de locks para la solicitud idempotente, el estado compartido del mercado, el estado activo del jugador, la cotización vigente, el saldo del usuario, el saldo del superusuario, el inventario del jugador y la tenencia del usuario; las operaciones normales no toman un lock exclusivo global.
4. Persistir el estado idempotente y usar un advisory lock transaccional de PostgreSQL junto con la propiedad de la fila para impedir ejecuciones concurrentes; confirmar atómicamente el resultado de la solicitud, los movimientos y el historial.
5. Usar un coordinador automático de disponibilidad de la aplicación y `MarketInitializationService` para sembrar asignaciones, emisiones y la cotización inicial mediante un único `QuotePublicationPort` compartido.
6. Tratar Flyway V16 como provisional únicamente después de coordinar con la rama remota de estadísticas V15.

## Fase 1: Diseño

Los artefactos de diseño son:

- [data-model.md](data-model.md): modelo conceptual de persistencia, relaciones, invariantes y recursos bloqueables.
- [contracts/market-api.md](contracts/market-api.md): superficie de API autenticada propuesta y semántica de respuestas/errores.
- [contracts/quote-contract.md](contracts/quote-contract.md): contrato de cotizaciones versionado estable y límite de responsabilidades entre features.
- [quickstart.md](quickstart.md): secuencia futura de verificación, comprobaciones con Testcontainers y escenarios separados de demostración/integración.

## Verificación de la Constitución posterior al diseño

| Puerta | Resultado | Evidencia |
|---|---|---|
| Límites de responsabilidad | PASS | Las estadísticas permanecen como entradas externas; valoración publica cotizaciones; el mercado consume cotizaciones y posee trading/contabilidad. |
| Seguridad | PASS | El principal JWT es el único selector de usuario; las operaciones internas del superusuario no son públicas. |
| Integridad transaccional | PASS | Locks de filas deterministas, restricciones de base de datos, estados de idempotencia y un único límite de commit para operaciones confirmadas. |
| Sin serialización global innecesaria | PASS | Solo se serializan inicialización/transiciones; las operaciones normales bloquean sus propios recursos. |
| Compatibilidad de contratos | PASS WITH COORDINATION | Las cotizaciones iniciales y dinámicas comparten un contrato; la publicación de Player Valuation y el orden de migraciones V15/V16 requieren coordinación. |
| Capacidad de prueba | PASS | Se planificaron verificaciones PostgreSQL/Testcontainers y de seguridad/API/UI. |

## Diseño de las mejoras de Portfolio, venta y gráficos

Esta ampliacion mantiene las responsabilidades existentes: Token Market conserva operaciones, tenencias, historial y consumo de cotizaciones; Player Valuation sigue publicando precios; Player Statistics Sync sigue siendo responsable de estadisticas. No se agrega scraping, persistencia de estadisticas ni calculo de precios al mercado.

### Contrato y enriquecimiento del Portfolio

El backend extendera la respuesta existente de Portfolio con totales calculados sobre todas las posiciones abiertas: jugadores distintos, tokens totales, costo pendiente total y valor actual total. Los campos contables se calcularan en `MarketPortfolioServiceImpl` con la precision interna existente. Si una o mas posiciones no tienen cotizacion vigente, los totales dependientes de valuacion permaneceran null y `combined_result_complete` seguira siendo false.

Las posiciones tambien incorporaran nombre canonico, equipo y liga cuando esten disponibles. El enriquecimiento debe resolverse en backend mediante una consulta batch/proyeccion sobre las fuentes canonicas de jugadores y equipos, evitando que React recorra paginas del catalogo o use snapshots del historial como identidad principal. `player_id` continuara siendo el identificador tecnico para operaciones.

### Reutilizacion del flujo de venta

Mercado conservara exclusivamente Comprar. Portfolio iniciara la venta reutilizando `MarketOperationPanel` y `sellMarketTokens`, sin duplicar validaciones ni formularios. El adaptador de una posicion usara `player_id`, `quote_id`, `quote_version` y `unit_price` solamente cuando `quote_availability` sea `CURRENT`. La cantidad visible sera `min(100, quantity)`; el backend continuara siendo la autoridad final sobre tenencia, vigencia, reserva, idempotencia y concurrencia.

Una venta confirmada provocara la recarga del Portfolio. Los estados `PROCESSING` y `UNCERTAIN` no modificaran visualmente saldo o posiciones como si fueran confirmados; la recuperacion existente continuara disponible.

### Decision de biblioteca grafica

`frontend/package.json` no contiene actualmente una biblioteca de graficos. Se planifica utilizar Recharts para dona, barras agrupadas y actividad temporal, sujeto a confirmar una version compatible durante implementacion. La dependencia se agregara solamente en la etapa de implementacion.

Cada grafico tendra una representacion alternativa mediante resumen textual y tabla HTML accesible. Los componentes seran responsive y tendran estados de carga, error y ausencia de datos. Las posiciones sin valor actual no se transformaran en cero: se excluiran de la visualizacion valuada y se explicaran como no disponibles.

### Datos temporales y paginacion

El endpoint de transacciones sigue paginado y contiene solo operaciones confirmadas. Para el grafico de actividad, el frontend cargara todas las paginas necesarias usando el `total_pages` devuelto, o se evaluara un endpoint agregado posterior si el volumen o la consistencia temporal lo exige. Nunca se mostrara una primera pagina como si fuera todo el historial.

El agrupamiento temporal se definira de forma determinista, preferentemente diario para rangos cortos y mensual para historiales extensos, documentando la zona horaria usada para agrupar instantes.

### Secuencia de entrega

1. Estabilizar contrato y agregados backend del Portfolio.
2. Enriquecer posiciones con identidad canonica y cubrir DTO/servicio/integracion.
3. Cambiar Mercado a solo Comprar y habilitar Venta desde Portfolio.
4. Parametrizar el limite de cantidad del panel y recargar despues de confirmar.
5. Agregar Recharts, adaptadores de datos y tablas accesibles.
6. Cargar historial completo para actividad y validar responsive, null, errores y estados inciertos.
7. Ejecutar la validacion transversal posterior, sin incorporar el feature adicional del TP.

## Seguimiento de complejidad

No se introducen incumplimientos constitucionales. La idempotencia persistente, los locks acotados a recursos y el límite compartido de cotizaciones son complejidad requerida por la especificación funcional aprobada y no son opcionales.
