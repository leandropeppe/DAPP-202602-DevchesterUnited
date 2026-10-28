# Feature Specification: Simulador de cotización futura de jugadores

**Feature Branch**: `005-player-valuation-simulator`

**Created**: 2026-10-28

**Status**: Draft

**Input**: User description: "Crear un simulador de cotización de jugadores que proyecte el rendimiento futuro a 3, 5, 8 o 10 partidos y calcule el impacto en la cotización sin modificar datos oficiales."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Simular una cotización desde el detalle del jugador (Priority: P1)

Como usuario del mercado, quiero abrir un simulador desde el detalle de un jugador, elegir un horizonte de partidos y ajustar sus estadísticas relevantes para conocer una cotización futura estimada sin abandonar la página.

**Why this priority**: Es el objetivo principal y entrega el valor de explorar escenarios de rendimiento antes de tomar decisiones.

**Independent Test**: Con un jugador que tenga cotización y estadísticas suficientes, abrir el simulador, elegir 5 partidos, modificar una estadística válida y ejecutar el cálculo; se debe mostrar el escenario y la comparación de cotizaciones dentro de la página de detalle.

**Acceptance Scenarios**:

1. **Given** un jugador con estadísticas, posición soportada y cotización actual, **When** el usuario abre el simulador desde su detalle, **Then** se muestra una interfaz modal o equivalente con sus estadísticas actuales, cotización actual y horizontes 3, 5, 8 y 10.
2. **Given** un delantero con 5 goles actuales, **When** el usuario ingresa 3 goles esperados y simula a 5 partidos, **Then** el escenario utiliza 8 goles proyectados, incrementa automáticamente apariciones y minutos de esos 5 partidos, y muestra la cotización simulada y sus diferencias.
3. **Given** una métrica promedio o porcentual, **When** el usuario la modifica, **Then** el valor ingresado se interpreta como valor final proyectado y puede aumentar o disminuir respecto del valor actual.
4. **Given** una estadística no modificada, **When** se ejecuta la simulación, **Then** conserva el valor actual en el escenario, salvo apariciones y minutos que se incrementan por el horizonte seleccionado.

### User Story 2 - Ajustar métricas según la posición (Priority: P1)

Como usuario, quiero ver y editar únicamente las métricas de mayor relevancia para la posición del jugador, para construir escenarios comprensibles y consistentes con las reglas de valorización existentes.

**Why this priority**: La selección de métricas determina la calidad del escenario y evita que el usuario modifique variables irrelevantes o incompatibles con la estrategia.

**Independent Test**: Abrir jugadores de cada posición soportada y verificar que las métricas visibles y editables correspondan a su configuración de valorización, incluyendo el tratamiento disponible para arqueros.

**Acceptance Scenarios**:

1. **Given** un delantero, **When** se muestra el formulario, **Then** se ofrecen goles, asistencias, rating, tiros, pases clave y premios Man of the Match.
2. **Given** un mediocampista, **When** se muestra el formulario, **Then** se ofrecen asistencias, rating, pases clave, porcentaje de pases exitosos, goles y premios Man of the Match.
3. **Given** un defensor, **When** se muestra el formulario, **Then** se ofrecen goles, rating, tackles, intercepciones, despejes, duelos aéreos y las faltas o tarjetas que la estrategia vigente utilice como penalización.
4. **Given** un arquero, **When** se muestra el formulario, **Then** solo se ofrecen las estadísticas disponibles y utilizables por la estrategia vigente para arqueros; no se inventan métricas ausentes en los datos de origen.
5. **Given** un jugador con posiciones múltiples, **When** se abre el simulador, **Then** se aplica la posición y estrategia que determinen las reglas existentes y se informa la selección utilizada cuando exista más de una alternativa.

### User Story 3 - Recalcular y comparar escenarios sin persistirlos (Priority: P1)

Como usuario, quiero recalcular varias veces, restablecer el escenario y comparar el valor actual con el simulado, con la seguridad de que ninguna simulación altera datos oficiales ni históricos.

**Why this priority**: La exploración iterativa es esencial para probar hipótesis y debe ser segura para la información oficial del mercado.

**Independent Test**: Ejecutar dos simulaciones diferentes sobre el mismo jugador, restablecer los valores, y consultar nuevamente sus estadísticas, cotización oficial e histórico para comprobar que permanecen sin cambios.

**Acceptance Scenarios**:

1. **Given** un simulador abierto, **When** el usuario cambia el escenario y vuelve a calcular, **Then** se reemplaza el resultado anterior por el nuevo sin cerrar el simulador ni modificar la cotización oficial.
2. **Given** valores modificados, **When** el usuario selecciona restablecer, **Then** todas las métricas editables vuelven a sus valores actuales y apariciones/minutos vuelven a mostrar sus valores de referencia antes de simular.
3. **Given** una simulación exitosa, **When** el usuario consulta la cotización oficial o el histórico, **Then** ambos conservan exactamente sus valores previos y no se crea un registro histórico de la simulación.
4. **Given** que el procesamiento tarda menos de 2 segundos, **When** se ejecuta una simulación, **Then** se muestra un loader contextual de Maradona haciendo jueguitos durante al menos 2 segundos antes del resultado.
5. **Given** que el procesamiento tarda más de 2 segundos, **When** finaliza el cálculo, **Then** el resultado se muestra inmediatamente al terminar, sin espera adicional.
6. **Given** un error de validación o cálculo, **When** se ejecuta la simulación, **Then** se muestra el error sin espera artificial y se conserva el escenario editable.

