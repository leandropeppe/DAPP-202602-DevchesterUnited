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

## Seguimiento de complejidad

No se introducen incumplimientos constitucionales. La idempotencia persistente, los locks acotados a recursos y el límite compartido de cotizaciones son complejidad requerida por la especificación funcional aprobada y no son opcionales.
