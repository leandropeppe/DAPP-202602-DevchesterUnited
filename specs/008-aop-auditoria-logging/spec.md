# Feature Specification: Auditoría transversal y logging seguro

**Feature Branch**: `008-aop-auditoria-logging`

**Created**: 2026-11-25

**Status**: Draft

**Input**: User description: "Crear una feature SDD para implementar auditoría transversal y logging seguro en el backend mediante Spring AOP, SLF4J y mecanismos de correlación HTTP, sin modificar la lógica de negocio."

## Clarifications

### Session 2026-11-25

- Q: ¿Cómo debe tratarse un `X-Correlation-ID` recibido del cliente para garantizar que cada solicitud tenga una identidad única y trazable? → A: Generar siempre un identificador interno único por solicitud; aceptar el valor del cliente únicamente como `externalCorrelationId` separado y validado.
- Q: ¿Qué regla de validación debe aplicarse al `X-Correlation-ID` recibido y qué debe ocurrir cuando no cumple esa regla? → A: Aceptar únicamente valores de 1 a 128 caracteres ASCII (`A-Z`, `a-z`, `0-9`, `.`, `_`, `-`); ignorar valores inválidos y conservar el identificador interno.
- Q: ¿Cuál debe ser el contrato mínimo obligatorio de cada evento de auditoría? → A: Incluir `operation`, `stage`, `result`, `durationMs`, `timestamp` y exactamente un identificador de ejecución obligatorio: `requestCorrelationId` para HTTP o `executionCorrelationId` para BACKGROUND; incluir `externalCorrelationId` solo cuando exista un valor de cliente validado; permitir `durationMs` nulo en inicio y exigirlo no negativo en finalización.
- Q: ¿Qué política debe usar la auditoría para impedir que mensajes de excepción, argumentos, headers o valores sensibles lleguen a los logs? → A: Usar una allowlist de campos estructurados; registrar únicamente metadatos definidos y código/tipo de error estable, excluyendo por defecto mensajes, argumentos, retornos, headers y causas completas.
- Q: ¿Qué comportamiento debe adoptar la aplicación cuando el mecanismo de auditoría falla durante una operación de negocio? → A: Aplicar un comportamiento de best effort: aislar y descartar el fallo del registro, emitir como máximo una señal técnica controlada y conservar el resultado o la excepción original; aceptar pérdida de eventos ante fallas graves de infraestructura.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Auditar operaciones críticas (Priority: P1)

Como responsable de operación del backend, quiero identificar cuándo comienzan y terminan las operaciones relevantes de sincronización, valorización y mercado, para investigar ejecuciones y resultados sin cambiar su comportamiento funcional.

**Why this priority**: La trazabilidad de las operaciones críticas es el valor principal de la feature y permite diagnosticar fallos sin instrumentar manualmente cada caso de uso.

**Independent Test**: Ejecutar de forma aislada una operación seleccionada en un entorno de prueba y comprobar que se produce un registro de inicio y otro de finalización con operación, resultado y duración, sin alterar la respuesta ni los cambios de negocio esperados.

**Acceptance Scenarios**:

1. **Given** una operación seleccionada que finaliza correctamente, **When** se ejecuta, **Then** queda registrado un evento de inicio y otro de finalización con `operation`, `stage`, `result`, `timestamp`, el identificador obligatorio del contexto (`requestCorrelationId` en HTTP o `executionCorrelationId` en BACKGROUND) y duración no negativa en la finalización.
2. **Given** una operación seleccionada que falla con una excepción funcional o técnica, **When** se ejecuta, **Then** queda registrado el fallo y la duración no negativa en la finalización, y la misma excepción se propaga al consumidor sin ser reemplazada.
3. **Given** una operación no incluida en el alcance de auditoría, **When** se ejecuta, **Then** no se genera una auditoría duplicada o no solicitada por esta feature.

### User Story 2 - Relacionar eventos de una solicitud (Priority: P1)

