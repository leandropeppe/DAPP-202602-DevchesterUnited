# Especificación de Funcionalidad: Sincronización Completa de Estadísticas de Jugadores

**Rama de Funcionalidad (Feature Branch)**: `002-player-statistics-sync`

**Fecha de Creación**: 2026-10-15

**Estado**: Draft

**Entrada**: Descripción del usuario: "Ampliar la feature existente 002-player-statistics-sync para completar el modelo de estadísticas de jugadores obtenido desde WhoScored, consolidando summary, offensive, defensive y passing, evolucionando la persistencia, la API y las cuatro pestañas del detalle del jugador, sin implementar valorización."

## Contexto y objetivo

Esta ampliación completa el dataset de estadísticas de jugadores que ya se sincroniza desde WhoScored. Debe preservar el comportamiento vigente de adquisición, asociación de jugadores, idempotencia, atomicidad y conservación del último estado local válido.

El resultado esperado es una única representación consolidada por jugador con 30 métricas, organizada en cuatro familias de dominio: generales, ofensivas, defensivas y pases. La separación por familias facilita que una futura feature de valorización seleccione y pondere métricas sin conocer el formato externo.

## Clarifications

### Session 2026-10-15

- Q: Cuando una nueva sincronización omite legítimamente una métrica que antes tenía valor, ¿debe reemplazarse el valor persistido por `null`? → A: Sí; una sincronización válida reemplaza el valor anterior por `null`.
- Q: ¿Qué convención de nombres debe usar la respuesta pública para las cuatro familias y sus métricas? → A: Familias y métricas en español `camelCase`, alineadas con el dominio.

## Escenarios de Usuario y Pruebas *(obligatorio)*

### User Story 1 - Sincronizar las cuatro familias de WhoScored (Prioridad: P1)

Como responsable autorizado de la plataforma, quiero sincronizar las familias `summary`, `offensive`, `defensive` y `passing` para disponer del conjunto completo de estadísticas de cada jugador.

**Por qué esta prioridad**: Es el objetivo central de la ampliación y habilita datos completos para las funcionalidades posteriores.

**Prueba Independiente**: Con respuestas externas controladas para las cuatro familias, ejecutar una sincronización y verificar que cada jugador válido queda asociado a las 30 métricas, conservando `null` cuando una estadística no fue informada legítimamente.

**Escenarios de Aceptación**:

1. **Dado** que las cuatro familias entregan datos válidos para un jugador, **cuando** se ejecuta la sincronización, **entonces** el jugador queda con 8 métricas generales, 9 ofensivas, 8 defensivas y 5 de pases.
2. **Dado** que una familia contiene una métrica que también aparece en otra, **cuando** se consolida el jugador, **entonces** existe una sola métrica conceptual y persistente con la familia propietaria definida.
3. **Dado** que `passSuccess` vale `86.44`, **cuando** se consulta la estadística, **entonces** se conserva como 86.44 por ciento y no como 0.8644.

---

### User Story 2 - Mantener sincronización idempotente y consistente (Prioridad: P1)

Como consumidor del catálogo, quiero que la sincronización actualice el único registro estadístico del jugador sin crear duplicados ni exponer estados parciales.

**Por qué esta prioridad**: La confiabilidad de la fuente local es necesaria para el uso posterior del dataset.

**Prueba Independiente**: Sincronizar dos veces el mismo jugador, cambiar una métrica en la segunda ejecución y verificar que se actualiza el registro único, que las demás métricas se conservan correctamente y que un fallo no reemplaza el último estado válido.

**Escenarios de Aceptación**:

1. **Dado** un jugador ya sincronizado, **cuando** se repite la sincronización con datos iguales, **entonces** queda una única representación consolidada.
2. **Dado** un jugador ya sincronizado, **cuando** llega una actualización válida, **entonces** se actualizan sus estadísticas sin crear otra fila ni otra representación por familia.
3. **Dado** que una de las cuatro familias falla, **cuando** termina la sincronización, **entonces** se informa el fallo, no se publican datos parciales y permanece el último estado local válido.

---

### User Story 3 - Consultar el detalle estadístico del jugador (Prioridad: P1)

Como consumidor del catálogo, quiero consultar el detalle local de un jugador con sus estadísticas organizadas por familia, sin depender en tiempo real de WhoScored.

**Por qué esta prioridad**: Expone el dataset consolidado a los consumidores y mantiene la lectura disponible aunque el proveedor externo no responda.

