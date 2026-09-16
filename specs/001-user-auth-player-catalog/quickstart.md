# Guía de validación: autenticación, sincronización y frontend

**Revisión 2026-09-15**. US1–US3 y T001–T050 constan realizadas en los artefactos actuales. Esta guía describe la validación objetivo después de implementar US4/US5 y los gates pendientes; V4, sync y frontend todavía no están implementados. El monorepo previsto ubica Gradle en `backend/` y React + JavaScript/Vite en `frontend/`; `.specify/`, `specs/`, `.github/`, `performance/` y `postman/` permanecen en la raíz. No representa una ejecución exitosa. Conservar resultados históricos en `validation.md` y añadir los nuevos por commit.

## Prerrequisitos

- Java/JDK 21 en PATH y JAVA_HOME; Docker Engine compatible con contenedores Linux, activo.
- Wrapper Gradle 8.14.4 y Boot 3.5.16 previstos por `plan.md`, dependencias disponibles desde sus repositorios.
- PostgreSQL 16 para arranque local; Testcontainers crea BD descartable para pruebas. No usar una BD compartida/productiva.
- PowerShell para los comandos siguientes; Postman para importar la colección. k6 en el entorno Linux separado para medir rendimiento.
- Ejecutar comandos backend desde `backend/`; el puerto 5432 local y 8080 deben estar disponibles.
- Node 24 LTS y npm para frontend; puerto 5173 (dev) o 4173 (preview). Paquetes y navegadores Playwright provisionados antes de ejecutar suites sin Internet.
- La prueba manual real de sincronización requiere una cuenta Football-Data.org con acceso efectivo a Squads; las suites automatizadas usan servidor local y no requieren esa cuenta.
- Antes de adaptar los fixtures SQL de tests existentes, presentar el diff y obtener el «SÍ» explícito constitucional. No omitir suites antiguas ni modificar V1–V3.

## 1. PostgreSQL y configuración local

Crear una instancia local dedicada (si ya existe una equivalente, reutilizar su configuración):

```powershell
docker run --name devchester-local -e POSTGRES_DB=devchester -e POSTGRES_USER=devchester -e POSTGRES_PASSWORD=devchester_local -p 127.0.0.1:5432:5432 -d postgres:16
docker exec devchester-local pg_isready -U devchester -d devchester
```

La contraseña anterior es solo para este contenedor local. Configurar el proceso que arrancará la aplicación:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:DB_URL = 'jdbc:postgresql://localhost:5432/devchester'
$env:DB_USERNAME = 'devchester'
$env:DB_PASSWORD = 'devchester_local'
$jwtBytes = New-Object byte[] 32
$jwtRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRandom.GetBytes($jwtBytes)
$jwtRandom.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
```

El código existente lee `jwt.secret` mediante `JWT_SECRET`. No imprimir ni guardar el secreto en Git. El arranque falla si falta o es insuficiente, excepto el valor exclusivo de test. Las variables DB_* coinciden con application.properties. La ausencia de FOOTBALL_DATA_API_KEY debe permitir arrancar y consultar datos locales; solo sync responde 503.

## 2. Build y pruebas

En otra consola sin el perfil local heredado, ejecutar desde `backend/`:

```powershell
Push-Location backend
java -version
docker info
.\gradlew.bat --version
.\gradlew.bat clean build
Pop-Location
```

Esperado: Java 21, Gradle 8.14.4, BUILD SUCCESSFUL y todas las pruebas sin fallos ni omisiones. `backend/build/reports/tests/test/index.html` incluye contextLoads, dominio, Services/Repositories, Adapter y E2E MockMvc. Las nuevas suites deben validar migración V3→V4, 96 equipos, dorsal null, inactivación/reactivación, transferencia, rollback y concurrencia global. Usan PostgreSQL Testcontainers y HTTP loopback, sin proveedor real. Docker ausente es fallo de prerrequisito, no motivo para omitir integración.

Verificar dependencias si se necesita diagnosticar compatibilidad:

```powershell
Push-Location backend
.\gradlew.bat dependencyInsight --dependency junit-jupiter --configuration testRuntimeClasspath
Pop-Location
```

Esperado: Jupiter 5, sin Framework 7 ni Jupiter 6. La CI deberá añadir cobertura JaCoCo y ejecutar SonarCloud; sus tareas se configurarán durante la implementación.

## 3. Arranque y flujo HTTP

En la consola con las variables locales:

```powershell
Push-Location backend
.\gradlew.bat bootRun
Pop-Location
```

Esperado tras implementar: Flyway aplica V1–V4, conserva UUID/datos previos y normaliza equipos; Hibernate valida y aplicación escucha en 8080. Antes de la primera sync válida, la semilla demo permanece consultable. Swagger UI: `http://localhost:8080/swagger-ui.html`; contrato generado: `http://localhost:8080/v3/api-docs`. Comparar los cinco endpoints, seguridad y esquemas con [contracts/openapi.json](./contracts/openapi.json).

