
# Especificación de Funcionalidad: Devchester United - Autenticación, Catálogo de Jugadores, Sincronización Externa y Frontend Web

**Rama de Funcionalidad (Feature Branch)**: `001-user-auth-player-catalog`

**Estado vigente (2026-09-16)**: T001–T069 completadas; US1–US4 implementadas y validadas. US5 y los gates finales T070–T088 permanecen pendientes.

**Fecha de Creación**: 2026-09-02

**Estado histórico**: esta línea conserva la descripción del corte inicial; el estado vigente aparece arriba.

**Entrada**: Descripción del usuario: "Crear los requerimientos y contratos para la Entrega Nro 1 de Devchester United: gestión de usuarios, autenticación JWT, generación de API Key, catálogo de jugadores, sincronización del catálogo real desde una API externa de fútbol y frontend web React para consumir las funcionalidades disponibles."

## Clarifications

- Q: ¿Qué debe hacer la sincronización si el proveedor devuelve un jugador válido con `position: null`? → A: Importarlo con posición null y devolver `position: null`; no inventar una categoría ni omitir al jugador por ese motivo.

### Session 2026-09-05

- Q: ¿Los filtros de liga, equipo y posición deben buscar coincidencias exactas o permitir coincidencias parciales? → A: Coincidencias parciales sin distinguir mayúsculas de minúsculas; `team=Madrid` debe encontrar tanto Real Madrid como Atlético de Madrid.
- Q: ¿Las API Keys deben vencer después de un plazo o seguir vigentes hasta que el usuario genere una nueva? → A: Sin vencimiento automático; permanecen vigentes hasta su revocación al generar una nueva.
- Q: Si llegan dos solicitudes simultáneas para generar una API Key del mismo usuario, ¿una debe tener éxito y la otra recibir 409 Conflict? → A: Sí; una sola solicitud genera la clave y las demás solicitudes competidoras reciben 409 Conflict sin modificar la clave ganadora.
- Q: Cuando una API Key se revoca, ¿las consultas al catálogo que ya fueron autorizadas pueden terminar, bloqueando su uso en las siguientes solicitudes? → A: Sí; validar la vigencia al inicio de cada solicitud y permitir que las consultas ya autorizadas terminen.
- Q: ¿Los límites de respuesta deben cumplirse para el 95 % de las solicitudes, permitiendo que hasta un 5 % tarde más? → A: Sí; percentil 95 inferior a 2 segundos para registro más login, 500 ms para generación de API Key y 300 ms para catálogo con 50 consultas concurrentes. El entorno y los datos de prueba se definirán en el plan.

### Session 2026-09-15

- Q: ¿Qué debe pasar con los equipos y jugadores previamente sincronizados que ya no aparezcan en una sincronización completa y válida? → A: Conservarlos como inactivos y excluirlos del catálogo consultable; reactivarlos con los mismos identificadores si reaparecen. El objetivo de 96 equipos cuenta únicamente los activos. Una sincronización fallida o incompleta conserva los datos anteriores.
- Q: ¿Qué debe ocurrir si se solicita una sincronización mientras otra sigue en curso? → A: Permitir una sola sincronización global en curso y rechazar las solicitudes adicionales con 409 Conflict, sin consultar al proveedor ni modificar el catálogo; la primera ejecución continúa.
- Q: ¿Hasta cuándo debe recordar el frontend el JWT y la API Key del usuario? → A: Conservarlos durante la pestaña, incluso al recargar, y descartarlos al cerrar la pestaña o cerrar sesión, respetando la expiración del JWT. Cuando falte la API Key, permitir ingresar la que el usuario guardó o generar otra mediante una acción explícita que revoca la anterior; no rotarla automáticamente.
- Q: ¿Qué debe hacer la sincronización si el proveedor devuelve un jugador válido pero sin número de camiseta? → A: Importarlo con dorsal null, devolver jersey_number: null en el catálogo y mostrar «Sin información» en el frontend, sin inventar un número ni omitir al jugador por ese motivo.
- Q: ¿La sincronización debe tomar la temporada vigente de cada liga o una temporada fijada para esta entrega? → A: Usar en cada sincronización la temporada vigente informada por el proveedor para cada una de las cinco ligas soportadas, sin fijar una temporada para toda la entrega.

## Escenarios de Usuario y Pruebas *(obligatorio)*

### User Story 1 - Registro de Usuario y Autenticación (Prioridad: P1)

Como desarrollador de la plataforma, quiero registrar una nueva cuenta de usuario e iniciar sesión para obtener un JSON Web Token (JWT) seguro, de modo que pueda autenticar mis solicitudes de sesión posteriores para generar mi API Key única.

**Por qué esta prioridad**: Alta. El registro y la autenticación de sesión segura (JWT) son las puertas de entrada a la plataforma. Sin ellos, ningún usuario puede generar credenciales ni acceder a endpoints seguros.

**Prueba Independiente**: Un cliente puede realizar una llamada de registro con credenciales de usuario válidas y únicas, recibir una respuesta exitosa y luego iniciar sesión con esas mismas credenciales para recibir un token JWT válido.

**Escenarios de Aceptación**:

1. **Dado** un nuevo desarrollador con credenciales no registradas, **Cuando** envía una solicitud de registro con un nombre de usuario único, un correo electrónico válido y una contraseña segura, **Entonces** la cuenta se crea exitosamente y se devuelve una respuesta de éxito.
2. **Dado** un desarrollador registrado, **Cuando** envía sus credenciales válidas al endpoint de login, **Entonces** se devuelve un JSON Web Token (JWT) con tiempo limitado.
3. **Dado** credenciales de registro donde el nombre de usuario o el correo electrónico ya están en uso, **Cuando** envía la solicitud de registro, **Entonces** el sistema rechaza la solicitud con un error de conflicto y no crea la cuenta.
4. **Dado** credenciales inválidas o incompletas (p. ej., contraseña débil, correo mal formado), **Cuando** se envían a los endpoints de registro o login, **Entonces** el sistema devuelve un error de validación detallando los problemas.

---

### User Story 2 - Generación Segura de API Key (Prioridad: P1)

Como desarrollador autenticado en la plataforma, quiero solicitar al sistema que genere una API Key única utilizando mi token JWT, para poder autenticar las solicitudes de mi aplicación externa al catálogo de jugadores sin exponer mi nombre de usuario y contraseña en el código del cliente.