**Prueba Independiente**: Consultar jugadores con las 30 métricas, con valores ausentes legítimos, sin estadísticas y con identificadores inexistentes.

**Escenarios de Aceptación**:

1. **Dado** un jugador activo con estadísticas persistidas, **cuando** se consulta su detalle, **entonces** la respuesta contiene `statistics` con las cuatro familias y sus métricas públicas.
2. **Dado** que falta legítimamente una métrica, **cuando** se consulta el detalle, **entonces** esa métrica se representa como `null` sin inventar un valor.
3. **Dado** un jugador sin estadísticas persistidas, **cuando** se consulta el detalle, **entonces** la respuesta mantiene `statistics: null`.
4. **Dado** un identificador inexistente, un jugador inactivo o un equipo inactivo, **cuando** se consulta el detalle, **entonces** responde como en el contrato vigente para un recurso no disponible.

---

### User Story 4 - Explorar las cuatro categorías en el frontend (Prioridad: P1)

Como usuario del catálogo, quiero navegar entre Resumen, Ataque, Defensa y Pases para encontrar las estadísticas agrupadas de forma comprensible.

**Por qué esta prioridad**: Completa el valor visible de la sincronización y permite revisar todo el dataset sin sobrecargar una sola vista.

**Prueba Independiente**: Abrir el detalle de un jugador con estadísticas, activar cada pestaña y verificar su contenido, estado activo y respuesta ante estadísticas ausentes o errores de carga.

**Escenarios de Aceptación**:

1. **Dado** un detalle cargado correctamente, **cuando** el usuario selecciona Resumen, Ataque, Defensa o Pases, **entonces** la pestaña se activa y muestra las métricas de su familia de forma coherente con el diseño existente.
2. **Dado** un detalle con `statistics: null`, **cuando** se visualiza el jugador, **entonces** se informa la ausencia sin mostrar datos inventados.
3. **Dado** un error o una carga en curso, **cuando** se abre el detalle, **entonces** se muestra el estado correspondiente y una acción clara para volver al catálogo.

---

### User Story 5 - Conservar el catálogo y la identidad existente (Prioridad: P2)

Como responsable de la plataforma, quiero que la ampliación reutilice el matching y la sincronización existentes para no alterar jugadores ya válidos ni crear representaciones alternativas.

**Por qué esta prioridad**: Reduce el riesgo de regresión en una feature ya operativa.

**Prueba Independiente**: Ejecutar los escenarios vigentes de ligas, matching, detalle, ausencia de estadísticas y catálogo, comprobando que sus resultados no se deterioran.

**Escenarios de Aceptación**:

1. **Dado** un jugador identificado por el flujo vigente, **cuando** se sincronizan las cuatro familias, **entonces** sus estadísticas se asocian al mismo jugador existente.
2. **Dado** el catálogo actual, **cuando** se despliega la ampliación, **entonces** se conservan su navegación, identidad y datos básicos.

### Casos límite

