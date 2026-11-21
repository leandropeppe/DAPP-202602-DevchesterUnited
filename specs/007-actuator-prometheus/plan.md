# Implementation Plan: Observabilidad del backend con Actuator y Prometheus

**Branch**: `feature/actuator-prometheus` | **Date**: 2026-11-15 | **Spec**: [spec.md](./spec.md)

## Summary

Incorporar salud operativa y métricas JVM, memoria y HTTP en formato compatible con Prometheus, manteniendo intactas las APIs de negocio y sus mecanismos JWT/API key. La solución usará un contexto de administración separado del puerto de negocio y una topología Docker mínima en la que el backend, PostgreSQL y un contenedor recolector autorizado comparten una red privada interna. Sólo el puerto de negocio se publica mediante el host; el puerto de administración no tendrá publicación Docker. La validación tratará por separado los accesos desde el host a la IP del contenedor, porque una red privada o la ausencia de `ports` no garantizan por sí solas aislamiento absoluto.

La primera implementación debe ser mínima: Actuator, el registro Micrometer para Prometheus, dos endpoints de observabilidad y una configuración Docker de validación. No se instalará Prometheus, no se crearán dashboards y no se modificarán frontend, lógica de negocio ni los Compose existentes de E2E/performance.

## Technical Context

**Language/Version**: Java 21; Spring Boot 3.5.16; Gradle existente.

**Primary Dependencies**: `spring-boot-starter-actuator` y `micrometer-registry-prometheus`, con versiones gestionadas por Spring Boot; se conservarán Spring Security, el resource server OAuth2/JWT y el filtro de API keys existentes.

**Storage**: PostgreSQL existente; la observabilidad no agrega entidades persistentes ni migraciones.

**Testing**: JUnit 5, Spring Boot Test, Spring Security Test, MockMvc, Testcontainers y pruebas black-box con Docker Compose/curl desde contenedores autorizados y no autorizados.

**Target Platform**: Backend Linux ejecutado en contenedor Docker; el diseño debe ser reproducible sin depender de IPs o rangos CIDR fijos.

**Project Type**: Servicio web REST Spring Boot con un puerto de negocio y un puerto de administración interno.

**Performance Goals**: La consulta de salud y la recolección de métricas deben ser lecturas de bajo costo y no alterar las operaciones de negocio; la validación debe comprobar que solicitudes repetidas no bloquean el servicio dentro de los límites del entorno de prueba.

**Constraints**: Exposición mínima; sin publicación Docker del puerto de administración; listener de administración limitado a la interfaz/red interna elegida y verificado contra accesos desde host y otras redes; rechazo por defecto ante red o configuración incompleta; sin secretos, payloads ni etiquetas HTTP sensibles; sin bypass de JWT, API keys o autorización; no modificar Compose E2E/performance.

**Scale/Scope**: Una aplicación backend, un contenedor recolector de prueba y un contenedor de origen no autorizado. Sólo salud, métricas JVM/memoria/HTTP, Docker mínimo y pruebas asociadas.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Capas y dominio**: PASS. La funcionalidad es infraestructura transversal; no agrega lógica de negocio, entidades de dominio ni acceso nuevo a repositorios.
- **Seguridad y secretos**: PASS condicionado a una única cadena efectiva reutilizada por el contexto principal y el contexto administrativo hijo. Las solicitudes administrativas se distinguirán por el listener/puerto administrativo efectivo y por una ruta aprobada; las demás solicitudes quedarán sujetas a la política de negocio y `anyRequest().denyAll()`. Las respuestas de observabilidad se limitan a información agregada y no contienen secretos.
- **OpenAPI**: PASS / N/A. Los endpoints de administración no son APIs de negocio; el contrato operativo se documenta en `contracts/observability.md`, sin ampliar Swagger público.
- **Testing**: PASS. Se contemplan pruebas MockMvc, integración de contexto y validación black-box en contenedores, incluyendo casos felices, negativos y de configuración incompleta.
- **Docker y aislamiento**: PASS condicionado a validar listener, redes efectivas, publicación de puertos y acceso desde host. La red privada es un límite de confianza de esta implementación mínima, no autenticación individual del contenedor. No se modifican los Compose existentes.
- **No regresión**: PASS. Se mantiene el comportamiento de negocio y se ejecutan compilación, pruebas existentes y verificaciones de seguridad.
- **Complejidad**: PASS. La separación de administración se usa para reducir exposición; no se justifican violaciones constitucionales.

