# Quickstart de validación: Actuator + Prometheus

Guía operativa para validar la topología de observabilidad aprobada. No instala Prometheus ni modifica los Compose de E2E o performance.

## Topología y puertos

- `backend` no publica puertos al host. Atiende negocio en el puerto interno `8080` y Actuator en el listener administrativo interno `8081`.
- `business-gateway` es la única superficie publicada: expone su `8080` en `18080` por defecto (`BUSINESS_PUBLISHED_PORT` permite cambiar sólo el puerto de host).
- El gateway reenvía exclusivamente tráfico de negocio a `backend:8080`; no publica ni enruta el listener administrativo.
- `collector-probe` comparte la red privada `observability` con backend; `unauthorized-probe` está en una red interna separada.

El endpoint `/actuator/prometheus` expone métricas en formato compatible con Prometheus, pero no instala ni ejecuta un servidor recolector Prometheus.

## Prerrequisitos

- Docker y Docker Compose disponibles.
- PowerShell 5.1 o posterior para los runners `.ps1`.
- Java 21 y el wrapper Gradle del backend para la validación local de regresión.
- Variables de entorno requeridas por `backend/docker/observability-compose.yml`, definidas en la sesión o en un mecanismo local no versionado: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `JWT_SECRET`, `VALORIZACION_GENERAL_VALOR_BASE`, `VALORIZACION_GENERAL_FACTOR_ESCALA`, `VALORIZACION_POSITIONAL_VALOR_BASE`, `VALORIZACION_POSITIONAL_FACTOR_ESCALA` y `VALORIZACION_ELEGIBILIDAD_CONFIGURADA`.

No se incluyen secretos reales en este documento. Son opcionales `BUSINESS_PUBLISHED_PORT` (por defecto `18080`), `SERVER_PORT` (por defecto `8080`), `MANAGEMENT_SERVER_PORT` (por defecto `8081`), `MANAGEMENT_SERVER_ADDRESS` (por defecto `0.0.0.0`) y los mínimos de elegibilidad.

## 1. Compilar y ejecutar pruebas del backend

Desde `backend/`:

```powershell
.\gradlew.bat test --no-daemon --console=plain
.\gradlew.bat bootJar --no-daemon --console=plain
```

**Resultado esperado**: compilación correcta y pruebas existentes aprobadas, sin modificar sus contratos.

## 2. Levantar la topología mínima

Desde la raíz del repositorio:

```powershell
docker compose -f backend/docker/observability-compose.yml up --build -d
docker compose -f backend/docker/observability-compose.yml ps
docker compose -f backend/docker/observability-compose.yml config
```

**Resultado esperado**: PostgreSQL queda saludable; backend, gateway y probes están disponibles. Sólo `business-gateway` publica negocio en `18080` por defecto; backend no tiene publicaciones de host y el administrativo `8081` permanece interno.

Compruebe las publicaciones con los servicios y puertos reales:

```powershell
$businessPort = docker compose -f backend/docker/observability-compose.yml port business-gateway 8080
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($businessPort)) { throw 'business-gateway no publicó el puerto de negocio.' }
$businessPort

$managementPort = docker compose -f backend/docker/observability-compose.yml port backend 8081 2>$null
if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($managementPort)) { throw 'El puerto administrativo fue publicado indebidamente.' }
'PASS: backend:8081 no está publicado al host.'
```

Los placeholders `BUSINESS_CONTAINER_PORT` y `MANAGEMENT_CONTAINER_PORT` no forman parte de la implementación y no deben utilizarse. El puerto de negocio interno es `8080`; el administrativo interno es `8081`.

## 3. Ejecutar los runners aprobados

Desde la raíz del repositorio y con el Compose anterior en ejecución:

```powershell
.\scripts\observability\validate-health-docker.ps1
.\scripts\observability\validate-metrics-docker.ps1
.\scripts\observability\validate-network-isolation.ps1
.\scripts\observability\validate-insecure-scenarios.ps1 -Scenario all
```

Resultados esperados:

