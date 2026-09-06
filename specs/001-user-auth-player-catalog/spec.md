
# Especificación de Funcionalidad: Autenticación de Usuario y API de Catálogo de Jugadores

**Rama de Funcionalidad (Feature Branch)**: `001-user-auth-player-catalog`

**Fecha de Creación**: 2026-09-02

**Estado**: Draft (Borrador)

**Entrada**: Descripción del usuario: "Crear los requerimientos y contratos API para la Entrega Nro 1: módulo de gestión de usuarios (registro y generación de API Key con autenticación JWT) y módulo de catálogo de jugadores (endpoint GET /players con filtros por liga, equipo y posición)."

## Clarifications

### Session 2026-09-05

- Q: ¿Los filtros de liga, equipo y posición deben buscar coincidencias exactas o permitir coincidencias parciales? → A: Coincidencias parciales sin distinguir mayúsculas de minúsculas; `team=Madrid` debe encontrar tanto Real Madrid como Atlético de Madrid.
- Q: ¿Las API Keys deben vencer después de un plazo o seguir vigentes hasta que el usuario genere una nueva? → A: Sin vencimiento automático; permanecen vigentes hasta su revocación al generar una nueva.
- Q: Si llegan dos solicitudes simultáneas para generar una API Key del mismo usuario, ¿una debe tener éxito y la otra recibir 409 Conflict? → A: Sí; una sola solicitud genera la clave y las demás solicitudes competidoras reciben 409 Conflict sin modificar la clave ganadora.
- Q: Cuando una API Key se revoca, ¿las consultas al catálogo que ya fueron autorizadas pueden terminar, bloqueando su uso en las siguientes solicitudes? → A: Sí; validar la vigencia al inicio de cada solicitud y permitir que las consultas ya autorizadas terminen.
- Q: ¿Los límites de respuesta deben cumplirse para el 95 % de las solicitudes, permitiendo que hasta un 5 % tarde más? → A: Sí; percentil 95 inferior a 2 segundos para registro más login, 500 ms para generación de API Key y 300 ms para catálogo con 50 consultas concurrentes. El entorno y los datos de prueba se definirán en el plan.

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

### Casos Borde (Edge Cases)

- **¿Qué sucede cuando una API Key es revocada durante una solicitud de búsqueda en el catálogo?**
  - El sistema valida la vigencia de la API Key al inicio de cada solicitud, antes de consultar el catálogo. Una consulta ya autorizada puede terminar aunque la clave se revoque durante su ejecución. Toda solicitud cuya validación ocurra después de confirmarse la revocación debe recibir 401 Unauthorized con un mensaje en español que indique que la clave es inválida o está revocada. Las API Keys no tienen vencimiento automático.
- **¿Cómo maneja el sistema los parámetros de filtro con caracteres inesperados o sospechosos de SQL-injection?**
  - El sistema debe sanitizar/parametrizar de forma segura todos los filtros de entrada para proteger contra inyecciones en la base de datos y devolver un error 400 Bad Request si los filtros violan los formatos estructurales.
- **¿Qué ocurre si se realizan múltiples solicitudes paralelas para generar una API Key para el mismo usuario simultáneamente?**
  - El sistema debe asegurar que solo una solicitud competidora tenga éxito con 200 OK. Las demás deben recibir 409 Conflict sin crear ni revocar claves. La generación de la clave ganadora y la revocación de la anterior deben ser atómicas; no se deben procesar las solicitudes competidoras como rotaciones sucesivas exitosas.

---

## Requerimientos *(obligatorio)*

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
  - Representa un perfil de deportista en el catálogo buscable.
  - *Atributos*: `id` (identificador único), `first_name` (string), `last_name` (string), `league` (string, p. ej., "LaLiga"), `team` (string, p. ej., "Real Madrid"), `position` (string, p. ej., "Forward"), `jersey_number` (entero), `nationality` (string).

---

## Criterios de Éxito *(obligatorio)*

### Resultados Medibles

- **SC-001**: El percentil 95 del tiempo total del flujo secuencial de registro más login, hasta recibir el JWT, DEBE ser inferior a 2 segundos.
- **SC-002**: El percentil 95 del tiempo de respuesta de generación de una nueva API Key DEBE ser inferior a 500 milisegundos.
- **SC-003**: El percentil 95 del tiempo de respuesta de las consultas al catálogo protegidas con una API Key válida DEBE ser inferior a 300 milisegundos bajo una carga de 50 consultas concurrentes.
- **SC-004**: Las solicitudes no autenticadas o no autorizadas a endpoints protegidos (protegidos por JWT o API Key) son bloqueadas con un 100% de precisión (cero falsos permitidos).
- **SC-005**: Los resultados de búsqueda en el catálogo coinciden con los criterios de filtro proporcionados con un 100% de precisión.

Para SC-001–SC-003, al menos el 95 % de las mediciones debe quedar por debajo del límite correspondiente; hasta un 5 % puede superarlo. El plan debe definir el entorno, el volumen y la distribución de los datos, la duración y el calentamiento de la prueba, y el procedimiento de medición reproducible antes de validar estos criterios. La tolerancia de latencia no permite considerar respuestas de error como operaciones exitosas.

---

## Contratos API (API Contracts)

Contrato ejecutable de referencia: [contracts/openapi.json](./contracts/openapi.json). Los límites de campos, normalización y respuestas adicionales se concretan en [data-model.md](./data-model.md). Todas las rutas usan el prefijo `/api/v1`; las menciones abreviadas a `/players` remiten a `/api/v1/players`.

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

---

## Suposiciones (Assumptions)

- **API Key Única Activa**: Para simplificar la seguridad y gestión en la entrega inicial, un usuario solo puede tener una API Key activa a la vez. La generación de una nueva clave revoca inmediatamente cualquier clave anterior.
- **Separación de Autenticación**: El login de usuario utiliza JWTs de sesión con expiración, mientras que el consumo del catálogo utiliza API Keys sin vencimiento automático, vigentes hasta su revocación al generar una nueva.
- **Catálogo Pre-poblado**: El catálogo de jugadores es de solo lectura para los consumidores generales de la API que utilizan las API Keys. Las operaciones CRUD administrativas en el catálogo están fuera del alcance de la Entrega 1.
- **Filtros Parciales Insensibles a Mayúsculas/Minúsculas**: Los filtros de parámetros de consulta (liga, equipo, posición) buscan subcadenas sin distinguir mayúsculas de minúsculas. La coincidencia parcial es obligatoria para los tres filtros.
- **Sin Asociaciones Complejas**: Los jugadores pertenecen a un solo equipo, una sola liga y tienen una posición principal.
