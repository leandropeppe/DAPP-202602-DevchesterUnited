# Feature Specification: Valorización de jugadores según estrategia

**Feature Branch**: `004-player-valuation-strategies`

**Created**: 2026-10-17

**Status**: Draft

**Input**: User description: "Feature 004 — Valorización de jugadores según estrategia"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Calcular cotizaciones para estrategias habilitadas (Priority: P1)

Como responsable del mercado de jugadores, necesito ejecutar una valorización sobre todas las estrategias habilitadas para cálculo, de modo que cada estrategia conserve su propia cotización actual e histórico y los resultados puedan compararse con el mismo snapshot estadístico.

**Why this priority**: Es el objetivo central del feature y permite producir simultáneamente las cotizaciones GENERAL v1 y POSITIONAL v1 sin depender de cuál se muestra por defecto.

**Independent Test**: Con GENERAL v1 y POSITIONAL v1 habilitadas, jugadores elegibles y estadísticas conocidas, ejecutar una valorización manual y verificar que se genera un resultado independiente por estrategia, cada uno con su score, versión y cotización.

**Acceptance Scenarios**:

1. **Given** GENERAL v1 y POSITIONAL v1 habilitadas, jugadores elegibles y un snapshot estadístico disponible, **When** se ejecuta una valorización, **Then** cada jugador que pueda valorizarse correctamente recibe un resultado independiente para cada estrategia habilitada.
2. **Given** una ejecución con varias estrategias habilitadas, **When** comienza el cálculo, **Then** se capturan sus identificadores y versiones y se usa el mismo snapshot lógico de estadísticas para todas ellas.
3. **Given** un jugador no elegible para POSITIONAL v1, **When** se ejecuta la valorización, **Then** su exclusión y causa para POSITIONAL v1 no impiden calcularlo para GENERAL v1 si cumple sus reglas.
4. **Given** un jugador con 10 tokens y una cotización de 100, **When** la cotización correspondiente cambia a 120 o 90, **Then** el valor conceptual de la posición sería 1200 o 900, sin que este feature cree ni administre tokens.

### User Story 2 - Comparar rendimiento general y posicional (Priority: P1)

Como responsable del mercado, necesito disponer de una estrategia general y una estrategia posicional para valorar de manera uniforme o contextualizada a los jugadores según el objetivo de análisis.

**Why this priority**: Las dos estrategias son un requisito funcional principal y permiten valorar defensores, mediocampistas, delanteros y arqueros con criterios adecuados a su función.

**Independent Test**: Ejecutar GENERAL v1 y POSITIONAL v1 sobre el mismo snapshot y verificar que la general usa una población común, mientras que la posicional usa la población de la misma posición y aplica pesos y penalizaciones propios.

**Acceptance Scenarios**:

1. **Given** jugadores de varias posiciones, **When** se aplica GENERAL v1, **Then** todos se evalúan con la misma fórmula y los mismos pesos positivos.
2. **Given** jugadores clasificados como DL, MC, DF y PO, **When** se aplica POSITIONAL v1, **Then** cada grupo utiliza la configuración correspondiente a su posición.
3. **Given** dos jugadores con igual estadística bruta pero pertenecientes a posiciones distintas, **When** se normaliza su estadística con POSITIONAL v1, **Then** cada uno se compara contra los jugadores de su propia posición.
4. **Given** un defensor con goles, tackles, intercepciones y despejes, **When** se calcula su score posicional, **Then** los goles contribuyen positivamente junto con las métricas defensivas.

### User Story 3 - Consultar cotización visible, específica e histórico trazable (Priority: P1)

Como consumidor del mercado, necesito consultar la cotización e histórico de un jugador por defecto según la estrategia visible, y poder solicitar una estrategia específica cuando corresponda.

**Why this priority**: La estrategia visible define la presentación oficial sin limitar qué estrategias se calculan ni borrar las series alternativas.

**Independent Test**: Generar resultados de GENERAL v1 y POSITIONAL v1, consultar sin especificar estrategia, consultar cada estrategia explícitamente, cambiar la estrategia visible y verificar que la respuesta por defecto cambia al último valor ya calculado sin modificar ningún histórico.

**Acceptance Scenarios**:

1. **Given** cotizaciones actuales para GENERAL v1 y POSITIONAL v1 y POSITIONAL v1 visible, **When** se consulta sin estrategia, **Then** se devuelve POSITIONAL v1.
2. **Given** cotizaciones actuales para ambas estrategias, **When** se consulta explícitamente GENERAL v1, **Then** se devuelve GENERAL v1 aunque POSITIONAL v1 sea visible.
3. **Given** históricos independientes por estrategia, **When** se consulta el histórico sin estrategia, **Then** se devuelve el histórico de la estrategia visible.
4. **Given** históricos independientes por estrategia, **When** se consulta el histórico con una estrategia específica, **Then** se devuelve únicamente la serie de esa estrategia y versión.
5. **Given** GENERAL v1 y POSITIONAL v1 ya calculadas, **When** cambia la estrategia visible de POSITIONAL v1 a GENERAL v1, **Then** se muestra inmediatamente el último valor GENERAL v1 y su histórico previo, sin recalcular ni eliminar datos.

### User Story 4 - Ejecutar valorizaciones periódicas y tolerantes a datos incompletos (Priority: P2)

Como responsable operativo, necesito que la valorización pueda ejecutarse semanalmente o de forma manual y que la falta de datos de un jugador o una estrategia no impida procesar el resto.

**Why this priority**: Mantiene las cotizaciones actualizadas y permite probar o administrar el cálculo sin depender exclusivamente del proceso automático.

**Independent Test**: Ejecutar una valorización con varias estrategias, un jugador sin estadísticas, otro con una métrica positiva requerida ausente, otro con una métrica negativa ausente y una población válida, comprobando que las causas se informan por estrategia sin impedir las cotizaciones válidas.

**Acceptance Scenarios**:

1. **Given** que llegó el momento de la periodicidad semanal, **When** se ejecuta el proceso, **Then** obtiene las estrategias habilitadas, captura sus versiones, usa un snapshot estadístico único, calcula cada estrategia y registra sus resultados.
2. **Given** una solicitud de valorización manual, **When** se ejecuta, **Then** produce el mismo resultado determinista que una ejecución equivalente del proceso periódico.
3. **Given** un jugador sin información suficiente para una estrategia y otros jugadores válidos, **When** se valoriza la población, **Then** la exclusión se registra para esa estrategia y los demás jugadores y estrategias continúan.
4. **Given** una estrategia habilitada que falla al procesar un jugador, **When** continúa la ejecución, **Then** la falla queda registrada y no impide procesar otras estrategias ni jugadores válidos.

## Edge Cases