Como persona que analiza logs, quiero que los eventos producidos durante una solicitud HTTP compartan un identificador de correlación, para reconstruir el recorrido de una petición entre sus distintas capas.

**Why this priority**: Sin correlación, los registros concurrentes son difíciles de distinguir y la auditoría pierde valor operativo.

**Independent Test**: Enviar una solicitud HTTP y verificar que todos los registros asociados contienen el mismo identificador de correlación, que una solicitud distinta recibe otro identificador y que el contexto no se reutiliza después de finalizar la solicitud.

**Acceptance Scenarios**:

1. **Given** una solicitud HTTP, **When** ingresa al backend, **Then** se genera siempre un identificador interno único de solicitud y se incorpora a los registros de esa solicitud.
2. **Given** una solicitud HTTP con un `X-Correlation-ID` de 1 a 128 caracteres ASCII permitidos (`A-Z`, `a-z`, `0-9`, `.`, `_`, `-`), **When** ingresa al backend, **Then** su valor se conserva únicamente como `externalCorrelationId` separado del identificador interno.
3. **Given** una solicitud HTTP con un `X-Correlation-ID` vacío, demasiado largo o con caracteres no permitidos, **When** ingresa al backend, **Then** el valor se ignora, se conserva el identificador interno y la solicitud no se rechaza por ese motivo.
4. **Given** que una solicitud HTTP finaliza, **When** el procesamiento termina con éxito o error, **Then** el contexto de correlación se limpia y no contamina una solicitud posterior.

### User Story 3 - Proteger la información registrada (Priority: P1)

Como responsable de seguridad, quiero que la auditoría excluya secretos, credenciales y datos personales sensibles, para poder consultar logs sin crear una exposición adicional.

**Why this priority**: Un log útil no debe convertirse en un canal de filtración de autenticadores o información sensible.

**Independent Test**: Ejecutar solicitudes autenticadas y operaciones con datos sensibles de prueba, inspeccionar los registros generados y comprobar que no contienen valores completos de contraseñas, JWT, API keys, credenciales, headers de autorización ni datos personales sensibles.

**Acceptance Scenarios**:

1. **Given** una solicitud con autenticación JWT o API key, **When** se registra la solicitud o una operación auditada, **Then** los autenticadores y headers de autorización no aparecen en claro ni completos.
2. **Given** una operación cuyos argumentos o resultado contienen información sensible, **When** se genera la auditoría, **Then** no se registran los argumentos ni los valores de retorno completos.
3. **Given** que un dato sensible aparece en un contexto de error, **When** se registra el error, **Then** el mensaje y los metadatos se filtran o resumen sin exponer el valor sensible.

### User Story 4 - Mantener la compatibilidad funcional y arquitectónica (Priority: P2)

Como equipo de desarrollo, quiero que la instrumentación sea transversal y no invasiva, para conservar los algoritmos actuales, las métricas existentes, la seguridad y las reglas arquitectónicas del proyecto.

**Why this priority**: La observabilidad solo es aceptable si no cambia los resultados del dominio ni introduce deuda arquitectónica.

**Independent Test**: Ejecutar las pruebas funcionales existentes y las nuevas pruebas de auditoría, y verificar que las reglas arquitectónicas, autenticación, respuestas, excepciones y métricas existentes mantienen su comportamiento.

**Acceptance Scenarios**:

1. **Given** una operación existente, **When** se habilita la auditoría, **Then** sus algoritmos, resultado, excepciones y efectos de negocio permanecen sin cambios.
2. **Given** una operación que produce métricas existentes, **When** se instrumenta, **Then** la feature no crea una segunda métrica equivalente ni modifica la configuración de observabilidad existente sin justificación aprobada.
3. **Given** el conjunto vigente de reglas arquitectónicas, **When** se validan los cambios de la feature, **Then** todas las reglas continúan cumpliéndose y no aparecen dependencias circulares.

### Edge Cases

