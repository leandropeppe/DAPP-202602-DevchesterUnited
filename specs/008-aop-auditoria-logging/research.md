# Research: Auditoría transversal y logging seguro

## 1. Estado real del proyecto

**Decision**: Diseñar sobre `backend/`, con Java 21 y Spring Boot 3.5.16.

**Rationale**: `backend/build.gradle` declara Web, Security, OAuth2 Resource Server, Actuator, Micrometer Prometheus, JPA, Flyway, Testcontainers y ArchUnit 1.5.1. No declara actualmente `spring-boot-starter-aop`, `@Aspect`, MDC ni una política propia de correlation ID.

**Alternatives considered**: Agregar una biblioteca de tracing completa o instrumentar controllers individualmente. Se descartan porque amplían el alcance, pueden duplicar observabilidad y no cubren tareas programadas mediante los mismos puntos de aplicación.

## 2. Puntos de entrada seleccionados

**Decision**: Interceptar los diez métodos documentados en `plan.md` por sus interfaces de servicio, priorizando llamadas proxyadas.

**Rationale**: El código real expone `sincronizar`, `ejecutarManual`, `ejecutarSemanal`, compra/venta inmediata, publicación/compra/cancelación P2P e inicialización del mercado. Los controllers delegan a interfaces; los schedulers delegan a `SincronizacionEstadisticasJugadorService` y `ValorizacionJugadorService`; el listener de startup delega a `MarketInitializationService`.

**Alternatives considered**: Interceptar todos los métodos de `service.impl` o todos los controllers. Se descarta por indiscriminación, eventos duplicados, captura de consultas y mayor riesgo de datos sensibles.

## 3. Spring AOP basado en proxies

**Decision**: Usar un aspecto `@Around` con pointcuts cerrados y probar el proxy efectivo de cada servicio.

**Rationale**: Las dependencias actuales usan interfaces y beans Spring, por lo que los puntos de entrada externos atraviesan proxies. Spring AOP proxy-based no intercepta `private`, `final`, constructores ni self-invocation. El plan evita prometer cobertura para esas llamadas y selecciona un único overload P2P que cruza el boundary HTTP.

**Alternatives considered**: Weaving de AspectJ para capturar cualquier llamada interna. Se descarta porque requiere configuración adicional, aumenta complejidad y contradice el objetivo mínimo.

## 4. Correlación HTTP y MDC

**Decision**: `OncePerRequestFilter` genera siempre un UUID interno como `requestCorrelationId`, devuelve ese valor como `X-Correlation-ID`, y conserva un header externo válido únicamente como `externalCorrelationId` separado. Las ejecuciones BACKGROUND generan un `executionCorrelationId` propio por ejecución.

**Rationale**: El cliente no debe definir la identidad de la solicitud. La validación ASCII de 1–128 caracteres es portable y evita controlar el formato interno. `finally` elimina MDC para evitar contaminación entre solicitudes.

**Alternatives considered**: Usar el header del cliente como ID principal, exigir UUID y rechazar headers inválidos, o introducir una librería de tracing. Se descartan por colisión, rechazo innecesario o acoplamiento adicional.

## 5. Evento estructurado y seguridad

**Decision**: Evento efímero e inmutable con allowlist fija: `eventName`, `operation`, `stage`, `result`, `durationMs`, `timestamp`, `contextType`, `requestCorrelationId` obligatorio en HTTP, `executionCorrelationId` obligatorio en BACKGROUND, `externalCorrelationId` opcional solo si fue validado del cliente y `errorCode` opcional.

**Rationale**: La lista permite pruebas negativas deterministas. Los eventos no contienen argumentos, retornos, mensajes, headers ni stack traces. Los códigos de error se derivan de tipos conocidos, no de texto arbitrario.

**Alternatives considered**: Serializar `ProceedingJoinPoint`, excepción completa o DTOs de entrada/salida y filtrar después. Se descarta porque el filtro posterior no es una garantía de no exposición.

## 6. Fallos de auditoría

**Decision**: Best effort: aislar `RuntimeException` producida al construir o escribir el evento, no alterar el flujo de negocio y aceptar pérdida de eventos cuando fallen proceso, destino o infraestructura de logs.

**Rationale**: La auditoría es secundaria respecto del resultado funcional. No se captura `Error`, no se reintenta indefinidamente y no se cambia la excepción original.

**Alternatives considered**: Hacer fallar la operación si no se registra, reintentos ilimitados o silencio total. Se descartan por impacto funcional, latencia impredecible o falta de diagnóstico operativo.

## 7. Actuator, Prometheus y seguridad

**Decision**: No modificar `SecurityConfig`, `ApiKeyAuthenticationFilter`, los endpoints `/actuator/health` y `/actuator/prometheus`, ni las propiedades de Actuator/Micrometer. El filtro de correlación será independiente de autenticación y no leerá credenciales.

**Rationale**: La configuración real ya separa el management port y permite health/prometheus bajo reglas específicas. El objetivo requiere trazabilidad, no nuevas métricas ni cambios de autorización.

**Alternatives considered**: Agregar métricas de auditoría o mover el filtro dentro de la cadena de seguridad. Se descarta porque puede duplicar señales o alterar precedencia y fallos de autenticación.

## 8. Dependencia AOP

**Decision**: Incorporar explícitamente `org.springframework.boot:spring-boot-starter-aop` durante la implementación, sin modificarla en esta fase de planificación.

**Rationale**: `build.gradle` no declara soporte AOP explícito. Una dependencia directa hace visible la intención y provee infraestructura compatible con Spring Boot del proyecto.

**Alternatives considered**: Depender de transitividad accidental o usar AspectJ weaving completo. Se descartan por falta de reproducibilidad o complejidad innecesaria.

## 9. Rendimiento

**Decision**: Validar SC-008 con 30 pares alternados baseline/tratamiento por escenario, misma compilación, configuración, operación, dataset sintético y entorno, después de warm-up controlado; reportar mediana y p95 y guardar muestras en `backend/build/diagnostics/auditoria-performance/`. La comparación principal mide baseline sin aspecto frente a tratamiento con AOP y writer habilitados. Una serie diagnóstica adicional con writer controlado sin emisión estima el costo del writer, pero no representa la eliminación del costo total AOP.

**Rationale**: Una comparación emparejada reduce variabilidad y permite reproducir el overhead de correlación, aspecto y construcción del evento sin confundirlo con el tiempo externo del destino de logs.

**Alternatives considered**: Un único promedio, una prueba de carga abierta o agregar una métrica nueva. Se descartan por baja reproducibilidad, ruido y duplicación de Prometheus.

## 10. Arquitectura y ArchUnit

**Decision**: Mantener todas las clases nuevas en `configuration.auditoria` y validar las 21 pruebas de `ArchitectureTest` sin modificar sus reglas.

**Rationale**: La prueba real importa el paquete base completo, verifica capas, repositorios, independencia del modelo, ausencia de ciclos y restricciones de configuración. El diseño no agrega tipos a `modelo` ni `persistence` y deja a los services existentes sin nuevas dependencias.

**Alternatives considered**: Crear una anotación de auditoría en `service` o un modelo persistente. Se descarta porque introduciría dependencias de capa o storage no solicitadas.
