# Contrato de integración: Football-Data.org v4

**Fecha**: 2026-09-15. Contrato de diseño del Adapter, no evidencia de una cuenta autenticada. El contrato público está en [openapi.json](./openapi.json).

## Transporte y configuración

Base productiva fija `https://api.football-data.org/v4`; cabecera `X-Auth-Token` tomada de `FOOTBALL_DATA_API_KEY`. Configurar `football-data.api-key` sin valor por defecto utilizable. Ausencia de credencial deshabilita solo sync (503), no el arranque ni las consultas locales. Rechazar redirects para no reenviar el secreto. Solo tests pueden sustituir base por un servidor loopback; nunca recibir URLs del cliente de la API.

Cinco GET secuenciales, sin `season` ni paginación cliente:

| Ruta | Nombre interno | Cantidad exigida |
|---|---|---:|
| `/competitions/PL/teams` | Premier League | 20 |
| `/competitions/PD/teams` | LaLiga | 20 |
| `/competitions/BL1/teams` | Bundesliga | 18 |
| `/competitions/SA/teams` | Serie A | 20 |
| `/competitions/FL1/teams` | Ligue 1 | 18 |

La temporada se toma de `season` recibido para cada competición. No se infiere un año desde el reloj local. Exigir `competition.code` correspondiente, count coherente con teams y temporada con id/fechas válidos. Las cantidades son condiciones de aceptación de esta entrega.

## Payload y traducción

DTOs Jackson del proveedor exclusivamente bajo `adapter/dto`; tolerar campos adicionales del proveedor, sin relajar campos requeridos para formar snapshot. Adapter devuelve representaciones internas explícitas, nunca DTOs HTTP al Controller. Service invoca las invariantes del dominio.

| Fuente | Representación interna |
|---|---|
| team.id | ID externo positivo; identidad estable |
| team.name | Nombre conservado, trim, máximo 100 caracteres |
| shortName, tla, crest | Opcionales; null si no informados; crest HTTPS sin descarga |
| competition.code | Liga canónica según tabla anterior |
| season.id/startDate/endDate | Metadatos de temporada por equipo |
| squad[].id | ID externo de jugador, único globalmente |
| firstName + lastName completos | Nombres separados conservados, trim, máximo 100 cada uno |
| name cuando faltan nombres separados | Nombre completo en first_name; last_name vacío; no dividir por espacios ni inventar apellidos |
| nationality | Texto no vacío, máximo 100; faltante invalida snapshot |
| shirtNumber | Ausente/null → null; presente exige entero 1–99 |
| squad[].position | Tabla explícita siguiente; desconocido/null invalida snapshot |

Diccionario de traducción permitido (valores externos exactos; agregar variantes solo con fixture/documentación):

| Posiciones externas | Posición interna |
|---|---|
| Goalkeeper | Goalkeeper |
| Defence, Defender, Centre-Back, Left-Back, Right-Back | Defender |
| Midfield, Midfielder, Defensive Midfield, Central Midfield, Attacking Midfield, Left Midfield, Right Midfield | Midfielder |
| Offence, Forward, Centre-Forward, Left Winger, Right Winger, Second Striker | Forward |

`squad` ausente, null o vacío es respuesta no utilizable para este caso de uso; no significa plantilla legítimamente vacía ni autoriza bajas. Miembros que no cumplan el contrato invalidan el snapshot; no se omiten silenciosamente. Colapsar registros idénticos del mismo ID/equipo, rechazar divergencias de datos o asociación entre equipos. Máximo técnico 10.000 jugadores únicos.

## Fallos y presupuesto

Conexión 5 s, respuesta 15 s, máximo 10 MiB por respuesta, separación mínima 6 s entre GET de una ejecución. Deadline global 120 s incluye esperas, obtención, validación y persistencia. Sin reintentos automáticos. El timeout transaccional de 150 s es respaldo, no sustituto de deadline de pared/timeout de socket. Proxy HTTP permite 180 s.

Si `X-RequestsAvailable` indica que no hay cuota para continuar, abortar. `X-RequestCounter-Reset` informa el restablecimiento; no esperar más allá del presupuesto ni trasladar headers/cuerpos externos sin procesar al cliente. Headers ausentes/malformados no permiten asumir cuota ilimitada: se mantiene cadence y se maneja 429.

| Resultado externo | Resultado interno/API |
|---|---|
| Credencial/configuración ausente | No hacer HTTP, 503 genérico |
| 400/401/403/404/429/5xx o redirect | Error propio de integración; 503 y rollback |
| Timeout, conexión interrumpida, JSON inválido/truncado | 503 y rollback |
| Cinco respuestas válidas, snapshot consistente | Aplicar cambios y 200 después del commit |
| Respuesta con error embebido o campos obligatorios faltantes | 503 y rollback |
| Otra ejecución con lock global ocupado | 409 antes de llamar al proveedor |

Un fallo no cambia equipos, jugadores, actividad ni timestamps del catálogo. No loguear API Key, cuerpo completo, cabeceras de autenticación ni detalles HTTP al consumidor. Logs locales: correlación, duración, resultado, código de competición y contadores agregados.

## Verificación y límites

Tests del Adapter con servidor HTTP local: casos de tabla, cuota, deadline con reloj/espera controlables, redirects, límite de cuerpo, nombres de una palabra y variantes de posición. Integración Service/PostgreSQL: barrera antes de publicación, competencia de usuarios distintos, lectura anterior durante sync, rollback en último equipo, ausentes/reactivación/transferencia e idempotencia. Todas las respuestas de pruebas son sintéticas; ningún test requiere credenciales reales ni acceso a Internet.

Gate manual real previo a aceptar US4: verificar permiso Squads, cinco respuestas con plantillas utilizables y distribución exigida, corrección de asociaciones y segunda ejecución sin duplicados. No usar una base compartida/productiva para esa validación. Guardar solo evidencia sin secretos. Si falla cobertura o asociación, informar error y conservar catálogo; no prometer completitud basándose en fixtures.

El proveedor documenta rosters de temporada, no garantiza una afiliación contractual exacta de hoy. El snapshot solo puede afirmar lo que publica esa fuente. No se incorpora `/persons/{id}` ni `/teams/{id}` como fallback: implicaría revisar cobertura, costo y presupuesto antes de cambiar el diseño.

Fuentes consultadas: [Competition](https://docs.football-data.org/general/v4/competition.html#_teams), [Team](https://docs.football-data.org/general/v4/team.html), [políticas](https://docs.football-data.org/general/v4/policies.html), [errores](https://docs.football-data.org/general/v4/errors.html), [headers](https://docs.football-data.org/general/v4/lookup_tables.html), [pricing](https://www.football-data.org/pricing).