**Por qué esta prioridad**: Alta. Esto proporciona las credenciales principales para el acceso programático a la API del Catálogo de Jugadores. Desvincula el acceso programático a largo plazo de los inicios de sesión interactivos basados en sesiones.

**Prueba Independiente**: Un usuario autenticado puede enviar su token JWT válido al endpoint de generación de claves y recibir una API Key nueva y segura.

**Escenarios de Aceptación**:

1. **Dado** un usuario autenticado con un JWT válido, **Cuando** solicita una nueva API Key, **Entonces** se genera y se devuelve una API Key única y segura exactamente una vez.
2. **Dado** una solicitud no autenticada o un JWT expirado/inválido, **Cuando** se solicita la generación de la API Key, **Entonces** el sistema rechaza la solicitud con un error de "no autorizado" (unauthorized).
3. **Dado** un usuario que ya tiene una API Key activa, **Cuando** solicita una nueva, **Entonces** la API Key anterior queda invalidada (revocada) y se devuelve la nueva API Key, garantizando un límite de una sola clave activa por usuario.
4. **Dado** un usuario con JWT válido, **Cuando** se procesan solicitudes concurrentes de generación de API Key para ese usuario, **Entonces** una sola devuelve 200 OK con la nueva clave y las demás devuelven 409 Conflict; las solicitudes rechazadas no crean ni revocan claves.

---

### User Story 3 - Filtrado del Catálogo de Jugadores (Prioridad: P2)

Como consumidor de la API utilizando una API Key válida, quiero buscar y filtrar el catálogo de jugadores por liga, equipo y posición, para que mi aplicación externa pueda obtener y mostrar plantillas y perfiles de jugadores específicos y relevantes.

**Por qué esta prioridad**: Media-Alta. Esta es la funcionalidad principal de recuperación de datos y entrega de valor del módulo de catálogo.

**Prueba Independiente**: Un consumidor puede proporcionar su API Key activa en las cabeceras (headers) y consultar el endpoint de jugadores con varias combinaciones de filtros, recibiendo exactamente el subconjunto de registros de jugadores que coinciden.

**Escenarios de Aceptación**:

1. **Dado** una API Key válida y un catálogo poblado de jugadores, **Cuando** se consulta con filtros de liga, equipo o posición (individualmente o combinados), **Entonces** el sistema devuelve una lista de objetos de jugadores que coinciden.
   - Por ejemplo, `team=Madrid`, `team=madrid` y `team=MADRID` devuelven el mismo conjunto de jugadores de Real Madrid y Atlético de Madrid, si ambos equipos están presentes en el catálogo.
2. **Dado** cualquier consulta al catálogo de jugadores, **Cuando** no se proporciona una API Key o la API Key proporcionada es inválida/revocada, **Entonces** el sistema bloquea la solicitud y devuelve un error de "no autorizado" (unauthorized).
3. **Dado** filtros de consulta que no coinciden con ningún jugador en el catálogo, **Cuando** se realiza la consulta, **Entonces** el sistema devuelve una lista vacía con un estado exitoso.
4. **Dado** una solicitud para buscar jugadores, **Cuando** se proporcionan o se usan por defecto parámetros de paginación page/limit, **Entonces** el sistema devuelve el subconjunto correspondiente de resultados junto con la metadata de paginación.

---

### User Story 4 - Sincronización del Catálogo Real de Jugadores (Prioridad: P1)

Como usuario autenticado existente (rol operativo de administrador u operador) de Devchester United, quiero sincronizar el catálogo de equipos y jugadores desde una fuente externa confiable, para disponer localmente de las plantillas reales de las cinco principales ligas europeas sin depender de la API externa durante las consultas normales del catálogo. En esta entrega la autorización requiere un JWT válido asociado a un usuario existente; no se define una comprobación RBAC adicional.

**Por qué esta prioridad**: Alta. El catálogo actual demuestra el funcionamiento de búsqueda y filtrado, pero la aplicación necesita datos reales y suficientemente completos para representar el dominio previsto.

**Prueba Independiente**: Partiendo de una base de datos sin datos externos previamente sincronizados, el sistema puede ejecutar una sincronización y persistir los 96 equipos pertenecientes a Premier League, LaLiga, Bundesliga, Serie A y Ligue 1 junto con sus jugadores. Una segunda ejecución no crea duplicados.

**Escenarios de Aceptación**:

1. **Dado** que la API externa está disponible y configurada correctamente, **Cuando** se ejecuta la sincronización, **Entonces** el sistema obtiene y persiste los equipos y jugadores pertenecientes a las cinco ligas soportadas.

2. **Dado** un catálogo vacío, **Cuando** finaliza exitosamente una sincronización completa, **Entonces** deben existir 96 equipos activos distribuidos entre:
  - Premier League: 20 equipos.
  - LaLiga: 20 equipos.
  - Bundesliga: 18 equipos.
  - Serie A: 20 equipos.
  - Ligue 1: 18 equipos.

3. **Dado** un equipo o jugador previamente sincronizado, **Cuando** una sincronización posterior devuelve nuevamente esa entidad utilizando el mismo identificador externo, **Entonces** el sistema actualiza sus datos en lugar de crear un duplicado.

4. **Dado** un catálogo previamente sincronizado, **Cuando** la API externa está temporalmente indisponible o devuelve un error, **Entonces** la sincronización falla de forma controlada y el catálogo local válido permanece disponible y sin corrupción.

5. **Dado** un catálogo sincronizado, **Cuando** un consumidor consulta `GET /api/v1/players`, **Entonces** la respuesta se obtiene desde PostgreSQL y no requiere una llamada en tiempo real al proveedor externo.

6. **Dado** que faltan credenciales válidas para la API externa, **Cuando** se intenta sincronizar, **Entonces** el sistema rechaza o aborta la operación sin exponer secretos ni modificar datos locales válidos.

7. **Dado** que se ejecuta dos o más veces la misma sincronización sobre datos externos sin cambios, **Entonces** el número de equipos y jugadores no aumenta por duplicación.

8. **Dado** un equipo o jugador previamente sincronizado, **Cuando** ya no aparece en una sincronización completa y válida, **Entonces** queda inactivo sin eliminarse y se excluye del catálogo consultable y de sus totales de paginación. El objetivo de 96 equipos cuenta únicamente los activos.

9. **Dado** un equipo o jugador inactivo, **Cuando** reaparece con el mismo identificador externo en una sincronización completa y válida, **Entonces** se actualiza y reactiva el registro existente conservando su identificador interno.