- `validate-health-docker.ps1`: `collector-probe` recibe `GET http://backend:8081/actuator/health` con HTTP 200, JSON interpretable y sin detalles sensibles; host y `unauthorized-probe` no alcanzan el puerto administrativo.
- `validate-metrics-docker.ps1`: `collector-probe` recibe `GET http://backend:8081/actuator/prometheus` con HTTP 200, formato Prometheus y familias JVM, memoria y HTTP, sin valores sensibles; host y `unauthorized-probe` no lo alcanzan.
- `validate-network-isolation.ps1`: valida las tres redes, que backend no publica puertos, que gateway publica únicamente negocio, el listener administrativo interno, health y Prometheus desde el collector, el rechazo desde host y probe no autorizado, y la protección de negocio y Actuator a través del gateway. Sin `OBSERVABILITY_BUSINESS_AUTHORIZATION` muestra un `SKIP` sólo para su comprobación positiva JWT opcional.
- `validate-insecure-scenarios.ps1 -Scenario all`: debe detectar los ocho escenarios negativos (`invalid-listener`, `invalid-port`, `loopback-listener`, `published-management-port`, `backend-second-network`, `collector-outside-private`, `host-reachable` y `broad-chain`) mediante proyectos temporales separados, sin alterar el Compose normal.

## 4. Verificar superficie de negocio y seguridad

El endpoint protegido real es `GET /api/v1/market/transactions`; exige JWT. No se requiere ni se documenta una API key para esta comprobación. Sin autorización debe conservar `401` o `403` a través de `business-gateway`:

```powershell
curl.exe --silent --show-error --output NUL --write-out '%{http_code}' http://127.0.0.1:18080/api/v1/market/transactions
```

Con un JWT de prueba válido suministrado sólo en la sesión actual, la misma ruta debe devolver HTTP 200:

```powershell
$env:OBSERVABILITY_BUSINESS_AUTHORIZATION = 'Bearer <JWT_DE_PRUEBA_VALIDO_NO_VERSIONADO>'
.\scripts\observability\validate-network-isolation.ps1
Remove-Item Env:OBSERVABILITY_BUSINESS_AUTHORIZATION
```

No pegue tokens reales en archivos ni en el historial compartido. El runner reutiliza esa variable sólo para la comprobación positiva opcional y valida asimismo que `/actuator/health` y `/actuator/prometheus` no se aceptan a través del puerto de negocio.

## 5. No regresión y coexistencia con Playwright

El Compose de observabilidad debe estar detenido durante Playwright: `business-gateway` publica `18080`, que Playwright reserva para el backend E2E. El backend local de IntelliJ puede permanecer activo porque usa sus puertos habituales `8080` (negocio) y `8081` (administración), distintos de los puertos E2E.

| Componente | Negocio | Administración | Otro puerto |
|---|---:|---:|---:|
| Backend local de IntelliJ | `8080` | `8081` | — |
| Backend E2E de Playwright | `18080` | `18081` | PostgreSQL E2E: `15432` |
| Frontend E2E | — | — | `4173` |

Antes de iniciar Playwright, compruebe únicamente los puertos que E2E necesita libres; `8080` y `8081` pueden pertenecer al backend local:

```powershell
$e2eListeners = @(Get-NetTCPConnection -State Listen -LocalPort 18080,18081,15432,4173 -ErrorAction SilentlyContinue)
if ($e2eListeners.Count -gt 0) { $e2eListeners | Format-Table LocalAddress,LocalPort,OwningProcess,State -AutoSize; throw 'Hay puertos E2E ocupados.' }
```

Durante E2E no se capturó directamente la dirección de escucha del listener administrativo `18081`; su separación queda acreditada funcionalmente por la coexistencia del backend local activo y la ejecución correcta del backend E2E.

Desde `frontend/`, una vez detenido el Compose de observabilidad:

```powershell
npm run verify:frontend
npm run build
npm run test:e2e
```

**Resultado esperado**: se preservan los contratos de negocio, JWT y API keys existentes; no se introduce acceso a negocio por endpoints administrativos. La ejecución validada completó Playwright con 6/6 PASS en 2,1 minutos, conservó el PID del backend local, obtuvo health local HTTP 200 con estado `UP` y Prometheus local HTTP 200, y eliminó los contenedores E2E liberando sus puertos.

## Limpieza

Para detener la topología sin borrar sus recursos persistentes ni afectar otros proyectos Docker:

```powershell
docker compose -f backend/docker/observability-compose.yml stop
```

Para retirar los contenedores y redes de este Compose cuando se decida explícitamente finalizar ese entorno:

```powershell
docker compose -f backend/docker/observability-compose.yml down
```

No use `down --volumes` ni `--remove-orphans` como limpieza rutinaria de un entorno existente. Cualquier eliminación de volúmenes requiere una decisión explícita y comprobación previa de su alcance.