## Design Decisions

### Dependencias y exposición mínima

1. Agregar las dos dependencias gestionadas por el BOM de Spring Boot: Actuator y el registry Prometheus de Micrometer.
2. Habilitar sólo salud y métricas para este alcance. No exponer env, beans, configprops, mappings, loggers, threaddump ni otros endpoints administrativos.
3. Usar `/actuator/health` y `/actuator/prometheus` como contratos mínimos, con detalles de salud no visibles por defecto y métricas HTTP sin query strings, headers, payloads, tokens ni identificadores de alta cardinalidad.

### Separación de puertos y seguridad de aplicación

1. Ejecutar el servidor de administración en un puerto configurable y distinto del puerto de negocio. El número concreto y sus variables de entorno se definirán en la implementación siguiendo el contrato de `contracts/observability.md`.
2. Configurar la interfaz de escucha de administración de forma explícita: no usar `127.0.0.1` porque impediría al recolector acceder; no fijar una IP Docker arbitraria; seleccionar una dirección/interfaz interna configurable y comprobar qué interfaces quedan realmente escuchando dentro del contenedor.
3. Configurar el puerto de administración sólo para health/metrics y rechazar cualquier otro path. La ausencia de `ports` se tratará como una condición necesaria, pero no suficiente: también se inspeccionará la IP del contenedor, la red `internal`, las rutas y la posibilidad real de conexión desde el host.
4. En Spring Boot 3.5.x, cuando `management.server.port` sea distinto de `server.port`, verificar el contexto de administración separado y reutilizar el `springSecurityFilterChain` efectivo del contexto principal. No crear un `FilterChainProxy` administrativo manual, no usar reflexión sobre clases internas y no asumir que una `SecurityFilterChain` declarada en el child reemplaza el registro servlet de Boot (ADR-007-01 en `research.md`).
5. Definir en la cadena principal una clasificación explícita de solicitudes administrativas que requiera simultáneamente la ruta aprobada (`/actuator/health` o `/actuator/prometheus`) y el listener/puerto administrativo efectivo. Una solicitud al mismo path desde el puerto de negocio no debe recibir autorización administrativa. Todas las demás solicitudes administrativas deben quedar denegadas por defecto.
6. Preservar en esa misma cadena las reglas actuales de negocio, el resource server JWT, el filtro API key y `anyRequest().denyAll()`. Verificar con pruebas en ambos puertos que la clasificación administrativa no intercepta ni relaja rutas `/api/**` y que JWT/API keys no son necesarios ni suficientes para convertir el listener de administración en un bypass.
7. No reutilizar JWT ni API keys de negocio como credenciales de recolección. La pertenencia a la red privada es el límite de confianza elegido para la implementación mínima; no autentica individualmente al contenedor ni protege frente a un contenedor autorizado comprometido.
8. No usar un endpoint de observabilidad para acceder al puerto de negocio, a datos, a tokens o a operaciones de la aplicación.

### Topología Docker propuesta

La configuración mínima nueva se ubicará bajo `backend/docker/observability-compose.yml` y construirá el backend desde `backend/`. PostgreSQL se incluirá como servicio de validación en la misma red privada, sin publicar su puerto. Esto permite que el backend mantenga su conectividad de base de datos sin agregar una segunda red de aplicación que exponga también el puerto de administración.

```text
cliente externo ──(sólo puerto de negocio publicado)──> backend
                                                        │
                         red privada Docker             │
                 collector-probe autorizado ───────────┘

unauthorized-probe ──(red distinta, sin acceso al backend)──X
```

