# Quickstart de validación

## Prerrequisitos

- Java 21 y Docker disponibles.
- PostgreSQL de pruebas iniciado por Testcontainers.
- Node.js/npm instalados.
- Datos de jugador, estadísticas sincronizadas y estrategias de 004 bootstrappeadas.

## Backend

Desde `backend/`:

```powershell
.\gradlew.bat test --no-daemon --console=plain
```

Para el subconjunto durante desarrollo:

```powershell
.\gradlew.bat test --tests '*Simulacion*' --no-daemon --console=plain
```

Comprobar en [contracts/valuation-simulator-api.yaml](contracts/valuation-simulator-api.yaml):

1. `GET` devuelve cotización, estadísticas, posición resuelta, estrategia/version y campos editables.
2. `POST` con horizonte 5 y `goals: 3` proyecta goles actuales + 3, apariciones + 5 y minutos + 450.
3. Un promedio enviado reemplaza el valor; uno omitido se conserva.
4. Un horizonte 4, porcentaje fuera de 0–100, incremento negativo, campo `apps` o métrica faltante devuelve 400 accionable.
5. Un éxito no modifica cotización actual ni histórico; repetir POST produce resultados independientes.
6. Arquero usa únicamente campos disponibles de la estrategia PO provisoria y rechaza métricas ausentes.

## Frontend

Desde `frontend/`:

```powershell
npm run test -- --run
npm run build
npm run test:e2e -- --grep 'simulador|valuation'
```

Validación manual: abrir el detalle, iniciar simulador, seleccionar 3/5/8/10, modificar una métrica, recalcular cinco veces, restablecer y verificar que el loader de Maradona dure al menos 2 s en respuestas rápidas. Verificar que un error no espere y conserve el formulario.

## Seguridad y contrato OpenAPI

- `GET /api/v1/players/{playerId}/valuation-simulator` conserva acceso publico.
- `POST /api/v1/players/{playerId}/valuation-simulator` requiere `Authorization: Bearer <jwt>`.
- POST sin JWT o con JWT invalido devuelve `401`; con JWT valido de usuario normal supera la autorizacion.
- El POST acepta unicamente `horizonMatches` y `modifications`.
- Swagger documenta `200`, `400`, `401`, `404` y `409` para el POST; GET documenta `200`, `404` y `409`.

No existe una coleccion Postman vigente mantenida en el repositorio, por lo que T050 no aplica y no se creo infraestructura nueva.

## Validacion T049-T054

- Backend: `compileJava` pasa. La suite completa fue ejecutada (914 tests, 380 fallos); en este entorno fallan tests preexistentes por `ValorizacionEjecucionConfig` cuando falta `valorizacion.elegibilidad.configurada`, no por cambios de T049-T054. Los tests específicos de simulación, seguridad y no persistencia pasan.
- Frontend: Vitest (24 archivos, 129 tests) y Vite build pasan. Playwright no pudo iniciar porque el puerto `127.0.0.1:4173` ya estaba ocupado por otro proceso; no se modifico el codigo funcional para evitarlo.
- SonarCloud: `scripts/verify-sonar-gate.ps1` se ejecuto. Quality Gate remoto: `OK`; quedaron 41 issues abiertas y 2 code smells criticos, todos ubicados fuera de los archivos de 005 (baseline preexistente). No se modificaron reglas globales ni se identificaron issues nuevos atribuibles a T049-T054.

## Evidencia de no persistencia

Consultar la cotización y el histórico antes y después de una simulación; deben ser idénticos. Los tests de integración deben afirmar que no se invocan repositorios de escritura/publicación y que no aparecen filas nuevas.
