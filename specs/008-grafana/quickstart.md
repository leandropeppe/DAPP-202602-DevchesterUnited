# Quickstart

1. Añadir `GRAFANA_ADMIN_PASSWORD` al archivo local ignorado `backend/docker/.env`, sin compartirlo.
2. Desde `backend/docker`, iniciar el stack:

   ```powershell
   docker compose -f observability-compose.yml -f grafana-compose.yml up -d
   ```

3. Abrir `http://127.0.0.1:3000`, iniciar sesión y comprobar el datasource `Prometheus` y el dashboard `Devchester United — System Monitoring`.
4. Para detener el stack sin borrar datos:

   ```powershell
   docker compose -f observability-compose.yml -f grafana-compose.yml stop
   ```

5. Si se ejecutan los validadores históricos de Actuator mientras el overlay está activo, usar sólo en la sesión de validación:

   ```powershell
   $env:COMPOSE_IGNORE_ORPHANS = 'true'
   ```

La futura métrica de negocio queda pendiente de definición por el equipo. Cuando exista un nombre, semántica, etiquetas permitidas y criterio de privacidad acordados, debe exponerse desde Micrometer, añadirse al contrato y verificarse antes de crear un nuevo panel.

La persistencia, aislamiento negativo y regresión fueron validados en G03. No se incorporó ninguna métrica personalizada de negocio.