- El backend, PostgreSQL y el probe autorizado estarán en una única red Docker privada marcada como interna para el escenario de validación. La pertenencia a esta red es el límite de confianza, no una autenticación por identidad.
- El puerto de negocio podrá publicarse para validar regresión, pero el puerto de administración no aparecerá en `ports`, `expose` hacia otra red ni en ningún mecanismo equivalente de publicación al host.
- La escucha de administración se comprobará desde dentro del contenedor: dirección enlazada, socket/listener y puerto efectivo. La implementación no puede concluir que está aislada sólo porque el Compose no tenga un mapeo.
- El probe autorizado consultará por el nombre de servicio interno y no será un servidor Prometheus; será una herramienta de validación efímera.
- El probe no autorizado se ejecutará en una red distinta y probará resolución/conectividad y una conexión real al puerto de administración sin depender de un CIDR fijo.
- El host Docker intentará conectarse a la IP de contenedor obtenida dinámicamente. Si el host logra acceder al puerto de administración, la configuración falla la validación; la ausencia de publicación no se considerará suficiente.
- No se conectará el backend a una segunda red de aplicación sólo para facilitar la prueba: hacerlo permitiría que un contenedor de esa red alcance cualquier puerto que escuche el backend. Si el entorno requiere otra conectividad, deberá resolverse sin ampliar el límite de confianza y quedar cubierta por una prueba efectiva.

### Caminos de acceso considerados

| Camino | Tratamiento | Resultado esperado |
|---|---|---|
| Cliente externo al puerto de negocio publicado | Permitido sólo según la política existente de negocio | El comportamiento JWT/API key no cambia |
| Recolector en la red privada | Permitido para health/metrics mínimos | Respuesta compatible con el contrato |
| Contenedor en otra red Docker | No enrutable al backend de administración | Rechazo o ausencia de conectividad |
| Host intentando el puerto de administración por IP Docker | No es un camino autorizado | Fallo de validación si conecta; se requiere política adicional del entorno |
| URL de administración con path distinto a health/metrics | Denegado | No se habilitan endpoints adicionales |
| Configuración incompleta de red, puerto, listener o autorización | Fallo de arranque, rechazo de acceso o fallo de validación según la matriz de configuración | Nunca se degrada a exposición pública |

### Pruebas y verificación

- MockMvc/context: comprobar la única cadena efectiva, la clasificación conjunta por puerto/listener y ruta, contenido mínimo, ausencia de información sensible, denegación de paths no permitidos y conservación de JWT/API key en endpoints de negocio. Estas pruebas no demuestran aislamiento Docker ni sustituyen las pruebas HTTP reales entre puertos.
- HTTP real autorizado: levantar el Compose nuevo y ejecutar `curl`/`wget` desde el contenedor probe conectado a la red privada contra el listener de administración; generar tráfico de negocio controlado y verificar métricas HTTP.
- HTTP real no autorizado: ejecutar un probe en otra red, comprobar resolución y conexión reales al listener de administración, y esperar fallo de conexión o rechazo sin cuerpo operativo.
- Host Docker: inspeccionar `docker compose port`, `docker port`, `docker inspect` de redes/listeners y probar la IP de contenedor obtenida dinámicamente desde el host; cualquier acceso exitoso al puerto de administración falla la validación.
- PostgreSQL: comprobar que backend y PostgreSQL se resuelven y conectan dentro de la única red privada prevista, sin publicar el puerto de PostgreSQL ni crear una red adicional accesible por el probe no autorizado.
- Prueba de configuración incompleta: ejecutar cada caso de la matriz definida en `contracts/observability.md` y comprobar fallo de arranque, rechazo o fallo de validación según corresponda.
- Regresión: ejecutar `backend/gradlew test`, compilación/packaging y el conjunto existente de seguridad/negocio; no modificar tests existentes.
- Calidad: conservar la ejecución de SonarCloud exclusivamente en el flujo configurado para `main`; la rama de la feature no introduce un gate Sonar adicional.

## Phase 0: Research Output

Las decisiones y alternativas se documentan en [research.md](./research.md). No quedan decisiones de producto bloqueantes; las decisiones de nombres exactos de variables, puerto concreto y detalles Compose quedan como trabajo técnico posterior, no como aclaraciones de alcance.

## Phase 1: Design Artifacts

- [data-model.md](./data-model.md): entidades operativas no persistentes y reglas de exposición.
- [contracts/observability.md](./contracts/observability.md): contrato de health/metrics y matriz de acceso.
- [quickstart.md](./quickstart.md): validación reproducible en Docker, pruebas autorizadas/no autorizadas y regresión.