10. **Dado** un catálogo previamente sincronizado, **Cuando** una sincronización falla o resulta incompleta, **Entonces** se conservan los datos y estados de actividad anteriores, sin desactivar entidades por ausencias en la respuesta parcial.

11. **Dado** una sincronización en curso, **Cuando** llega otra solicitud de sincronización con JWT válido, del mismo usuario o de otro, **Entonces** la nueva solicitud recibe `409 Conflict` sin consultar al proveedor ni modificar el catálogo; la ejecución en curso continúa. Al finalizar esta, con éxito o error, puede admitirse una nueva sincronización.

12. **Dado** un jugador válido cuyo dorsal está ausente o es `null` en la fuente externa, **Cuando** se completa la sincronización, **Entonces** el jugador se importa o actualiza con dorsal `null` y el catálogo devuelve `jersey_number: null`; esta ausencia no provoca la omisión del jugador ni el fallo de la sincronización.

13. **Dado** que el proveedor informa la temporada vigente de cada liga soportada, **Cuando** se ejecuta una sincronización, **Entonces** se utilizan los equipos y plantillas correspondientes a esa temporada para cada liga, sin fijar una temporada global para la entrega. Si cambia la temporada, una sincronización completa y válida incorpora o reactiva los nuevos participantes y desactiva los ausentes según las reglas de identidad y actividad existentes.

---

### User Story 5 - Frontend Web MVP (Prioridad: P2)

Como usuario de Devchester United, quiero utilizar una interfaz web para registrarme, iniciar sesión, generar mi API Key y consultar el catálogo de jugadores, para utilizar las funcionalidades principales sin tener que interactuar directamente con Swagger o Postman.

**Por qué esta prioridad**: Media-Alta. El backend ya expone las capacidades principales, pero se necesita una interfaz mínima para validar el uso de la plataforma como aplicación web.

**Prueba Independiente**: Un usuario puede abrir el frontend, registrarse, iniciar sesión, generar una API Key y consultar el catálogo aplicando filtros y paginación utilizando exclusivamente la API REST de Devchester United.

**Escenarios de Aceptación**:

1. **Dado** un visitante sin cuenta, **Cuando** completa correctamente el formulario de registro, **Entonces** el frontend utiliza `POST /api/v1/auth/register` y muestra el resultado de la operación.

2. **Dado** un usuario registrado, **Cuando** inicia sesión correctamente, **Entonces** el frontend utiliza `POST /api/v1/auth/login` y conserva el JWT durante la pestaña, incluso al recargar, respetando su expiración; lo descarta al cerrar la pestaña o cerrar sesión.

3. **Dado** un usuario autenticado mediante JWT, **Cuando** solicita generar o rotar su API Key, **Entonces** el frontend utiliza `POST /api/v1/auth/apikey` y muestra la clave completa únicamente en la respuesta en la que fue generada.

4. **Dado** un usuario con una API Key válida, **Cuando** accede al catálogo, **Entonces** puede consultar jugadores utilizando `GET /api/v1/players`.

5. **Dado** el catálogo visible, **Cuando** se aplican filtros por liga, equipo o posición, **Entonces** el frontend envía los parámetros correspondientes al backend y representa los resultados recibidos.

6. **Dado** un catálogo paginado, **Cuando** el usuario cambia de página, **Entonces** el frontend utiliza la metadata entregada por el backend para navegar entre resultados.

7. **Dado** que el backend devuelve un error de validación, autenticación, autorización o disponibilidad, **Cuando** el frontend recibe la respuesta, **Entonces** muestra al usuario un mensaje comprensible sin exponer detalles internos.

8. **Dado** cualquier operación del frontend, **Cuando** necesita información de negocio, **Entonces** consume exclusivamente la API REST de Devchester United y nunca PostgreSQL ni Football-Data.org directamente.

9. **Dado** un JWT vigente y una API Key válida conservados en la pestaña, **Cuando** el usuario recarga o navega dentro del frontend, **Entonces** puede seguir operando sin volver a ingresarlos; la recarga no vuelve a mostrar la clave completa ni genera otra.

10. **Dado** credenciales conservadas por el frontend, **Cuando** el usuario cierra la pestaña o cierra sesión, **Entonces** se descartan tanto el JWT como la API Key del frontend. Esto no revoca la API Key en el backend ni modifica la expiración del JWT.

11. **Dado** que el frontend no conserva una API Key, **Cuando** el usuario quiere consultar el catálogo, **Entonces** puede ingresar una clave que haya guardado o solicitar explícitamente una nueva mediante un JWT vigente, con aviso de que esta acción revoca la anterior. El frontend nunca genera ni rota claves automáticamente.

12. **Dado** un JWT expirado, **Cuando** el usuario intenta una operación que requiere JWT, **Entonces** el frontend solicita un nuevo inicio de sesión; la expiración no revoca la API Key ni modifica su vigencia para consultar el catálogo.

13. **Dado** un jugador con `jersey_number: null`, **Cuando** el frontend muestra sus datos, **Entonces** representa el dorsal como «Sin información».

---

### Casos Borde (Edge Cases)

- **¿Qué sucede cuando una API Key es revocada durante una solicitud de búsqueda en el catálogo?**
  - El sistema valida la vigencia de la API Key al inicio de cada solicitud, antes de consultar el catálogo. Una consulta ya autorizada puede terminar aunque la clave se revoque durante su ejecución. Toda solicitud cuya validación ocurra después de confirmarse la revocación debe recibir 401 Unauthorized con un mensaje en español que indique que la clave es inválida o está revocada. Las API Keys no tienen vencimiento automático.
- **¿Cómo maneja el sistema los parámetros de filtro con caracteres inesperados o sospechosos de SQL-injection?**
  - El sistema debe sanitizar/parametrizar de forma segura todos los filtros de entrada para proteger contra inyecciones en la base de datos y devolver un error 400 Bad Request si los filtros violan los formatos estructurales.
- **¿Qué ocurre si se realizan múltiples solicitudes paralelas para generar una API Key para el mismo usuario simultáneamente?**
  - El sistema debe asegurar que solo una solicitud competidora tenga éxito con 200 OK. Las demás deben recibir 409 Conflict sin crear ni revocar claves. La generación de la clave ganadora y la revocación de la anterior deben ser atómicas; no se deben procesar las solicitudes competidoras como rotaciones sucesivas exitosas.

#### Sincronización externa

