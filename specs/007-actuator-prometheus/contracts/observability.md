# Contrato operativo de observabilidad

Este contrato describe la superficie mínima que debe implementar el backend. No reemplaza la documentación OpenAPI de negocio y no habilita un servidor Prometheus.

## Límite de confianza y listener

- El backend debe usar un puerto de administración distinto del puerto de negocio cuando se ejecute la topología Docker de esta feature.
- El listener debe ser accesible por el contenedor autorizado de la red privada, pero no por loopback únicamente si eso impide la recolección; la dirección/interfaz efectiva debe inspeccionarse en el contenedor y no fijarse mediante una IP Docker arbitraria.
- El puerto de administración no debe aparecer en `ports`, en una publicación equivalente al host ni en una segunda red de aplicación.
- La red privada es el límite de confianza mínimo elegido para esta implementación: no autentica individualmente al contenedor. Un miembro comprometido de la red puede intentar leer los endpoints y esta limitación debe quedar documentada.
- La ausencia de publicación no se considera prueba suficiente: también deben probarse la IP efectiva del contenedor, el bridge Docker y el host.

## Endpoint de salud

- **Ruta relativa de administración**: `GET /actuator/health`
- **Acceso**: sólo desde el contenedor/origen conectado a la red privada de observabilidad.
- **Respuesta esperada**: estado resumido de disponibilidad o degradación.
- **Detalles**: ocultos por defecto; no incluir credenciales, URLs internas, cadenas de conexión, tokens ni datos de negocio.
- **Efectos**: lectura sin mutaciones.

## Endpoint de métricas

- **Ruta relativa de administración**: `GET /actuator/prometheus`
- **Acceso**: sólo desde el contenedor/origen conectado a la red privada de observabilidad.
- **Formato**: texto compatible con Prometheus/Micrometer.
- **Contenido mínimo**: métricas de JVM, memoria y solicitudes HTTP.
- **Etiquetas**: agregadas y de baja cardinalidad.
- **Exclusiones**: payloads, Authorization, API keys, cookies, query strings, información personal, URLs sensibles y valores de credenciales.
- **Efectos**: lectura sin mutaciones.

## Superficie no expuesta

Las siguientes familias no forman parte del contrato y deben permanecer inaccesibles: `env`, `beans`, `configprops`, `mappings`, `loggers`, `threaddump`, endpoints de negocio y cualquier endpoint administrativo no aprobado.

## Matriz de acceso

| Actor/origen | Health | Prometheus | Negocio |
|---|---:|---:|---:|
| Contenedor recolector autorizado en red privada | Permitido | Permitido | No concede acceso adicional |
| Origen no autorizado fuera de red privada | Rechazado/no enrutable | Rechazado/no enrutable | Sigue política existente |
| Host sin publicación de administración | No debe ser accesible; si conecta por IP Docker, falla la validación | No debe ser accesible; si conecta por IP Docker, falla la validación | Sólo puerto de negocio publicado |
| Cliente de negocio con JWT/API key | No obtiene bypass | No obtiene bypass | Política actual sin cambios |
| Configuración Docker incompleta | Denegado por defecto | Denegado por defecto | Política actual sin cambios |

## Compatibilidad y errores

- Health debe comunicar estado operativo de forma segura.
- Metrics debe devolver el formato esperado cuando la solicitud provenga del origen autorizado.
- Un origen no autorizado no debe recibir una respuesta que revele métricas o detalles internos; puede observar rechazo HTTP o fallo de conectividad según el punto donde se bloquee el acceso.
- El contrato no fija IPs, CIDR, nombre final del servicio, número concreto de puerto ni comandos de orquestación; esos detalles pertenecen al plan de implementación y al entorno.

## Seguridad Spring Boot 3.5.x

**ADR-007-01 — Cadena de seguridad efectiva única para el contexto administrativo**: aprobada el 21/11/2026 y documentada en `../research.md`. Esta decisión sustituye la hipótesis histórica de una `SecurityFilterChain` independiente por contexto.

- Con un puerto de administración distinto, Spring Boot crea un contexto hijo, pero su configuración servlet de management reutiliza el `springSecurityFilterChain` y el registro servlet del contexto principal.
- La implementación debe reutilizar esa cadena efectiva. No debe crear un `FilterChainProxy` administrativo manual, sustituir internamente el registro servlet ni usar reflexión sobre clases internas de Spring Boot.
- La autorización de health/Prometheus debe requerir simultáneamente una ruta administrativa aprobada y el listener/puerto administrativo efectivo. Autorizar sólo por `/actuator/health` o `/actuator/prometheus` no es suficiente.
- Una solicitud a los mismos paths desde el puerto de negocio debe ser rechazada o no tener mapping, pero nunca debe recibir una respuesta autorizada de Actuator. Las rutas administrativas desconocidas deben ser rechazadas por la política de seguridad por defecto, sin usar un `404` como única evidencia.
- La cadena principal debe conservar JWT, API keys, autorización, filtros actuales y `anyRequest().denyAll()` donde corresponda.
- Las pruebas deben demostrar que un request a Actuator no puede acceder a `/api/**`, que las credenciales de negocio no convierten el listener de administración en un bypass y que la clasificación por puerto/listener funciona en ambos puertos.

## Matriz de configuraciones incompletas o inseguras

| Configuración | Resultado obligatorio | Evidencia |
|---|---|---|
| Dirección/puerto de listener inválidos | Fallo de arranque | Estado y logs del backend |
| Listener sólo en loopback mientras el collector es externo al contenedor | Fallo de validación de conectividad autorizada | HTTP real desde collector |
| Puerto de administración publicado al host | Fallo de validación | `docker compose port`, `docker port` e intento desde host |
| Backend unido a segunda red de aplicación | Fallo de validación de aislamiento | `docker inspect` y conexión desde otra red |
| Collector fuera de la red privada | Rechazo o fallo de conectividad | HTTP real desde contenedor no autorizado |
| Host alcanza IP/puerto de administración | Fallo de validación | Conexión real desde host |
| Red privada o autorización del collector omitida | Rechazo por defecto | Arranque/HTTP real |
| Cadena Actuator ausente, solapada o demasiado amplia | Fallo de prueba de seguridad | Contexto Spring Security y paths no aprobados |
| JWT/API key de negocio alterados | Fallo de regresión | Pruebas de seguridad existentes |
| PostgreSQL no resoluble en la única red privada | Fallo de arranque o health no saludable | Logs, health y conectividad |
| Métricas con query strings, secretos o cardinalidad alta | Fallo de validación de información | Inspección de `/actuator/prometheus` |
