# Especificación de Funcionalidad: Sincronización de Estadísticas de Jugadores

**Rama de Funcionalidad (Feature Branch)**: `002-player-statistics-sync`

**Fecha de Creación**: 2026-10-03

**Estado**: Draft

**Entrada**: Descripción del usuario: "Crear el Feature 2 correspondiente a la obtención y sincronización de estadísticas de jugadores desde WhoScored, como parte de la segunda entrega de Devchester United."

## Escenarios de Usuario y Pruebas *(obligatorio)*

### User Story 1 - Obtener estadísticas de las ligas soportadas (Prioridad: P1)

Como responsable autorizado de la plataforma, quiero ejecutar una sincronización de estadísticas desde WhoScored para las cinco principales ligas europeas, de modo que el sistema disponga de información actualizada de todos los jugadores disponibles.

**Por qué esta prioridad**: Es el objetivo principal de la feature y habilita que las funcionalidades posteriores de cotización utilicen información estadística vigente.

**Prueba Independiente**: Con una fuente externa controlada que contenga jugadores de las cinco competiciones, se ejecuta una sincronización y se verifica que cada jugador disponible quede representado con sus estadísticas generales, ofensivas y defensivas cuando la fuente las proporcione.

**Escenarios de Aceptación**:

1. **Dado** que la fuente externa dispone de jugadores de Premier League, La Liga, Serie A, Bundesliga y Ligue 1, **cuando** se ejecuta una sincronización válida, **entonces** el sistema procesa las cinco competiciones y deja disponibles las estadísticas obtenidas para cada jugador disponible.
2. **Dado** que una competición contiene una cantidad de jugadores distinta en dos ejecuciones, **cuando** se ejecuta la segunda sincronización, **entonces** el sistema procesa todos los jugadores disponibles en esa ejecución sin depender de una cantidad prefijada.
3. **Dado** que la fuente entrega información general, ofensiva y defensiva en conjuntos separados, **cuando** se normalizan los datos, **entonces** las tres familias quedan asociadas al jugador correspondiente.

---

### User Story 2 - Mantener estadísticas consistentes y sin duplicados (Prioridad: P1)

Como consumidor de los datos de jugadores, quiero que cada sincronización actualice los registros correspondientes sin duplicarlos, para poder utilizar información confiable en funcionalidades posteriores.

**Por qué esta prioridad**: La consistencia del catálogo estadístico es necesaria para que cualquier cálculo futuro de cotización parta de una única representación vigente por jugador.

**Prueba Independiente**: Se ejecuta una sincronización, se repite sin cambios en la fuente y luego se ejecuta otra con cambios en algunas estadísticas; se comprueba que no aparecen duplicados, que los datos sin cambios permanecen equivalentes y que los modificados se actualizan.

**Escenarios de Aceptación**:

1. **Dado** que un jugador ya posee estadísticas sincronizadas, **cuando** una nueva sincronización contiene información actualizada del mismo jugador, **entonces** se actualiza su información existente sin crear una segunda representación.
2. **Dado** que los datos externos no cambiaron, **cuando** se repite la sincronización, **entonces** el estado local final es equivalente al estado anterior y no se generan duplicados.
3. **Dado** que un mismo jugador aparece en distintos conjuntos de información de la fuente, **cuando** se completa la asociación, **entonces** sus estadísticas se consolidan en un único registro lógico.

---

### User Story 3 - Conservar el último estado válido ante fallos (Prioridad: P1)

Como responsable de la plataforma, quiero que una sincronización fallida no exponga resultados parciales ni elimine estadísticas válidas, para mantener la confiabilidad de la información disponible.

**Por qué esta prioridad**: La disponibilidad de datos locales confiables debe mantenerse aunque WhoScored esté indisponible, devuelva información inválida o falle el procesamiento.