- **¿Qué sucede si se solicitan sincronizaciones simultáneas?**
  - Solo puede ejecutarse una sincronización global a la vez. Las solicitudes adicionales autenticadas reciben `409 Conflict`, sin encolarse, consultar al proveedor ni modificar el catálogo. La ejecución en curso continúa y, cuando finaliza con éxito o error, puede admitirse una nueva.
- **¿Qué sucede si Football-Data.org responde parcialmente?**
  - Una respuesta inválida o incompleta que impida garantizar una sincronización consistente debe provocar el fallo controlado de la operación. Deben conservarse los datos y estados de actividad anteriores; no deben eliminarse ni desactivarse entidades por ausencias en una respuesta parcial.
- **¿Qué sucede si un jugador válido no tiene dorsal informado?**
  - El dorsal es opcional: su ausencia o valor `null` se representa como `null` en el dominio, la persistencia y el catálogo, y como «Sin información» en el frontend. Por sí sola, esta ausencia no convierte la respuesta en incompleta ni impide importar o actualizar al jugador. No se inventan números ni se conserva un dorsal anterior si el nuevo dato es desconocido.
- **¿Qué sucede si un equipo o jugador deja de aparecer en la fuente externa?**
  - Solo una sincronización completa y válida puede marcarlo como inactivo. El registro se conserva y se excluye del catálogo consultable; si reaparece con el mismo identificador externo, se actualiza y reactiva conservando su identificador interno. Un jugador solo es consultable si tanto él como su equipo están activos.
- **¿Qué sucede si la sincronización se ejecuta nuevamente?**
  - Las entidades se identifican mediante sus identificadores externos. Los registros existentes deben actualizarse cuando corresponda y no duplicarse.
- **¿Qué sucede si un jugador cambia de equipo?**
  - Una nueva sincronización debe actualizar la asociación local del jugador al equipo informado actualmente por la fuente externa.
- **¿Qué sucede si un equipo cambia algún dato descriptivo?**
  - El registro local correspondiente al mismo `external_id` debe actualizarse sin crear un nuevo equipo.
- **¿Qué sucede si la API externa no está disponible?**
  - La sincronización debe fallar de manera controlada. `GET /api/v1/players` debe continuar funcionando utilizando la información local existente.
- **¿Qué sucede si falta la API Key del proveedor?**
  - La sincronización no debe iniciarse o debe fallar de forma controlada sin exponer la credencial ni modificar los datos existentes.

---

## Requerimientos *(obligatorio)*

### Semántica normativa de concurrencia para la rotación de API Keys

Para un mismo usuario, la rotación debe adquirir la fila del usuario mediante `FOR UPDATE NOWAIT`. Se considera solicitud concurrente o competidora la que intenta entrar en la sección crítica de rotación mientras otra transacción del mismo usuario mantiene ese bloqueo. De las solicitudes que compiten simultáneamente por el bloqueo, una puede adquirirlo y continuar; las demás que encuentren el bloqueo ocupado deben responder `409 Conflict`, sin reintentos, revocaciones ni otras mutaciones. Una solicitud que adquiere el bloqueo después del `commit` de la transacción anterior ya no es concurrente con ella y puede realizar una nueva rotación válida con `200 OK`. En todo momento debe existir como máximo una API Key activa por usuario. Las rotaciones de usuarios distintos no deben bloquearse mutuamente.

### Requerimientos Funcionales

- **FR-001**: El sistema DEBE permitir a los usuarios registrarse con un nombre de usuario único, un correo electrónico válido y una contraseña segura.
- **FR-002**: El sistema DEBE validar los campos de entrada durante el registro (p. ej., longitud mínima de la contraseña, patrón de correo electrónico válido, formato alfanumérico del nombre de usuario).
- **FR-003**: El sistema DEBE realizar el hashing seguro de las contraseñas de los usuarios antes de almacenarlas utilizando un algoritmo de hashing estándar de la industria (p. ej., bcrypt/Argon2).
- **FR-004**: El sistema DEBE verificar las credenciales durante el login y emitir un JSON Web Token (JWT) seguro y con tiempo limitado.
- **FR-005**: El sistema DEBE validar el token JWT para la autenticación en endpoints protegidos de usuario (como la generación de API Key).
- **FR-006**: El sistema DEBE generar una API Key criptográficamente fuerte y única ante la solicitud de un usuario autenticado.
- **FR-007**: El sistema DEBE almacenar las API Keys de forma segura (p. ej., hasheadas en la base de datos, almacenando solo un prefijo corto visible en texto plano para identificación).
- **FR-008**: El sistema DEBE soportar como máximo una API Key activa por usuario. La generación de una nueva clave debe revocar automáticamente la anterior.
  - Ante solicitudes concurrentes de generación para el mismo usuario, solo una DEBE generar la clave y responder 200 OK; las restantes DEBEN responder 409 Conflict sin alterar las claves. La creación y la revocación se realizan de forma atómica.
- **FR-012**: Las API Keys NO DEBEN vencer por el paso del tiempo. Una clave activa permanece vigente hasta su revocación al generar una nueva; esta regla no modifica la expiración de los JWT.
- **FR-009**: El sistema DEBE aplicar la autenticación en el endpoint `GET /players` requiriendo una API Key válida y activa en la cabecera `X-API-KEY`.
  - La vigencia DEBE comprobarse al inicio de cada solicitud, antes de consultar el catálogo. Las consultas autorizadas antes de confirmarse una revocación pueden terminar; las validaciones posteriores DEBEN rechazar la clave revocada con 401 Unauthorized.
- **FR-010**: El sistema DEBE permitir el filtrado del Catálogo de Jugadores por `league`, `team` y `position` mediante parámetros de consulta (query parameters). Cada filtro busca el texto proporcionado como una subcadena del campo correspondiente, sin distinguir mayúsculas de minúsculas.
- **FR-011**: El sistema DEBE soportar paginación en el endpoint `/players` con límites por defecto y máximos para proteger el consumo de recursos.

### Sincronización Externa

- **FR-013**: El sistema DEBE permitir sincronizar el catálogo de equipos y jugadores desde una API externa de fútbol mediante la capa `Adapter`.
- **FR-014**: La sincronización DEBE incluir exclusivamente las cinco competiciones soportadas en esta entrega: Premier League, LaLiga, Bundesliga, Serie A y Ligue 1.
  - En cada ejecución DEBE utilizarse la temporada vigente informada por el proveedor para cada competición. La entrega NO fija una temporada histórica ni permite seleccionarla mediante el endpoint de sincronización.