En otra consola, registrar un usuario único y realizar el flujo completo:

```powershell
$baseUri = 'http://localhost:8080/api/v1'
$nombre = 'dev' + [Guid]::NewGuid().ToString('N').Substring(0, 12)
$claveLocal = 'PruebaLocal123!'
$registro = @{ username = $nombre; email = "$nombre@example.test"; password = $claveLocal } | ConvertTo-Json
$usuario = Invoke-RestMethod -Method Post -Uri "$baseUri/auth/register" -ContentType 'application/json' -Body $registro
$login = @{ username = $nombre; password = $claveLocal } | ConvertTo-Json
$sesion = Invoke-RestMethod -Method Post -Uri "$baseUri/auth/login" -ContentType 'application/json' -Body $login
$jwtHeaders = @{ Authorization = "Bearer $($sesion.data.access_token)" }
$generada = Invoke-RestMethod -Method Post -Uri "$baseUri/auth/apikey" -Headers $jwtHeaders
$apiHeaders = @{ 'X-API-KEY' = $generada.data.api_key }
$catalogo = Invoke-RestMethod -Uri "$baseUri/players?team=Madrid&page=1&limit=20" -Headers $apiHeaders
$catalogo.data.players | Select-Object first_name, last_name, team
$catalogo.data.pagination
```

Esperado: registro 201, login 200 con expires_in=3600, generación 200 sin fecha de vencimiento, catálogo 200 con ambos equipos de Madrid de la semilla. `madrid` y `MADRID` producen el mismo conjunto. Tokens quedan en memoria; no volcar respuestas de login/generación en logs de evidencia.

Rotar y comprobar revocación:

```powershell
$renovada = Invoke-RestMethod -Method Post -Uri "$baseUri/auth/apikey" -Headers $jwtHeaders
try {
    Invoke-RestMethod -Uri "$baseUri/players" -Headers $apiHeaders
    throw 'La clave anterior fue aceptada indebidamente'
} catch {
    if ($null -eq $_.Exception.Response) { throw }
    if ([int]$_.Exception.Response.StatusCode -ne 401) { throw }
}
$apiHeaders = @{ 'X-API-KEY' = $renovada.data.api_key }
Invoke-RestMethod -Uri "$baseUri/players?team=madrid" -Headers $apiHeaders
```

## 4. Casos de aceptación

| Caso | Resultado esperado |
|------|--------------------|
| Repetir registro | 409, sin usuario adicional |
| Contraseña débil/email inválido | 400 con mensaje en español |
| Credenciales incorrectas | 401 genérico |
| Generar clave sin JWT o con JWT expirado/alterado | 401 |
| Catálogo sin API Key, con revocada o solo con JWT | 401 |
| Filtros combinados | AND de subcadenas, mismos resultados ante cambio de mayúsculas |
| `%`, `_` o comilla en filtro | Búsqueda literal segura; nunca expansión SQL |
| Filtro inexistente/página fuera de rango | 200, players vacío y totales coherentes |
| page=0, limit=101, filtro vacío | 400 |
| Pasar tiempo sin rotar clave | Sigue activa; probar con Clock, sin esperar días |
| BD inaccesible | 503 sin conceder acceso ni revelar detalles |

