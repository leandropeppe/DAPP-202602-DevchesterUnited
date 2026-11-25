# Contrato operativo

| Servicio | Redes | Puerto publicado |
|---|---|---|
| backend | observability | ninguno |
| prometheus | observability, grafana-ui | ninguno |
| grafana | grafana-ui, grafana-access | 127.0.0.1:3000 |

Prometheus sólo scrapea el backend. `grafana-access` es bridge no interna exclusiva de Grafana y no incluye Prometheus, backend ni PostgreSQL. Grafana no configura datasource directo a backend ni PostgreSQL.