- **FR-015**: Una sincronización completa exitosa DEBE mantener localmente exactamente 96 equipos activos correspondientes a las cinco ligas soportadas; los equipos inactivos conservados no cuentan para este objetivo.
- **FR-016**: El sistema DEBE persistir localmente los equipos y jugadores obtenidos desde la fuente externa.
- **FR-017**: Toda entidad sincronizada DEBE conservar el identificador externo necesario para reconocerla en sincronizaciones posteriores.
- **FR-018**: La sincronización DEBE ser idempotente respecto del identificador externo: una entidad previamente importada debe actualizarse y no duplicarse.
- **FR-019**: Los fallos de la API externa NO DEBEN eliminar, corromper ni volver inaccesibles datos locales previamente sincronizados.
- **FR-020**: `GET /api/v1/players` DEBE consultar únicamente datos locales y NO DEBE depender de llamadas en tiempo real al proveedor externo.
- **FR-021**: Las credenciales de la API externa DEBEN obtenerse desde configuración segura y NO DEBEN quedar versionadas, persistidas como datos de dominio ni expuestas mediante respuestas HTTP.
- **FR-022**: Las respuestas y estructuras propias del proveedor externo DEBEN traducirse mediante el Adapter antes de ser utilizadas por las capas internas.
- **FR-030**: Tras una sincronización completa y válida, los equipos y jugadores previamente sincronizados que estén ausentes DEBEN conservarse como inactivos. Si reaparecen con el mismo identificador externo, DEBEN actualizarse y reactivarse conservando el identificador interno. Una sincronización fallida o incompleta DEBE conservar los datos y estados de actividad anteriores.
- **FR-031**: `GET /api/v1/players` DEBE incluir únicamente jugadores activos cuyo equipo esté activo; los filtros y totales de paginación DEBEN calcularse sobre ese conjunto consultable.
- **FR-032**: El sistema DEBE permitir como máximo una sincronización global en curso, independientemente del usuario solicitante. Mientras esté en curso, las solicitudes adicionales autenticadas DEBEN recibir `409 Conflict` sin encolarse, consultar al proveedor ni modificar el catálogo. La exclusión DEBE abarcar desde antes de la primera consulta al proveedor hasta la finalización de la operación; tras finalizar con éxito o error, DEBE poder admitirse una nueva sincronización.
- **FR-035**: La sincronización DEBE importar o actualizar jugadores válidos aunque su dorsal esté ausente o sea `null`, representándolo como `null` sin inventar un número. `GET /api/v1/players` DEBE incluir `jersey_number: null` para esos jugadores y el frontend DEBE mostrar «Sin información». Cuando el dorsal esté informado, se mantiene la validación de entero entre 1 y 99.

### Frontend

- **FR-023**: El sistema DEBE disponer de un frontend web implementado en React.
- **FR-024**: El frontend DEBE permitir registro e inicio de sesión utilizando los endpoints existentes del backend.
- **FR-025**: El frontend DEBE permitir generar o rotar la API Key utilizando el JWT del usuario.
- **FR-026**: El frontend DEBE permitir consultar, filtrar y paginar el catálogo utilizando la API Key correspondiente.
- **FR-027**: El frontend DEBE utilizar exclusivamente los contratos REST públicos de Devchester United para acceder a funcionalidades de negocio.
- **FR-028**: El frontend NO DEBE comunicarse directamente con PostgreSQL ni con las APIs externas utilizadas por el backend.
- **FR-029**: El frontend DEBE representar de forma comprensible los errores funcionales devueltos por el backend sin exponer detalles internos.
- **FR-033**: El frontend DEBE conservar el JWT y la API Key durante la pestaña, permitiendo recargas y navegación sin volver a ingresarlos, y DEBE descartarlos al cerrar la pestaña o cerrar sesión. DEBE respetar la expiración del JWT y solicitar un nuevo login para operaciones que lo requieran cuando expire. Descartar las credenciales del frontend NO DEBE revocar la API Key ni modificar la expiración del JWT en el backend.
- **FR-034**: Si no dispone de una API Key, el frontend DEBE permitir ingresar una clave guardada por el usuario o generar una nueva mediante una acción explícita con JWT vigente, advirtiendo que revoca la anterior. NO DEBE generar ni rotar API Keys automáticamente. Conservar la clave para enviarla al catálogo NO habilita volver a mostrarla completa después de la respuesta de generación.

---

### Entidades Clave *(Key Entities)*

- **Usuario (User)**:
  - Representa a un desarrollador registrado o administrador de la aplicación.
  - *Atributos*: `id` (identificador único), `username` (único, alfanumérico), `email` (único, patrón de email), `password_hash` (contraseña hasheada de forma segura), `created_at` (timestamp), `updated_at` (timestamp).
- **APIKey**:
  - Representa una credencial programática asociada a un Usuario.
  - *Ciclo de vida*: Sin fecha de vencimiento; pasa de activa a revocada cuando el usuario genera una nueva clave.
  - *Atributos*: `id` (identificador único), `user_id` (clave foránea a User), `key_hash` (hash criptográfico seguro del token), `key_prefix` (primeros 14 caracteres visibles del token), `is_active` (booleano), `created_at` (timestamp), `revoked_at` (timestamp opcional).
- **Jugador (Player)**:
  - Representa un perfil de deportista perteneciente al catálogo local.
  - *Atributos*: `id` (identificador interno único), `external_id` (identificador estable de la fuente externa), `first_name`, `last_name`, `team_id`, `position`, `jersey_number` (entero de 1 a 99 o `null` si no está informado), `nationality`, `is_active` (booleano), `created_at`, `updated_at`.
  - Cada jugador pertenece a un único Equipo.
  - `external_id` permite identificar y actualizar al jugador durante sincronizaciones posteriores.
  - *Ciclo de vida*: Activo al importarse; pasa a inactivo si está ausente en una sincronización completa y válida, y se reactiva si reaparece. Conserva sus identificadores interno y externo en todas las transiciones.
- **Equipo (Team)**:
  - Representa un club perteneciente a una de las cinco ligas soportadas.
  - *Atributos*: `id` (identificador interno único), `external_id` (identificador estable proporcionado por la fuente externa), `name`, `short_name` (opcional), `tla` (opcional), `league`, `crest_url` (opcional), `is_active` (booleano), `created_at`, `updated_at`.
  - `external_id` debe ser único para evitar duplicados durante sincronizaciones sucesivas.
  - *Ciclo de vida*: Activo al importarse; pasa a inactivo si está ausente en una sincronización completa y válida, y se reactiva si reaparece. Conserva sus identificadores interno y externo en todas las transiciones.
