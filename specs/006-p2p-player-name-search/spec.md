# Feature Specification: Búsqueda P2P por nombre de jugador

**Feature Branch**: `006-p2p-player-name-search`

**Created**: 2026-11-01

**Status**: Draft

**Input**: User description: "Mejorar el mercado P2P para mostrar nombres de jugadores y buscar ofertas por nombre con aplicación explícita de filtros, manteniendo UUID interno y compatibilidad con filtros existentes"

## User Scenarios & Testing *(mandatory)*

<!--
  IMPORTANT: User stories should be PRIORITIZED as user journeys ordered by importance.
  Each user story/journey must be INDEPENDENTLY TESTABLE - meaning if you implement just ONE of them,
  you should still have a viable MVP (Minimum Viable Product) that delivers value.

  Assign priorities (P1, P2, P3, etc.) to each story, where P1 is the most critical.
  Think of each story as a standalone slice of functionality that can be:
  - Developed independently
  - Tested independently
  - Deployed independently
  - Demonstrated to users independently
-->

### User Story 1 - Identificar ofertas por nombre (Priority: P1)

Como usuario del mercado P2P quiero reconocer el jugador de cada oferta por su nombre visible y poder buscarlo por nombre, para encontrar ofertas sin conocer un UUID.

**Why this priority**: Es el problema principal de usabilidad y afecta tanto la lectura de resultados como el descubrimiento de ofertas.

**Independent Test**: Con ofertas asociadas a jugadores conocidos, escribir una parte del nombre con distintas mayúsculas y comprobar que aparecen únicamente las ofertas coincidentes, con el nombre visible en cada card.

**Acceptance Scenarios**:

1. **Given** una oferta asociada a un jugador con nombre visible, **When** se muestra la oferta, **Then** la card presenta ese nombre y no el UUID como texto principal.
2. **Given** ofertas de jugadores como “Federico Valverde”, **When** el usuario busca “val”, “Valverde”, “VALVERDE” o “Federico”, **Then** se incluyen las ofertas coincidentes por fragmento sin distinguir mayúsculas y minúsculas.
3. **Given** un texto que no coincide con ningún jugador, **When** termina la búsqueda, **Then** el listado muestra el estado vacío sin reutilizar resultados de una búsqueda anterior.

---

### User Story 2 - Consultar resultados paginados (Priority: P1)

Como usuario del mercado P2P quiero que la búsqueda respete el listado paginado, para obtener resultados completos y una navegación coherente aunque existan muchas ofertas.

**Why this priority**: Filtrar solamente la página visible produciría resultados incompletos y una paginación engañosa.

**Independent Test**: Crear más ofertas coincidentes que el tamaño de página, consultar la primera y segunda página y verificar que el conteo y los elementos corresponden al conjunto completo filtrado.

**Acceptance Scenarios**:

1. **Given** más resultados coincidentes que una página, **When** el usuario navega entre páginas, **Then** cada página contiene resultados distintos del conjunto filtrado y la información de paginación es correcta.
2. **Given** un cambio de texto o de otro filtro, **When** se aplica, **Then** la consulta comienza en la página 1.
3. **Given** el usuario modifica un filtro, **When** todavía no confirma la acción, **Then** no se ejecuta ninguna consulta hasta presionar `Buscar` o Enter.
4. **Given** el usuario presiona `Buscar` o Enter, **When** existen valores editados, **Then** se aplican nombre, estado y pertenencia simultáneamente.
5. **Given** el usuario presiona `Limpiar`, **When** existen filtros aplicados o editados, **Then** todos vuelven a sus valores iniciales y se consulta la primera página sin filtros.

---

### User Story 3 - Mantener operaciones del mercado (Priority: P1)

Como usuario del mercado P2P quiero seguir comprando, cancelando y publicando ofertas usando los identificadores internos, para que la mejora de búsqueda no altere operaciones existentes.

**Why this priority**: La legibilidad del jugador no debe cambiar la identidad técnica ni introducir regresiones en operaciones con dinero y tokens.

**Independent Test**: Desde resultados obtenidos por nombre, comprar o cancelar una oferta y publicar desde una posición; comprobar que las operaciones usan el UUID y mantienen el comportamiento vigente.

**Acceptance Scenarios**:

1. **Given** una oferta encontrada por nombre, **When** el usuario compra o cancela según sus permisos, **Then** la operación se ejecuta sobre la oferta y jugador correctos.
2. **Given** filtros existentes de estado y “mis ofertas”, **When** se combinan con una búsqueda por nombre, **Then** se aplican simultáneamente sin perder compatibilidad con dichos filtros.
3. **Given** un consumidor que todavía envía el filtro UUID soportado, **When** consulta ofertas, **Then** el comportamiento existente permanece disponible mientras se incorpora el filtro por nombre.

---

[Add more user stories as needed, each with an assigned priority]

### Edge Cases

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right edge cases.
-->

