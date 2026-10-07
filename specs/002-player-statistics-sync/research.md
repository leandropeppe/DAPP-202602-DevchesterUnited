# Investigación técnica: sincronización y vinculación de identidades

**Feature**: 002-player-statistics-sync | **Fecha**: 2026-10-07

## Atributos realmente disponibles

El catálogo actual de Football-Data normaliza jugadores con nombre, posición, nacionalidad y equipo; los equipos tienen nombre, liga, nombre corto y sigla. El dominio persistido de Jugador conserva nombre, posición, nacionalidad y equipoId; Equipo conserva nombre y liga.

El contrato de WhoScored todavía no existe en el código. Por eso el Adapter deberá producir únicamente hints normalizados si la respuesta externa realmente los entrega: nombre de jugador, posición normalizada, nacionalidad, nombre/identificador de equipo y competición. Si falta un hint requerido, no se inventará una regla alternativa.

## Puerto específico

ProveedorEstadisticasJugador vive en adapter y no reutiliza ProveedorCatalogo. ProveedorCatalogo entrega el snapshot del catálogo Football-Data; el nuevo puerto entrega páginas de estadísticas WhoScored. El puerto no expone HTTP, JSON, DTOs, stage IDs ni nombres específicos del proveedor.

## Vinculación inicial automática controlada

La vinculación se ejecuta en Service antes de guardar el registro de estadísticas. Primero se intenta resolver la identidad WHOSCORED ya persistida. Solo si no existe se ejecuta el enrolamiento inicial.

Para equipos:

1. El Adapter entrega teamId, nombre normalizado y competición/liga cuando estén disponibles.
2. Se obtienen candidatos locales activos de la liga/competición correspondiente.
3. Se comparan nombres canónicos de forma determinista: trim, case-fold, espacios Unicode normalizados y comparación exacta; no se usa similitud difusa.
4. Se acepta solo un candidato inequívoco.
5. Se persiste IdentidadExternaEquipo con proveedor WHOSCORED y el teamId.

El nombre corto y la sigla solo pueden actuar como evidencia adicional si el contrato de WhoScored entrega una señal comparable; no son identidad persistente. Si hay cero, múltiples candidatos o falta la liga cuando sea necesaria para desambiguar, el equipo no se vincula.

Para jugadores:

1. El Adapter entrega playerId, nombre, equipo resuelto por teamId/nombre, posición normalizada y nacionalidad cuando existan.
2. Se resuelve primero el equipo local.
3. Se buscan jugadores locales activos de ese equipo cuyo nombre canónico coincida exactamente.
4. Si existen varios, se filtra únicamente con posición normalizada y nacionalidad cuando ambas fuentes las informan y son compatibles.
5. Se acepta solo un candidato después de todos los filtros.
6. Se persiste IdentidadExternaJugador con proveedor WHOSCORED y el playerId.

La posición y nacionalidad no son claves permanentes: solo evidencia del enrolamiento. La ausencia o incompatibilidad de evidencia impide elegir. No se usan dorsal, nombre parecido, orden de aparición ni ID Football-Data como sustitutos.

Después de crear la identidad, las páginas posteriores y sincronizaciones futuras resuelven exclusivamente por las tablas de identidades. El matching no se repite innecesariamente.

## Resultado conservador

La clasificación es:

- cero candidatos: no resuelta;
- un candidato con evidencia suficiente: vinculación permitida;
- varios candidatos: ambigua;
- atributos faltantes o incompatibles: evidencia insuficiente.

Cualquier identidad requerida no resuelta impide validar y promocionar el snapshot completo. Las identidades creadas durante la ejecución deben formar parte de la misma publicación atómica o quedar revertidas; no debe quedar una identidad nueva asociada a una sincronización fallida.

## Atomicidad y staging

Las páginas se obtienen fuera de la transacción de promoción. El staging por ejecución conserva los datos normalizados y los resultados de resolución. Las escrituras por lote son transacciones cortas. La promoción incluye estadísticas e identidades WHOSCORED nuevas en una única transacción. Un fallo de página, matching o promoción conserva el snapshot anterior y no deja enrolamientos parciales visibles.