---

## Criterios de Éxito *(obligatorio)*

**Modelo vigente de Jugador:** el contrato REST expone únicamente `name` como nombre completo. Football-Data se almacena exactamente desde `player.name`, sin separar ni inferir apellido. V6 migra los datos históricos concatenando `first_name` y `last_name`, conserva UUIDs e IDs externos y elimina las columnas antiguas.

### Resultados Medibles

### Fuente normativa de exactitud para SC-005

SC-005 se valida contra fixtures deterministas y versionados, no contra cantidades aproximadas ni contra un catálogo externo. El fixture funcional canónico contiene los siguientes seis jugadores, identificados por el número estable de fixture que se transforma en el identificador público `ply_<uuid>` durante la respuesta:

| Fixture | Nombre | Liga | Equipo | Posición | Dorsal | Nacionalidad |
|---:|---|---|---|---|---:|---|
| 1 | Julián Álvarez | LaLiga | Atlético de Madrid | Forward | 9 | Argentina |
| 2 | Jude Bellingham | LaLiga | Real Madrid | Midfielder | 5 | Inglaterra |
| 3 | Phil Foden | Premier League | Manchester City | Forward | 47 | Inglaterra |
| 4 | Dani Carvajal | LaLiga | Real Madrid | Defender | 2 | España |
| 5 | Jan Oblak | LaLiga | Atlético de Madrid | Goalkeeper | 13 | Eslovenia |
| 6 | Manuel Neuer | Bundesliga | Bayern | Goalkeeper | 1 | Alemania |

Este fixture contiene Real Madrid, Atlético de Madrid, LaLiga y todas las posiciones permitidas por el modelo: `Forward`, `Midfielder`, `Defender` y `Goalkeeper`. Sus seis registros permiten comprobar paginación con más de una página usando `limit=3`. Las pruebas de Repository utilizan además un fixture determinista de borde que conserva registros con `%`, `_`, `!`, comillas y variantes con/sin tilde para verificar el escape literal y la significancia de tildes.

### Oráculo normativo de SC-005

Los filtros `league`, `team` y `position` buscan subcadenas sin distinguir mayúsculas/minúsculas; los filtros presentes se combinan mediante AND y los ausentes no restringen. Por tanto, `team=Madrid`, `team=madrid` y `team=MADRID` deben devolver exactamente los mismos cuatro registros del fixture canónico: 1, 2, 4 y 5, incluyendo ambos equipos de Madrid. Los caracteres `%`, `_` y `!` se comparan literalmente mediante la estrategia de escape definida; las tildes son significativas y no se eliminan ni normalizan para hacerlas equivalentes.

El conjunto consultable incluye únicamente jugadores activos cuyo equipo esté activo; los seis jugadores del fixture canónico y sus equipos se consideran activos. El resultado esperado se calcula antes de paginar: primero se determina el conjunto completo de coincidencias dentro del conjunto consultable, luego `total_records`, `total_pages=ceil(total_records/limit)` y finalmente la página ordenada por UUID ascendente. Una página fuera de rango debe responder `200`, devolver `players=[]` y conservar `current_page`, `page_size`, `total_records` y `total_pages` correspondientes al conjunto completo. Una consulta sin coincidencias debe devolver `200`, `players=[]`, `total_records=0` y `total_pages=0`.

La validación de SC-005 debe comparar exactamente el contenido de cada jugador y todos los metadatos de paginación contra el oráculo derivado del fixture: identificador público, nombre, apellido, liga, equipo, posición, dorsal, nacionalidad, orden y totales. Comparar únicamente el número de resultados no acredita SC-005.

### Matriz normativa de seguridad para SC-004

La siguiente matriz define de forma normativa la credencial admitida por cada endpoint. “Permitido” significa que la solicitud puede continuar con las validaciones funcionales propias del endpoint. La presencia de una credencial no requerida no cambia el mecanismo de autenticación del endpoint público. Toda solicitud rechazada por autenticación debe detenerse antes de ejecutar la operación protegida.

| Endpoint / situación | Credencial presentada | Resultado normativo |
|---|---|---|
| `POST /api/v1/auth/register` | Ninguna | Permitido; endpoint público. |
| `POST /api/v1/auth/register` | JWT, API Key o ambas | Permitido; ninguna es necesaria ni se utiliza para autorizar el registro. |
| `POST /api/v1/auth/login` | Ninguna | Permitido; endpoint público. |
| `POST /api/v1/auth/login` | JWT, API Key o ambas | Permitido; ninguna es necesaria ni se utiliza para autorizar el login. |
| `POST /api/v1/auth/apikey` | JWT válido de un usuario existente | Permitido; se autentica exclusivamente mediante JWT. |
| `POST /api/v1/auth/apikey` | JWT ausente, alterado o expirado | `401 Unauthorized`; no se genera ni revoca ninguna API Key. |
| `POST /api/v1/auth/apikey` | JWT válido de un usuario inexistente | `401 Unauthorized`; no se genera ni revoca ninguna API Key. |
| `POST /api/v1/auth/apikey` | Solo API Key, o API Key sin JWT válido | `401 Unauthorized`; una API Key no autoriza esta operación. |
| `POST /api/v1/auth/apikey` | JWT válido + API Key | Permitido si el JWT es válido; la API Key no participa en la autorización. |
| `GET /api/v1/players` | API Key válida y activa | Permitido; se autentica exclusivamente mediante API Key. |
| `GET /api/v1/players` | API Key ausente, inválida o revocada | `401 Unauthorized`; no se consulta el catálogo. |
| `GET /api/v1/players` | Solo JWT, sin API Key | `401 Unauthorized`; un JWT no autoriza esta operación. |
| `GET /api/v1/players` | JWT + API Key válida y activa | Permitido; el JWT no participa en la autorización. |
| `GET /api/v1/players` | Error real de BD al validar la API Key | `503 Service Unavailable`; acceso denegado y no se transforma el fallo en `401`. |
| `GET /api/v1/players` | Solicitud ya autorizada y revocación posterior durante su ejecución | Puede finalizar normalmente. Las nuevas solicitudes que validen la clave después de la revocación reciben `401 Unauthorized`. |
| `POST /api/v1/catalog/sync` | JWT válido de un usuario existente | Permitido; se autentica exclusivamente mediante JWT. |
| `POST /api/v1/catalog/sync` | JWT ausente, alterado o expirado | `401 Unauthorized`; no se inicia la sincronización. |
| `POST /api/v1/catalog/sync` | Solo API Key | `401 Unauthorized`; una API Key no autoriza esta operación. |
| `POST /api/v1/catalog/sync` | JWT válido + API Key | Permitido mediante JWT; la API Key no participa en la autorización. |
| `POST /api/v1/catalog/sync` | JWT válido y otra sincronización en curso | `409 Conflict`; no se consulta al proveedor ni se modifica el catálogo desde la solicitud rechazada. |
| `POST /api/v1/catalog/sync` | Proveedor externo indisponible o respuesta no utilizable | `503 Service Unavailable`; los datos locales válidos permanecen intactos. |