- Una respuesta puede omitir legítimamente una métrica; se conserva `null`.
- Una familia puede repetir `minsPlayed`, `rating`, `assistTotal`, `keyPassPerGame` o `passSuccess`; no se crean atributos ni columnas conceptuales duplicados.
- La ausencia o fallo de una familia completa no debe confundirse con la ausencia de una métrica individual: el fallo de adquisición invalida la sincronización lógica y conserva el estado anterior.
- Una respuesta externa puede estar vacía, incompleta, inválida o contener un jugador no asociable; se aplican las reglas vigentes de validación y matching.
- Una sincronización concurrente o repetida no debe crear duplicados ni hacer visibles actualizaciones parciales.
- Un valor decimal puede requerir una escala decimal consistente, pero no debe interpretarse como normalización estadística a `[0,1]`.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE conservar el alcance vigente de sincronización de las cinco competiciones soportadas y procesar todos los jugadores válidos disponibles sin asumir una cantidad fija.
- **FR-002**: El sistema DEBE adquirir, validar y participar de la misma operación de sincronización para las familias `summary`, `offensive`, `defensive` y `passing`.
- **FR-003**: El dominio DEBE representar las estadísticas mediante una composición equivalente a `MetricasEstadisticasJugador` con las familias `MetricasGenerales`, `MetricasOfensivas`, `MetricasDefensivas` y `MetricasPases`, cada una como representación de dominio independiente.
- **FR-004**: El conjunto consolidado DEBE contener exactamente estas 8 métricas generales: `apariciones`, `ingresosSuplente`, `minutosJugados`, `vecesJugadorDelPartido`, `duelosAereosGanadosPorPartido`, `valoracion`, `tarjetasAmarillas` y `tarjetasRojas`.
- **FR-005**: El sistema DEBE mapear desde WhoScored a las métricas generales: `apps` a `apariciones`, `subOn` a `ingresosSuplente`, `minsPlayed` a `minutosJugados`, `manOfTheMatch` a `vecesJugadorDelPartido`, `aerialWonPerGame` a `duelosAereosGanadosPorPartido`, `rating` a `valoracion`, `yellowCard` a `tarjetasAmarillas` y `redCard` a `tarjetasRojas`.
- **FR-006**: El conjunto consolidado DEBE contener exactamente estas 9 métricas ofensivas: `goles`, `asistencias`, `tirosPorPartido`, `pasesClavePorPartido`, `regatesGanadosPorPartido`, `faltasRecibidasPorPartido`, `fuerasDeJuegoPorPartido`, `desposesionesPorPartido` y `perdidasPorPartido`.
- **FR-007**: El sistema DEBE mapear `goal`, `assistTotal`, `shotsPerGame`, `keyPassPerGame`, `dribbleWonPerGame`, `foulGivenPerGame`, `offsideGivenPerGame`, `dispossessedPerGame` y `turnoverPerGame` a las métricas ofensivas indicadas, respectivamente.
- **FR-008**: El conjunto consolidado DEBE contener exactamente estas 8 métricas defensivas: `tacklesPorPartido`, `intercepcionesPorPartido`, `faltasCometidasPorPartido`, `offsidesProvocadosPorPartido`, `despejesPorPartido`, `regatesRecibidosPorPartido`, `bloqueosPorPartido` y `golesEnContra`.
- **FR-009**: El sistema DEBE mapear `tacklePerGame`, `interceptionPerGame`, `foulsPerGame`, `offsideWonPerGame`, `clearancePerGame`, `wasDribbledPerGame`, `outfielderBlockPerGame` y `goalOwn` a las métricas defensivas indicadas, respectivamente.
- **FR-010**: El conjunto consolidado DEBE contener exactamente estas 5 métricas de pases: `pasesPorPartido`, `precisionPases`, `centrosPrecisosPorPartido`, `pasesLargosPrecisosPorPartido` y `pasesFiltradosPrecisosPorPartido`.
- **FR-011**: El sistema DEBE mapear `totalPassesPerGame`, `passSuccess`, `accurateCrossesPerGame`, `accurateLongPassPerGame` y `accurateThroughBallPerGame` a las métricas de pases indicadas, respectivamente.
- **FR-012**: `passSuccess` DEBE conservar semántica porcentual de 0 a 100; por ejemplo, 86.44 DEBE serializarse y persistirse como 86.44, no como 0.8644.
- **FR-013**: Cada métrica repetida DEBE tener una única representación pública, de dominio y persistente, obtenida de la familia propietaria definida en este contrato: `minsPlayed` y `rating` en generales; `assistTotal` y `keyPassPerGame` en ofensivas; `passSuccess` en pases.
- **FR-014**: La ausencia legítima de una métrica DEBE conservarse como `null` según las reglas existentes del dominio; en una sincronización válida, ese `null` DEBE reemplazar cualquier valor anterior de la misma métrica y no debe completarse con valores inventados, cero implícito o una conversión de escala.
- **FR-015**: La sincronización DEBE asociar las cuatro familias al jugador existente correspondiente y no crear un Jugador alternativo.
- **FR-016**: La sincronización DEBE ser idempotente: repetirla para un jugador existente DEBE actualizar su representación consolidada sin crear duplicados.
- **FR-017**: Una sincronización que falle durante adquisición, validación, consolidación, staging o persistencia DEBE conservar el último estado local válido y no publicar resultados parciales.
- **FR-018**: El staging DEBE soportar `passing` y todas las métricas nuevas, manteniendo el mismo control de errores, asociación de identidad y promoción transaccional que las familias vigentes.
- **FR-019**: La persistencia DEBE mantener una única representación consolidada por jugador en `jugador_estadisticas`; NO DEBE crear una tabla por familia.
- **FR-020**: La evolución del esquema DEBE realizarse mediante una nueva migración `V16`; la migración histórica `V15__Create_player_statistics_sync.sql` NO DEBE modificarse.
- **FR-021**: Los DTO externos DEBEN permanecer dentro del Adapter y el contrato de dominio NO DEBE depender de sus estructuras.
- **FR-022**: El adapter DEBE extender la respuesta externa solo con los campos necesarios para las 30 métricas y DEBE consumir la categoría `passing`.
- **FR-023**: Los errores externos DEBEN traducirse a errores propios de la aplicación sin filtrar detalles del proveedor al consumidor.
- **FR-024**: La adquisición externa DEBE continuar desacoplada del caso de uso de sincronización y no debe exigir Internet ni credenciales reales en tests.

