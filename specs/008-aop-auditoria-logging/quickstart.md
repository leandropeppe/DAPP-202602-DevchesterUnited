# Quickstart de validación: Auditoría y correlación

Este documento describe cómo validar la implementación cuando se ejecuten las
tareas de código. No agrega código, tablas ni migraciones.

## Prerrequisitos

- Java 21 y Docker disponibles.
- Variables de entorno de PostgreSQL definidas según el proyecto para pruebas
  que no usen Testcontainers.
- Ejecutar desde `backend/`.

## Validación estática y de arquitectura

```powershell
./gradlew.bat test --tests '*ArchitectureTest' --no-daemon --console=plain
```

Resultado esperado: las 21 reglas ArchUnit pasan, incluida la ausencia de
ciclos y restricciones de capas.

## Tests unitarios de auditoría

```powershell
./gradlew.bat test --tests '*auditoria*' --no-daemon --console=plain
```

Validar generación/validación del ID, limpieza MDC, allowlist, códigos de
error, eventos START/END, duración y fallo best effort del writer.

## Flujo HTTP con MockMvc

1. Sin `X-Correlation-ID`: comprobar respuesta con UUID interno y eventos HTTP
   que usan ese mismo valor.
2. Con header externo válido: comprobar que el ID interno de respuesta es
   distinto y que el evento conserva el externo como campo separado.
3. Con header vacío, de 129 caracteres o con caracteres fuera de allowlist:
   comprobar que la solicitud continúa y recibe un ID interno.
4. Con JWT y API key sintéticos: inspeccionar eventos y comprobar que no hay
   credenciales ni headers de autorización.
5. En health y Prometheus: comprobar que seguridad y exposición existentes no
   cambian.

## Operaciones de negocio

Ejecutar escenarios existentes o equivalentes para cada operación de
`contracts/audit-event.md`. Comprobar exactamente un START y un END, resultado
funcional sin cambios y código controlado en errores. En compra P2P comprobar
que el overload interno no genera un segundo par de eventos.

## Background y proxy

Ejecutar `EstadisticasScheduler.ejecutarSemanal`,
`ValorizacionScheduler.ejecutarSemanal` y el listener de inicialización con
configuración de prueba. Comprobar `contextType=BACKGROUND`, presencia de un
`executionCorrelationId` nuevo por ejecución, ausencia de ID HTTP inventado y
propagación original de errores. Agregar una prueba explícita
de self-invocation/no-proxy para documentar que queda fuera del alcance.

## Concurrencia

Lanzar solicitudes concurrentes con el mismo `X-Correlation-ID` externo y
verificar IDs internos distintos, MDC aislado y ausencia de mezcla de eventos.

## Rendimiento SC-008

Para cada escenario representativo:

1. Fijar compilación, perfil, configuración, dataset sintético, máquina y
   destino de logs.
2. Ejecutar warm-up y descartarlo.
3. Ejecutar 30 pares alternados baseline sin el aspecto / tratamiento con AOP y
   writer habilitados.
4. Registrar cada duración de backend, excluyendo el tiempo externo del
   destino de logs.
5. Calcular mediana, p95 e incremento relativo.
6. Adjuntar muestras, entorno y método al informe bajo
   `backend/build/diagnostics/auditoria-performance/` (directorio no
   versionado). Ejecutar además 30 pares diagnósticos con writer controlado sin
   emisión para estimar el costo del writer; no usar esa serie para afirmar que
   desaparece el costo de AOP.

Resultado esperado: incremento mediano <=10% y p95 <=20%, conforme a SC-008.

## Smoke test de arranque local

1. Compilar desde `backend/` con la configuración local válida ya existente,
   sin imprimir ni copiar secretos: `./gradlew.bat compileJava --no-daemon --console=plain`.
2. Levantar el backend usando el mecanismo local documentado por el proyecto y
   verificar que el proceso inicia correctamente.
3. Consultar endpoints básicos representativos, por ejemplo health y un
   endpoint HTTP funcional no destructivo, usando la autenticación/configuración
   local existente. Comprobar que `X-Correlation-ID` se devuelve en la respuesta.
4. No alterar propiedades de Actuator, Prometheus, seguridad o puertos; detener
   el proceso al finalizar y documentar únicamente resultado, versión y
   endpoint, nunca valores sensibles.

## Validación final

```powershell
./gradlew.bat test --no-daemon --console=plain
```

La suite completa debe pasar sin migraciones nuevas, sin cambios de contrato
funcional y sin duplicar métricas de Actuator/Prometheus. SonarCloud y la
revisión de seguridad se ejecutan en el cierre de la feature según la
Constitution.

## Evidencia de ejecución T047–T051 (2026-11-25/26)

- Suite completa: `BUILD SUCCESSFUL`; 1.203 tests, 0 fallos, 0 errores y 0 omitidos.
- ArchUnit: 21 reglas aprobadas. Auditoría, correlación, seguridad, background,
  concurrencia y regresiones de Actuator/Prometheus aprobadas.
- Eventos reales inspeccionados por las pruebas: allowlist de campos, 10
  operaciones cerradas, correlación HTTP/BACKGROUND, exclusión de argumentos,
  retornos, headers, credenciales, mensajes y causas completas.
- SC-008: evidencia previa de 30 pares conservada; dentro de los límites del
  criterio.
- SonarCloud: Quality Gate `OK`; 0 issues abiertas, 0 vulnerabilidades y 0
  code smells críticos.
- Smoke real T051: completado. `compileJava` pasó y Spring Boot arrancó en 8080,
  con management en 8081, PostgreSQL 16.15 y Flyway 23/23 vigentes. Se usó un
  `JWT_SECRET` aleatorio efímero, `-Duser.timezone=UTC` y los valores
  documentados de valorización `1.00/99.00`, solo en el proceso y sin guardar ni
  imprimir secretos. Consultas reales: management `/actuator/health` 200,
  management `/actuator/prometheus` 200, `/api/v1/players?page=1&limit=1` 200,
  `/actuator/health` en puerto de negocio 401 y
  `/api/v1/market/portfolio` sin credenciales 401. Las respuestas de negocio
  devolvieron un UUID interno en `X-Correlation-ID`, distinto de
  `smoke-external-001`; el header externo no sustituyó la identidad interna.
  No se observaron secretos, argumentos, retornos ni headers sensibles en los
  logs de auditoría. El proceso iniciado para la prueba fue detenido y no
  quedaron listeners en 8080/8081.