## Project Structure

### Documentation (this feature)

```text
specs/007-actuator-prometheus/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    └── observability.md
```

### Source Code (repository root)

```text
backend/
├── build.gradle
├── src/main/java/com/devchesterunited/devchesterunitedv2/configuration/security/
│   ├── SecurityConfig.java
│   └── ApiKeyAuthenticationFilter.java
├── src/main/resources/application.properties
├── src/test/java/com/devchesterunited/devchesterunitedv2/
│   ├── integration/web/
│   ├── integration/auth/
│   └── e2e/
├── Dockerfile                         # propuesto, no creado en esta fase
├── .dockerignore                      # propuesto si el contexto de build lo requiere
└── docker/observability-compose.yml   # propuesto, no creado en esta fase
```

**Structure Decision**: Se mantiene el proyecto backend Gradle existente y sus paquetes `configuration`, `controller`, `service`, `persistence` y `adapter`. La observabilidad se implementará como configuración de infraestructura y pruebas; no se crea una capa de dominio nueva. La configuración Docker será nueva y separada de `frontend/e2e/compose.yml` y `performance/compose.yml`.

## Riesgos y cuestiones pendientes

- **Contexto Docker y `.dockerignore`**: el `.dockerignore` raíz contiene `Dockerfile*`; si el build usa la raíz como contexto podría excluir el Dockerfile. La implementación debe usar contexto `backend/` o un `.dockerignore` específico, y comprobar que no se envían secretos ni artefactos de build.
- **Aislamiento por redes**: conectar el backend a una segunda red para simplificar el acceso del negocio podría volver alcanzable el puerto de administración. La prueba debe inspeccionar las redes y puertos efectivos, no sólo el archivo Compose.
- **Acceso del host al bridge**: Docker puede permitir al host alcanzar una IP interna aunque no exista publicación `ports`, según el motor y la plataforma. El plan lo trata como camino independiente; si el host conecta, la validación falla y debe aplicarse una política de red/host compatible con el entorno antes de aprobar la ejecución.
- **Confianza del contenedor**: cualquier contenedor dentro de la red privada puede intentar consultar observabilidad. Esto es una limitación aceptada de la implementación mínima y no debe describirse como autenticación individual.
- **Delegación de seguridad del child context**: Spring Boot 3.5.16 registra para el contexto administrativo hijo un filtro que reutiliza el `springSecurityFilterChain` y el registro servlet del contexto padre. Una configuración local del child o un `FilterChainProxy` manual no se considerarán solución. La implementación debe usar APIs públicas de Spring Security para distinguir listener/puerto y ruta, y demostrar con pruebas que los matchers no autorizan Actuator desde el puerto de negocio.
- **Conectividad PostgreSQL**: incorporar una red adicional para separar base de datos puede volver multi-homed al backend y exponer administración a esa red. La configuración mínima debe mantener PostgreSQL en la red privada de validación o justificar y probar cualquier alternativa.
- **Dependencias de salud**: incluir demasiadas dependencias en health puede marcar el servicio como no saludable por fallos ajenos al proceso. La selección debe mantenerse mínima y documentar el significado operativo del estado.
- **Cardinalidad de métricas**: etiquetas HTTP demasiado detalladas pueden filtrar información o degradar memoria. Deben revisarse las etiquetas generadas y probarse con rutas, query strings y cabeceras sensibles.
- **Puerto configurable**: el número final del puerto de administración y sus nombres de variables quedan pendientes de implementación; no deben implicar publicación al host ni fijar IP/CIDR.

## Constitution Check (Post-Design)

- **PASS**: la solución no modifica lógica de negocio ni introduce dependencias de dominio.
- **PASS**: la red privada y la ausencia de publicación del puerto de administración reducen exposición; el rechazo por defecto está definido para caminos incompletos.
- **PASS**: el puerto de negocio conserva sus filtros JWT/API key y la cadena de autorización existente.
- **PASS**: las pruebas incluyen aislamiento de red, endpoints, datos sensibles, regresión y compilación.
- **PASS**: no se agregan dashboards, servidor Prometheus, frontend ni cambios a los Compose existentes.

## Complexity Tracking

Sin violaciones constitucionales que requieran justificación.