- El texto de búsqueda vacío debe equivaler a no aplicar filtro de nombre.
- Los espacios al inicio y al final no deben impedir una coincidencia; un texto compuesto sólo por espacios debe tratarse como vacío.
- Una oferta cuyo nombre no pueda resolverse debe conservar su UUID para operaciones internas y usar un fallback excepcional, sin convertir el UUID en la presentación normal.
- Cambiar nombre, estado o “mis ofertas” debe reiniciar la página a 1 y mantener el conteo del conjunto filtrado.
- La paginación fuera de rango debe conservar la respuesta y las reglas actuales del mercado.
- Cambios rápidos de búsqueda no deben permitir que una respuesta antigua reemplace resultados más recientes.

## Requirements *(mandatory)*

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right functional requirements.
-->

### Functional Requirements

- **FR-001**: El listado de ofertas debe devolver el nombre visible del jugador junto con su identificador interno cuando la relación con el jugador exista.
- **FR-002**: La interfaz debe mostrar normalmente el nombre del jugador en cada card y no debe mostrar el UUID como nombre.
- **FR-003**: El usuario debe poder buscar ofertas mediante un texto parcial del nombre, ignorando diferencias de mayúsculas y minúsculas.
- **FR-004**: La búsqueda por nombre debe aplicarse sobre el conjunto completo persistido antes de paginar, y el total de resultados y páginas debe corresponder al conjunto filtrado.
- **FR-005**: El endpoint de listado debe aceptar un filtro opcional de nombre y continuar aceptando `status`, `mine`, `page` y `limit`.
- **FR-006**: El filtro UUID existente debe conservarse mientras haya contratos o consumidores vigentes que lo utilicen.
- **FR-007**: La interfaz no debe consultar mientras el usuario edita nombre, estado o pertenencia.
- **FR-008**: El botón `Buscar` y Enter deben aplicar los valores editados, recortar el nombre y volver a la página 1.
- **FR-009**: El botón `Limpiar` debe vaciar el nombre, restaurar el estado inicial, desmarcar la pertenencia y consultar la primera página sin filtros.
- **FR-010**: Las operaciones de publicar, comprar y cancelar deben continuar usando identificadores internos y no depender del texto visible del nombre.
- **FR-011**: El nombre del jugador debe propagarse de forma consistente desde la relación persistida hasta la respuesta del listado y la representación visual.
- **FR-012**: Las búsquedas sin coincidencias deben mostrar un estado vacío claro y no datos de una consulta previa.
- **FR-013**: La evolución del endpoint debe quedar reflejada en la documentación pública del contrato.

*Example of marking unclear requirements:*

- **FR-006**: System MUST authenticate users via [NEEDS CLARIFICATION: auth method not specified - email/password, SSO, OAuth?]
- **FR-007**: System MUST retain user data for [NEEDS CLARIFICATION: retention period not specified]

### Key Entities

- **Oferta P2P**: Oferta publicada con UUID de oferta, UUID de jugador, nombre del jugador, vendedor, cantidades, estado y cotización.
- **Jugador**: Registro asociado a una oferta, con UUID estable y nombre visible utilizado para presentar y buscar ofertas.
- **Filtro de ofertas**: Criterios opcionales de nombre, UUID, estado, pertenencia al usuario y paginación.

## Success Criteria *(mandatory)*

<!--
  ACTION REQUIRED: Define measurable success criteria.
  These must be technology-agnostic and measurable.
-->

### Measurable Outcomes

- **SC-001**: El 100% de las ofertas con jugador resoluble muestran el nombre visible en la card y no el UUID como etiqueta principal.
- **SC-002**: Las búsquedas exactas, parciales y con cualquier combinación de mayúsculas devuelven el mismo conjunto de coincidencias semánticas.
- **SC-003**: En un conjunto de al menos 41 ofertas coincidentes, la navegación paginada devuelve conteo, páginas y elementos correctos sin filtrar resultados sólo en el cliente.
- **SC-004**: Editar cualquier filtro sin confirmarlo genera cero consultas adicionales; presionar `Buscar`, Enter o `Limpiar` genera exactamente una consulta efectiva.
- **SC-005**: Las operaciones existentes de publicación, compra y cancelación continúan completando sus pruebas de regresión sin cambiar el identificador técnico utilizado.

## Assumptions

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right assumptions based on reasonable defaults
  chosen when the feature description did not specify certain details.
-->

- Se reutilizan autenticación, permisos, estados, ordenamiento y paginación actuales del mercado P2P.
- La comparación de nombres se realiza de forma insensible a mayúsculas y con coincidencia de fragmento; no se incorpora búsqueda fonética ni corrección ortográfica.
- El nombre proviene del registro local actual del jugador y no de una llamada externa en tiempo real.
- El filtro UUID se mantiene por compatibilidad y no se expone como filtro principal de la interfaz.
- Los valores editables del formulario permanecen separados de los filtros aplicados hasta una acción explícita del usuario.
