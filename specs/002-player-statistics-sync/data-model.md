# Modelo de datos: estadísticas y vinculación de identidades

## EstadisticasJugador

Fotografía vigente asociada por jugador_id al Jugador local. Contiene posición, minutos, valoración, goles, asistencias, tiros, pases clave, regates, tackles, tarjetas amarillas y rojas. Las métricas individuales ausentes permanecen NULL; no se crean Jugador ni Equipo alternativos.

## Representaciones normalizadas del puerto

El puerto ProveedorEstadisticasJugador atraviesa solo value objects internos, no DTOs:

- PaginaEstadisticasJugador: competición, familia, número/tamaño de página, total esperado y registros.
- RegistroEstadisticasJugador: playerId externo, nombre normalizado, posición normalizada opcional, nacionalidad opcional, teamId externo opcional, nombre de equipo opcional y métricas de la familia.
- CompeticionEstadistica y FamiliaEstadistica: vocabularios internos controlados.

Los atributos de nombre/equipo/posición/nacionalidad son hints transitorios para el primer enrolamiento. No son identidad persistente.

## Persistencia publicada

jugador_estadisticas tendrá una fila por jugador_id, FK a jugadores(id), UNIQUE, métricas normalizadas, referencia de ejecución y auditoría. Se actualiza por upsert dentro de la promoción.

## Ejecución y staging técnico

estadisticas_sync contiene UUID, estado INICIADA/RECIBIENDO/VALIDADA/PUBLICADA/FALLIDA, timestamps, competiciones/familias esperadas y error sanitizado.

estadisticas_sync_staging contiene sync_id, competición, playerId WhoScored, teamId WhoScored cuando exista, jugador_id y equipo_id locales resueltos cuando estén disponibles, hints normalizados, métricas consolidadas y marcas de familias. La clave evita repetir una misma identidad por ejecución, competición y jugador.

El staging no es visible para consultas de negocio. Las identidades nuevas no se consideran publicadas hasta la promoción.

## Identidades

Las relaciones persistentes son:

IdentidadExternaJugador(WHOSCORED, playerId) -> Jugador local

IdentidadExternaEquipo(WHOSCORED, teamId) -> Equipo local

Se reutilizan las entidades y restricciones únicas existentes. No se crea una tabla de matching ni se reintroducen external_id en jugadores/equipos.

## Reglas de enrolamiento

Equipo: comparar nombre canónico exacto, restringido por liga/competición cuando exista. Nombre corto o sigla solo son evidencia adicional comparable, nunca clave.

Jugador: primero resolver equipo; después comparar nombre canónico exacto dentro del equipo. Posición y nacionalidad solo desempatan si están presentes en ambas fuentes y son compatibles. No se usa similitud difusa, dorsal ni ID de Football-Data.

Cero candidatos, múltiples candidatos o evidencia insuficiente producen identidad no resuelta y bloquean la publicación completa. Una coincidencia única permite crear la identidad WHOSCORED durante la promoción.

## Transacción y estados

INICIADA -> RECIBIENDO -> VALIDADA -> PUBLICADA

INICIADA/RECIBIENDO/VALIDADA -> FALLIDA ante error.

La promoción de estadísticas, identidades nuevas y estado PUBLICADA ocurre en una transacción. Si falla, todo revierte y el snapshot anterior permanece visible.

## Migraciones y repositorios

Se agregan migraciones posteriores a V14 para jugador_estadisticas, estadisticas_sync y estadisticas_sync_staging.

Se agregan repositories para estadísticas, ejecuciones y staging. Se amplían los repositories existentes de identidad solo si hace falta obtener candidatos en lote. La búsqueda de candidatos puede agregarse a JugadorRepository y EquipoRepository; no se crea un repository paralelo de matching.