- **SC-001**: El percentil 95 del tiempo total del flujo secuencial de registro más login, hasta recibir el JWT, DEBE ser inferior a 2 segundos.
- **SC-002**: El percentil 95 del tiempo de respuesta de generación de una nueva API Key DEBE ser inferior a 500 milisegundos.
- **SC-003**: El percentil 95 del tiempo de respuesta de las consultas al catálogo protegidas con una API Key válida DEBE ser inferior a 300 milisegundos bajo una carga de 50 consultas concurrentes.
- **SC-004**: Las solicitudes no autenticadas o no autorizadas a endpoints protegidos (protegidos por JWT o API Key) son bloqueadas con un 100% de precisión (cero falsos permitidos).
- **SC-005**: Los resultados de búsqueda en el catálogo coinciden con los criterios de filtro proporcionados con un 100% de precisión.
- **SC-006**: Una sincronización completa exitosa DEBE mantener exactamente 96 equipos activos pertenecientes a las cinco ligas soportadas, según la composición objetivo definida para la entrega. Los equipos inactivos conservados no cuentan para este objetivo.
- **SC-007**: Ejecutar dos veces consecutivas una sincronización contra el mismo conjunto de datos externo DEBE producir cero equipos y cero jugadores duplicados.
- **SC-008**: El 100 % de los equipos y jugadores sincronizados que posean identificador externo válido DEBEN poder ser asociados posteriormente con el mismo registro local.
- **SC-009**: Ante una indisponibilidad simulada del proveedor externo, el 100 % de los datos locales válidos existentes antes del intento de sincronización DEBEN permanecer disponibles después del fallo.
- **SC-010**: El frontend MVP DEBE permitir completar exitosamente el flujo registro → login → generación de API Key → consulta del catálogo utilizando exclusivamente la API REST documentada.

Para SC-001–SC-003, al menos el 95 % de las mediciones debe quedar por debajo del límite correspondiente; hasta un 5 % puede superarlo. El plan debe definir el entorno, el volumen y la distribución de los datos, la duración y el calentamiento de la prueba, y el procedimiento de medición reproducible antes de validar estos criterios. La tolerancia de latencia no permite considerar respuestas de error como operaciones exitosas.

---

## Contratos API (API Contracts)

Contrato ejecutable de referencia: [contracts/openapi.json](./contracts/openapi.json). Los límites de campos, normalización y respuestas adicionales se concretan en [data-model.md](./data-model.md). Todas las rutas usan el prefijo `/api/v1`; las menciones abreviadas a `/players` remiten a `/api/v1/players`.

El plan, el modelo de datos y el contrato ejecutable existentes deben actualizarse durante la planificación para incorporar US4, US5 y las aclaraciones de esta especificación, incluido el dorsal opcional. Sus definiciones anteriores no sustituyen los requisitos aquí aclarados.

### 1. Módulo de Gestión de Usuarios

#### 1.1 Registro de Usuario
- **Endpoint**: `POST /api/v1/auth/register`
- **Autenticación**: Ninguna (Público)
- **Cuerpo de la Solicitud (JSON)**:
  ```json
  {
    "username": "devchesteradmin",
    "email": "admin@devchester.united",
    "password": "SecurePassword123!"
  }
  ```
- **Respuesta - 201 Created (JSON)**:
  ```json
  {
    "status": "success",
    "message": "Usuario registrado correctamente",
    "data": {
      "id": "usr_9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d",
      "username": "devchesteradmin",
      "email": "admin@devchester.united",
      "created_at": "2026-09-02T12:00:00Z"
    }
  }
  ```
- **Respuesta - 400 Bad Request (JSON)**:
  ```json
  {
    "status": "error",
    "message": "Error de validación",
    "errors": [
      {
        "field": "password",
        "issue": "La contraseña es demasiado débil. Debe tener al menos 8 caracteres e incluir una letra mayúscula, una minúscula y un número."
      }
    ]
  }
  ```
- **Respuesta - 409 Conflict (JSON)**:
  ```json
  {
    "status": "error",
    "message": "El nombre de usuario o el correo electrónico ya está registrado."
  }
  ```

#### 1.2 Login de Usuario (Generación de JWT)
- **Endpoint**: `POST /api/v1/auth/login`
- **Autenticación**: Ninguna (Público)
- **Cuerpo de la Solicitud (JSON)**:
  ```json
  {
    "username": "devchesteradmin",
    "password": "SecurePassword123!"
  }
  ```
- **Respuesta - 200 OK (JSON)**:
  ```json
  {
    "status": "success",
    "message": "Autenticación exitosa",
    "data": {
      "token_type": "Bearer",
      "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c3JfOWE4YjdjNmQtNWU0Zi0zYTJiLTFjMGQtOWU4ZjdhNmI1YzRkIiwidXNlcm5hbWUiOiJkZXZjaGVzdGVyX2FkbWluIiwiZXhwIjoxNzk4ODg4MDAwfQ.signature_hash",
      "expires_in": 3600
    }
  }
  ```
- **Respuesta - 401 Unauthorized (JSON)**:
  ```json
  {
    "status": "error",
    "message": "Nombre de usuario o contraseña incorrectos"
  }
  ```

#### 1.3 Generación de API Key
- **Endpoint**: `POST /api/v1/auth/apikey`
- **Autenticación**: Requiere Token JWT Bearer
- **Vigencia**: La API Key generada no vence automáticamente. Generar una nueva revoca la anterior.
- **Concurrencia**: Para solicitudes competidoras del mismo usuario, una sola tiene éxito; las demás reciben 409 Conflict sin alterar las claves.
- **Cabeceras (Headers)**:
  - `Authorization`: `Bearer <access_token>`