- Si no existen estrategias habilitadas, la ejecución finaliza sin nuevas cotizaciones y comunica la causa.
- Si una estrategia habilitada no tiene jugadores elegibles, no genera históricos para esa estrategia, registra la causa y no impide el resto.
- Si un jugador no tiene estadísticas, no se genera una cotización inválida ni se detiene la valorización de otras combinaciones jugador-estrategia.
- Si falta una métrica positiva requerida por una estrategia, el jugador queda no elegible para esa estrategia y se registra la causa; no se alteran implícitamente los pesos.
- Si falta una métrica utilizada exclusivamente como penalización negativa, no se inventa una penalización y se registra la ausencia para esa estrategia.
- Si la población elegible está vacía, la ejecución finaliza sin nuevas cotizaciones y comunica la causa.
- Si `min == max` para una métrica, la normalización devuelve un valor seguro y consistente definido por la configuración, sin división por cero.
- Si la posición es desconocida o no pertenece a DL, MC, DF o PO, el jugador no se valoriza con POSITIONAL v1 y queda informado como no elegible para esa estrategia.
- Si un jugador no alcanza la muestra estadística mínima configurable, no recibe cotización para esa estrategia; el motivo queda disponible para auditoría.
- Si el score queda fuera de [0,1] por penalizaciones, se limita al rango [0,1].
- Si se intenta dejar el sistema sin estrategia visible cuando existe configuración operativa, la selección se rechaza y se conserva la visible anterior.
- Si se selecciona una estrategia visible no habilitada o inexistente, la operación se rechaza sin modificar la selección visible.
- Si se cambia la estrategia visible, no se ejecuta una nueva valorización, no se modifican históricos, no se recalculan valores y no se eliminan cotizaciones de otras estrategias.
- Si se cambia una configuración o versión, las cotizaciones históricas ya generadas permanecen inmutables.
- Si la estrategia de arquero no dispone de estadísticas específicas, se utiliza explícitamente la configuración provisoria de PO y no se presenta como evaluación especializada de atajadas.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST representar múltiples estrategias de valorización disponibles simultáneamente, cada una con identificador, nombre, versión, métricas, pesos positivos, penalizaciones y estado de habilitación para cálculo.
- **FR-002**: Deben existir inicialmente GENERAL v1 y POSITIONAL v1 habilitadas para cálculo.
- **FR-003**: El sistema MUST distinguir explícitamente entre estrategia habilitada para cálculo y estrategia visible/oficial; el término "activa" no debe utilizarse para representar habilitación cuando pueda generar ambigüedad.
- **FR-004**: El sistema MUST mantener como máximo una estrategia visible seleccionada; cuando existe configuración operativa, el caso de uso de selección MUST impedir dejar el sistema sin estrategia visible.
- **FR-005**: Cambiar la estrategia visible MUST ser atómico y MUST cambiar únicamente qué cotización e histórico se devuelven por defecto; no debe iniciar una valorización, recalcular, modificar históricos, eliminar cotizaciones ni deshabilitar cálculos.
- **FR-006**: El sistema MUST obtener el conjunto de estrategias habilitadas al inicio de cada ejecución y capturar el identificador y versión de cada una antes de calcular.
- **FR-007**: El sistema MUST utilizar un mismo snapshot lógico de estadísticas persistidas para todas las estrategias de una ejecución y MUST no refrescar estadísticas entre estrategias dentro de esa ejecución.
- **FR-008**: El sistema MUST determinar la elegibilidad de cada jugador de forma independiente para cada estrategia, utilizando reglas de minutos, partidos u otra muestra mínima configurable sin fijar un umbral funcional por defecto.
- **FR-009**: El sistema MUST normalizar las métricas al rango [0,1] mediante Min-Max antes de aplicarlas a una fórmula.
- **FR-010**: GENERAL v1 MUST normalizar cada métrica respecto de todos los jugadores elegibles para GENERAL v1 y aplicar los pesos: goals 25%, assists 15%, shotsPerGame 10%, keyPassPerGame 10%, dribbleWonPerGame 10%, tacklePerGame 10% y rating 20%.
- **FR-011**: GENERAL v1 MUST producir `scoreGeneral = 0.25*goals + 0.15*assists + 0.10*shots + 0.10*keyPasses + 0.10*dribbles + 0.10*tackles + 0.20*rating`, limitado a [0,1].
- **FR-012**: POSITIONAL v1 MUST normalizar cada métrica respecto de los jugadores elegibles de la misma posición para POSITIONAL v1.
- **FR-013**: POSITIONAL v1 MUST soportar DL, MC, DF y PO con configuraciones distintas, permitiendo que una métrica se use, se omita o tenga un peso diferente según la posición.
- **FR-014**: Para DL, POSITIONAL v1 MUST utilizar goals 35%, assists 10%, shotsPerGame 18%, keyPassPerGame 12%, dribbleWonPerGame 10% y rating 15%.
- **FR-015**: Para MC, POSITIONAL v1 MUST utilizar goals 10%, assists 15%, keyPassPerGame 20%, totalPassesPerGame 15%, passSuccess 14%, dribbleWonPerGame 8%, tacklePerGame 8% y rating 10%.
- **FR-016**: Para DF, POSITIONAL v1 MUST utilizar goals 12%, tacklePerGame 20%, interceptionPerGame 19%, clearancePerGame 12%, outfielderBlockPerGame 7%, totalPassesPerGame 8%, passSuccess 7% y rating 15%.
- **FR-017**: Para PO, POSITIONAL v1 MUST identificarse como provisoria y utilizar rating 30%, passSuccess 30%, accurateLongPassPerGame 25% y manOfTheMatch 15%, sin aumentar directamente el rendimiento por minutos o partidos.
- **FR-018**: Los pesos positivos de cada fórmula MUST sumar 100% y el sistema MUST impedir registrar una configuración que no cumpla esa invariante.
- **FR-019**: POSITIONAL v1 MUST aplicar penalizaciones configurables a métricas negativas normalizadas dentro de la misma posición, usando al menos foulsPerGame, yellowCard, redCard, dispossessedPerGame, offsideGivenPerGame y wasDribbledPerGame según la configuración posicional definida.
- **FR-020**: La penalización posicional MUST calcularse como la suma de cada métrica negativa normalizada multiplicada por su peso de penalización, y el score final MUST ser `clamp(positionScore - penalty, 0, 1)`.
- **FR-021**: La configuración MUST conservar una penalización significativa para redCard en todas las posiciones, impacto cero de wasDribbledPerGame para DL, impacto pequeño para MC e impacto considerable para DF.
- **FR-022**: El sistema MUST convertir cualquier score normalizado mediante `valor = valorBase + (score * factorEscala)`, donde valorBase y factorEscala pertenecen a la configuración y no representan una moneda específica.
- **FR-023**: Cada ejecución MUST calcular de forma independiente todas las estrategias habilitadas, persistir resultados válidos e históricos por estrategia/version y actualizar la cotización actual correspondiente a cada estrategia.
- **FR-024**: Una falla o inelegibilidad de un jugador para una estrategia MUST registrarse sin impedir procesar otras estrategias ni otros jugadores válidos.
- **FR-025**: El sistema MUST poder ejecutar una valorización manual y una automática con frecuencia semanal usando el mismo caso de uso y la estrategia visible vigente solo para consultas posteriores, no para limitar cálculos.
- **FR-026**: Cada cotización histórica MUST ser inmutable y conservar jugador, ejecución, fecha/hora, estrategia, versión, score, cotización, valorBase, factorEscala y datos de trazabilidad.
- **FR-027**: Debe existir como máximo una cotización actual por combinación jugador + estrategia + versión, o una representación equivalente que conserve simultáneamente el último resultado de cada estrategia.
- **FR-028**: El sistema MUST permitir consultar la cotización actual y el histórico por jugador; sin estrategia específica, debe devolver la serie de la estrategia visible, y con estrategia específica debe devolver únicamente esa serie.
- **FR-029**: El sistema MUST permitir explicar cada cotización mediante estrategia, versión, score, valor, snapshot de estadísticas, normalizaciones, penalizaciones, ausencias negativas y decisiones de elegibilidad relevantes.
- **FR-030**: La persistencia MUST garantizar como máximo una estrategia visible seleccionada y el cambio visible MUST ser atómico; la invariante no debe depender exclusivamente de una constraint SQL.
- **FR-031**: La administración posterior de la estrategia visible MUST poder agregarse sin modificar el modelo fundamental de cálculo, cotización actual e histórico, pero este feature MUST excluir usuario ADMIN, roles, autorización administrativa y pantalla de administración.
- **FR-032**: El cálculo MUST quedar desacoplado de tokenización, emisión, compra, venta, wallets, portfolios, balances, ganancias, pérdidas, marketplace, trading y reglas monetarias.

