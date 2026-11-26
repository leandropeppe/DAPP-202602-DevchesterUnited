# Implementation Plan: Auditoría transversal y logging seguro

**Branch**: `008-aop-auditoria-logging` | **Date**: 2026-11-25 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/008-aop-auditoria-logging/spec.md`

## Summary

Se añadirá una capacidad transversal de auditoría para un conjunto cerrado de casos de uso existentes. La solución usará un aspecto Spring AOP aplicado a puntos de entrada públicos de servicios, un filtro HTTP previo a seguridad para crear el identificador interno de solicitud y un writer estructurado con allowlist. No se modificará la lógica de negocio, la persistencia, la cadena de seguridad ni las métricas de Actuator/Prometheus.

La instrumentación se ubicará en `configuration.auditoria`, porque es una preocupación de infraestructura y no debe introducir dependencias desde `service`, `modelo` o `persistence` hacia configuración. Los métodos internos que no atraviesan un proxy Spring no se presentarán como auditados.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.5.16, Spring AOP/AspectJ runtime (dependencia explícita a incorporar durante la implementación), Spring Web/Servlet, SLF4J, Spring Security existente y MDC de SLF4J

**Storage**: N/A. No se crearán tablas, migraciones ni repositorios de auditoría.

**Testing**: JUnit 5, Spring Boot Test, MockMvc, Spring Security Test, Testcontainers/PostgreSQL para regresión de integración y ArchUnit 1.5.1 para las 21 reglas existentes.

**Target Platform**: Backend Spring Boot ejecutado en JVM Java 21; servidor HTTP principal y management server separado según la configuración existente.

**Project Type**: Servicio web REST con tareas programadas y procesos de inicialización.

**Performance Goals**: Medición reproducible con 30 pares baseline/tratamiento por escenario, mismo entorno, compilación, configuración y dataset, después de warm-up controlado; incremento mediano <=10% y p95 <=20% según SC-008. Se separará el diagnóstico del costo del writer del costo total de la instrumentación AOP; un writer no-op no se tratará como eliminación de todo el costo AOP.

**Constraints**: Eventos START/END con allowlist; sin argumentos, retornos, headers de autorización, secretos, mensajes arbitrarios ni stack traces; comportamiento best effort; ID interno único por solicitud; `X-Correlation-ID` externo separado y validado; no alterar negocio, seguridad ni observabilidad existente.

**Scale/Scope**: Diez operaciones de negocio seleccionadas y un flujo HTTP transversal. El alcance no incluye todos los servicios, consultas de lectura, repositorios, helpers internos ni propagación automática a hilos asíncronos.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Result | Evidence / design response |
|---|---|---|
| Capas y dependencias | PASS | Las nuevas clases viven en `configuration.auditoria`; no se agregan dependencias desde `modelo`, `persistence` o `service` hacia configuración. |
| Modelo independiente | PASS | No se modifican clases de dominio ni se agregan entidades de auditoría. |
| Integraciones externas | PASS | El aspecto observa casos de uso; no cambia adapters ni contratos de proveedores. |
| Seguridad y secretos | PASS | El filtro no lee tokens; el writer usa allowlist y el código de error no usa mensajes ni causas completas. |
| Persistencia/Flyway | PASS | No hay almacenamiento persistente ni migración. |
| Testing | PASS | Se planifican pruebas unitarias, Spring/MockMvc, concurrencia, seguridad, regresión y las 21 reglas ArchUnit. |
| Observabilidad existente | PASS | No se agregan métricas ni se modifica Actuator/Prometheus; solo se usan logs SLF4J/MDC ya disponibles o provistos por AOP. |
| Complejidad y robustez | PASS | El diseño separa correlación, selección, construcción y escritura; no captura `Error` ni depende de self-invocation. |

No se detectan violaciones constitucionales que requieran justificar una alternativa más compleja.

## Project Structure

### Documentation (this feature)

```text
specs/008-aop-auditoria-logging/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── audit-event.md
│   └── http-correlation.md
└── checklists/requirements.md
```

### Source Code (repository root)

```text
backend/
├── build.gradle
├── src/main/java/com/devchesterunited/devchesterunitedv2/
│   └── configuration/auditoria/
│       ├── AuditoriaAspect.java
│       ├── AuditoriaErrorCodeResolver.java
│       ├── AuditoriaEventWriter.java
│       ├── AuditoriaOperationCatalog.java
│       ├── CorrelacionContext.java
│       ├── CorrelacionHttpFilter.java
│       └── AuditoriaEvento.java
└── src/test/java/com/devchesterunited/devchesterunitedv2/
    ├── architecture/ArchitectureTest.java
    ├── unit/auditoria/
    └── integration/auditoria/
