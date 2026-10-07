# Contratos internos de sincronización

No se agrega endpoint HTTP. El contrato define el puerto entre Service y Adapter y las consultas de persistencia necesarias para enrolamiento inicial.

## Puerto ProveedorEstadisticasJugador

Vive en adapter. Solicita una página por CompeticionEstadistica, FamiliaEstadistica, número de página y tamaño de lote.

Devuelve PaginaEstadisticasJugador con:

- metadata de paginación;
- competición y familia internas;
- registros RegistroEstadisticasJugador.

Cada registro contiene playerId externo, nombre normalizado, posición y nacionalidad opcionales, teamId y nombre de equipo opcionales, y las métricas de su familia. Estos tipos son value objects internos; no son DTOs HTTP ni representan Jugador o Equipo.

## Responsabilidad del Adapter

Traducir DTOs WhoScored a esos value objects, validar HTTP, cuerpo, límites, timeout, familias y metadata. Los DTOs, rutas, stage IDs, nombres de campos JSON, headers y errores del proveedor permanecen dentro de Adapter.

No resuelve identidades locales, no escribe repositorios y no ejecuta matching.

## Responsabilidad del Service

1. Resolver identidades WHOSCORED persistidas.
2. Para identidades ausentes, ejecutar el enrolamiento conservador:
   - equipos por nombre canónico exacto y contexto de competición;
   - jugadores por equipo, nombre canónico exacto y evidencia compatible de posición/nacionalidad.
3. Persistir las identidades nuevas únicamente junto con la promoción.
4. Rechazar cero, múltiples o insuficientes candidatos.
5. Consolidar familias, escribir staging y promover estadísticas atómicamente.

## Repositorios necesarios

Se reutilizan IdentidadExternaJugadorRepository e IdentidadExternaEquipoRepository para resolver y persistir identidades. Se usan JugadorRepository y EquipoRepository para obtener candidatos activos, preferentemente en lote y restringidos por equipo/liga/nombre. Se agregan repositories únicamente para EstadisticasJugador, EstadisticasSync y EstadisticasSyncStaging.

No se agrega un repository de matching ni una tabla de correspondencias.

## Errores

Una respuesta inválida, timeout, paginación incompleta, identidad no resuelta o fallo de persistencia marca la ejecución como fallida y evita la promoción. No se exponen detalles HTTP ni se modifica el último snapshot válido.
