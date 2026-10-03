# Contrato de sincronización de estadísticas de jugadores

## Caso de uso administrativo

Se incorpora un disparador administrativo para ejecutar una sincronización completa de estadísticas. El contrato debe documentarse con OpenAPI y respetar la autenticación administrativa ya existente.

### Solicitud

- Método: `POST`
- Ruta propuesta: `/api/v1/player-statistics/sync`
- Cuerpo: ausente
- Parámetros de WhoScored: no forman parte del contrato público.

### Respuesta exitosa

Código `200 OK`, con una respuesta envolvente consistente con los contratos existentes y un resumen que incluya como mínimo:

- competiciones procesadas;
- jugadores considerados;
- estadísticas creadas;
- estadísticas actualizadas;
- identificador/fecha de la última sincronización publicada, sin exponer detalles internos del proveedor.

La respuesta solo se entrega después de la promoción atómica. No se devuelve éxito si el staging quedó incompleto.

### Errores públicos

| Código | Situación | Comportamiento |
|---|---|---|
| `400` | Solicitud con cuerpo o formato no permitido | No inicia la sincronización. |
| `401` | Credencial ausente, inválida o usuario inexistente | No consulta al proveedor. |
| `409` | Otra sincronización de estadísticas en curso | No consulta al proveedor ni modifica estadísticas. |
| `503` | WhoScored indisponible, timeout, respuesta inválida, cuota no disponible o persistencia no disponible | Conserva el último estado local válido. |
| `500` | Error inesperado | No expone detalles del proveedor; conserva el estado publicado si la promoción no terminó. |

Los mensajes de error son propios de la aplicación y no incluyen URL, parámetros, cuerpo JSON, credenciales ni mensajes internos de WhoScored.

## Contrato interno del proveedor

La interfaz interna de proveedor debe permitir solicitar una página por:

- competición soportada;
- familia `general`, `ofensiva` o `defensiva` de la representación interna;
- número de página;
- tamaño de lote configurado.

Debe devolver una página normalizada con:

- jugadores estadísticos identificados por `playerId`;
- métricas de la familia solicitada;
- metadata de paginación necesaria para continuar o validar completitud.

La interfaz no debe mencionar URL, nombres de parámetros, `stageId`, JSON, clases DTO externas ni detalles de transporte. La traducción de `summary/all`, `summary/offensive` y `summary/defensive` ocurre exclusivamente dentro del Adapter.

## Compatibilidad

- No se cambia el contrato de consulta pública del catálogo de jugadores.
- No se agregan estadísticas al frontend en esta feature.
- El nuevo contrato administrativo se registrará en OpenAPI v3 y se probará con MockMvc.
