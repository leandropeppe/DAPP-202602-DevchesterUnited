# Implementation Plan: Sincronización de Estadísticas de Jugadores

**Branch**: `002-player-statistics-sync` | **Date**: 2026-10-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-player-statistics-sync/spec.md`, Constitution vigente y relevamiento técnico de WhoScored provisto para esta entrega.

## Summary

La feature agregará la obtención, normalización y sincronización de estadísticas generales, ofensivas y defensivas de todos los jugadores de las cinco ligas soportadas. Las estadísticas se asociarán al `Jugador` existente mediante su identificador externo ya persistido; no se creará ni remodelará una segunda entidad de jugador.

El diseño separa la fase de obtención/validación de la fase de publicación. WhoScored se consultará por páginas y por familia de estadísticas, cada página se normalizará y se depositará en un staging técnico de la sincronización. Solo cuando las tres familias y todas sus páginas hayan sido validadas se ejecutará una transacción breve de promoción hacia las estadísticas locales. Así se compatibiliza el procesamiento por lotes con la atomicidad y se evita mantener una transacción abierta durante las llamadas externas.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.5.x, Spring Data JPA, Flyway, cliente HTTP ya utilizado por el backend y Jackson, siguiendo las dependencias vigentes del proyecto.

**Storage**: PostgreSQL con migraciones Flyway. Se agregarán tablas para estadísticas publicadas y staging técnico de sincronización.

**Testing**: JUnit 5, Spring Boot Test y Testcontainers con PostgreSQL real para integración; respuestas externas controladas para pruebas del Adapter.

**Target Platform**: Backend web desplegado en el entorno existente de Devchester United.

**Project Type**: Servicio web backend con arquitectura por capas y persistencia relacional.

**Performance Goals**: Procesar las cinco competiciones sin depender de una cantidad fija de jugadores ni páginas; usar lotes configurables inicialmente de 50 jugadores y conservar un uso de memoria acotado a la página actual y a metadatos de control. La operación debe respetar el presupuesto temporal existente de sincronizaciones, ajustado durante implementación mediante configuración.

**Constraints**: No llamadas externas dentro de la transacción final; no exposición de actualizaciones parciales; último estado válido conservado ante cualquier fallo; no acceso real a WhoScored desde tests; no duplicación de `Jugador`; no constructores de más de siete parámetros.

**Scale/Scope**: Cinco competiciones, todos los jugadores disponibles por ejecución, tres familias por jugador, una fotografía vigente de estadísticas por jugador y sin histórico de cotizaciones.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Gate inicial

- **Capas**: PASS. El caso de uso dependerá de una abstracción propia; el Adapter encapsulará WhoScored; persistencia no consumirá fuentes externas.
- **Modelo independiente**: PASS. El dominio de estadísticas no conocerá Spring, JPA, HTTP ni JSON de WhoScored.
- **Persistencia local**: PASS. Las estadísticas se publicarán localmente y las consultas posteriores no dependerán de la disponibilidad de WhoScored.
- **Atomicidad**: PASS con staging técnico. La obtención paginada ocurrirá fuera de la transacción de promoción; la publicación se revertirá completa ante error.
- **Idempotencia y duplicados**: PASS. La correspondencia final será por `Jugador` existente y habrá restricciones únicas en estadísticas publicadas y staging.
- **Volumen**: PASS. La obtención será paginada, con lote inicial configurable de 50, y no se acumularán respuestas completas en memoria.
- **Seguridad y secretos**: PASS. Las credenciales permanecerán en configuración segura y nunca en código, respuestas o logs.
- **Testing**: PASS. Se cubrirán dominio, Adapter, servicio, persistencia y contrato sin Internet ni credenciales reales.
- **OpenAPI**: PASS. El contrato del disparador administrativo se documentará si se incorpora el nuevo caso de uso HTTP.

## Project Structure

### Documentation (this feature)

```text
specs/002-player-statistics-sync/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    └── player-statistics-sync.md
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/devchesterunited/devchesterunitedv2/
│   ├── adapter/
│   │   ├── dto/                         # DTOs JSON de WhoScored
│   │   └── impl/                        # Adapter concreto de WhoScored
│   ├── controller/
│   │   └── dto/                         # Contratos HTTP públicos
│   ├── modelo/                          # Estadísticas y value objects de dominio
│   ├── persistence/
│   │   └── dto/                         # Entidades JPA de estadísticas/staging
│   └── service/
│       ├── dto/                         # Representaciones internas normalizadas
│       ├── exception/
│       ├── impl/                        # Orquestación y promoción atómica
│       └── interfaces/                  # Abstracción del proveedor estadístico
├── src/main/resources/db/migration/     # Migraciones de estadísticas/staging
└── src/test/java/com/devchesterunited/devchesterunitedv2/
    ├── adapter/
    ├── modelo/
    ├── persistence/
    ├── service/
    └── controller/
