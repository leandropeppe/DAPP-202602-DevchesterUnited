# Quickstart de validación: Actuator + Prometheus

Guía de validación prevista para después de la implementación. No instala Prometheus ni modifica los Compose existentes de E2E/performance.

## Prerrequisitos

- Docker y Docker Compose disponibles.
- Java 21 y el wrapper Gradle del backend disponibles para la validación local.
- Variables existentes del backend configuradas sin incluir secretos en archivos versionados.
- Archivos propuestos por el plan: `backend/Dockerfile` y `backend/docker/observability-compose.yml`.

## 1. Compilar y ejecutar pruebas del backend

Desde `backend/`:

```text
./gradlew test
./gradlew bootJar
```

**Resultado esperado**: compilación correcta y pruebas existentes en estado PASSED; no se modifican tests existentes.

## 2. Levantar la topología mínima

Desde la raíz del repositorio, usando el Compose específico de la feature:

```text
docker compose -f backend/docker/observability-compose.yml up --build -d
docker compose -f backend/docker/observability-compose.yml ps
```

**Resultado esperado**: backend, red privada y probe autorizado levantan; el estado de puertos publicados muestra sólo el puerto de negocio. El puerto de administración no aparece publicado al host.

Comprobar la configuración efectiva, no sólo el YAML:

```text
docker compose -f backend/docker/observability-compose.yml config
docker compose -f backend/docker/observability-compose.yml port backend ${BUSINESS_CONTAINER_PORT}
docker compose -f backend/docker/observability-compose.yml port backend ${MANAGEMENT_CONTAINER_PORT}
docker compose -f backend/docker/observability-compose.yml ps
docker inspect $(docker compose -f backend/docker/observability-compose.yml ps -q backend)
docker network ls
docker network inspect <observability-network-name>
```

**Resultado esperado**: existe publicación sólo para `${BUSINESS_CONTAINER_PORT}`; `${MANAGEMENT_CONTAINER_PORT}` no tiene publicación al host. El backend, PostgreSQL y `collector-probe` pertenecen a la red privada interna; `unauthorized-probe` no pertenece a ella. Las variables son referencias al puerto definido por la configuración futura, no direcciones IP o CIDR fijos.

La inspección debe incluir el listener real dentro del contenedor, por ejemplo mediante el comando de diagnóstico disponible en la imagen (`ss -lnt`, `netstat -lnt` o equivalente). Debe comprobarse que el listener no queda limitado a loopback si el collector necesita acceso y que no existe una interfaz/red de aplicación adicional no prevista.

## 3. Probar desde el contenedor autorizado

Ejecutar el probe autorizado definido por el Compose para consultar:

```text
GET /actuator/health
GET /actuator/prometheus
```

**Resultado esperado**:

- health devuelve estado interpretable sin detalles sensibles.
- prometheus devuelve formato compatible y contiene familias de JVM, memoria y HTTP.
- no aparecen Authorization, API keys, cookies, payloads, query strings, URLs sensibles ni datos personales.
- el backend mantiene conectividad con PostgreSQL por el nombre de servicio interno, sin publicar el puerto de PostgreSQL.

## 4. Probar el origen no autorizado

Ejecutar `unauthorized-probe` en una red Docker diferente y aislada. Ese contenedor debe comprobar únicamente que no puede resolver ni acceder al puerto de administración del backend, porque el backend tampoco pertenece a la red de `unauthorized-probe`.

**Resultado esperado**:

- no existe conectividad hacia el puerto de administración;
- no se obtiene health ni metrics;
- el resultado no depende de una IP o CIDR fijo;
- el puerto de administración no está publicado en el host.

El intento debe ser real, no sólo una comprobación de DNS. Desde `unauthorized-probe`, ejecutar el cliente HTTP disponible (por ejemplo `wget` o `curl`) contra el nombre de servicio y el puerto de administración, y registrar timeout, rechazo o ausencia de ruta. No se debe exigir conectividad desde este contenedor al puerto de negocio, ya que el backend tampoco pertenece a su red.

La prueba positiva del puerto de negocio publicado debe ejecutarse desde el host Docker, usando el puerto publicado por Compose y respetando las reglas JWT/API key existentes:

**Git Bash (Windows/macOS/Linux)**:

```bash
curl --fail http://127.0.0.1:${BUSINESS_PUBLISHED_PORT}/api/endpoint-de-negocio \
  -H "Authorization: Bearer ${JWT_TOKEN}" \
  -H "X-API-KEY: ${API_KEY}"
```

**PowerShell (Windows)**:

```powershell
$headers = @{ Authorization = "Bearer $env:JWT_TOKEN"; 'X-API-KEY' = $env:API_KEY }
Invoke-WebRequest -Uri "http://127.0.0.1:$env:BUSINESS_PUBLISHED_PORT/api/endpoint-de-negocio" -Headers $headers
```

El endpoint concreto y las credenciales deben corresponder a un caso ya soportado por las pruebas de negocio; no se deben introducir credenciales reales ni relajar JWT/API keys para esta validación.

Las pruebas negativas desde el host hacia el puerto de administración deben mantenerse. Obtener dinámicamente la IP del contenedor y realizar un intento HTTP sin introducir la IP en archivos ni comandos permanentes.

**Git Bash (Windows/macOS/Linux)**:

```bash
docker inspect -f "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}" $(docker compose -f backend/docker/observability-compose.yml ps -q backend)
curl --connect-timeout 3 --max-time 5 http://<IP_OBTENIDA>:${MANAGEMENT_CONTAINER_PORT}/actuator/health
```

**PowerShell (Windows)**:

```powershell
$backendId = docker compose -f backend/docker/observability-compose.yml ps -q backend
$backendIp = docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' $backendId
curl.exe --connect-timeout 3 --max-time 5 "http://${backendIp}:$env:MANAGEMENT_CONTAINER_PORT/actuator/health"
```

**Resultado esperado**: el host no obtiene una respuesta de health/metrics. Si la conexión tiene éxito, aunque no exista `ports`, la configuración falla la validación y no se considera aislada; se requiere una política de red/host compatible con el entorno antes de aprobarla.

La red privada y la ausencia de publicación no se interpretan como autenticación individual del collector: cualquier contenedor que pertenezca a ella comparte el límite de confianza.

## 5. Probar configuración incompleta y superficie mínima

Repetir la validación con la conexión de red privada o la autorización del probe omitida. Intentar también rutas administrativas no incluidas en el contrato.

**Resultado esperado**: acceso denegado por defecto y rechazo de endpoints no aprobados; no se expone información operativa adicional.

Repetir según esta matriz mínima:

| Alteración controlada | Resultado esperado |
|---|---|
| Listener con dirección/puerto inválidos | Fallo de arranque |
| Collector fuera de la red privada | Rechazo o fallo de conectividad |
| Puerto de administración publicado | Fallo de validación |
| Backend unido a segunda red de aplicación | Fallo de validación por posible alcance |
| Cadena Actuator ausente o path administrativo adicional | Fallo de prueba de seguridad |
| JWT/API key de negocio alterados | Fallo de regresión |
| PostgreSQL no resoluble | Fallo de arranque o health no saludable |
| Etiqueta HTTP con query string/secret/cardinalidad alta | Fallo de validación de métricas |

## 6. Diferenciar MockMvc de HTTP real

Las pruebas MockMvc/context deben validar rutas, contenido, cadenas Spring Security y no bypass sin abrir sockets ni crear redes Docker. Deben comprobar explícitamente la cadena de Actuator y la cadena de negocio por separado.

Las pruebas HTTP reales anteriores son obligatorias para validar el puerto de administración separado, la interfaz de escucha, el acceso del collector, el rechazo desde otra red, el acceso potencial del host y la conectividad con PostgreSQL. Un resultado positivo de MockMvc no sustituye estas pruebas.

## 7. Probar no regresión

- Invocar endpoints de negocio públicos y protegidos con los mismos casos de JWT y API key ya existentes.
- Confirmar que un endpoint de observabilidad no permite invocar, enumerar ni leer recursos de negocio.
- Ejecutar las suites E2E/performance existentes sin modificar sus Compose.

**Resultado esperado**: resultados equivalentes a la línea base y sin cambios en contratos de negocio.

## Limpieza

```text
docker compose -f backend/docker/observability-compose.yml down --volumes --remove-orphans
```

No debe quedar ningún servidor Prometheus ni dashboard instalado como parte de esta validación.
