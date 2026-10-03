# Investigación Técnica: Sincronización de Estadísticas de Jugadores

**Feature**: `002-player-statistics-sync`
**Fecha**: 2026-10-03

## Decisión 1: Reutilizar `Jugador` como identidad local

**Decisión**: Las estadísticas serán un agregado asociado al UUID del `Jugador` existente. El `playerId` de WhoScored se utilizará para unir familias y, para la asociación local, solo se aceptará una identidad de fuente compatible o una correspondencia persistida y única entre la fuente y el `jugadorId`.

**Rationale**: Evita duplicar perfiles, conserva la identidad local y permite actualizar una única fotografía estadística por jugador. El nombre, equipo o posición no se utilizarán como claves silenciosas, y no se asumirán iguales identificadores de proveedores distintos.

**Alternativas consideradas**: Crear un jugador específico para WhoScored (rechazada por duplicación e inconsistencia), asociar por nombre/equipo (rechazada por no ser estable), o comparar IDs de proveedores sin validar el namespace (rechazada por riesgo de asociaciones incorrectas).

## Decisión 2: Interfaz de proveedor y Adapter específico

**Decisión**: El Service dependerá de una interfaz propia que entregue datos normalizados por competición, familia y página. El Adapter de WhoScored será la única implementación inicial y encapsulará endpoint, parámetros, stage IDs, credenciales, JSON y errores externos.

**Rationale**: Cumple la Constitution y permite sustituir el mecanismo de obtención sin cambiar el caso de uso.

**Alternativas consideradas**: Inyectar directamente el Adapter en el Service (rechazada por acoplamiento), o exponer DTOs externos en el dominio (rechazada por fuga de contrato del proveedor).

## Decisión 3: Paginación guiada por metadata

**Decisión**: Consultar lotes iniciales de 50 y continuar hasta `totalPages`, validando `currentPage`, `resultsPerPage` y `totalResults`. El valor 50 será configuración de infraestructura.

**Rationale**: La cantidad de jugadores y páginas varía; la metadata de la fuente es la única señal válida de completitud.

**Alternativas consideradas**: detenerse tras una página vacía o usar un número fijo de páginas (rechazadas porque pueden truncar la competición).

## Decisión 4: Staging persistido antes de promoción

**Decisión**: Cada página normalizada se guardará en staging aislado por sincronización. Luego de validar las tres familias y la asociación con todos los jugadores locales, una única transacción promoverá el snapshot a las estadísticas publicadas.

**Rationale**: Mantiene memoria acotada y evita una transacción de PostgreSQL abierta durante llamadas de red, sin aceptar commits parciales por página.

**Alternativas consideradas**: acumular todas las páginas en memoria (rechazada por escala), hacer commit de cada página (rechazada por atomicidad), o mantener una transacción abierta durante la red (rechazada por la Constitution).

## Decisión 5: Fotografía vigente, no histórico

**Decisión**: Persistir una única fotografía vigente por `Jugador` con los campos requeridos por esta feature. Los datos de cotización, score normalizado, ponderaciones, estrategias e histórico quedan fuera de alcance.

**Rationale**: El spec prepara información para funcionalidades posteriores y no solicita versionado histórico.

**Alternativas consideradas**: guardar todas las respuestas externas o un histórico por ejecución (rechazadas por sobrepersistencia y alcance).

## Decisión 6: Scope de la API de aplicación

**Decisión**: Incorporar un caso de uso administrativo separado para la sincronización de estadísticas, documentado mediante OpenAPI y protegido por la política de API Key existente. No se modifica el frontend.

**Rationale**: Permite que el fallo de WhoScored no revierta ni deje ambiguo el catálogo de equipos/jugadores ya sincronizado por el flujo existente. El agregado estadístico se publica solo cuando los `Jugador` requeridos existen.

**Alternativas consideradas**: mezclar estadísticas dentro del snapshot actual del catálogo (rechazada porque acopla dos fuentes y amplía el rollback), o crear un flujo de lectura en tiempo real (rechazada por la Constitution).

## Decisión 7: Tratamiento de datos ausentes

**Decisión**: Una métrica individual ausente se conserva como no informada. Una familia sin respuesta válida, una página perdida o un jugador ausente en una familia requerida invalida la sincronización completa.

**Rationale**: Distingue ausencia legítima de información de una respuesta incompleta del proveedor y evita publicar estadísticas mezcladas.

**Alternativas consideradas**: completar con cero (rechazada porque inventa información), o publicar cada familia por separado (rechazada por atomicidad del conjunto).