### Edge Cases

- Si faltan estadísticas actuales, se informa qué datos impiden simular y no se presenta una cotización estimada incompleta como válida.
- Si no existe cotización actual, se informa que no puede calcularse la diferencia monetaria y porcentual; no se inventa un valor base.
- Si la posición no es soportada o no existe estrategia aplicable, el simulador no permite ejecutar y explica la causa.
- Si una posición múltiple no puede resolverse según las reglas existentes, se solicita una selección válida o se informa que el jugador no es simulable.
- Si una estadística requerida por la estrategia está ausente, el sistema rechaza la simulación y enumera la métrica faltante.
- Los incrementos acumulativos no aceptan valores negativos ni tipos inválidos; las métricas promedio o porcentuales aceptan variaciones dentro de sus rangos válidos.
- Los valores fuera de rango, porcentajes fuera de 0–100, ratings fuera de su escala y horizontes distintos de 3, 5, 8 o 10 rechazan el cálculo sin modificar datos oficiales.
- Apariciones y minutos no pueden editarse manualmente; el sistema suma una aparición y 90 minutos por cada partido del horizonte.
- Si la cotización simulada es igual a la actual, las diferencias monetaria y porcentual se muestran como cero.
- Si el valor actual es cero, la diferencia porcentual se muestra como no disponible o se informa explícitamente que no puede calcularse, sin división por cero.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir iniciar el simulador desde el detalle de un jugador y mantener al usuario en esa página durante toda la interacción.
- **FR-002**: El simulador MUST permitir seleccionar exactamente uno de los horizontes: 3, 5, 8 o 10 partidos.
- **FR-003**: El sistema MUST cargar y mostrar la cotización actual, posición o posiciones, apariciones, minutos y estadísticas actuales disponibles del jugador.
- **FR-004**: El sistema MUST seleccionar las estadísticas editables según la posición y las métricas configuradas por la estrategia de valorización aplicable.
- **FR-005**: Para delanteros, el sistema MUST contemplar goles, asistencias, rating, tiros, pases clave y premios Man of the Match cuando estén disponibles.
- **FR-006**: Para mediocampistas, el sistema MUST contemplar asistencias, rating, pases clave, porcentaje de pases exitosos, goles y premios Man of the Match cuando estén disponibles.
- **FR-007**: Para defensores, el sistema MUST contemplar goles, rating, tackles, intercepciones, despejes, duelos aéreos y las métricas de faltas o tarjetas utilizadas por la estrategia vigente.
- **FR-008**: Para arqueros, el sistema MUST limitar las métricas a las disponibles en los datos sincronizados y utilizadas por la estrategia vigente, sin inventar datos ausentes.
- **FR-009**: El sistema MUST interpretar las estadísticas acumulativas ingresadas como incrementos esperados durante el horizonte y sumarlas al valor actual.
- **FR-010**: El sistema MUST interpretar rating, porcentajes y demás métricas promedio configuradas como el valor final proyectado, permitiendo aumentarlo o disminuirlo dentro de su rango válido.
- **FR-011**: El sistema MUST conservar el valor actual de toda estadística que el usuario no modifique.
- **FR-012**: El sistema MUST calcular automáticamente las apariciones y los minutos proyectados sumando una aparición y 90 minutos por cada partido del horizonte seleccionado.
- **FR-013**: El sistema MUST impedir la edición manual de apariciones y minutos proyectados.
- **FR-014**: El cálculo MUST reutilizar exactamente el mismo motor, estrategia, versión, métricas, ponderaciones, normalizaciones, penalizaciones y reglas de elegibilidad de la feature `004-player-valuation-strategies`.
- **FR-015**: El sistema MUST calcular la cotización simulada usando un escenario temporal en memoria y MUST no persistir estadísticas oficiales, cotizaciones oficiales ni históricos de valorización.
- **FR-016**: El resultado MUST mostrar cotización actual, cotización simulada, diferencia monetaria y diferencia porcentual, identificando cuando una diferencia porcentual no sea calculable.
- **FR-017**: El usuario MUST poder ejecutar múltiples simulaciones consecutivas sobre el mismo jugador, editar el escenario y recalcular sin cerrar el simulador.
- **FR-018**: El usuario MUST poder restablecer todos los valores editables a los valores actuales del jugador y reiniciar el escenario seleccionado.
- **FR-019**: El sistema MUST validar tipos, campos obligatorios, rangos, escalas, porcentajes, valores negativos y coherencia de incrementos antes de ejecutar el cálculo.
- **FR-020**: El sistema MUST rechazar horizontes no permitidos, posiciones no soportadas, jugadores sin estrategia aplicable y escenarios sin estadísticas suficientes, comunicando una causa accionable.
- **FR-021**: Durante una simulación exitosa, el sistema MUST mostrar un loader contextual de Maradona haciendo jueguitos durante un mínimo de 2 segundos.
- **FR-022**: Si el procesamiento exitoso supera 2 segundos, el sistema MUST mostrar el resultado inmediatamente al finalizar, sin agregar espera artificial adicional.
- **FR-023**: Ante un error de validación, datos insuficientes o cálculo, el sistema MUST omitir la espera artificial y mantener disponible el escenario para corregirlo.
- **FR-024**: El sistema MUST tratar explícitamente la falta de estadísticas, ausencia de cotización actual, posiciones múltiples, posiciones no soportadas y falta de estrategia como estados diferenciados para el usuario.
- **FR-025**: El sistema MUST preservar sin cambios los datos oficiales y el histórico aunque el usuario ejecute varias simulaciones o cierre el simulador.