Concurrencia se verifica mediante pruebas deterministas PostgreSQL, no lanzando peticiones manuales esperando una carrera: mantener lock de la ganadora, ejecutar competidoras y exigir 409 antes de liberarla. Luego una clave activa, anterior revocada y sin cambios de perdedoras. Otra prueba autoriza catálogo, pausa consulta, rota clave y comprueba que la consulta autorizada termina mientras solicitudes posteriores con la anterior fallan. Verificar también rollback y usuarios distintos.

## 5. Sincronización: pruebas y comprobación manual real

El build automatizado verifica el [contrato de proveedor](./contracts/proveedor.md) con servidor HTTP local: cinco ligas y 96 equipos, temporadas variables, null dorsal, 403/429/5xx, timeout, JSON inválido, plantillas vacías, IDs conflictivos y fallos en última respuesta. Debe demostrar que el catálogo previo conserva datos y actividad tras cualquier fallo. Barreras deterministas mantienen una ejecución activa para comprobar 409 de otro usuario y cero llamadas adicionales al proveedor. No intentar reproducir carreras mediante sleeps manuales.

Validación real separada y manual: usar exclusivamente esta BD local dedicada, configurar FOOTBALL_DATA_API_KEY en el entorno del proceso backend mediante el mecanismo seguro habitual y reiniciar el proceso. No escribir el secreto literal en comandos versionados ni recoger headers en logs. Confirmar previamente permisos/cobertura de la cuenta según research.md. No ejecutar este paso en CI.

Con las variables HTTP de la sección 3:

```powershell
$sync = Invoke-RestMethod -Method Post -Uri "$baseUri/catalog/sync" -Headers $jwtHeaders -TimeoutSec 180
$sync.data
$catalogoReal = Invoke-RestMethod -Uri "$baseUri/players?page=1&limit=20" -Headers $apiHeaders
$catalogoReal.data.pagination
```

Esperado: 200 después de commit; teams_processed=96, created+updated=processed por entidad; cantidad de jugadores variable. Plantillas consultadas localmente, sin jugadores demo activos. Segunda ejecución sobre la misma respuesta externa: cero creados, mismos UUID, sin duplicados. Las entidades existentes procesadas cuentan como updated aunque no cambien. Si la cuota externa se agotó, puede responder 503; no reintentar en bucle.

Verificar sin credencial, solo API Key, JWT inválido/expirado o usuario inexistente →401. Otra ejecución en curso →409. Datos no utilizables o proveedor caído →503 conservando snapshot. Catálogo consultable con proveedor desconectado. El test fixture valida ausentes y reaparición; no manipular datos reales del proveedor para forzar esos escenarios. Si no hay cuenta con plantillas o el roster es ambiguo, dejar aceptación externa pendiente, sin marcar US4 completa.

## 6. Frontend

Después de crear frontend/package.json y lockfile según el plan:

```powershell
node --version
npm --prefix frontend ci
npm --prefix frontend run test -- --run
npm --prefix frontend run build
Push-Location frontend
npm exec -- playwright install chromium
Pop-Location
npm --prefix frontend run dev -- --host 127.0.0.1
```

La instalación de paquetes/navegador es preparación; puede usar Internet. Las suites posteriores no deben hacerlo. Dev debe usar puerto 5173 estricto y proxy /api/v1→8080; abrir `http://127.0.0.1:5173`. No abrir el frontend alternando localhost y 127.0.0.1 durante la prueba de sesión, porque son orígenes distintos.

Flujo manual SC-010: registro→login→generar clave→consultar catálogo, filtrar Madrid, paginar y comprobar cero resultados. Validar dorsal null con fixture local preparado por las suites nuevas; no requiere sync real. Recargar conserva credenciales sin mostrar la clave otra vez; cerrar sesión borra datos locales; nueva pestaña independiente requiere credenciales. La restauración especial de sesiones del navegador está fuera de la garantía de eliminación ordinaria documentada en [frontend.md](./contracts/frontend.md).

