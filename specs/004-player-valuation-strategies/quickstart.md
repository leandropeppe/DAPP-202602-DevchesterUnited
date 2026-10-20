# Quickstart de validación: Valorización de jugadores según estrategia

## Prerrequisitos

- Java 21 y Docker disponibles.
- Ejecutar comandos desde `backend/`.
- Las pruebas construyen estadísticas locales; no llaman a WhoScored ni requieren credenciales externas.

## Validación de dominio

```powershell
.\gradlew.bat test --tests '*Valorizacion*' --tests '*Normalizacion*' --tests '*Estrategia*' --no-daemon --console=plain
```

Verificar pesos exactos y score general/posicional para DL, MC, DF y PO; normalización global y por posición; `min == max`; score en [0,1]; métrica positiva faltante con exclusión y causa; métrica negativa faltante sin penalización inventada; y umbral de partidos/minutos definido explícitamente por el test.

## Validación de persistencia y ejecución

```powershell
.\gradlew.bat test --tests '*Cotizacion*IntegrationTest' --tests '*Valorizacion*IntegrationTest' --no-daemon --console=plain
```

Verificar GENERAL v1 y POSITIONAL v1 habilitadas simultáneamente, una única estrategia visible, históricos inmutables separados por estrategia/version, actualización de cotización actual por estrategia/version y procesamiento de jugadores válidos aunque otros sean excluidos.

## Validación de contratos HTTP

```powershell
.\gradlew.bat test --tests '*Cotizacion*E2ETest' --no-daemon --console=plain
```

Consultar [contracts/valuation-api.yaml](contracts/valuation-api.yaml). Sin parámetro de estrategia, la respuesta debe usar la estrategia visible; con estrategia/version explícitas, debe devolver únicamente esa serie. Las respuestas deben incluir score, valor, estrategia, versión y fecha; un jugador sin cotización debe devolver el error documentado.

## Flujo manual/semanal

1. Preparar jugadores de las cuatro posiciones con estadísticas locales.
2. Configurar explícitamente la muestra mínima.
3. Ejecutar el caso de uso manual y consultar cotización e histórico.
4. Cambiar la estrategia visible sin ejecutar valorización y confirmar que inmediatamente se muestra el último resultado ya calculado de la nueva estrategia visible, sin cambiar históricos.
5. Consultar explícitamente cada estrategia y confirmar que las series permanecen separadas.
6. Ejecutar nuevamente y confirmar una nueva entrada por cada combinación jugador + estrategia valorizada.
7. Repetir mediante el disparador semanal y comparar resultados usando el mismo snapshot lógico.

La validación no incluye emisión, compra, venta, balances ni portfolios.

## Migración

La migración de valorización existente es V17__Create_player_valuation.sql, que utiliza snapshot_at dentro de ejecuciones_valorizacion para representar el snapshot lógico único de una ejecución. No se agrega una tabla independiente de snapshot: todas las estrategias de la corrida comparten la lista capturada en memoria y su instante persistido.