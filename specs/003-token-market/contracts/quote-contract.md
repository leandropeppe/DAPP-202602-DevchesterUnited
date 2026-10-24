# Contrato de cotizaciones: Player Valuation a Token Market

## Propósito

Este es un único contrato funcional de cotizaciones y una única fuente de verdad compartida por Player Valuation y Token Market. Token Market consume versiones publicadas; no calcula precios ni mantiene una tabla paralela para el precio inicial.

## Datos requeridos de una cotización

Cada versión de cotización inmutable identifica:

- identificador estable del jugador;
- identificador de cotización y versión distinguible monotónicamente;
- precio por token, representado con semántica monetaria de dos decimales;
- fecha de publicación y comienzo de vigencia;
- fin de vigencia opcional y estado explícito de vigencia;
- referencia opcional a la estrategia de valoración utilizada.

Los estados de vigencia distinguen al menos válida, reemplazada, invalidada y vencida. La escritura exacta del enum es una coordinación de implementación.

## Reglas de versión y ejecución

Player Valuation publica una nueva versión inmutable y avanza la cabecera de cotización actual del jugador. Token Market recibe la identidad/versión seleccionada por el usuario y, en el mismo flujo transaccional ordenado de la operación, verifica que esa versión exacta continúe válida. El reemplazo, la invalidación o el vencimiento rechazan toda la operación aunque la cotización nueva tenga el mismo precio numérico. El mercado nunca sustituye silenciosamente una versión.

La cotización inicial es de 1.00 crédito por token y se persiste mediante el mismo límite de publicación y fuente de datos. No vence automáticamente después de siete días; permanece válida hasta que se reemplace o invalide explícitamente. Si no existe una cotización válida, la ejecución de operaciones no está disponible. El último precio conocido inválido/histórico puede mostrarse únicamente con una marca histórica explícita.

## Invalidación exacta

El mismo límite de publicación expone `invalidate(QuoteInvalidationRequest)` para que
Player Valuation solicite la invalidación de la referencia exacta `(playerId,
quoteId, version)`. La operación solo modifica la cabecera operativa: las
versiones históricas, su precio y sus fechas permanecen inmutables.

La cabecera conserva `VALID` o `INVALIDATED`, `invalidatedAt` y el motivo
normalizado. La solicitud solo se acepta si la referencia sigue siendo la
vigente y no venció (`validUntil` es exclusivo). Una referencia reemplazada,
ausente o vencida se rechaza; una repetición exacta devuelve el resultado
durable original. Una publicación posterior avanza la cabecera y la restablece
a `VALID`, sin reactivar la versión anterior.

## Publicación inicial antes de Player Valuation

Antes de que exista la feature completa Player Valuation, el `MarketInitializationService` automático invoca el `QuotePublicationPort.publishInitialQuote(playerId, 1.00, nonExpiring)` interno para cada jugador elegible. El port es el único límite de publicación, respaldado por el adaptador de persistencia de cotizaciones compartido. Crea la misma representación de versión inmutable/cabecera actual que utilizarán las publicaciones dinámicas futuras. El inicializador solo puede utilizar el valor fijo 1.00; no contiene estrategias, estadísticas, scraping ni una segunda estructura de cotizaciones.

El coordinador de inicio se dispara cuando la aplicación está disponible, no mediante un endpoint público. Se ejecuta idempotentemente bajo la barrera de inicialización del estado del mercado: crea/verifica el superusuario protegido y la reserva finita, asigna una vez los créditos a usuarios preexistentes, emite una vez para jugadores activos/activados por primera vez y llama al port de publicación solo cuando un jugador elegible no posee una cotización inicial. Un fallo deja el mercado no disponible para trading; un reintento reanuda pasos faltantes sin reemplazar cotizaciones iniciales válidas ni duplicar asignaciones/emisiones.

## Distribución de responsabilidades

| Responsabilidad | Token Market | Player Valuation | Player Statistics Sync |
|---|---|---|---|
| Calcular precio/estrategia | No | Sí | No |
| Publicar versiones inmutables | Orquesta la siembra inicial mediante `QuotePublicationPort` | Autoridad funcional y publicador dinámico futuro mediante el mismo port | No |
| Validar cotización exacta en la ejecución | Sí | Proporciona el contrato de vigencia/versión | No |
| Mantener vigencia/historial de cotizaciones | Almacena la referencia de la operación y utiliza el estado publicado | Posee el ciclo de vida, la política y los registros de fallos de recálculo | No |
| Obtener/persistir estadísticas de jugadores | No | Las consume cuando corresponde | Sí |
| Registrar movimientos confirmados del mercado | Sí | No | No |

## Requisitos de coordinación

Player Valuation debe acordar la clave estable del jugador, la identidad/versión de cotización, la semántica de estados de vigencia, la publicación atómica de la cabecera y el formato de referencia de estrategia. El port de publicación y la representación de persistencia son compartidos; ninguna implementación de Token Market puede crear una segunda fuente de verdad. La coordinación restante es la propiedad del adaptador/paquete compartido y el nombre final del port interno, no un cambio del límite funcional.
