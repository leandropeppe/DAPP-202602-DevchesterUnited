# Guía de validación: autenticación y catálogo

Esta guía se ejecuta **después de implementar las tareas del plan**. Hoy el repositorio contiene el esqueleto; los endpoints, migraciones, scripts de rendimiento y gates descritos todavía deben implementarse. No representa una ejecución exitosa.

## Prerrequisitos

- Java/JDK 21 en PATH y JAVA_HOME; Docker Engine compatible con contenedores Linux, activo.
- Wrapper Gradle 8.14.4 y Boot 3.5.16 previstos por `plan.md`, dependencias disponibles desde sus repositorios.
- PostgreSQL 16 para arranque local; Testcontainers crea BD descartable para pruebas. No usar una BD compartida/productiva.
- PowerShell para los comandos siguientes; Postman para importar la colección. k6 en el entorno Linux separado para medir rendimiento.
- Ejecutar desde la raíz del repositorio. El puerto 5432 local y 8080 deben estar disponibles.

## 1. PostgreSQL y configuración local

Crear una instancia local dedicada (si ya existe una equivalente, reutilizar su configuración):

```powershell
docker run --name devchester-local -e POSTGRES_DB=devchester -e POSTGRES_USER=devchester -e POSTGRES_PASSWORD=devchester_local -p 127.0.0.1:5432:5432 -d postgres:16
docker exec devchester-local pg_isready -U devchester -d devchester
```

La contraseña anterior es solo para este contenedor local. Configurar el proceso que arrancará la aplicación:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5432/devchester'
$env:SPRING_DATASOURCE_USERNAME = 'devchester'
$env:SPRING_DATASOURCE_PASSWORD = 'devchester_local'
$jwtBytes = New-Object byte[] 32
$jwtRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRandom.GetBytes($jwtBytes)
$jwtRandom.Dispose()
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($jwtBytes)
```

`application.properties` deberá vincular `JWT_SECRET_BASE64` a la configuración JWT. No imprimir ni guardar el secreto en Git. El arranque debe fallar si falta el secreto o es insuficiente, excepto el valor exclusivo de test configurado en recursos de test.

## 2. Build y pruebas

En otra consola sin el perfil local heredado, ejecutar:

```powershell
java -version
docker info
.\gradlew.bat --version
.\gradlew.bat clean build
```

Esperado: Java 21, Gradle 8.14.4, BUILD SUCCESSFUL y todas las pruebas sin fallos ni omisiones. `build/reports/tests/test/index.html` incluye el `contextLoads` existente, unitarias de dominio, integración de Services/Repositories y E2E. Recursos test deben configurar PostgreSQL Testcontainers incluso para el test existente; no editarlo para hacer pasar el build. Docker ausente es fallo de prerrequisito, no motivo para omitir integración.

Verificar dependencias si se necesita diagnosticar compatibilidad:

```powershell
.\gradlew.bat dependencyInsight --dependency junit-jupiter --configuration testRuntimeClasspath
```

Esperado: Jupiter 5, sin Framework 7 ni Jupiter 6. La CI deberá añadir cobertura JaCoCo y ejecutar SonarCloud; sus tareas se configurarán durante la implementación.

## 3. Arranque y flujo HTTP

En la consola con las variables locales:

```powershell
.\gradlew.bat bootRun
```

Esperado: Flyway aplica migraciones y semilla, Hibernate valida el esquema, aplicación escucha en 8080. Swagger UI: `http://localhost:8080/swagger-ui.html`; contrato generado: `http://localhost:8080/v3/api-docs`. Comparar rutas, seguridad y esquemas con [contracts/openapi.json](./contracts/openapi.json).

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

## 5. Rendimiento

Implementar `performance/registro-login.js`, `performance/api-key.js`, `performance/catalogo.js` y un fixture determinista según [plan.md](./plan.md). Antes de ejecutarlos, preparar la BD descartable de rendimiento con los 10.000 jugadores y 1.000 usuarios indicados. Registrar checksum y recursos; no poblar mediante un endpoint administrativo nuevo.

En el generador Linux, con BASE_URL y credenciales del fixture configuradas como variables de entorno y sin imprimir secretos:

```bash
k6 run --summary-export registro-login.json performance/registro-login.js
k6 run --summary-export api-key.json performance/api-key.js
k6 run --summary-export catalogo.json performance/catalogo.js
```

Los scripts deben implementar el calentamiento excluido, las métricas y los umbrales del plan. Ejecutar tres repeticiones restaurando el fixture en la BD descartable. Esperado: salida cero, p95 conjunto registro+login <2000 ms, API Key <500 ms, catálogo <300 ms a 50 usuarios virtuales; cero errores inesperados y checks al 100 %. Conservar resúmenes y tiempos completos del cliente, no sumar percentiles de endpoints. Estas mediciones todavía no se ejecutaron.

## 6. Cierre de entrega

Importar `postman/DevchesterUnited.postman_collection.json`, configurar baseUrl y variables, ejecutar el flujo y errores; comprobar que no contiene secretos reales. Registrar en `specs/001-user-auth-player-catalog/validation.md`:

- Commit, comandos, versiones, build/pruebas PASSED, arranque y migraciones.
- Resultados E2E, concurrencia, revocación, rendimiento y comparación OpenAPI.
- Ruta y resultado de la colección Postman.
- URL de GitHub Actions con Build SUCCESS para ese commit/PR.
- URL SonarCloud, máximo 10 issues abiertos, cero vulnerabilidades y cero smells críticos. Un gate estándar aprobado no reemplaza comprobar estos conteos.

Todo resultado faltante o fallido mantiene pendiente el DoD. Detener el contenedor local al terminar con `docker stop devchester-local`; conservarlo para reutilización, sin borrado de datos en esta guía.
