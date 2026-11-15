# Feature Specification: Observabilidad del backend con salud y métricas

**Feature Branch**: `feature/actuator-prometheus`

**Created**: 2026-11-15

**Status**: Draft

**Input**: User description: "Incorporar observabilidad al backend de Devchester United mediante Spring Boot Actuator y métricas compatibles con Prometheus, permitiendo verificar salud y obtener métricas operativas sin comprometer la seguridad existente."

**Contexto confirmado**: El backend se ejecutará en contenedores Docker. El alcance incluye una configuración Docker mínima para validar el aislamiento de observabilidad; los nombres de servicios, puertos concretos y detalles de orquestación quedan para la planificación técnica.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Verificar la salud del backend (Priority: P1)

Como responsable de operación quiero consultar un estado básico de salud del backend para saber si el servicio está disponible y puede ser monitoreado sin acceder a datos de negocio.

**Why this priority**: La detección temprana de indisponibilidad es el valor mínimo necesario para operar el backend con confianza.

**Independent Test**: Con el backend levantado en contenedores, consultar el recurso de salud desde un contenedor conectado a la red privada autorizada y comprobar una respuesta de estado válida, sin credenciales, secretos ni datos sensibles.

**Acceptance Scenarios**:

1. **Given** el backend está disponible y el origen está autorizado, **When** se consulta el recurso de salud, **Then** se obtiene un estado de salud básico que permite distinguir disponibilidad de indisponibilidad.
2. **Given** una dependencia relevante para la salud no está disponible, **When** se consulta el recurso de salud, **Then** el resultado refleja una condición no saludable sin revelar credenciales, cadenas de conexión ni detalles internos innecesarios.
3. **Given** el backend mantiene sus endpoints de negocio operativos, **When** se consulta la salud, **Then** la consulta no modifica datos ni altera el comportamiento de negocio.
4. **Given** el contenedor autorizado está conectado a la red privada de observabilidad, **When** consulta la salud, **Then** obtiene el estado permitido sin requerir que el puerto de administración esté publicado hacia redes externas.

---

### User Story 2 - Recolectar métricas operativas (Priority: P1)

Como responsable de operación quiero recolectar métricas de ejecución, memoria y solicitudes HTTP en un formato compatible con mi sistema de monitoreo para detectar degradaciones y tendencias.

**Why this priority**: Las métricas complementan el estado puntual de salud y permiten investigar rendimiento, capacidad y errores.

**Independent Test**: Desde un contenedor autorizado conectado a la red privada, generar solicitudes controladas y consultar el recurso de métricas; comprobar que se observan métricas de JVM, memoria y solicitudes HTTP, sin datos de negocio ni secretos.

**Acceptance Scenarios**:

1. **Given** el backend ha procesado solicitudes, **When** el recolector autorizado consulta las métricas, **Then** recibe una representación compatible con Prometheus que incluye métricas de JVM, memoria y solicitudes HTTP.
2. **Given** se ejecutan solicitudes exitosas y fallidas controladas, **When** se vuelven a consultar las métricas, **Then** los contadores o mediciones reflejan la actividad sin exponer payloads, tokens, API keys, cabeceras sensibles ni valores de credenciales.
3. **Given** no se ha instalado ni desplegado un servidor Prometheus, **When** un recolector compatible solicita las métricas, **Then** puede obtenerlas directamente del backend sin que esta funcionalidad incluya dashboards o un servidor adicional.
4. **Given** un contenedor recolector autorizado comparte la red privada con el backend, **When** consulta las métricas, **Then** la recolección funciona sin publicar el puerto de administración hacia redes externas.

---

### User Story 3 - Mantener la frontera de seguridad (Priority: P1)

Como responsable de seguridad quiero que la observabilidad sea accesible sólo desde una red interna autorizada y que no permita eludir las protecciones actuales del backend.

**Why this priority**: Salud y métricas pueden revelar información operativa; su exposición pública o uso como bypass aumentaría el riesgo del sistema.

**Independent Test**: Ejecutar pruebas desde un contenedor autorizado conectado a la red privada y desde un origen no autorizado fuera de ella, con credenciales JWT/API key válidas e inválidas; comprobar respuestas, ausencia de publicación externa y ausencia de acceso indirecto a endpoints de negocio.

**Acceptance Scenarios**:

1. **Given** una solicitud proviene de la red interna autorizada, **When** consulta observabilidad, **Then** sólo obtiene la información permitida por la política de seguridad.
2. **Given** una solicitud proviene de Internet u otra red no autorizada, **When** consulta salud o métricas, **Then** el acceso es rechazado y no se entrega información operativa.
3. **Given** una solicitud intenta usar un endpoint de observabilidad para evitar JWT, API keys, roles u otras restricciones existentes, **When** intenta acceder a un recurso de negocio, **Then** las restricciones existentes continúan aplicándose y no se concede acceso adicional.
4. **Given** una solicitud tiene credenciales inválidas, ausentes o insuficientes para un recurso protegido, **When** consulta observabilidad o negocio según corresponda, **Then** se mantiene el rechazo definido por la política actual, sin mensajes que revelen detalles internos.
5. **Given** se consulta cualquier recurso de observabilidad, **When** se inspecciona la respuesta, **Then** no contiene secretos, credenciales, tokens, API keys, información personal ni detalles internos innecesarios.
6. **Given** el puerto de administración no está publicado hacia redes externas, **When** un origen fuera de la red privada intenta conectarse directamente, **Then** no puede establecer acceso al recurso de observabilidad.
7. **Given** la red privada o la autorización del contenedor está incompleta, **When** un origen intenta consultar observabilidad, **Then** el acceso es denegado por defecto.

