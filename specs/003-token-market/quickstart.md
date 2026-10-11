# Guía de verificación del Plan de Token Market

Esta guía describe las comprobaciones que se ejecutarán después de la implementación. No ejecuta migraciones, crea fixtures ni prescribe nombres definitivos de endpoints/clases.

## Prerrequisitos

- Java 21, el wrapper Gradle del repositorio, Testcontainers compatible con PostgreSQL y Docker disponibles.
- La aplicación utiliza una base de datos de prueba aislada. No se modifica ninguna base de datos de producción.
- Las fixtures de demostración están identificadas explícitamente como datos de prueba y se mantienen separadas del catálogo/usuarios/estadísticas reales.
- No seleccionar una versión Flyway definitiva hasta reconciliar V15 de Player Statistics Sync; V16 es solo una candidata.

## Capas de verificación

### Comprobaciones a nivel unitario

Cubrir transiciones del estado del mercado, asignaciones únicas de 1.000 créditos, emisión única de 100 tokens, cantidades enteras positivas, costo promedio ponderado, costo proporcional de venta, cierre de posición, P/L realizada/no realizada, validación de versión de cotización y transiciones de estado de idempotencia.

### Comprobaciones PostgreSQL/Testcontainers

Verificar:

1. restricciones únicas de superusuario, cuentas, emisiones, tenencias y `(user, key)`;
2. saldos/cantidades no negativos y exactamente 100 tokens emitidos por jugador elegible;
3. rollback atómico cuando falla una validación o ocurre un error técnico;
4. las compras concurrentes no pueden vender en exceso el inventario del superusuario ni gastar créditos dos veces;
5. las ventas concurrentes no pueden vender en exceso una tenencia ni sobregirar la reserva del superusuario;
6. todas las operaciones utilizan el mismo orden de locks y no generan deadlocks bajo carga mixta de compra/venta;
7. las carreras de publicación/invalidación rechazan versiones exactas obsoletas sin sustituirlas por una nueva;
8. los reintentos idempotentes idénticos devuelven un único resultado, mientras los parámetros en conflicto se rechazan;
9. las solicitudes `PROCESSING` abandonadas pueden recuperarse sin movimientos duplicados;
10. una operación normal concurrente con una inicialización/transición de estado se serializa mediante la barrera compartida/exclusiva sin serializar entre sí las operaciones normales;
11. una desactivación de jugador concurrente con una operación se resuelve mediante el lock de la fila del jugador y nunca opera contra un estado inactivo;
12. no existe una transacción confirmada sin sus movimientos confirmados y resultado final de solicitud.

### Comprobaciones de API/seguridad

Utilizar MockMvc y las convenciones de pruebas API existentes para verificar la propiedad derivada del JWT, el acceso prohibido entre usuarios, el estado de mercado no operativo, la aceptación de cotizaciones exactas, errores de cotizaciones obsoletas, recuperación pendiente/incierta, alcance de paginación/filtros y consistencia de OpenAPI/respuestas de error.

### Demostración controlada reproducible

Preparar cuatro usuarios de prueba claramente identificados y cinco jugadores de prueba claramente identificados. Publicar cotizaciones iniciales de 1.00, realizar compras iniciales, publicar versiones posteriores diferentes mediante el contrato compartido, comparar valores de portfolios abiertos, realizar ventas, verificar ganancias/pérdidas realizadas, cerrar al menos una posición e inspeccionar los historiales personales. Confirmar que las posiciones cerradas desaparecen mientras los resultados realizados permanecen en el resumen del portfolio.

Esta demostración controlada es independiente de los datos de producción y de la verificación de integración posterior que se describe a continuación.

El conjunto de aceptación de `spec.md` contiene FR-001 a FR-052 y SC-001 a SC-011. Las comprobaciones anteriores y los escenarios de API/seguridad/UI cubren esos requisitos; la ejecución posterior entre features cubre específicamente FR-051 y SC-011.

### Verificación posterior de integración entre features

Cuando Player Statistics Sync y Player Valuation estén disponibles, verificar que las estadísticas sincronizadas reales puedan alimentar la valoración y que la valoración publique versiones dinámicas de cotizaciones consumidas por el mercado. Confirmar que Token Market continúa sin realizar scraping, persistencia de estadísticas, cálculo de estrategias ni lógica de rankings. Verificar jugadores inactivos/reactivados y cotizaciones históricas en la integración real.

### Comprobaciones frontend

Utilizar las convenciones Vitest/Playwright del repositorio para verificar listado del mercado, saldo, confirmación de compra/venta, portfolio, filtros del historial, estados de carga, mensajes de cotización obsoleta/ausente, operaciones rechazadas, recuperación pendiente y mensajes de resultado incierto. El cliente no contiene cálculos de precios ni gráficos avanzados.

## Evidencia de finalización del Plan

La implementación debe conservar evidencia de pruebas de atomicidad, concurrencia, idempotencia, invariantes de base de datos, aislamiento de seguridad, contabilidad de promedio ponderado y demostración de cuatro usuarios/cinco jugadores. El Plan queda completo sin ejecutar estas pruebas; Tasks/Implement las programará.