- **Cuerpo de la Solicitud**: Ninguno
- **Respuesta - 200 OK (JSON)**:
  ```json
  {
    "status": "success",
    "message": "API Key generada correctamente. Guardá esta clave; no se volverá a mostrar.",
    "data": {
      "api_key": "dev_key_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
      "prefix": "dev_key_AAAAAA",
      "is_active": true,
      "created_at": "2026-09-02T12:05:00Z"
    }
  }
  ```
- **Respuesta - 401 Unauthorized (JSON)**:
  ```json
  {
    "status": "error",
    "message": "No autorizado: JWT ausente, inválido o expirado"
  }
  ```
- **Respuesta - 409 Conflict (JSON)**:
  ```json
  {
    "status": "error",
    "message": "Otra solicitud está generando una API Key para este usuario."
  }
  ```

---

### 2. Módulo de Catálogo de Jugadores

#### 2.1 Obtener Jugadores
- **Endpoint**: `GET /api/v1/players`
- **Autenticación**: Requiere API Key
- **Momento de validación**: Al inicio de cada solicitud. Una consulta ya autorizada puede terminar si la clave se revoca durante su ejecución; las validaciones posteriores a la revocación reciben 401 Unauthorized.
- **Visibilidad**: Solo se devuelven jugadores activos cuyo equipo esté activo. Los registros inactivos quedan excluidos de los resultados y de los totales de paginación.
- **Dorsal opcional**: `jersey_number` siempre se incluye en cada jugador y admite un entero de 1 a 99 o `null` cuando no está informado.
- **Cabeceras (Headers)**:
  - `X-API-KEY`: `dev_key_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA` (ejemplo ilustrativo, no utilizable)
- **Parámetros de Consulta (Query Parameters)**:
  - `league` (opcional): Coincidencia parcial por nombre del torneo/liga, sin distinguir mayúsculas de minúsculas (p. ej., `Premier` encuentra `Premier League`).
  - `team` (opcional): Coincidencia parcial por nombre del equipo, sin distinguir mayúsculas de minúsculas (p. ej., `Madrid` encuentra `Real Madrid` y `Atlético de Madrid`).
  - `position` (opcional): Coincidencia parcial por posición de juego, sin distinguir mayúsculas de minúsculas (p. ej., `mid` encuentra `Midfielder`).
  - `page` (opcional, por defecto: `1`): El número de página para la paginación.
  - `limit` (opcional, por defecto: `20`, máx: `100`): Número de registros de jugadores a devolver por página.
- **Respuesta - 200 OK (JSON)**:
  ```json
  {
    "status": "success",
    "data": {
      "players": [
        {
          "id": "ply_0c1b2a3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
          "first_name": "Luka",
          "last_name": "Modric",
          "league": "LaLiga",
          "team": "Real Madrid",
          "position": "Midfielder",
          "jersey_number": 10,
          "nationality": "Croatia"
        }
      ],
      "pagination": {
        "current_page": 1,
        "page_size": 20,
        "total_pages": 1,
        "total_records": 1
      }
    }
  }
  ```
- **Respuesta - 401 Unauthorized (JSON)**:
  ```json
  {
    "status": "error",
    "message": "No autorizado: API Key inválida o ausente"
  }
  ```

### 2.2 Sincronizar Catálogo

- **Endpoint**: `POST /api/v1/catalog/sync`
- **Autenticación**: Requiere JWT Bearer.
- **Cuerpo**: Ninguno.
- **Objetivo**: Obtener equipos y jugadores de la temporada vigente informada por el proveedor para cada una de las cinco ligas soportadas y sincronizarlos con el catálogo local.
- **Concurrencia**: Una sola sincronización global en curso. Las solicitudes adicionales autenticadas reciben `409 Conflict`, sin encolarse ni realizar llamadas al proveedor o cambios en el catálogo.

- **Respuesta - 200 OK**
```json
{
  "status": "success",
  "message": "Catálogo sincronizado correctamente",
  "data": {
    "teams_processed": 96,
    "players_processed": 2473,
    "teams_created": 0,
    "teams_updated": 96,
    "players_created": 0,
    "players_updated": 2473
  }
}
  ```

- **Respuesta - 401 Unauthorized**
```json
{
  "status": "error",
  "message": "No autorizado: JWT ausente, inválido o expirado"
}
  ```
- **Respuesta - 409 Conflict**
```json
{
  "status": "error",
  "message": "Ya hay una sincronización del catálogo en curso"
}
```

- **Respuesta - 503 Service Unavailable**
```json
{
  "status": "error",
  "message": "No fue posible sincronizar el catálogo con el proveedor externo"
}
  ```
---

## Suposiciones (Assumptions)

- **API Key Única Activa**: Para simplificar la seguridad y gestión en la entrega inicial, un usuario solo puede tener una API Key activa a la vez. La generación de una nueva clave revoca inmediatamente cualquier clave anterior.
- **Separación de Autenticación**: El login de usuario utiliza JWTs de sesión con expiración, mientras que el consumo del catálogo utiliza API Keys sin vencimiento automático, vigentes hasta su revocación al generar una nueva.
- **Catálogo Sincronizado**: El catálogo local se construye y actualiza mediante sincronización con la fuente externa definida. Los consumidores generales continúan teniendo acceso de solo lectura al catálogo.
- **Fuente Externa No Operacionalmente Crítica para Consultas**: La disponibilidad del proveedor externo es necesaria para sincronizar, pero no para consultar el catálogo ya persistido.
- **Composición Objetivo de Ligas**: Para esta entrega se consideran 96 equipos activos distribuidos entre Premier League (20), LaLiga (20), Bundesliga (18), Serie A (20) y Ligue 1 (18). Los registros inactivos se conservan fuera del catálogo consultable y no cuentan para este objetivo.
- **Temporada Vigente**: Cada sincronización utiliza la temporada vigente que informe el proveedor para cada liga. El catálogo refleja ese conjunto actual, sin ofrecer consulta de temporadas históricas.
- **Identidad Externa**: Los identificadores proporcionados por la fuente externa se consideran la referencia para reconocer equipos y jugadores durante sucesivas sincronizaciones.
- **Frontend MVP**: La primera versión del frontend prioriza funcionalidad sobre diseño avanzado y consume exclusivamente funcionalidades ya expuestas por el backend.
- **Filtros Parciales Insensibles a Mayúsculas/Minúsculas**: Los filtros de parámetros de consulta (liga, equipo, posición) buscan subcadenas sin distinguir mayúsculas de minúsculas. La coincidencia parcial es obligatoria para los tres filtros.
- **Sin Asociaciones Complejas**: Los jugadores pertenecen a un solo equipo, una sola liga y tienen una posición principal.