### Key Entities

- **Estrategia de valorización**: definición identificable y versionada de una fórmula, sus métricas, pesos, penalizaciones, habilitación para cálculo y tratamiento posicional.
- **Selección de estrategia visible**: configuración única que determina qué serie se devuelve por defecto; no controla qué estrategias se calculan.
- **Configuración de valorización**: parámetros comunes de conversión, incluyendo valorBase, factorEscala y reglas configurables de elegibilidad y datos insuficientes.
- **Snapshot estadístico de ejecución**: conjunto lógico de estadísticas usado por todas las estrategias de una misma ejecución.
- **Jugador elegible por estrategia**: jugador con posición, muestra y métricas positivas requeridas suficientes para una estrategia determinada.
- **Cotización actual por estrategia**: último resultado vigente por jugador, estrategia y versión.
- **Histórico de cotización por estrategia**: serie inmutable de cada valorización, separada por jugador, estrategia y versión.
- **Ejecución de valorización**: operación manual o semanal que procesa el mismo snapshot contra todas las estrategias habilitadas y registra resultados y exclusiones por estrategia.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de los resultados válidos genera scores dentro de [0,1] y cada ejecución calcula todas las estrategias habilitadas capturadas al inicio.
- **SC-002**: El 100% de las configuraciones aceptadas tiene pesos positivos que suman 100%, y las configuraciones inválidas son rechazadas antes de producir cotizaciones.
- **SC-003**: En pruebas con jugadores de al menos cuatro posiciones, GENERAL v1 usa un único conjunto de pesos y POSITIONAL v1 usa cuatro configuraciones distinguibles con normalización por posición.
- **SC-004**: Sobre toda la población disponible, el 100% de las combinaciones jugador + estrategia con datos suficientes recibe una cotización, y las combinaciones no elegibles no impiden completar las demás.
- **SC-005**: Cada nueva ejecución conserva el 100% de los históricos previos y agrega como máximo un registro por combinación jugador + estrategia + versión valorizada correctamente.
- **SC-006**: Para cualquier cotización consultada, el consumidor puede identificar jugador, estrategia, versión, score, valor, valorBase, factorEscala y momento del cálculo.
- **SC-007**: Después de cambiar la estrategia visible, ninguna cotización histórica cambia y la consulta por defecto devuelve inmediatamente el último resultado ya calculado de la nueva estrategia visible.
- **SC-008**: La valorización semanal y la manual producen resultados equivalentes cuando parten del mismo snapshot, población, configuración y conjunto de estrategias habilitadas.
- **SC-009**: Todas las estrategias de una ejecución utilizan el mismo snapshot lógico; ninguna actualización posterior o consulta adicional entre estrategias cambia los insumos de esa corrida.
- **SC-010**: En pruebas de casos borde, min==max, población vacía, posición desconocida y métricas ausentes no generan división por cero, cotizaciones fuera de rango ni interrupción de combinaciones válidas.
- **SC-011**: Ningún flujo de este feature crea, modifica o depende de tokens, compras, ventas, wallets, portfolios o balances.
- **SC-012**: La configuración provisoria de PO queda identificada como tal en el 100% de las cotizaciones POSITIONAL v1 de arqueros.

