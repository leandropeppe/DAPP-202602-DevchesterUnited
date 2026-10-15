# Contratos: sincronización y detalle de estadísticas

## 1. GET `/api/v1/players/{id}`

`id` recibe el identificador opaco `ply_<uuid>` entregado por el listado. No se
acepta UUID desnudo como contrato público.

Para un jugador activo de un equipo activo, la respuesta conserva el envoltorio
vigente `status`/`data`, mantiene los datos básicos del jugador e incluye:

```json
{
  "status": "success",
  "data": {
    "id": "ply_00000000-0000-0000-0000-000000000000",
    "name": "Jugador Ejemplo",
    "statistics": {
      "generales": {
        "apariciones": 30,
        "ingresosSuplente": 4,
        "minutosJugados": 2570,
        "vecesJugadorDelPartido": 3,
        "duelosAereosGanadosPorPartido": 1.4,
        "valoracion": 7.21,
        "tarjetasAmarillas": 2,
        "tarjetasRojas": 0
      },
      "ofensivas": {
        "goles": 8,
        "asistencias": 5,
        "tirosPorPartido": 2.3,
        "pasesClavePorPartido": 1.1,
        "regatesGanadosPorPartido": 0.8,
        "faltasRecibidasPorPartido": 1.2,
        "fuerasDeJuegoPorPartido": 0.2,
        "desposesionesPorPartido": 0.7,
        "perdidasPorPartido": 0.9
      },
      "defensivas": {
        "tacklesPorPartido": 1.3,
        "intercepcionesPorPartido": 0.6,
        "faltasCometidasPorPartido": 0.8,
        "offsidesProvocadosPorPartido": 0.1,
        "despejesPorPartido": 2.2,
        "regatesRecibidosPorPartido": 0.3,
        "bloqueosPorPartido": 0.2,
        "golesEnContra": 0
      },
      "pases": {
        "pasesPorPartido": 45.7,
        "precisionPases": 86.44,
        "centrosPrecisosPorPartido": 0.4,
        "pasesLargosPrecisosPorPartido": 2.1,
        "pasesFiltradosPrecisosPorPartido": 0.3
      }
    }
  }
}
```

El ejemplo ilustra la forma y no exige que todas las métricas tengan valor.
Cada campo puede ser `null` cuando la ausencia sea legítima. `precisionPases`
conserva porcentaje 0..100: 86.44 no se convierte a 0.8644.

### Respuestas

| Caso | HTTP | Contrato |
|---|---:|---|
| Jugador activo con estadísticas | 200 | `statistics` es el objeto de cuatro familias |
| Jugador activo sin fila estadística | 200 | `statistics: null` |
| Identificador inexistente, jugador/equipo inactivo | 404 | Error público vigente |
| Error inesperado | Según contrato vigente | Error sanitizado, sin detalles de WhoScored |

La consulta usa exclusivamente `JugadorRepository` y
`EstadisticasJugadorRepository.findByJugadorId`. No invoca Adapter, Selenium,
WhoScored ni sincronización.

## 2. Puerto `ProveedorEstadisticasJugador`

El puerto mantiene una operación de adquisición completa por
`CompeticionEstadistica` y devuelve un snapshot consolidado por `playerId`.
El Service no recibe familias separadas y no conoce `category`, `subcategory`,
URLs, parámetros HTTP, Selenium, `stageId`, `tournamentOptions`, `page` ni
`pageSize`.

El snapshot contiene la competición interna, registros consolidados y
`MetricasEstadisticasJugador`. La metadata de transporte, si se necesita para
validación, permanece interna al Adapter.

## 3. Responsabilidad del Adapter

Para cada competición el Adapter:

1. Reutiliza una sesión WebDriver por sincronización.
2. Adquiere `summary/all`, `summary/offensive`, `summary/defensive` y
   `summary/passing`.
3. Deserializa solo los campos necesarios de cada respuesta.
4. Valida `currentPage == 1`, `totalPages == 1`,
   `players.size == totalResults` y metadata coherente.
5. Consolida por `playerId` y aplica la familia propietaria de cada métrica.
6. Devuelve un snapshot completo o un error propio de proveedor.
7. Cierra WebDriver aun ante excepciones.

No realiza matching local ni escribe repositories.

## 4. Responsabilidad del Service

El Service resuelve equipos y jugadores mediante las identidades persistidas,
coordina la consolidación, staging y promoción y conserva la atomicidad.

Una respuesta inválida, incompleta, una falla de cualquiera de las cuatro
familias, un error de staging o una falla de promoción aborta la operación
lógica. No se publica staging parcial y se conserva el snapshot local anterior.

Una métrica ausente en una respuesta válida se escribe como `null`, reemplazando
el valor anterior de esa métrica.

## 5. Contrato de frontend

El frontend usa únicamente `GET /api/v1/players/{id}` y la ruta
`/catalogo/jugadores/{id}`. No consume WhoScored ni APIs de sincronización.

| Estado | Comportamiento |
|---|---|
| Carga | Mostrar estado `loading` comprensible |
| 200 con estadísticas | Habilitar Resumen, Ataque, Defensa y Pases |
| 200 con `statistics: null` | Mostrar datos básicos y aviso de ausencia |
| 404 | Mostrar jugador no encontrado y regreso al catálogo |
| Otro error | Mostrar error sanitizado y acción de regreso |

Las pestañas usan controles semánticos y accesibles. Resumen puede presentar
un subconjunto; Ataque, Defensa y Pases muestran sus familias correspondientes.

## 6. Tests del contrato

- Fixtures locales para las cuatro familias y repetición de campos.
- Tests de respuesta con las 30 métricas y `null` legítimo.
- Test de `precisionPases: 86.44` sin conversión de escala.
- Tests de estados 200, 404, ausencia y error.
- Tests de navegación y activación de las cuatro pestañas.
- Ningún test depende de Internet, credenciales reales o DTOs externos en el
  dominio.