- Una solicitud llega sin correlation ID, con un valor vacío, excesivamente largo o con formato no permitido: debe generarse o normalizarse un identificador seguro sin bloquear una solicitud válida.
- Una solicitud termina abruptamente o lanza una excepción antes de completar el flujo: el contexto debe limpiarse y el error debe quedar registrado sin modificarlo.
- El mecanismo de logging está degradado o no acepta un evento: la operación de negocio no debe fallar por la imposibilidad de escribir un log.
- Una operación auditada es invocada desde otro flujo que no tiene contexto HTTP: debe conservar trazabilidad mediante un contexto disponible o funcionar sin inventar datos de solicitud.
- Se producen solicitudes concurrentes: los identificadores y metadatos de una solicitud no deben cruzarse con los de otra.
- Un nombre, mensaje o excepción contiene contenido potencialmente sensible: el evento debe aplicar las reglas de exclusión antes de registrarlo.
- Una operación no seleccionada contiene llamadas internas o anidadas: no deben aparecer auditorías duplicadas por interceptaciones redundantes.

## Requirements *(mandatory)*

### Functional Requirements

**Inventario vigente:** esta especificación contiene 26 requisitos funcionales, identificados de forma continua como FR-001 a FR-026.

- **FR-001**: El sistema DEBE identificar y auditar las operaciones relevantes de sincronización, valorización y mercado definidas para esta feature mediante una selección explícita y verificable.
- **FR-002**: Para cada operación seleccionada, el sistema DEBE registrar un evento de inicio y un evento de finalización.
- **FR-003**: Cada evento DEBE incluir `operation`, `stage`, `result`, `durationMs`, `timestamp` y exactamente un identificador de ejecución obligatorio según el contexto: `requestCorrelationId` para HTTP o `executionCorrelationId` para BACKGROUND; `externalCorrelationId` DEBE incluirse únicamente cuando exista un valor recibido y validado del cliente.
- **FR-004**: En un evento de inicio, `durationMs` PUEDE ser `null`; en un evento de finalización, `durationMs` DEBE ser un valor no negativo y el resultado DEBE distinguir éxito de error.
- **FR-005**: Cada evento de auditoría DEBE incluir únicamente metadatos resumidos necesarios para la trazabilidad y no argumentos ni valores de retorno completos.
- **FR-006**: El sistema DEBE conservar la respuesta funcional, los efectos de negocio y las excepciones originales de la operación auditada.
- **FR-007**: El sistema DEBE generar siempre un identificador interno único para cada solicitud HTTP; dicho identificador no puede ser definido ni reemplazado por el cliente.
- **FR-008**: El sistema DEBE aceptar opcionalmente `X-Correlation-ID` como `externalCorrelationId` separado cuando tenga entre 1 y 128 caracteres ASCII de las clases `A-Z`, `a-z`, `0-9`, `.`, `_` o `-`; DEBE ignorar valores inválidos sin usarlos como identidad interna ni rechazar la solicitud por ese motivo.
- **FR-009**: El `requestCorrelationId` interno DEBE acompañar a los registros producidos durante la solicitud y DEBE devolverse en la respuesta HTTP mediante el header `X-Correlation-ID`; el `externalCorrelationId` no se devuelve automáticamente y no aplica a ejecuciones BACKGROUND.
- **FR-010**: El sistema DEBE aislar y limpiar el contexto de correlación al finalizar cada solicitud, tanto en éxito como en error.
- **FR-011**: El sistema DEBE registrar los eventos mediante el mecanismo de logging estructurado vigente, incluyendo el nivel apropiado y campos trazables sin depender de texto no estructurado como única fuente.
- **FR-012**: El sistema DEBE construir eventos únicamente con una allowlist de campos estructurados definidos por la feature; cualquier campo no permitido DEBE quedar excluido por defecto.
- **FR-013**: El sistema DEBE excluir contraseñas, JWT, API keys, headers de autorización, secretos y datos personales sensibles de todos los eventos de auditoría y error.
- **FR-014**: El sistema NO DEBE registrar argumentos completos, valores de retorno completos, mensajes de excepción, headers ni cadenas completas de causas de los métodos auditados.
- **FR-015**: Los errores DEBEN registrar información suficiente para diagnóstico —operación, correlación, tipo o código estable de error y duración— sin exponer secretos ni detalles sensibles.
- **FR-016**: El registro de un evento no DEBE reemplazar, envolver, suprimir ni alterar una excepción funcional o técnica de la operación auditada.
- **FR-017**: Una incidencia del propio mecanismo de logging o auditoría DEBE aislarse con comportamiento best effort, emitir como máximo una señal técnica controlada y NO DEBE impedir ni cambiar el resultado o la excepción original de la operación de negocio; se acepta pérdida de eventos ante fallas graves del proceso, destino o infraestructura de logs.
- **FR-018**: La feature NO DEBE crear tablas, migraciones ni cambios de persistencia para almacenar auditorías.
- **FR-019**: La feature NO DEBE modificar los algoritmos de valorización, scraping, sincronización ni operaciones de mercado.
- **FR-020**: La feature NO DEBE duplicar métricas ya expuestas por la observabilidad existente ni modificar su configuración salvo justificación y aprobación explícitas.
- **FR-021**: La instrumentación DEBE respetar la autenticación y autorización actuales, incluyendo JWT y API keys, sin debilitar sus controles.
- **FR-022**: La solución DEBE mantener la organización por capas, evitar dependencias circulares y cumplir las 21 reglas ArchUnit existentes.
- **FR-023**: La feature DEBE incorporar pruebas automatizadas para escenarios exitosos, errores, correlación, concurrencia, limpieza de contexto y exclusión de información sensible.
- **FR-024**: Las pruebas nuevas DEBEN validar que el comportamiento funcional existente, las excepciones, la seguridad y las métricas no presentan regresiones.
- **FR-025**: La solución DEBE mantener compatibilidad con Java 21, Spring Boot 3.5.16, la seguridad vigente, Actuator, Micrometer Prometheus, PostgreSQL, Flyway y Gradle, sin introducir cambios no justificados en dichas capacidades.
- **FR-026**: La documentación funcional y los mensajes nuevos asociados a esta feature DEBEN seguir las convenciones de idioma del proyecto, manteniendo en inglés únicamente términos técnicos o normativos establecidos.