---

### User Story 4 - Preservar el comportamiento del producto (Priority: P2)

Como usuario de la API quiero que las funcionalidades de negocio y los mecanismos de autenticación existentes continúen funcionando sin cambios después de habilitar la observabilidad.

**Why this priority**: La observabilidad debe ser una capacidad transversal y no introducir regresiones funcionales o de seguridad.

**Independent Test**: Ejecutar la regresión existente de autenticación, autorización y casos de uso principales después de habilitar la funcionalidad; comparar resultados con el comportamiento previo.

**Acceptance Scenarios**:

1. **Given** un consumidor válido usa JWT o API key conforme al contrato actual, **When** invoca operaciones de negocio, **Then** recibe el mismo resultado autorizado que antes de la incorporación de observabilidad.
2. **Given** un consumidor no autorizado intenta una operación de negocio, **When** la invoca después de habilitar observabilidad, **Then** continúa recibiendo el rechazo correspondiente.
3. **Given** el backend recibe tráfico normal de negocio, **When** se consultan salud o métricas, **Then** no se modifican respuestas, permisos, datos ni flujos de negocio.

### Edge Cases

- La red autorizada no está definida o está mal configurada: el acceso a observabilidad debe quedar denegado por defecto, sin exposición pública.
- El backend se ejecuta sin un contenedor recolector autorizado conectado a la red privada: las solicitudes deben seguir rechazándose desde orígenes no autorizados.
- Una configuración de contenedores intenta publicar accidentalmente el puerto de administración: la validación debe detectarlo como una violación de seguridad.
- El recurso de métricas recibe una solicitud sin tráfico previo: debe responder de forma segura y consistente, aunque algunos contadores estén en cero.
- El backend está parcialmente degradado: la información de salud debe indicar la condición sin filtrar la causa interna sensible.
- Una métrica o etiqueta podría contener una URL, identificador, query string o cabecera con información sensible: debe omitirse, sanitizarse o agregarse antes de exponerse.
- Un recolector realiza solicitudes repetidas o concurrentes: la observabilidad no debe bloquear ni alterar las operaciones de negocio dentro de los límites operativos acordados.
- Se envían credenciales en una solicitud de observabilidad: no deben aparecer completas en respuestas, métricas ni registros asociados a la funcionalidad.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El backend DEBE proporcionar una consulta de salud que permita determinar de forma básica si el servicio está disponible.
- **FR-002**: La consulta de salud DEBE comunicar de manera segura las condiciones no saludables de dependencias relevantes, sin revelar secretos ni detalles internos innecesarios.
- **FR-003**: El backend DEBE proporcionar métricas operativas compatibles con Prometheus para su recolección por un sistema externo autorizado.
- **FR-004**: Las métricas DEBEN incluir información agregada sobre ejecución de JVM, memoria y solicitudes HTTP.
- **FR-005**: Las métricas HTTP NO DEBEN incluir payloads, tokens, API keys, credenciales, información personal ni valores sensibles en etiquetas, nombres o valores expuestos.
- **FR-006**: El acceso a las métricas compatibles con Prometheus DEBE estar restringido exclusivamente a la red interna autorizada y DEBE rechazar solicitudes provenientes de redes públicas o no autorizadas.
- **FR-006A**: La ejecución en contenedores DEBE incluir una red privada de Docker para que un contenedor autorizado pueda consultar observabilidad sin convertir esos recursos en accesibles desde redes externas.
- **FR-006B**: El puerto utilizado para administración y observabilidad NO DEBE publicarse ni exponerse hacia redes externas; su accesibilidad DEBE limitarse a la red privada autorizada.
- **FR-006C**: La configuración Docker mínima DEBE permitir probar el acceso desde al menos un contenedor autorizado y demostrar el rechazo desde un origen no autorizado, sin fijar direcciones IP ni rangos CIDR arbitrarios en esta especificación.
- **FR-006D**: Si la red privada, la autorización del contenedor o cualquier configuración necesaria está incompleta, la observabilidad DEBE permanecer denegada por defecto.
- **FR-007**: El acceso a salud y métricas DEBE aplicar una política explícita de exposición mínima, sin publicar endpoints de administración adicionales que no sean necesarios para este alcance.
- **FR-008**: La incorporación de observabilidad NO DEBE modificar ni permitir eludir la autenticación JWT, la autenticación mediante API keys, la autorización, los roles ni las restricciones de Spring Security existentes.
- **FR-009**: La incorporación de observabilidad NO DEBE conceder acceso indirecto a endpoints, datos o acciones de negocio a un consumidor no autorizado.
- **FR-010**: Las funcionalidades de negocio existentes DEBEN conservar sus contratos y comportamiento, salvo los cambios de observabilidad explícitamente definidos en esta especificación.
- **FR-011**: La solución DEBE incluir pruebas verificables de salud, disponibilidad restringida de métricas, acceso desde el contenedor autorizado, rechazo desde un origen no autorizado, ausencia de datos sensibles y conservación de las restricciones de seguridad.
- **FR-012**: El alcance DEBE limitarse al backend y a la configuración Docker mínima necesaria para validar el aislamiento; NO DEBE incluir dashboards, instalación o despliegue de un servidor Prometheus, cambios del frontend ni modificaciones de la lógica de negocio.