### Requisitos de lectura y API

- **FR-025**: El detalle del jugador DEBE conservar el endpoint y las reglas de disponibilidad vigentes, incluyendo el identificador público opaco y la lectura exclusivamente local.
- **FR-026**: Cuando existan estadísticas, la respuesta DEBE exponer una estructura pública coherente con cuatro familias: `generales`, `ofensivas`, `defensivas` y `pases`, usando nombres de familias y métricas en español `camelCase`, sin exponer DTOs de WhoScored, identificadores de infraestructura ni metadatos internos de sincronización.
- **FR-027**: La respuesta DEBE incluir las 30 métricas definidas, con `null` para ausencias legítimas y `precisionPases` en escala porcentual 0..100.
- **FR-028**: Cuando no exista una fila consolidada para el jugador, la API DEBE conservar la respuesta vigente con `statistics: null`.
- **FR-029**: La consulta de detalle NO DEBE invocar WhoScored, Selenium ni una sincronización.
- **FR-030**: Los endpoints públicos afectados DEBEN mantener documentación OpenAPI coherente con la nueva respuesta.

### Requisitos de visualización frontend

- **FR-031**: `PlayerDetailPage` DEBE permitir activar las cuatro pestañas `Resumen`, `Ataque`, `Defensa` y `Pases`; ninguna debe permanecer deshabilitada cuando la respuesta contiene estadísticas.
- **FR-032**: `Resumen` DEBE mostrar un subconjunto coherente de estadísticas generales y datos básicos; no es obligatorio mostrar allí las 30 métricas.
- **FR-033**: `Ataque` DEBE mostrar las métricas ofensivas y `Defensa` DEBE mostrar las métricas defensivas, respetando la distribución del dominio.
- **FR-034**: `Pases` DEBE mostrar las cinco métricas de pases y conservar `precisionPases` como porcentaje visible.
- **FR-035**: La página DEBE conservar sus estados de carga, ausencia, error y regreso al catálogo, respetando el diseño, accesibilidad y convenciones visuales existentes.
- **FR-036**: El frontend DEBE consumir únicamente el contrato documentado del backend y no debe acceder directamente a WhoScored ni a persistencia.

### Requisitos de pruebas y regresión

- **FR-037**: Deben mantenerse todos los tests existentes; no se deben modificar ni eliminar sin autorización explícita.
- **FR-038**: Deben agregarse o extenderse tests sin Internet ni credenciales reales para parsing de `summary`, `offensive`, `defensive` y `passing`.
- **FR-039**: Deben cubrirse consolidación por jugador, repetición de métricas, persistencia, actualización idempotente, staging, ausencias legítimas y fallo de una familia.
- **FR-040**: Deben cubrirse serialización/API y comportamiento de las cuatro pestañas del frontend, además de los estados de carga, ausencia y error.

### Entidades Clave

- **Jugador**: Entidad existente a la que se asocia la información consolidada.
- **Métricas Estadísticas del Jugador**: Composición de cuatro familias de dominio, con una representación única de las 30 métricas.
- **Métricas Generales**: Apariciones, ingresos como suplente, minutos, jugador del partido, duelos aéreos ganados, valoración y tarjetas.
- **Métricas Ofensivas**: Goles, asistencias, tiros, pases clave, regates, faltas recibidas, fueras de juego, desposesiones y pérdidas.
- **Métricas Defensivas**: Tackles, intercepciones, faltas, offsides provocados, despejes, regates recibidos, bloqueos y goles en contra.
- **Métricas de Pases**: Pases, precisión, centros precisos, pases largos precisos y pases filtrados precisos.
- **Familia estadística externa**: Respuesta de WhoScored de tipo `summary`, `offensive`, `defensive` o `passing`, válida solo dentro del Adapter.
- **Registro consolidado**: Única representación persistente de estadísticas asociada a un jugador.

## Criterios de Éxito *(obligatorio)*

