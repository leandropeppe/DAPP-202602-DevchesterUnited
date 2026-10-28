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

## Evidencia de no persistencia

Consultar la cotización y el histórico antes y después de una simulación; deben ser idénticos. Los tests de integración deben afirmar que no se invocan repositorios de escritura/publicación y que no aparecen filas nuevas.
