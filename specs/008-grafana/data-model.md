# Modelo operativo

- **Target**: `backend:8081/actuator/prometheus`, `job=devchester-backend`.
- **Datasource**: Grafana consulta `http://prometheus:9090` por la red interna.
- **Dashboard**: UID `devchester-system-monitoring`; series ausentes se muestran como `No data`.
