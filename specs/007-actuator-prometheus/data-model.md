# Modelo de datos: Observabilidad del backend

La funcionalidad no agrega datos persistentes ni modifica el modelo de dominio. Las siguientes entidades son conceptos operativos necesarios para diseñar y probar el aislamiento.

## Estado de salud

- **Propósito**: representar si el backend está disponible o degradado.
- **Origen**: indicadores del proceso y dependencias que el plan técnico considere relevantes.
- **Exposición**: estado resumido; los detalles de componentes, credenciales, URLs internas y cadenas de conexión no se muestran por defecto.
- **Persistencia**: ninguna.
- **Validación**: debe distinguir una aplicación disponible de una condición no saludable y no debe modificar datos.

## Métrica operativa

- **Propósito**: representar una medición agregada para monitoreo.
- **Categorías mínimas**: JVM, memoria y solicitudes HTTP.
- **Atributos**: nombre, tipo, valor y etiquetas de baja cardinalidad.
- **Exclusiones**: payloads, tokens, API keys, credenciales, información personal, query strings y URLs o headers sensibles.
- **Persistencia**: ninguna; la recolección es una lectura del estado actual del proceso.

## Red privada de observabilidad

- **Propósito**: limitar el camino de red entre backend y recolector autorizado.
- **Miembros**: backend, PostgreSQL y probe recolector autorizado en la configuración mínima de validación.
- **No miembros**: origen no autorizado y clientes externos.
- **Límite de confianza**: la pertenencia a la red es el mecanismo de confianza elegido para esta implementación mínima; no es autenticación individual del contenedor. Un contenedor comprometido dentro de la red puede intentar consultar health/metrics.
- **Regla**: pertenecer a la red no habilita paths distintos de health/metrics; todo path no definido permanece denegado.
- **Aislamiento efectivo**: debe comprobarse además la interfaz de escucha, la IP efectiva del contenedor, las rutas del bridge, los puertos publicados y el acceso desde el host. La red o la ausencia de `ports` no bastan por sí solas.
- **Persistencia**: configuración de ejecución Docker, no datos de negocio.

## Listener de administración

- **Propósito**: representar el socket HTTP separado donde se exponen health/metrics.
- **Relación**: pertenece al backend y se ejecuta en un puerto distinto al de negocio cuando el perfil Docker está activo.
- **Interfaz**: no debe quedar limitado a loopback si el recolector está en otro contenedor; tampoco debe quedar accesible por una interfaz/red no autorizada. La dirección efectiva se configura sin IP Docker fija y se verifica en runtime.
- **Publicación**: no tiene mapeo al host ni exposición a una segunda red de aplicación.
- **Verificación**: se prueban conexiones desde el collector, otra red y el host; el host no puede alcanzar el listener en una configuración aprobada.

## Configuración de seguridad

- **Contexto de administración**: cadena Spring Security específica para endpoints Actuator mínimos, con denegación de cualquier path restante.
- **Contexto de negocio**: cadena existente con JWT, API keys, autorización y denegación por defecto sin cambios.
- **Regla de separación**: ninguna cadena puede convertir health/metrics en acceso a `/api/**`, ni permitir que una credencial de negocio relaje el límite de red.

## Contenedor recolector autorizado

- **Propósito**: representar el consumidor interno que valida o recolecta métricas.
- **Requisitos**: estar conectado a la red privada y consultar sólo los contratos definidos.
- **Credenciales**: no reutiliza JWT ni API keys de negocio en la prueba mínima.
- **Resultado esperado**: health/metrics accesibles; puerto de administración no publicado al host.

## Origen no autorizado

- **Propósito**: representar un proceso o contenedor fuera de la red privada.
- **Resultado esperado**: no puede resolver/conectar el endpoint de administración y no obtiene información operativa.

## Relaciones

```text
Red privada de observabilidad
├── Backend
├── PostgreSQL
└── Contenedor recolector autorizado

Origen no autorizado ──X──> Backend:administración
Cliente externo ────────> Backend:negocio publicado
```

## Estados de configuración y validación

| Estado | Resultado esperado | Tipo de verificación |
|---|---|---|
| Listener de administración no arranca por puerto/dirección inválidos | Fallo de arranque | Logs/estado del contenedor y prueba de startup |
| Puerto de administración publicado al host | Configuración insegura | Fallo de validación, aunque health responda |
| Backend conectado a una segunda red de aplicación | Configuración insegura | Fallo de validación por posible alcance del listener |
| Probe sin pertenencia a la red privada | Rechazo o fallo de conectividad | HTTP real desde contenedor no autorizado |
| Host alcanza la IP/puerto de administración | Configuración insegura | Fallo de validación; no se acepta como aislamiento |
| Cadena Actuator ausente o con paths amplios | Rechazo de revisión | Prueba de contexto/seguridad y revisión de superficie |
| Cadena de negocio modificada o filtro API key aplicado a Actuator | Regresión de seguridad | Regresión JWT/API key y prueba de no bypass |
| PostgreSQL no accesible desde backend en la red privada | Fallo de arranque o health no saludable | Logs, health y prueba de conectividad de servicio |
| Etiquetas HTTP con query string, token o alta cardinalidad | Fallo de validación de métricas | Inspección de `/actuator/prometheus` con tráfico sensible |