```

**Structure Decision**: Se conserva el package by layer existente bajo `backend/src/main/java/com/devchesterunited/devchesterunitedv2`. Se agrega la capacidad de estadísticas dentro de las capas actuales, sin crear un módulo paralelo ni duplicar `Jugador`. Los DTOs externos permanecen en `adapter/dto`; las entidades de persistencia se mantienen en `persistence/dto`.

## Diseño técnico

### Componentes y responsabilidades

1. **Proveedor de estadísticas**: interfaz definida por la aplicación que entrega páginas normalizadas por competición, familia y página. No expone URL, parámetros, JSON ni nombres de campos del proveedor.
2. **Adapter de WhoScored**: único componente que conoce el recurso `/statisticsfeed/1/getplayerstatistics`, `category=summary`, las familias `all`, `offensive` y `defensive`, los `stageId`, la paginación, credenciales, timeouts y la forma JSON externa. Traduce respuestas a datos internos y convierte fallos de transporte o contrato en errores de integración propios.
3. **Normalizador/consolidador**: combina las páginas de una familia por `playerId`, valida que `currentPage`, `totalPages`, `resultsPerPage` y `totalResults` formen una secuencia completa y consolida las tres familias en una representación de estadísticas por jugador.
4. **Servicio de sincronización**: coordina competición/familia/página, resuelve cada `playerId` contra `JugadorRepository`, valida el conjunto completo y solicita la promoción. No conoce detalles HTTP o JSON.
5. **Persistencia**: conserva estadísticas publicadas asociadas por clave foránea al `Jugador` existente y staging aislado por identificador de sincronización. La promoción actualiza filas existentes y crea únicamente las que aún no existen.

### Obtención y paginación

- La configuración de las cinco competiciones se mantendrá en el Adapter, con los `stageId` relevados como valores de la edición consultada, no como identidad universal de la liga.
- La configuración inicial relevada es: Premier League `25544`, La Liga `25662`, Serie A `25518`, Bundesliga `25666` y Ligue 1 `25554`. Estos valores se tratarán como identificadores de la edición consultada y deberán poder reemplazarse cuando cambie la temporada.
- El lote inicial será 50 mediante una propiedad configurable de infraestructura.
- Para cada familia se seguirá `totalPages`; no se calculará el fin a partir de una cantidad fija ni se detendrá por una página vacía antes de validar la metadata.
- Cada página se transformará y escribirá en staging antes de solicitar la siguiente. La memoria de la fase externa se limita a la respuesta/página y a estructuras pequeñas de control.
- La ausencia de una métrica individual se representa como valor no informado. La falta de una fila de jugador, una metadata de paginación inconsistente o la ausencia completa de una familia invalidan la sincronización.

### Mapeo de métricas

| Concepto interno | Campo de WhoScored |
|---|---|
| Participación: apariciones | `apps` |
| Participación: minutos | `minsPlayed` |
| Ofensiva: goles | `goal` |
| Ofensiva: asistencias | `assistTotal` |
| Ofensiva: tiros por partido | `shotsPerGame` |
| Ofensiva: pases clave por partido | `keyPassPerGame` |
| Ofensiva: regates ganados por partido | `dribbleWonPerGame` |
| Defensiva: entradas por partido | `tacklePerGame` |
| Disciplina: amarillas | `yellowCard` |
| Disciplina: rojas | `redCard` |
| Valoración general | `rating` |

La tabla define únicamente los campos necesarios para el modelo; cualquier
campo adicional recibido se ignora después de la validación del contrato.

### Asociación con `Jugador`

- `playerId` se conserva como identidad de integración en la capa Adapter y se usa en el servicio para resolver una identidad de fuente compatible o una correspondencia persistida hacia el `Jugador` existente.
- La estadística publicada se vincula por el UUID local de `Jugador`; no crea, modifica ni duplica el perfil del jugador como consecuencia de una estadística.
- Un `playerId` desconocido, duplicado con datos incompatibles o asociado a más de un jugador local invalida la sincronización completa y deja intacto el estado publicado.

### Gate de identidad entre fuentes

El relevamiento del código muestra que `Jugador.idExterno` se utiliza hoy para
la identidad del proveedor del catálogo, mientras que `playerId` pertenece a
WhoScored. El plan prohíbe comparar ambos valores sin validar que pertenezcan
al mismo namespace. Antes de publicar se debe comprobar una de estas
condiciones:

1. el catálogo vigente ya conserva el `playerId` de WhoScored como identidad
   externa compatible; o
2. existe una correspondencia persistida y única por fuente que traduzca el
   `playerId` de WhoScored al `jugadorId` local.

Si ninguna condición se cumple para un jugador, la sincronización falla sin
publicar cambios. No se usará nombre, equipo o posición como clave silenciosa
de respaldo. Si la implementación requiere la segunda alternativa, la tabla
de correspondencias será infraestructura de integración y no una remodelación
ni duplicación de `Jugador`; su carga deberá definirse antes de habilitar la
sincronización productiva.

### Modelo cohesivo

Se propone un agregado `EstadisticasJugador` asociado a un `jugadorId` y compuesto por objetos de valor cohesivos:

- `Participacion`: `apps`, `minsPlayed`.
- `EstadisticasOfensivas`: `goals`, `assists`, `shotsPerGame`, `keyPassPerGame`, `dribbleWonPerGame`.
- `EstadisticasDefensivas`: `tacklePerGame`.
- `Disciplina`: `yellowCard`, `redCard`.
- `ValoracionGeneral`: `rating`.

Los nombres son la decisión de diseño propuesta para la fase siguiente, no un reflejo directo del JSON externo. Los constructores se limitarán a conceptos cohesivos y las métricas opcionales conservarán ausencia explícita. No se incluyen score normalizado, cotización, ponderaciones ni histórico.

### Atomicidad sin transacción de red abierta

1. El servicio adquiere el control de concurrencia de la sincronización sin abrir una transacción de persistencia que abarque la red.
2. Crea un identificador de sincronización y un staging aislado. Cada página se valida y se guarda mediante transacciones breves, idempotentes para ese identificador.
3. Al finalizar las cinco competiciones y las tres familias, valida conteos, cobertura de jugadores, ausencia de duplicados y asociación con jugadores locales.
4. Ejecuta una única transacción final de promoción: reemplaza o actualiza el conjunto publicado de forma consistente, conserva filas sin cambios cuando corresponda y retira únicamente estadísticas del snapshot válido que hayan quedado fuera según la política definida para la edición vigente.
5. Si falla la validación, una página, la asociación, la promoción o la limpieza técnica, la transacción final se revierte y el último conjunto publicado permanece visible. El staging fallido se marca o limpia fuera de la visibilidad funcional, sin alterar el estado anterior.

Este enfoque evita el patrón inválido de hacer commit por página y permite que la memoria no crezca con todas las respuestas externas.

### Idempotencia y restricciones de persistencia

- `jugador_estadisticas.jugador_id` será único para garantizar una fotografía vigente por jugador.
- El staging tendrá una clave única por sincronización, competición, familia y `playerId`, evitando duplicados de páginas o reintentos.
- La promoción hará upsert lógico por `jugador_id`; una repetición con los mismos datos produce el mismo estado y no crea filas adicionales.
- `Jugador.external_id` continúa siendo la identidad externa del perfil; no se agrega una entidad `Jugador` paralela ni se usa el nombre como clave.

### Manejo de errores

- Indisponibilidad, timeout, respuesta no exitosa, cuerpo inválido o JSON inválido: error de integración traducido, staging abortado, estado publicado intacto.
- Paginación inconsistente o familia incompleta: error de validación de sincronización, sin promoción.
- Métrica individual ausente: dato opcional nulo, sin fallar por sí solo.
- `playerId` sin `Jugador` local o con asociación ambigua: error de asociación, sin promoción.
- Error de base de datos en staging o promoción: rollback de la operación afectada y conservación del snapshot anterior.
- Las respuestas públicas no expondrán mensajes, URLs, parámetros ni cuerpos internos de WhoScored.

## Phase 0: Research

La investigación se consolidó en [research.md](./research.md). Las decisiones pendientes del template quedan resueltas: se reutiliza el stack vigente, se usa una interfaz de proveedor, se adopta paginación por metadata y se emplea staging para no mantener abierta la transacción durante la red.

## Phase 1: Design & Contracts

- [data-model.md](./data-model.md): agregado, objetos de valor, entidades publicadas/staging, restricciones e invariantes.
- [contracts/player-statistics-sync.md](./contracts/player-statistics-sync.md): contrato del caso de uso administrativo y errores públicos.
- [quickstart.md](./quickstart.md): guía de validación con proveedor simulado, PostgreSQL de pruebas y escenarios de atomicidad.

## Constitution Check (post-design)

- **PASS — Arquitectura**: Service depende de una interfaz; WhoScored queda en Adapter; Repository solo persiste.
- **PASS — Dominio**: `Jugador` se reutiliza por identidad local y el agregado estadístico no conoce infraestructura.
- **PASS — DTOs**: JSON externo en `adapter/dto`; contratos HTTP públicos separados; ninguna estructura externa llega al dominio.
- **PASS — Persistencia local**: las consultas futuras utilizan estadísticas publicadas sin red en tiempo real.
- **PASS — Atomicidad**: staging + promoción única; no hay commits funcionales por página.
- **PASS — Volumen**: lote inicial configurable de 50 y páginas controladas por metadata del proveedor.
- **PASS — Errores**: indisponibilidad, datos incompletos y fallos de persistencia conservan el estado local anterior.
- **PASS — Seguridad**: credenciales fuera del repositorio y detalles del proveedor fuera de respuestas/logs.
- **PASS — Testing/DoD**: unitarias, Adapter aislado, integración con Testcontainers y E2E del contrato sin red real.
- **PASS — OpenAPI**: el caso de uso HTTP queda documentado en el contrato; no se planifican cambios de frontend.

No se identifican violaciones que requieran una entrada en Complexity Tracking.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| — | No hay violaciones constitucionales justificadas. | — |
