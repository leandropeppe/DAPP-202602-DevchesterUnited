# Plan de implementación: Sincronización de estadísticas de jugadores

**Branch**: 002-player-statistics-sync | **Fecha**: 2026-10-07 | **Spec**: spec.md

## Resumen

La feature sincroniza una fotografía vigente de estadísticas hacia Jugador local existente. WhoScored se consume mediante ProveedorEstadisticasJugador; ProveedorCatalogo continúa siendo exclusivamente el puerto del catálogo Football-Data.

La sincronización obtiene y valida páginas fuera de la transacción final, conserva lotes en staging técnico y publica estadísticas e identidades WHOSCORED nuevas mediante una única promoción atómica. El staging es necesario para memoria acotada, red fuera de transacción y consistencia global.

## Contexto actual

- Jugador y Equipo no tienen campos legacy de identificación externa.
- IdentidadExternaJugador/Equipo y sus repositories son la fuente persistente de identidad por proveedor.
- ProveedorIdentidadExterna.WHOSCORED ya existe.
- Jugador actual expone nombre, posición, nacionalidad y equipoId; Equipo expone nombre y liga.
- El catálogo Football-Data entrega nombre, posición, nacionalidad y equipo para jugadores, y nombre/liga/nombre corto/sigla para equipos.
- No se modifica JugadorMapper para transportar estadísticas.

El contrato WhoScored aún no existe en el código. El Adapter deberá entregar los hints normalizados definidos en contracts. Si una respuesta no contiene evidencia suficiente para vinculación, el Service no inventará una regla.

## Flujo arquitectónico

1. El Service adquiere exclusión y crea una ejecución INICIADA.
2. El Adapter solicita páginas de las cinco competiciones y familias.
3. El Adapter valida transporte, respuesta y paginación, y devuelve value objects internos.
4. El Service primero intenta resolver playerId/teamId por identidades WHOSCORED persistidas.
5. Para cada identidad ausente ejecuta la fase de enrolamiento inicial controlado.
6. Valida y consolida familias por playerId.
7. Escribe cada lote en staging mediante transacciones cortas.
8. Marca la ejecución VALIDADA solo con las cinco competiciones completas.
9. Una transacción breve promueve estadísticas, persiste identidades WHOSCORED nuevas y marca PUBLICADA.
10. Libera exclusión.

## Fase explícita de enrolamiento inicial

### Equipos

Si falta IdentidadExternaEquipo WHOSCORED, se obtienen candidatos locales activos de la liga/competición. Se compara nombre canónico exacto: trim, case-fold, espacios Unicode normalizados y comparación exacta. La liga restringe el conjunto cuando está disponible. Nombre corto o sigla solo pueden aportar evidencia adicional comparable; nunca son identidad persistente.

Solo un candidato inequívoco puede vincularse. Cero candidatos, múltiples candidatos, liga ausente cuando sea necesaria o datos insuficientes dejan el teamId no resuelto.

### Jugadores

Si falta IdentidadExternaJugador WHOSCORED, primero se resuelve el equipo. Luego se buscan candidatos locales activos de ese equipo con nombre canónico exacto. Posición normalizada y nacionalidad solo filtran/desempatan cuando ambas fuentes las informan y son compatibles. Se acepta solo un candidato final.

No se usa similitud difusa, dorsal, orden de aparición ni el ID de Football-Data. Los atributos de matching son evidencia transitoria, no claves persistentes.

Las nuevas identidades se escriben como IdentidadExternaJugador/Equipo con proveedor WHOSCORED y se publican en la misma transacción que las estadísticas. Sincronizaciones posteriores resuelven exclusivamente por identidad persistida.

## Resultados no resolubles

Cero coincidencias, múltiples coincidencias o evidencia insuficiente producen identidad no resuelta/ambigua. Si es requerida, la sincronización completa falla, no promociona staging ni identidades nuevas y conserva el último snapshot válido.

## Staging y atomicidad

El staging contiene ejecución, competición, playerId/teamId externos, hints normalizados, UUID locales resueltos, métricas consolidadas y marcas de familias. No representa Jugador/Equipo ni es visible para negocio.

Se escribe después de validar cada página/lote. Una página intermedia fallida marca la ejecución FALLIDA y no toca lo publicado. La promoción de estadísticas, identidades nuevas y estado PUBLICADA es una sola transacción; cualquier error hace rollback.

## Estructura prevista

backend/src/main/java/com/devchesterunited/devchesterunitedv2/
- adapter/dto: DTOs exclusivos de WhoScored
- adapter/impl/WhoScoredStatisticsAdapter.java
- adapter/ProveedorEstadisticasJugador.java
- modelo: estadísticas y value objects normalizados
- persistence/dto: entidades publicadas y staging
- persistence: repositories
- service/impl y service/interfaces: orquestación

No se agregan DTOs públicos, representaciones alternativas de Jugador/Equipo, tablas de matching ni endpoint HTTP.

## Constitution Check

- Adapter encapsula WhoScored; Service orquesta; Modelo permanece independiente.
- Identidad persistente por IdentidadExternaJugador/Equipo, nunca por atributos de matching.
- Red separada de transacción de promoción.
- Staging justificado por atomicidad y volumen.
- Errores de proveedor, matching o persistencia conservan el snapshot anterior.
- Tests cubren matching 0/1/múltiples, enrolamiento, idempotencia, fallos y rollback sin Internet.

## Complejidad justificada

| Elemento | Motivo |
|---|---|
| Staging persistido | Necesario para no acumular todas las páginas y mantener publicación atómica. |
| Fase de enrolamiento | Necesaria porque los IDs Football-Data y WhoScored no se presuponen equivalentes y el catálogo actual puede carecer de identidad WHOSCORED. |