**Prueba Independiente**: Se parte de un estado local válido, se provoca un fallo durante la obtención, normalización o persistencia y se verifica que los consumidores sigan viendo el estado válido anterior, sin mezclas parciales.

**Escenarios de Aceptación**:

1. **Dado** un conjunto de estadísticas locales válido, **cuando** la fuente externa no está disponible, **entonces** la sincronización informa el fallo y conserva sin cambios el último estado local válido.
2. **Dado** que una sincronización falla después de haber procesado parte de la información, **cuando** termina la operación, **entonces** no quedan visibles actualizaciones parciales ni una combinación inconsistente de datos nuevos y antiguos.
3. **Dado** que la fuente devuelve información incompleta, inválida o imposible de asociar, **cuando** se intenta sincronizar, **entonces** el sistema rechaza la sincronización afectada, conserva el estado local anterior y no persiste registros corruptos.

### Casos límite

- Una competición puede devolver cero jugadores disponibles; la sincronización debe distinguir ese resultado de un fallo de obtención y aplicar únicamente un resultado que haya sido validado como completo y válido.
- La cantidad de jugadores puede aumentar o disminuir entre ejecuciones; el procesamiento debe incluir todos los disponibles sin truncar el resultado por una cantidad esperada.
- Un jugador puede no disponer legítimamente de determinados valores estadísticos en la fuente; dicha ausencia no debe interpretarse como un fallo de sincronización ni debe completarse con valores inventados. La ausencia esperada de información debe distinguirse de la imposibilidad de obtener una familia de estadísticas requerida para completar la sincronización.
- La misma persona puede aparecer repetida dentro de la información externa; la sincronización no debe crear duplicados locales.
- Una estadística puede no estar informada para un jugador; el sistema no debe inventar valores y debe conservar la ausencia de información de forma coherente.
- Puede ocurrir un fallo antes, durante o después del procesamiento de una competición; el resultado visible debe seguir siendo el último estado local válido.
- Una sincronización repetida puede coincidir con otra en curso; el comportamiento de concurrencia deberá definirse durante el diseño técnico sin comprometer la atomicidad ni producir duplicados.

## Requisitos *(obligatorio)*

### Requisitos Funcionales

- **FR-001**: El sistema DEBE obtener estadísticas de todos los jugadores disponibles de cada una de las siguientes competiciones: Premier League, La Liga, Serie A, Bundesliga y Ligue 1.
- **FR-002**: El sistema DEBE procesar la cantidad de jugadores que la fuente externa entregue en cada ejecución, sin asumir una cantidad fija por competición.
- **FR-003**: El sistema DEBE obtener y normalizar, como mínimo, la posición del jugador, minutos jugados, goles, asistencias, tiros, pases clave, regates, tackles, valoración general, tarjetas amarillas y tarjetas rojas.
- **FR-004**: El sistema DEBE contemplar información estadística general, ofensiva y defensiva necesaria para las estrategias posteriores de valoración.
- **FR-005**: Las estadísticas sincronizadas DEBEN conservar su significado individual y permanecer desacopladas de las reglas que posteriormente determinen su peso o impacto sobre la cotización de un jugador.- **FR-006**: El sistema DEBE actualizar las estadísticas de jugadores existentes cuando la nueva información corresponda al mismo jugador.
- **FR-007**: El sistema DEBE evitar la creación de duplicados cuando un jugador ya fue sincronizado o cuando aparece en más de un conjunto de información.
- **FR-008**: El sistema DEBE producir un resultado idempotente cuando los datos externos no hayan cambiado.
- **FR-009**: El sistema DEBE procesar los datos de manera controlada, sin requerir mantener simultáneamente en memoria la totalidad de jugadores de las cinco competiciones.
- **FR-010**: Cada sincronización DEBE tratarse como una única operación lógica sobre los datos persistidos.
- **FR-011**: El sistema NO DEBE hacer visibles actualizaciones parciales si la sincronización no puede completarse correctamente.
- **FR-012**: Ante un fallo de sincronización, el sistema DEBE conservar los últimos datos locales válidos previamente disponibles.
- **FR-013**: Los errores, respuestas inválidas o indisponibilidad de WhoScored DEBEN gestionarse sin corromper, eliminar ni reemplazar parcialmente la información local válida.
- **FR-014**: El mecanismo de obtención de datos externos DEBE permanecer desacoplado del caso de uso de sincronización.
- **FR-015**: El caso de uso DEBE poder utilizar otro mecanismo de obtención de datos sin modificar su lógica funcional de sincronización.
- **FR-016**: Los datos sincronizados DEBEN quedar disponibles para su utilización posterior por las funcionalidades de cotización, sin incluir en esta feature reglas de valorización.
- **FR-017**: El sistema DEBE asociar las estadísticas obtenidas al Jugador existente correspondiente y NO DEBE crear una representación alternativa de Jugador como parte del modelo estadístico.