### Key Entities *(include if feature involves data)*

- **Escenario de simulación**: Valores temporales del jugador, horizonte seleccionado y modificaciones realizadas por el usuario; existe solo durante la interacción.
- **Estadística actual del jugador**: Valor oficial de referencia, con indicación de si es acumulativo, promedio, porcentual o no disponible.
- **Estadística proyectada**: Valor calculado para el escenario a partir de un incremento acumulativo, un valor final editable o la conservación del valor actual.
- **Resultado de simulación**: Cotización actual, cotización simulada, diferencia monetaria, diferencia porcentual y estado de cálculo; no constituye un histórico oficial.
- **Estrategia de valorización aplicable**: Estrategia y versión reutilizadas desde `004-player-valuation-strategies`, junto con sus métricas y reglas posicionales.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En el 100% de los casos válidos, los horizontes disponibles son exactamente 3, 5, 8 y 10 partidos y las apariciones/minutos se proyectan automáticamente con 90 minutos por partido.
- **SC-002**: En el 100% de los casos válidos, las estadísticas acumulativas se calculan como valor actual más incremento, mientras que las métricas promedio o porcentuales se tratan como valor final proyectado.
- **SC-003**: Para cada posición soportada por el simulador, el 100% de las métricas mostradas coincide con las disponibles y aplicables para su posición y estrategia.
- **SC-004**: El 100% de las simulaciones exitosas muestra cotización actual, cotización simulada, diferencia monetaria y diferencia porcentual o una explicación verificable de por qué esta última no es calculable.
- **SC-005**: El 100% de las simulaciones exitosas reutiliza las mismas reglas de valorización de `004-player-valuation-strategies` y produce el mismo valor que produciría el motor para el mismo escenario.
- **SC-006**: Después de cualquier cantidad de simulaciones, el 100% de las estadísticas oficiales, cotizaciones oficiales e históricos consultados conserva sus valores previos.
- **SC-007**: Al menos el 95% de las simulaciones válidas muestra el loader durante al menos 2 segundos y ninguna simulación fallida agrega una espera artificial.
- **SC-008**: El 100% de los escenarios con tipos inválidos, rangos inválidos, negativos no permitidos, datos faltantes o posiciones no aplicables se rechaza con un mensaje accionable y sin cambios persistidos.
- **SC-009**: Un usuario puede modificar, recalcular y restablecer al menos 5 escenarios consecutivos sobre el mismo jugador sin cerrar el simulador ni perder el contexto del detalle.

## Assumptions

- Las estadísticas actuales de jugadores y las reglas de valorización de la feature 004 ya están disponibles para consulta.
- Los rangos y escalas de cada métrica se determinan por las reglas y datos existentes; si una métrica no tiene rango válido definido, no se habilita para edición hasta contar con esa definición.
- La clasificación de cada estadística como acumulativa, promedio o porcentual se determinará a partir de la semántica de los datos sincronizados y de las reglas utilizadas por la estrategia de valorización existente.
- El supuesto de 90 minutos por partido aplica a todos los horizontes y no representa una predicción de lesiones, sustituciones o disponibilidad real.
- Para posiciones múltiples se usa la resolución posicional definida por la estrategia vigente; si no puede resolverse de forma determinista, la simulación se bloquea.
- El loader de Maradona es un recurso visual ya disponible o será provisto por la implementación; la especificación no define su formato.
- El porcentaje de diferencia se calcula respecto de la cotización actual cuando esta es distinta de cero.
- Las simulaciones no requieren autenticación, permisos ni persistencia propios distintos de los controles existentes del detalle del jugador.

## Out of Scope

- Modificar o persistir estadísticas oficiales, cotizaciones oficiales, históricos o resultados de valorización.
- Crear estrategias nuevas, cambiar ponderaciones, alterar el motor de valorización o modificar la feature `004-player-valuation-strategies`.
- Predecir lesiones, minutos reales, participación parcial, calendario, rivales o resultados deportivos.
- Guardar, compartir, exportar o comparar históricamente escenarios de simulación.
- Agregar nuevas estadísticas de WhoScored o cambiar su sincronización.
- Administrar estrategias, posiciones, rangos o permisos desde el simulador.