### Key Entities

- **Evento de auditoría**: Registro resumido de una operación seleccionada, con operación, etapa, resultado, duración, instante y contexto de correlación; no contiene argumentos ni retornos completos.
- **Contexto de correlación**: Identificador interno que relaciona los registros de una ejecución; es `requestCorrelationId` obligatorio y devuelto como `X-Correlation-ID` en HTTP, o `executionCorrelationId` obligatorio y generado por ejecución en BACKGROUND. Se elimina al finalizar su procesamiento.
- **Correlación externa**: Valor opcional recibido en `X-Correlation-ID`, validado y conservado como `externalCorrelationId` separado; no define la identidad interna ni se devuelve automáticamente.
- **Clasificación de datos sensibles**: Reglas que determinan qué valores, campos o contenidos deben excluirse, enmascararse o resumirse antes de generar un evento.

### Scope Boundaries

**Incluido**: auditoría transversal de operaciones seleccionadas de sincronización, valorización y mercado; inicio, finalización, duración, resultado, errores, correlación HTTP, protección de logs y pruebas automatizadas.

**Excluido**: cambios de algoritmos o reglas de negocio; almacenamiento persistente de auditoría; nuevas métricas equivalentes; rediseño de autenticación/autorización; modificación no aprobada de la configuración de observabilidad; commits, push, merge o cambios de rama.