### Entidades Clave

- **Jugador**: Entidad ya existente en el dominio de Devchester United. Esta feature no redefine ni reemplaza su modelado. Las estadísticas sincronizadas deben asociarse al jugador correspondiente.
- **Estadísticas de jugador**: Información estadística normalizada obtenida desde la fuente externa y asociada a un Jugador existente. Debe contener la información necesaria para que funcionalidades posteriores puedan evaluar su rendimiento y calcular su cotización.
- **Competición soportada**: Una de las cinco ligas europeas incluidas en el alcance de la sincronización.
- **Sincronización**: Operación lógica que obtiene, valida, normaliza y actualiza las estadísticas asociadas a los jugadores existentes, conservando atomicidad y el último estado local válido ante fallos.

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: En una sincronización válida, el 100% de las cinco competiciones soportadas queda procesado y todos los jugadores válidos disponibles en la fuente quedan correctamente considerados por la sincronización.
- **SC-002**: En ejecuciones repetidas con datos externos sin cambios, el 100% de los jugadores conserva una única representación local y el estado final de sus estadísticas es equivalente al anterior.
- **SC-003**: En una ejecución con cambios externos válidos, el 100% de los jugadores afectados queda actualizado sin duplicar jugadores ni familias de estadísticas.
- **SC-004**: En el 100% de las sincronizaciones fallidas de las pruebas, no quedan visibles actualizaciones parciales y se conserva íntegramente el último estado local válido.
- **SC-005**: La solución puede procesar una competición con una cantidad variable de jugadores sin truncar el resultado por una cantidad fija definida previamente.
- **SC-006**: Las funcionalidades posteriores pueden consultar las estadísticas sincronizadas sin depender de la disponibilidad en tiempo real de WhoScored.

## Supuestos

- La sincronización será ejecutada por un responsable o proceso autorizado según las reglas de acceso ya existentes en el sistema; esta feature no redefine autenticación ni autorización.
- WhoScored es la fuente externa de esta entrega y sus datos constituyen la referencia para actualizar las estadísticas locales cuando la información sea válida.
- La identificación necesaria para reconocer al mismo jugador entre ejecuciones y conjuntos de información será resuelta durante el diseño técnico a partir de los datos disponibles, sin exponer detalles del proveedor en el dominio.
- La ausencia de una estadística individual no invalida por sí misma al jugador; se conserva como dato no informado, salvo que la validación determine que el conjunto es inconsistente.
- La selección de temporadas, criterios de actualización y política de concurrencia que no estén definidas por esta especificación se resolverán en `/speckit-plan` sin alterar el alcance funcional.

## Fuera de Alcance

- Cálculo de cotizaciones o reglas de valorización de jugadores.
- Compra, venta o cualquier funcionalidad de mercado.
- Presentación de estadísticas en el frontend, salvo que una historia funcional posterior la requiera explícitamente.
- Exposición de nuevos contratos públicos o definición de detalles técnicos del mecanismo de obtención.
