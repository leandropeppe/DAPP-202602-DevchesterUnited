# Research

- **Imagen Grafana OSS**: se usa `grafana/grafana:13.3.2`; el repositorio solicitado `grafana/grafana-oss:13.3.2` no existe porque Grafana dejó de actualizarlo desde 12.4. La documentación oficial identifica `grafana/grafana` como la imagen OSS vigente.
- **Overlay Compose**: se elige para preservar los escenarios que no usan Grafana.
- **Redes**: Prometheus conecta ambas redes internas; Grafana sólo `grafana-ui` y no puede acceder a backend/PostgreSQL.
- **Acceso local Docker Desktop**: la publicación quedó inactiva cuando Grafana pertenecía sólo a `grafana-ui` interna (`docker port` informó `:0` y el host no conectó). Se añade `grafana-access`, bridge no interna y exclusiva de Grafana, para que Docker Desktop active el forwarding. Grafana conserva `grafana-ui` para Prometheus; ningún servicio protegido se une a la nueva red. La conectividad saliente de Grafana queda limitada por la política del host/Docker Desktop, mientras que no se crea ruta desde la nueva red al backend, PostgreSQL o Prometheus.
- **Telemetría y plugins**: se desactivan reporting, comprobaciones de actualización, preinstalación y administración de plugins con variables nativas de Grafana. Esto elimina comunicaciones opcionales observadas hacia grafana.com; `grafana-access` sigue implicando conectividad saliente potencial según la política del host/Docker Desktop.

## Evidencia G03 (2026-11-25)

- Tráfico controlado: 20 lecturas HTTP 200 a `GET /api/v1/players` por el gateway publicado.
- Los siete PromQL del dashboard devolvieron una serie: disponibilidad, CPU, heap, peticiones, latencia, 5xx y HikariCP. El 5xx usa `or (0 * (up == 1))`: expresa cero sólo mientras el target está disponible y no hay errores; ante target caído o ausencia de `up` queda en `No data`, sin provocar fallos artificiales.
- La recreación sin borrar volúmenes preservó la serie HTTP de Prometheus, el datasource y el dashboard de Grafana.
- `validate-health-docker.ps1`, `validate-metrics-docker.ps1` y `validate-network-isolation.ps1` completaron PASS. JWT Docker positivo permaneció como SKIP opcional documentado por ausencia de `OBSERVABILITY_BUSINESS_AUTHORIZATION`.
- Revisión técnica final: se corrigió la semántica 5xx y se validó en Prometheus tanto el target disponible como un job sin datos. Se inspeccionaron todas las series actuales del backend sin revelar etiquetas: no se detectaron nombres ni valores con patrones de credenciales o datos sensibles. El único nombre relacionado con autorización fue `spring_security_authorization_decision`, metadato operativo sin valores que coincidan con patrones sensibles.
- **Métricas**: se usan series Micrometer filtradas por `job="devchester-backend"`; HikariCP se valida contra scrape real sin inventar datos.