### Resultados funcionales

- **SC-001**: El 100% de las sincronizaciones válidas de prueba que contienen las cuatro familias produce exactamente 30 métricas conceptuales por jugador, distribuidas 8/9/8/5.
- **SC-002**: El 100% de las métricas repetidas verificadas se representa una sola vez en dominio, persistencia y respuesta pública.
- **SC-003**: El 100% de los valores `passSuccess` verificados conserva su escala porcentual original, incluyendo valores decimales como 86.44.
- **SC-004**: En el 100% de las sincronizaciones repetidas de prueba queda una única representación por jugador y los cambios válidos actualizan el estado.
- **SC-005**: En el 100% de los fallos inducidos de una familia no se publican datos parciales y se conserva el estado local válido anterior.
- **SC-006**: La persistencia puede evolucionar desde el esquema vigente con V16 sin modificar V15 y sin crear tablas separadas por familia.

### Resultados de API y frontend

- **SC-007**: El 100% de las respuestas de detalle con estadísticas expone las cuatro familias y las 30 métricas definidas con convenciones consistentes.
- **SC-008**: El 100% de las ausencias legítimas verificadas se devuelve como `null` o se presenta como no disponible, sin valores inventados.
- **SC-009**: En el 100% de los casos de prueba, las cuatro pestañas se pueden activar y cada una muestra la familia correspondiente.
- **SC-010**: El 100% de los escenarios de carga, error, jugador inexistente y `statistics: null` conserva un mensaje comprensible y una recuperación clara.
- **SC-011**: El 100% de las consultas de detalle verificadas funciona con datos locales aunque WhoScored no esté disponible.

### Resultados de calidad y regresión

- **SC-012**: Todos los tests existentes continúan pasando sin ser eliminados ni modificados sin autorización.
- **SC-013**: Los tests nuevos de Adapter, servicio, persistencia, API y frontend se ejecutan sin Internet ni credenciales reales.
- **SC-014**: Las suites de compilación y pruebas del backend y frontend pasan antes de considerar terminada la ampliación.

## Supuestos

- La feature amplía `002-player-statistics-sync`; no se crea una carpeta de feature nueva ni se cambia la identidad de la rama existente.
- Se reutilizan las ligas, matching, autorización, identidad, staging, atomicidad y manejo de fallos ya definidos, salvo los cambios necesarios para incorporar `passing` y las nuevas métricas.
- Los tipos numéricos y la escala decimal seguirán las convenciones actuales; estandarizar escala no equivale a normalizar a `[0,1]`.
- La respuesta pública utiliza las familias y métricas del dominio en español `camelCase`, manteniendo las convenciones actuales de la API y sin exponer contratos externos.
- La ausencia de una métrica individual es válida; la ausencia o fallo de una familia requerida para completar una sincronización es un error de la operación.
- La futura valorización elegirá y ponderará métricas, pero no forma parte de esta ampliación.

## Reglas vigentes que se conservan

- La sincronización continúa cubriendo Premier League, La Liga, Serie A,
  Bundesliga y Ligue 1, con la cantidad de jugadores que entregue la fuente en
  cada ejecución.
- La adquisición continúa encapsulada en el Adapter mediante el mecanismo
  vigente de WhoScored. La ampliación agrega la categoría `passing` al mismo
  flujo y no expone sus URLs, parámetros, identificadores de infraestructura ni
  detalles de Selenium al dominio, servicio o API pública.
- Cada respuesta de familia debe validarse como completa según las reglas
  vigentes antes de entrar en staging. Una respuesta estructuralmente
  incompleta no puede promocionarse silenciosamente.
- El matching continúa restringido a jugadores activos del equipo local
  previamente resuelto. Las identidades ambiguas se omiten y se informan; los
  errores técnicos, estructurales, de staging o de promoción siguen siendo
  fatales para la operación lógica.
- La publicación de identidades y estadísticas válidas continúa siendo
  transaccional, idempotente y compatible con el último estado local válido.

## Fuera de Alcance

- Normalización estadística a `[0,1]`.
- Cálculo de score, valor base, factor de escala o estrategias de valorización.
- Cotización, histórico de cotizaciones, rankings por estrategia o mercado.
- Tokenización, compra, venta o transacciones.
- Gráficos u otras visualizaciones no necesarias para las cuatro pestañas.
- Nuevos endpoints públicos de sincronización.
- Consumo de WhoScored desde el frontend o desde una capa distinta del Adapter.
