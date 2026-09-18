# E2E local de US5

Desde `frontend/`, con Docker Desktop disponible:

```powershell
npm install
npm run verify:frontend
npm run build
..ackend\gradlew.bat bootJar --no-daemon --console=plain
$env:DEV_API_TARGET = 'http://127.0.0.1:18080'
npm run test:e2e
```

Playwright levanta PostgreSQL 16 aislado mediante `e2e/compose.yml`, inicia el `bootJar` local en `127.0.0.1:18080`, espera `/v3/api-docs`, ejecuta Flyway y carga `fixture.sql`. Vite sirve el bundle en `127.0.0.1:4173`. Todas las credenciales son sintéticas y no se realizan llamadas a Football-Data.