## Assumptions

- Las estadísticas de WhoScored ya están sincronizadas y persistidas; este feature consume esa información sin modificar su obtención.
- La frecuencia automática por defecto es semanal y puede configurarse sin cambiar las fórmulas.
- La elegibilidad se determina con una muestra mínima de partidos y/o minutos configurable; no se fija un umbral funcional por defecto y los tests establecen explícitamente el umbral de cada escenario.
- Todas las métricas positivas utilizadas por la fórmula de cada estrategia habilitada son necesarias para producir su cotización; una ausencia excluye al jugador solo de esa estrategia y registra la causa.
- Las métricas utilizadas exclusivamente como penalizaciones negativas no son necesarias para producir una cotización; su ausencia no genera penalización y queda registrada.
- Cuando todos los jugadores elegibles de una población tienen el mismo valor para una métrica, su contribución normalizada será 0 para evitar una ventaja artificial.
- Los jugadores con posición desconocida no son elegibles para POSITIONAL v1, pero no impiden GENERAL v1 ni otras estrategias aplicables.
- El valor de cotización es una unidad numérica sin moneda, redondeo financiero ni reglas monetarias hasta que exista una decisión funcional específica.
- GENERAL v1 y POSITIONAL v1 estarán habilitadas para cálculo inicialmente, y una de ellas estará seleccionada como estrategia visible inicial.
- La selección de estrategia visible podrá administrarse posteriormente por otro caso de uso; no se incorpora autorización administrativa en este feature.
- El histórico se conserva mientras el jugador y el sistema permanezcan disponibles; no se contempla una política de borrado.
- La versión de migración se resolverá al implementar usando la próxima versión Flyway realmente disponible, sin fijarla en esta especificación.

## Out of Scope

- Creación, emisión, compra, venta o transferencia de tokens.
- Wallets, portfolios, balances, ganancias, pérdidas, marketplace y trading.
- Scraping nuevo, cambios en la sincronización de WhoScored o incorporación de nuevas estadísticas externas.
- Usuario ADMIN, roles, autorización administrativa y pantalla para cambiar estrategia visible.
- Endpoint administrativo para habilitar/deshabilitar estrategias o seleccionar estrategia visible.
- Definición de moneda, valuación financiera, liquidación o reglas contables.
