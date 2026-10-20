# Research: Valorización de jugadores según estrategia

## Decisiones

### D1. Reutilizar el modelo interno de estadísticas existente

- **Decisión**: El cálculo recibirá `EstadisticasJugador` y sus familias de métricas, no DTOs de WhoScored.
- **Racional**: El proyecto ya normaliza y persiste las estadísticas antes de exponerlas al dominio. Esto evita duplicar scraping, mapeo o reglas del proveedor.
- **Alternativas consideradas**: Leer directamente entidades JPA o DTOs del adapter. Se descartan porque violan la separación de capas.

### D2. Mantener estrategias versionadas y configuraciones explícitas

- **Decisión**: Cada estrategia será una configuración identificable, versionada e inmutable para una ejecución; las fórmulas iniciales se expresarán como configuraciones de dominio, con validación de pesos positivos al 100%.
- **Racional**: Permite coexistencia, auditoría, evolución y cambio de estrategia visible sin reescribir históricos.
- **Alternativas consideradas**: Pesos como literales dispersos o una única clase con condicionales. Se descartan por dificultar auditoría y nuevas versiones.

### D3. Normalización en dos fases según alcance

- **Decisión**: Se construirán rangos Min-Max sobre la población elegible completa para la estrategia general y por posición para la posicional. Cuando `min == max`, la contribución normalizada será 0.
- **Racional**: Evita división por cero y mantiene una regla determinista sin ventaja artificial para una métrica constante.
- **Alternativas consideradas**: Normalizar a 0.5 o usar el valor bruto. Se descartan porque cambiarían la semántica acordada.

### D4. Tratamiento de datos faltantes

- **Decisión**: Toda métrica positiva usada por la fórmula activa es requerida para que el jugador sea elegible. Una métrica negativa ausente no produce penalización y se registra como ausencia.
- **Racional**: Evita alterar implícitamente los pesos positivos y evita penalizaciones inventadas.
- **Alternativas consideradas**: Imputar cero, omitir términos o renormalizar pesos. Se descartan por introducir sesgos.

### D5. Elegibilidad sin umbral funcional predeterminado

- **Decisión**: La configuración de ejecución incluye reglas de minutos y/o partidos, pero no se fija un umbral por defecto. Los tests y futuros casos de uso proveerán explícitamente la configuración aplicable.
- **Racional**: El negocio aún no definió el umbral y la especificación exige mantenerlo configurable.
- **Alternativas consideradas**: Adoptar minutos positivos o al menos un partido. Se descartan porque convertirían una decisión pendiente en regla funcional.

### D6. Persistencia separada del mercado de tokens

- **Decisión**: Se crearán entidades/repositorios propios para cotización actual, histórico y ejecución. El cálculo no invocará operaciones de compra, venta, wallet o portfolio.
- **Racional**: La cotización es una unidad numérica sin moneda y no debe quedar acoplada a `pricePerToken` ni a reglas del mercado.
- **Alternativas consideradas**: Reutilizar directamente el `QuotePublicationPort` existente. Se descarta porque introduciría dependencia de tokenización en el dominio.

### D7. Estrategias habilitadas y estrategia visible

- **Decisión**: Se distinguen `habilitadaParaCalculo` y `estrategiaVisible`. Una ejecución calcula todas las estrategias habilitadas, inicialmente GENERAL v1 y POSITIONAL v1; la estrategia visible solo selecciona la serie devuelta por defecto.
- **Racional**: Permite comparar estrategias simultáneamente y cambiar la presentación sin recalcular ni eliminar resultados.
- **Alternativas consideradas**: Usar una única estrategia seleccionada para cálculo. Se descarta porque impide conservar resultados comparables de las demás estrategias.

### D8. Ejecución manual y semanal

- **Decisión**: Una operación manual y una entrada programada semanal delegarán en el mismo caso de uso. El conjunto de estrategias habilitadas y sus versiones se captura al inicio, junto con un snapshot lógico único de estadísticas.
- **Racional**: Garantiza resultados equivalentes y evita que cambios concurrentes alteren una ejecución a mitad del proceso.
- **Alternativas consideradas**: Duplicar lógica o recalcular al cambiar estrategia. Se descartan por duplicación y alcance.

### D9. Contratos públicos mínimos

- **Decisión**: Se documentarán consultas bajo `/api/v1/players/{id}/valuation` y `/api/v1/players/{id}/valuation/history`, con un parámetro opcional de estrategia/version. Sin parámetro se usa la estrategia visible. La ejecución manual queda como caso de uso interno.
- **Racional**: La especificación exige consultas y comparación opcional, pero excluye administración y autorización para cambiar estrategia visible.
- **Alternativas consideradas**: Exponer endpoints administrativos. Se descarta por estar fuera de alcance.

### D10. Versión de migración

- **Decisión**: La migración de valorización utilizará durante la implementación la próxima versión Flyway realmente disponible en `backend/src/main/resources/db/migration`, sin asumir V17.
- **Racional**: Evita colisiones con migraciones agregadas entre planificación e implementación.
- **Alternativas consideradas**: Fijar `V17`. Se descarta porque puede estar ocupada.

## Resultado de la investigación

No quedan decisiones técnicas sin resolver que bloqueen el diseño. El umbral funcional de elegibilidad permanece deliberadamente configurable y la versión concreta de Flyway se resolverá contra el repositorio al implementar.
