# Guía de validación: Estadísticas de jugadores

## Prerrequisitos

- Java 21 disponible mediante la configuración del proyecto.
- Docker disponible para Testcontainers/PostgreSQL.
- No se requiere acceso a Internet, credencial real ni cuenta de WhoScored.
- Ejecutar los comandos desde `backend/`.

## Validación automatizada

```powershell
.\gradlew.bat test --no-daemon --console=plain
```

La suite debe compilar y ejecutar las pruebas existentes sin modificarlas. Agregar pruebas de la feature en los paquetes existentes de `modelo`, `adapter`, `service`, `persistence` y `controller`.

## Escenarios mínimos

1. **Modelo**: construir estadísticas válidas por grupos cohesivos; rechazar negativos o rangos inválidos; conservar métricas ausentes sin inventar cero.
2. **Adapter**: procesar respuestas controladas de las cinco competiciones, las tres familias y varias páginas; continuar hasta `totalPages`; rechazar metadata incoherente, JSON inválido, timeout y códigos no exitosos.
3. **Consolidación**: unir `all`, `offensive` y `defensive` por `playerId`, incluyendo métricas individuales ausentes; rechazar jugadores incompatibles o familias incompletas.
4. **Asociación**: resolver contra `Jugador.idExterno`; rechazar un jugador externo no existente y comprobar que no se crea un `Jugador` paralelo.
5. **Idempotencia**: ejecutar dos veces los mismos datos y verificar una fila de estadísticas por jugador con estado equivalente.
6. **Actualización**: cambiar métricas de jugadores existentes y verificar que se actualizan sin duplicados.
7. **Atomicidad**: provocar fallo en una página intermedia, en la validación y en la promoción; comprobar que las consultas siguen mostrando el snapshot anterior completo.
8. **Volumen**: simular más de un lote y verificar que el proceso continúa con metadata de paginación sin depender de una cantidad fija.
9. **Concurrencia**: iniciar dos sincronizaciones y verificar que solo una obtiene el control; la otra devuelve conflicto sin tocar staging publicado.
10. **Contrato**: probar el disparador administrativo con MockMvc, OpenAPI, autenticación, códigos de error y ausencia de detalles del proveedor.

## Validación de persistencia

Las pruebas de integración deben levantar PostgreSQL mediante Testcontainers y aplicar las migraciones desde cero. Verificar:

- FK y unicidad de `jugador_estadisticas.jugador_id`;
- aislamiento por `sync_id` en staging;
- rollback completo de promoción;
- conservación del último snapshot ante fallo externo o de base de datos;
- limpieza o marcado de ejecuciones fallidas sin alterar datos publicados.

## Referencias

- [Modelo de datos](./data-model.md)
- [Contrato](./contracts/player-statistics-sync.md)
- [Especificación funcional](./spec.md)