La selección de operaciones será explícita y limitada a los puntos de entrada definidos durante la planificación; no se instrumentarán indiscriminadamente todos los servicios. Dado el modelo de interceptación basado en proxies, solo se auditarán invocaciones que atraviesen el proxy de Spring; las llamadas internas por self-invocation y otros métodos que no atraviesen el proxy quedarán fuera, deberán identificarse durante el diseño y no se presentarán como auditados.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las operaciones seleccionadas produce eventos de inicio y finalización en las pruebas de aceptación, incluyendo resultado y duración.
- **SC-002**: El 100% de los eventos generados durante una misma solicitud de prueba comparte un único identificador interno de solicitud, cada respuesta devuelve ese valor en `X-Correlation-ID`, y dos solicitudes concurrentes no comparten identificadores internos aunque reciban el mismo `externalCorrelationId`.
- **SC-003**: El 100% de las pruebas de seguridad de logging confirma ausencia de contraseñas, JWT, API keys, headers de autorización, secretos y datos personales sensibles en los registros inspeccionados.
- **SC-004**: El 100% de los escenarios de error verificados conserva el tipo, mensaje contractual y propagación de la excepción funcional esperada, sin alteración del resultado de negocio.
- **SC-005**: Las 21 reglas ArchUnit existentes continúan aprobadas y no se detectan dependencias circulares nuevas al validar la feature.
- **SC-006**: Las pruebas automatizadas nuevas cubren como mínimo los cuatro grupos de operaciones definidos —sincronización, valorización, mercado y flujo HTTP— con escenarios de éxito y error.
- **SC-007**: La ejecución completa de las pruebas relevantes no presenta métricas duplicadas respecto del conjunto existente ni requiere nuevas tablas o migraciones.
- **SC-008**: En una comparación reproducible con la misma compilación, configuración, operación, dataset sintético y entorno, después de un período de calentamiento controlado y 30 pares alternados por escenario, el tratamiento con AOP y writer habilitados mantiene un incremento mediano de duración igual o inferior al 10% y un incremento del percentil 95 igual o inferior al 20% frente a la baseline sin aspecto; las muestras, método, versión y entorno se almacenan en `backend/build/diagnostics/auditoria-performance/`, sin atribuir al backend el tiempo externo del destino de logs. Una serie diagnóstica adicional con writer controlado sin emisión se utiliza únicamente para estimar el costo del writer y no para eliminar el costo total de AOP.
- **SC-009**: El equipo puede rastrear una solicitud de extremo a extremo usando su identificador de correlación sin inspeccionar credenciales ni datos sensibles.

## Assumptions

- La infraestructura de logging vigente es el destino operativo de los eventos; esta feature no define un repositorio histórico adicional.
- Las operaciones concretas que se auditarán se identificarán durante la planificación a partir de los casos de uso existentes de sincronización, valorización y mercado, manteniendo una lista explícita y revisable.
- La lista explícita de operaciones auditadas se limitará a puntos de entrada que atraviesen el proxy de Spring; las llamadas internas que no atraviesen el proxy no se considerarán cubiertas salvo que el diseño defina un punto de entrada proxyado equivalente.
- El `externalCorrelationId` enviado por un cliente se considera una etiqueta de trazabilidad, no una credencial ni una prueba de autenticación; la identidad trazable de la solicitud es siempre el identificador interno generado por el backend.
- Las reglas de seguridad existentes siguen siendo la autoridad para autenticar y autorizar solicitudes.
- La auditoría se aplica a solicitudes HTTP y a operaciones seleccionadas invocadas fuera de HTTP cuando exista un contexto de ejecución disponible.
- Ante un fallo del mecanismo de auditoría, la aplicación continuará con el resultado funcional original y podrá perder el evento; no se promete auditoría completa durante fallas graves del proceso, del destino o de la infraestructura de logs.
- La medición de rendimiento se realizará mediante comparaciones emparejadas, dataset sintético fijo, calentamiento, 30 repeticiones por escenario y reporte de mediana y percentil 95; el detalle del entorno será parte de la evidencia de validación.
- Las pruebas usarán datos sintéticos y no consumirán proveedores externos reales, credenciales reales ni Internet.
- Los tests existentes no se modificarán ni eliminarán sin autorización explícita del usuario.
- La feature se especifica para backend y no incluye cambios de frontend ni de contratos públicos salvo que la planificación determine que exponer el correlation ID en la respuesta es necesario y compatible.
