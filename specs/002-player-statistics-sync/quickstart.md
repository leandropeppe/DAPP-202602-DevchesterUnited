# Guía de validación: estadísticas y enrolamiento WhoScored

## Prerrequisitos

- Java 21 y Docker.
- PostgreSQL de Testcontainers.
- Sin Internet ni credenciales reales de WhoScored.
- Ejecutar desde backend/.

## Comando

    .\gradlew.bat test --no-daemon --console=plain

No se modifican ni borran tests existentes. No se agrega endpoint HTTP.

## Escenarios obligatorios

1. Adapter: convertir respuestas controladas de WhoScored a PaginaEstadisticasJugador y RegistroEstadisticasJugador sin exponer DTOs externos.
2. Paginación: cinco ligas, tres familias, múltiples páginas, cantidades variables y metadata inconsistente.
3. Equipo no enrolado: una coincidencia exacta inequívoca crea identidad WHOSCORED; cero o múltiples coincidencias no vinculan.
4. Jugador no enrolado: una coincidencia por equipo y nombre con evidencia compatible de posición/nacionalidad crea identidad; múltiples o evidencia insuficiente no vinculan.
5. Identidades existentes: una identidad WHOSCORED se resuelve directamente y no vuelve a ejecutar matching.
6. Namespaces: demostrar que IDs iguales de Football-Data y WhoScored no se consideran la misma identidad.
7. Atomicidad: fallar después de crear candidatos, durante una página y durante promoción; no deben quedar identidades WHOSCORED nuevas ni estadísticas parciales.
8. Idempotencia: repetir datos no crea identidades ni filas duplicadas.
9. Valores: aceptar métricas individuales ausentes, rechazar valores inválidos y no inventar ceros.
10. Persistencia: verificar FK, unicidad por proveedor/externalId, unicidad por jugador/proveedor, aislamiento por sync_id y rollback completo.
11. Matching determinista: nombres con diferencias de espacios/mayúsculas se normalizan; similitud difusa, dorsal y orden de aparición no se aceptan.

## Resultado esperado

La suite termina en PASSED, Flyway aplica desde una base vacía y una ejecución fallida conserva el último snapshot válido y no deja enrolamientos parciales visibles.