```

**Structure Decision**: Se conserva el `package by layer` real del backend. La auditoría es infraestructura transversal bajo `configuration.auditoria` y no se crean paquetes `modelo`, `persistence` o `service` nuevos. Los nombres de clase son previstos para la implementación; no implican que el plan agregue código en esta etapa.

## Selected Operations

La selección es explícita y se limita a invocaciones públicas a través de interfaces de servicio. El aspecto debe interceptar solo estos contratos:

| Contexto | Clase real | Método real | Operación lógica | Entrada principal |
|---|---|---|---|---|
| Sincronización de catálogo | `SincronizacionCatalogoServiceImpl` | `sincronizar()` | `catalog.sync` | `POST /api/v1/catalog/sync` |
| Sincronización de estadísticas | `SincronizacionEstadisticasJugadorServiceImpl` | `sincronizar()` | `statistics.sync` | `EstadisticasScheduler.ejecutarSemanal()` |
| Valorización manual | `ValorizacionJugadorServiceImpl` | `ejecutarManual(ConfiguracionElegibilidad)` | `valuation.manual` | Punto de entrada interno proxyado |
| Valorización semanal | `ValorizacionJugadorServiceImpl` | `ejecutarSemanal(ConfiguracionElegibilidad)` | `valuation.weekly` | `POST /api/v1/quotes/recalculate` y `ValorizacionScheduler.ejecutarSemanal()` |
| Compra inmediata | `MarketPurchaseServiceImpl` | `comprar(MarketPurchaseRequest)` | `market.purchase` | `POST /api/v1/market/purchases` |
| Venta inmediata | `MarketSaleServiceImpl` | `vender(MarketSaleRequest)` | `market.sale` | `POST /api/v1/market/sales` |
| Publicación P2P | `MarketP2POfferServiceImpl` | `publicar(MarketP2POfferCommand)` | `market.p2p.offer.publish` | `POST /api/v1/market/p2p/offers` |
| Compra P2P | `MarketP2PPurchaseExecutionServiceImpl` | `comprar(UUID, String, UUID, Long, MarketP2PQuoteIdentity)` | `market.p2p.purchase` | `POST /api/v1/market/p2p/offers/{offerId}/purchases` |
| Cancelación P2P | `MarketP2PCancellationServiceImpl` | `cancelar(MarketP2PCancelCommand)` | `market.p2p.offer.cancel` | `POST /api/v1/market/p2p/offers/{offerId}/cancel` |
| Inicialización de mercado | `MarketInitializationServiceImpl` | `initialize()` | `market.initialize` | `ApplicationReadyEvent` |

La tabla contiene diez operaciones; el límite de alcance se expresa como una lista cerrada y revisable, no como instrumentación indiscriminada. La compra P2P se interceptará en el overload invocado por el controller; el overload que recibe `MarketP2PPurchaseCommand` y las llamadas internas no generarán un segundo evento. No se auditarán `QuotePublicationPort.publish/invalidate`, consultas, repositorios, locks, mappers ni helpers internos porque duplicarían el evento del caso de uso o expondrían detalles de infraestructura.

## Architecture Design

### Correlation and HTTP boundary

`CorrelacionHttpFilter` será un `OncePerRequestFilter` ordenado antes del procesamiento de seguridad. En cada solicitud:

1. Generará un UUID interno nuevo, siempre, sin aceptar que el cliente lo reemplace.
2. Leerá únicamente `X-Correlation-ID` como valor externo opcional.
3. Aceptará el valor externo solo si tiene 1–128 caracteres ASCII de `A-Z`, `a-z`, `0-9`, `.`, `_` o `-`; valores inválidos se ignorarán sin rechazar la solicitud.
4. Guardará `requestCorrelationId` y, si existe, `externalCorrelationId` en `CorrelacionContext` y MDC con claves estables.
5. Fijará el ID interno en la respuesta mediante `X-Correlation-ID`.
6. Limpiará MDC y el contexto en un bloque `finally`, incluso ante excepción.

No leerá `Authorization`, `X-API-KEY` ni otros headers de autenticación. El filtro no cambia `SecurityConfig`, `ApiKeyAuthenticationFilter` ni las reglas del management port.

### Aspect and event writer

`AuditoriaAspect` tendrá un `@Around` acotado a las firmas seleccionadas. Antes de `proceed()` emitirá START; medirá tiempo monotónico; después emitirá END con `SUCCESS`, o END con `ERROR` y un código estable obtenido por `AuditoriaErrorCodeResolver`. Nunca pasará argumentos, retornos, mensajes de excepción o `Throwable` al writer.

`AuditoriaOperationCatalog` mantendrá el mapa cerrado de firmas a operaciones lógicas. Si una firma no pertenece a la lista, no se audita. El aspecto no interceptará constructores, métodos privados, repositorios, consultas ni llamadas internas por `self-invocation`.

`AuditoriaEventWriter` construirá `AuditoriaEvento` solo con la allowlist del contrato. Emitirá mediante SLF4J usando un mensaje constante y valores de la allowlist como campos del evento y claves MDC estables (`audit.eventName`, `audit.operation`, `audit.stage`, `audit.result`, `audit.durationMs`, `audit.timestamp`, `audit.contextType`, `audit.requestCorrelationId`, `audit.executionCorrelationId`, `audit.externalCorrelationId`, `audit.errorCode`). La presencia de MDC no implica que el backend renderice JSON: los tests inspeccionarán el evento estructurado/capturado y los pares MDC, sin exigir cambios de configuración de logging. No serializará objetos de negocio. Si la escritura falla con una excepción de runtime, la aislará y emitirá como máximo una señal técnica controlada sin reintento ilimitado ni recursión de auditoría. La excepción o resultado del negocio se preserva exactamente.

Para tareas programadas y eventos de startup no habrá ID HTTP inventado: el contexto será `BACKGROUND`, se generará un `executionCorrelationId` por ejecución, `requestCorrelationId` estará ausente y `externalCorrelationId` no se incluirá.

### Controlled error codes

El resolver mapeará solo categorías conocidas a códigos estables, por ejemplo: `VALIDATION_ERROR`, `AUTHENTICATION_ERROR`, `AUTHORIZATION_ERROR`, `NOT_FOUND`, `CONFLICT`, `CONCURRENCY_ERROR`, `EXTERNAL_PROVIDER_ERROR`, `PERSISTENCE_ERROR` y `UNEXPECTED_ERROR`. La clasificación usará tipos y contratos conocidos, nunca el texto del mensaje. Los stack traces quedan fuera del evento de auditoría; los logs funcionales existentes se preservan y no se reconfiguran como parte de esta feature.

## Tests and Validation Strategy

- Unitarios de `CorrelationIdPolicy`/`CorrelacionHttpFilter`: generación, validación de límites, caracteres, ID externo separado, respuesta y limpieza.
- Unitarios de `AuditoriaErrorCodeResolver`: tipos conocidos, fallback estable, ausencia de mensajes, headers y stack traces.
- Unitarios de `AuditoriaEventWriter`: allowlist, campos START/END, duración, contexto HTTP/background y fallo best effort del writer.
- Tests Spring/AOP: cada operación seleccionada produce exactamente START y END; el overload P2P no duplica; una llamada self-invocation no se afirma como auditada; métodos no seleccionados no generan eventos.
- MockMvc/Spring Security: respuesta con `X-Correlation-ID`, valor externo válido separado, valor inválido ignorado, JWT/API key no presentes en eventos, endpoints de health/prometheus sin cambios de autorización.
- Concurrencia: solicitudes paralelas reciben IDs internos distintos, no comparten MDC y no cruzan `externalCorrelationId`.
- Regresión: respuestas, excepciones, resultados de negocio, autenticación, Actuator/Prometheus y migraciones existentes permanecen sin cambios.
- ArchUnit: ejecutar las 21 reglas existentes y comprobar que las clases de `configuration.auditoria` no introducen dependencias prohibidas ni ciclos.
- Rendimiento SC-008: seleccionar escenarios representativos de sincronización, valorización y mercado; ejecutar 30 pares alternados de baseline sin el aspecto y tratamiento con AOP más writer habilitados, con la misma compilación, configuración, dataset sintético, operación y entorno; controlar y descartar warm-up; guardar muestras, mediana, p95, método, versión y entorno en `backend/build/diagnostics/auditoria-performance/`. Ejecutar además una serie diagnóstica equivalente con writer controlado sin emisión para estimar el costo del writer, sin presentarla como eliminación del costo AOP. El tiempo del destino externo de logs se excluye del tiempo del backend y no se agregan métricas para medirlo.

## Phase 0/1 Resolution Status

No quedan decisiones técnicas bloqueantes. Las limitaciones de proxy, el contrato HTTP, la allowlist, el tratamiento best effort, el alcance de operaciones, el contexto background y el procedimiento SC-008 están resueltos en `research.md`, `data-model.md`, los contratos y `quickstart.md`.

## Complexity Tracking

No se registran violaciones constitucionales.
