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

## Decision: Seguridad de administración mínima y separada

Permitir sólo los endpoints de salud y métricas definidos en el contrato, denegar el resto y no reutilizar credenciales JWT/API key de negocio para el recolector interno. Cuando el puerto de administración sea distinto, definir una cadena Spring Security específica para el contexto de Actuator y conservar otra para negocio. La red privada Docker es el límite de confianza mínimo elegido, no autenticación individual ni defensa frente a un contenedor comprometido.

**Rationale**: Evita distribuir secretos de negocio al recolector y mantiene separados los dominios de confianza. En Spring Boot 3.5.x, una `SecurityFilterChain` personalizada hace que la auto-configuración de seguridad de Actuator retroceda; por eso deben probarse explícitamente una cadena de administración limitada por endpoints y una cadena de negocio sin cambios. La red no sustituye la denegación de paths ni la política de exposición mínima.

**Alternatives considered**: Hacer públicos los endpoints con autenticación JWT/API key fue rechazado porque expone una superficie operativa innecesaria y acopla el monitoreo a credenciales de negocio. Permitir todos los endpoints Actuator fue rechazado por exposición excesiva. Confiar sólo en la auto-configuración de Actuator fue rechazado porque la `SecurityFilterChain` personalizada existente puede desactivarla.

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
