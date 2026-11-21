# Research: Observabilidad del backend con Actuator y Prometheus

**Referencias primarias consultadas**: [Spring Boot 3.5 Actuator endpoints](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html) y [Spring Boot monitoring over HTTP](https://docs.spring.io/spring-boot/reference/actuator/monitoring.html).

## Decision: Dependencias gestionadas por Spring Boot

Agregar `org.springframework.boot:spring-boot-starter-actuator` y `io.micrometer:micrometer-registry-prometheus` al módulo backend, sin fijar versiones individuales porque Spring Boot 3.5.16 y el dependency management de Gradle deben gobernarlas.

**Rationale**: Mantiene compatibilidad con el stack declarado, reduce divergencias de versiones y habilita health/metrics sin introducir un servidor Prometheus.

**Alternatives considered**: Exponer métricas mediante un controlador propio fue descartado por duplicar funcionalidad, aumentar superficie de seguridad y perder convenciones de Micrometer.

## Decision: Puerto de administración separado

Usar un puerto de administración distinto del puerto de negocio, configurable por entorno y sin publicación Docker. Configurar explícitamente la dirección/interfaz de escucha: no usar loopback porque impediría al recolector interno acceder, no fijar una IP Docker arbitraria y verificar desde el contenedor qué listener queda activo. Exponer sólo health y metrics en ese contexto.

**Rationale**: La separación reduce el riesgo de que una regla pública del API de negocio alcance endpoints operativos y permite que el aislamiento de red sea verificable. No se considera que el puerto separado, la red privada o la ausencia de `ports` garanticen por sí solos aislamiento frente al host.

**Alternatives considered**: Compartir el puerto de negocio fue descartado porque aumenta la superficie pública y obliga a resolver el aislamiento sólo con filtros de aplicación. Un proxy inverso separado fue considerado como alternativa de defensa, pero excede la configuración Docker mínima mientras el backend puede publicar sólo su puerto de negocio.

## Decision: Red privada de Docker con backend de una sola red

El backend, PostgreSQL y el probe recolector autorizado compartirán una única red privada de observabilidad marcada como interna. El backend no se conectará a una segunda red de aplicación accesible por clientes no autorizados; sólo se publicará su puerto de negocio mediante el host. El probe no autorizado se ubicará en una red distinta y el host se probará como origen independiente.

**Rationale**: Conectar un contenedor a dos redes no aísla automáticamente sus puertos: un proceso en cualquiera de esas redes podría intentar alcanzar el puerto de administración. Una sola red privada para el backend evita ese camino y permite probar conectividad por pertenencia a red sin CIDR fijo, pero la pertenencia es sólo el límite de confianza elegido para esta implementación mínima, no autenticación individual del contenedor. El host puede tener rutas especiales al bridge y debe probarse por separado.

**Alternatives considered**: Multi-homing del backend en red pública y privada fue rechazado por el camino de acceso anterior. Fijar una IP de administración o una regla de firewall por CIDR fue rechazado por no ser portable y contradecir la especificación. Un sidecar/proxy que publique sólo negocio conserva aislamiento, pero agrega componentes y complejidad no necesarios para el alcance mínimo. Si el host puede acceder al listener interno, la configuración no se aprobará sólo por carecer de `ports`: deberá aplicarse una política de host/red del entorno y repetirse la prueba.

## ADR-007-01 — Cadena de seguridad efectiva única para el contexto administrativo

**Estado**: Aprobada por el usuario el 21/11/2026. **Contrato relacionado**: `contracts/observability.md`.

La hipótesis original contemplaba dos cadenas independientes: una de administración en el contexto hijo y otra de negocio en el contexto principal. Cuando el puerto de administración es distinto, Spring Boot 3.5.16 crea el contexto hijo, pero registra allí un filtro que obtiene el `springSecurityFilterChain` y el registro servlet del contexto padre. Por tanto, la implementación usará la cadena principal como única cadena efectiva y no creará un `FilterChainProxy` administrativo manual ni usará reflexión sobre clases internas.

La autorización administrativa requerirá dos condiciones verificables: la ruta debe ser exactamente uno de los endpoints aprobados y la solicitud debe llegar al listener/puerto administrativo efectivo. La misma ruta recibida por el puerto de negocio no debe recibir autorización administrativa. Cualquier otra ruta, incluyendo rutas administrativas desconocidas, quedará denegada por la regla por defecto de la cadena principal.

**Rationale**: Esta decisión coincide con el funcionamiento real de Boot y evita intentar sustituir un registro servlet interno. Conserva JWT, API keys y `anyRequest().denyAll()` del negocio, mientras que la separación de puerto/listener y ruta evita que una autorización basada sólo en `/actuator/health` o `/actuator/prometheus` relaje el puerto de negocio. La red privada Docker continúa siendo el límite de confianza mínimo, no autenticación individual ni defensa frente a un contenedor comprometido.

**Alternatives considered**: Una segunda `SecurityFilterChain` o un `FilterChainProxy` local en el contexto hijo fueron descartados porque Boot registra el filtro administrativo delegando al bean del padre. Reemplazar el registro servlet de Boot requeriría infraestructura interna o personalizada y desviaría el plan aprobado. Hacer públicos los endpoints con autenticación JWT/API key fue rechazado porque expone una superficie operativa innecesaria. Permitir todos los endpoints Actuator fue rechazado por exposición excesiva.

### Evidencia de Spring Boot 3.5.16

- `ManagementContextAutoConfiguration.DifferentManagementContextConfiguration.childManagementContextInitializer(...)` crea el inicializador del contexto administrativo cuando el puerto es diferente.
- `ChildManagementContextInitializer.start()` crea el contexto hijo, registra sus beans y lo refresca.
- `ManagementContextFactory.createManagementContext(ApplicationContext)` crea el contexto web hijo y establece el contexto principal como padre.
- `ServletManagementChildContextConfiguration.ServletManagementContextSecurityConfiguration.springSecurityFilterChain(HierarchicalBeanFactory)` obtiene `BeanIds.SPRING_SECURITY_FILTER_CHAIN` del `getParentBeanFactory()`.
- `securityFilterChainRegistration(HierarchicalBeanFactory)` reutiliza desde el padre el `securityFilterChainRegistration` para el registro servlet.

Por lo tanto, una cadena o un `FilterChainProxy` declarado sólo en el child no es suficiente para controlar las solicitudes. La única cadena efectiva debe distinguir administración mediante listener/puerto y ruta, usando APIs públicas de Spring Security. La evidencia de código se conserva en la versión [3.5.16 de `ServletManagementChildContextConfiguration`](https://github.com/spring-projects/spring-boot/blob/v3.5.16/spring-boot-project/spring-boot-actuator-autoconfigure/src/main/java/org/springframework/boot/actuate/autoconfigure/web/servlet/ServletManagementChildContextConfiguration.java).

### Estrategia fundada para el puerto administrativo efectivo

La estrategia propuesta para el matcher es conservar el puerto efectivo en un componente de estado mínimo del contexto principal, actualizado por un listener público de `WebServerInitializedEvent`. El listener debe:

1. observar el evento del servidor administrativo que se propaga desde el contexto hijo al padre;
2. identificarlo mediante la relación pública `event.getApplicationContext().getParent()` con el contexto principal, sin inspeccionar clases internas ni depender de cabeceras;
3. guardar `event.getWebServer().getPort()`, que representa el puerto local realmente asignado y también funciona cuando `management.server.port=0`;
4. dejar el valor sin establecer hasta recibir ese evento: `matchesKnownPort()` debe devolver `false` y no puede autorizar health ni Prometheus, mientras `matches()` sigue identificando el contexto hijo para que la regla posterior `denyAll()` lo rechace;
5. comparar en cada solicitud ese valor con `HttpServletRequest.getLocalPort()` y exigir además la ruta administrativa aprobada.

Esta estrategia utiliza sólo APIs públicas: `WebServerInitializedEvent.getApplicationContext()` y `getWebServer()`, `WebServer.getPort()`, `ApplicationContext.getParent()` y `HttpServletRequest.getLocalPort()`. El evento se publica cuando el servidor está listo y el contexto hijo propaga sus eventos al padre; por ello el listener no necesita obtener ni inicializar directamente el contexto hijo. La solicitud se clasifica por el puerto local del socket, no por `Host`, `X-Forwarded-Port` u otra cabecera controlable por el cliente.

`local.management.port` no se considera una solución para este caso: en `ManagementContextAutoConfiguration.SameManagementContextConfiguration` el alias se agrega sólo para el contexto compartido y mapea `local.management.port` a `local.server.port`. Además, `ManagementServerProperties.getPort()` representa la configuración solicitada, no el puerto asignado cuando el valor es `0`. El evento de servidor es, por tanto, la fuente pública adecuada para el puerto efectivo en el contexto separado.

La implementación deberá mantener el listener y el holder independientes de `ChildManagementContextInitializer` y de cualquier bean del contexto hijo, para evitar referencias circulares. Antes de conocer el puerto efectivo, `matchesKnownPort()` no puede clasificar como autorizable ninguna solicitud, incluidas health y Prometheus; sin embargo, `matches()` debe seguir reconociendo el contexto administrativo hijo para que `denyAll()` rechace sus solicitudes. Las solicitudes del contexto de negocio conservan su clasificación y política habituales. La autorización final seguirá siendo la conjunción de puerto local administrativo y ruta exacta health/Prometheus; el resto continuará sujeto a la denegación por defecto y a las reglas JWT/API key existentes.

**Evidencia pública de código**: [`ManagementContextAutoConfiguration.java` de Spring Boot 3.5.16](https://github.com/spring-projects/spring-boot/blob/v3.5.16/spring-boot-project/spring-boot-actuator-autoconfigure/src/main/java/org/springframework/boot/actuate/autoconfigure/web/server/ManagementContextAutoConfiguration.java) documenta el alias limitado al contexto compartido; [`WebServerInitializedEvent`](https://github.com/spring-projects/spring-boot/blob/v3.5.16/spring-boot-project/spring-boot/src/main/java/org/springframework/boot/web/context/WebServerInitializedEvent.java) expone el contexto y el servidor; [`WebServer`](https://github.com/spring-projects/spring-boot/blob/v3.5.16/spring-boot-project/spring-boot/src/main/java/org/springframework/boot/web/server/WebServer.java) expone `getPort()`; y [`AbstractApplicationContext.publishEvent`](https://github.com/spring-projects/spring-framework/blob/v6.2.14/spring-context/src/main/java/org/springframework/context/support/AbstractApplicationContext.java) propaga eventos al padre.

## Decision: Instrumentación HTTP predeterminada de baja cardinalidad

Para T023 se conserva la instrumentación HTTP predeterminada de Spring Boot/Micrometer, sin `MeterFilter`, `MeterRegistryCustomizer`, convenciones HTTP personalizadas ni etiquetas adicionales. `http.server.requests` usa dimensiones HTTP normalizadas y la URI se basa en la plantilla de ruta cuando está disponible; no añade por defecto query strings, cabeceras, cookies ni payloads. El registro Prometheus la exporta como `http_server_requests_seconds`.

`PrometheusSensitiveTagsTest` valida que las etiquetas HTTP expuestas estén dentro del conjunto esperado y que los valores de query string, cabeceras sensibles, payloads y rutas variables no aparezcan en las métricas. Esta interpretación satisface el objetivo de T023 sin crear componentes bajo `configuration/management/`; la validación de la tarea continúa pendiente hasta ejecutar dichas pruebas.

## Decision: Pruebas black-box en contenedores más pruebas de aplicación

Combinar pruebas MockMvc/context para comportamiento HTTP y seguridad con HTTP real contra el puerto de administración separado, un Compose específico de la feature y probes efímeros para comprobar listener, red, puertos publicados y caminos autorizados/no autorizados desde contenedores y host.

**Rationale**: MockMvc no puede demostrar aislamiento real de interfaces Docker ni acceso del host al bridge; una prueba HTTP real sí puede verificar que el collector llega, que otro contenedor no llega y que el host no obtiene acceso al listener. La topología también debe comprobar la conexión backend→PostgreSQL sin redes adicionales.

**Alternatives considered**: Sólo pruebas MockMvc fueron descartadas por no probar publicación de puertos, interfaces o rutas del host. Modificar los Compose E2E/performance fue descartado para evitar regresiones y acoplamiento entre entornos.

## Resolved Unknowns

- No se fija un CIDR ni una IP: la pertenencia a la red privada y la inspección de publicación Docker son la base de la prueba.
- La pertenencia a la red privada es el límite de confianza elegido; no es autenticación individual del contenedor y un contenedor comprometido dentro de ella puede intentar consultar health/metrics.
- Se inspeccionan listeners, interfaces, redes, rutas y puertos publicados; no se infiere aislamiento sólo por el archivo Compose.
- No se instala Prometheus: el collector es un probe de validación compatible con el formato expuesto.
- PostgreSQL comparte la única red privada de validación para evitar una segunda red que haga multi-homed al backend; su puerto no se publica.
- No se requiere un dashboard ni cambios frontend.
- Los nombres definitivos de variables de entorno, el puerto numérico de administración y los detalles exactos de Compose se decidirán en implementación siguiendo este diseño.
- El puerto administrativo efectivo se obtendrá mediante `WebServerInitializedEvent.getWebServer().getPort()` en el listener público del contexto principal. Hasta recibir el evento, `matchesKnownPort()` debe devolver `false` y `matches()` debe conservar la clasificación del contexto administrativo para su denegación por defecto. No se usará `local.management.port` como fuente para el contexto separado ni se confiará en cabeceras HTTP. La prueba debe demostrar que el mismo path no se autoriza en el puerto de negocio y que el mecanismo funciona con `management.server.port=0`.