Validación automatizada en otra terminal, usando la infraestructura prevista `frontend/e2e/compose.yml` (PostgreSQL tmpfs y backend dedicado). Preparar previamente la imagen con el bootJar del build y las imágenes base; luego ejecutar:

```powershell
docker compose -p devchester-e2e -f frontend/e2e/compose.yml build
docker compose -p devchester-e2e -f frontend/e2e/compose.yml up -d --wait --pull never
$env:DEV_API_TARGET = 'http://127.0.0.1:18080'
npm --prefix frontend run test:e2e
docker compose -p devchester-e2e -f frontend/e2e/compose.yml stop
```

El soporte de pruebas carga el fixture normalizado después de Flyway, solo en este proyecto Docker dedicado, antes de marcar readiness. Playwright inicia preview del build en 4173 con proxy al backend 18080 y ejecuta SC-010 sin proveedor; nunca toma DB_URL del entorno personal. Las imágenes/base se preparan antes de desconectar la red; las suites no descargan dependencias. No sustituir todas las rutas por mocks para acreditar integración. Pruebas adicionales simulan errores 400/401/409/503 y bloquean hosts externos. Reportar build Vitest/Playwright, navegador y commit, sin secretos reales en traces/capturas. Detener solo el proyecto devchester-e2e al terminar; su BD tmpfs es descartable.

## 7. Rendimiento

Implementar `performance/registro-login.js`, `performance/api-key.js`, `performance/catalogo.js` y un fixture determinista según [plan.md](./plan.md). Antes de ejecutarlos, preparar la BD descartable de rendimiento con los 10.000 jugadores y 1.000 usuarios indicados. Registrar checksum y recursos; no poblar mediante un endpoint administrativo nuevo.

En el generador Linux, con BASE_URL y credenciales del fixture configuradas como variables de entorno y sin imprimir secretos:

```bash
k6 run --summary-export registro-login.json performance/registro-login.js
k6 run --summary-export api-key.json performance/api-key.js
k6 run --summary-export catalogo.json performance/catalogo.js
```

Los scripts deben implementar el calentamiento excluido, las métricas y los umbrales del plan. Ejecutar tres repeticiones restaurando el fixture en la BD descartable. Esperado: salida cero, p95 conjunto registro+login <2000 ms, API Key <500 ms, catálogo <300 ms a 50 usuarios virtuales; cero errores inesperados y checks al 100 %. Conservar resúmenes y tiempos completos del cliente, no sumar percentiles de endpoints. Estas mediciones todavía no se ejecutaron.

## 8. Cierre de entrega

Importar `postman/DevchesterUnited.postman_collection.json`, configurar baseUrl y variables, ejecutar el flujo y errores; comprobar que no contiene secretos reales. Registrar en `specs/001-user-auth-player-catalog/validation.md`:

- Commit, comandos, versiones, build/pruebas PASSED, arranque y migraciones.
- Resultados E2E, Adapter, migración, concurrencia, revocación, rendimiento y comparación OpenAPI de los cinco endpoints.
- Build/pruebas frontend y SC-010 con backend real; fixtures/versiones, null, recarga/logout y ausencia de llamadas externas en suites.
- Validación real de cobertura/plantillas/temporadas y segunda sincronización, o identificación expresa de este gate pendiente.
- Ruta y resultado de la colección Postman.
- URL de GitHub Actions con Build SUCCESS para ese commit/PR.
- URL SonarCloud, máximo 10 issues abiertos, cero vulnerabilidades y cero smells críticos. Un gate estándar aprobado no reemplaza comprobar estos conteos.

Todo resultado faltante o fallido mantiene pendiente el DoD. Detener el contenedor local al terminar con `docker stop devchester-local`; conservarlo para reutilización, sin borrado de datos en esta guía.