### Key Entities

- **Estado de salud**: Resultado resumido que representa la disponibilidad operativa del backend y, cuando corresponda, la condición segura de dependencias relevantes.
- **Métrica operativa**: Medición agregada de ejecución, memoria o solicitudes HTTP, identificada con etiquetas no sensibles y destinada a análisis de monitoreo.
- **Red interna autorizada**: Origen de red definido por la infraestructura operativa desde el cual se permite recolectar observabilidad; cualquier otro origen se considera no autorizado.
- **Red privada de Docker**: Red de contenedores que permite la comunicación entre el backend y los consumidores autorizados sin publicar el puerto de administración hacia redes externas.
- **Contenedor autorizado**: Contenedor conectado a la red privada y habilitado por la política de observabilidad para consultar salud o métricas.
- **Origen no autorizado**: Contenedor, proceso o cliente que no pertenece a la red privada autorizada o no cumple la política de acceso.
- **Política de exposición**: Conjunto de reglas que determina qué información de observabilidad se publica, quién puede consultarla y qué datos deben excluirse o sanitizarse.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En pruebas de aceptación, el 100% de las consultas de salud desde un origen autorizado devuelven un estado interpretable de disponibilidad o degradación, sin secretos ni datos de negocio.
- **SC-002**: En pruebas controladas con solicitudes exitosas y fallidas, el 100% de las métricas verificadas de JVM, memoria y HTTP están disponibles en formato compatible con el recolector objetivo y reflejan la actividad generada.
- **SC-003**: En pruebas de aislamiento, el 100% de las solicitudes de salud y métricas desde un contenedor autorizado conectado a la red privada son atendibles, mientras que el 100% de los intentos equivalentes desde un origen no autorizado son rechazados.
- **SC-004**: La configuración Docker validada no publica el puerto de administración hacia redes externas y cualquier configuración incompleta mantiene el 100% de los accesos de observabilidad en estado denegado por defecto.
- **SC-005**: El 100% de los casos de regresión de JWT, API keys, autorización y endpoints de negocio existentes conserva el resultado esperado antes de habilitar observabilidad.
- **SC-006**: En una revisión de respuestas, nombres, etiquetas y valores de observabilidad no se encuentran credenciales, secretos, tokens, información personal ni payloads de negocio.
- **SC-007**: La observabilidad no introduce cambios en las funcionalidades de negocio ni requiere dashboards o un servidor Prometheus para que un recolector autorizado consulte las métricas.

## Assumptions

- La red privada de Docker y la pertenencia del contenedor autorizado serán definidas durante la planificación sin fijar direcciones IP ni rangos CIDR arbitrarios en esta especificación.
- La configuración Docker mínima servirá para ejecutar el backend y demostrar el aislamiento; los detalles de Compose, nombres de servicios, puertos y reglas concretas quedan para la planificación técnica.
- El puerto de administración no tendrá publicación hacia redes externas; la comunicación autorizada ocurrirá dentro de la red privada de Docker.
- Los endpoints de salud y métricas se expondrán únicamente en el nivel mínimo necesario para este alcance; no se habilitarán endpoints administrativos adicionales.
- La autenticación, autorización, JWT, API keys, roles, filtros y restricciones actuales de Spring Security son la fuente de verdad y se reutilizan sin relajarlos.
- Las métricas serán agregadas y de baja cardinalidad; no se registrarán valores de usuario, tokens, URLs con query strings ni cuerpos de solicitudes o respuestas.
- La disponibilidad de salud se interpreta como estado operativo del backend, no como garantía de que cada funcionalidad de negocio externa esté disponible.
- No se incluye almacenamiento histórico de métricas, dashboards, alertas ni instalación o despliegue de Prometheus.
- La validación incluirá escenarios positivos y negativos desde un contenedor autorizado y un origen no autorizado, además de regresión de autenticación y negocio.

## Aclaraciones para la planificación técnica

- Durante la planificación técnica debe definirse el mecanismo concreto para conectar el contenedor recolector autorizado a la red privada de Docker y la estrategia de pruebas desde un origen no autorizado. No es necesario fijar direcciones IP ni rangos CIDR en la especificación; cualquier configuración incompleta debe conservar el rechazo por defecto. Esta definición no bloquea el inicio de `speckit-plan`.
