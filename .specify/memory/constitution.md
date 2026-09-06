# Constitution: Core & Foundation (Devchester United)

## 1. Stack Tecnológico
- **Lenguaje y Framework:** Java 21 con Spring Boot.
- **Base de Datos:** PostgreSQL para persistencia de datos.
- **Testing Frameworks:** JUnit 5 + Testcontainers (para entorno de base de datos real en pruebas de integración).

## 2. Principios de Arquitectura y Diseño por Capas
- **División Estricta de Capas:**
    - `Controller`: Maneja la capa de entrada HTTP/REST. **Solo habla con la capa de Service.**
    - `Service`: Orquesta la lógica entre los objetos de Modelo y la capa de Persistencia.
    - `Modelo`: Contiene la lógica de negocio rica (modelo lógico del dominio). **No conoce ni interactúa con ninguna otra capa.**
    - `Persistencia / Repository`: Conoce las entidades del modelo para persistirlas, pero **no realiza llamadas de lógica sobre él.**
- **OpenAPI v3 / Swagger:** Todos los endpoints expuestos deben documentarse bajo el estándar OpenAPI v3, accesible mediante interfaz de Swagger UI.

- **Estructura de Paquetes Obligatoria (Package by Layer):**
  El proyecto DEBE seguir estrictamente la siguiente organización de paquetes tradicional a partir del paquete raíz (p. ej. `com.devchester.united`):

  ```text
  src/main/java/com/devchester/united/
  ├── configuration/         # Configuración general de Spring / Security / Swagger / App
  ├── controller/            # Controladores REST de la aplicación
  │   ├── dto/               # DTOs de Request y Response (ej. LoginRequestDTO, RegisterRequestDTO)
  │   └── exceptionHandler/  # Manejador global de excepciones (@ControllerAdvice)
  ├── modelo/                # Objetos del modelo lógico del dominio puro
  │   ├── exception/         # Excepciones propias del dominio
  │   └── utils/             # Utilidades o helpers del modelo
  ├── persistence/           # Capa de datos y repositorios/DAOs JPA
  │   └── dto/               # Entidades JPA o DAOs de persistencia
  └── service/               # Servicios y Casos de Uso
      ├── config/            # Configuraciones específicas de servicios
      ├── impl/              # Implementaciones concretas de los servicios
      └── interfaces/        # Interfaces de los servicios

## 3. Políticas de Validación en Capas
- **Validación en DTO (Request):** Forma, tipo de datos, validaciones de formato (`@NotNull`, `@NotBlank`, etc.), trimming y sanitización del input.
- **Validación en Service:** Existencia de recursos y viabilidad operacional (resolución de IDs, búsqueda de entidades en BD).
- **Validación en Modelo:** Invariantes de dominio en los propios objetos de negocio, lanzando **excepciones propias del dominio** cuando se violen.

## 4. Estrategia de Testing y Definición de Listo (Definition of Done)
- **Niveles de Pruebas:**
    - **Unitarias:** Pruebas del dominio puro sin contexto de Spring y sin conexión a BD.
    - **Integración:** Pruebas de Services y Repositories corriendo contra una base de datos PostgreSQL real levantada mediante **Testcontainers**.
    - **End-to-End (E2E):** Pruebas de controladores con **MockMvc**, ubicadas exclusivamente en su propio paquete (jamás dentro de un test de service).
    - **Cobertura:** Todos los tests deben contemplar tanto **casos felices** como **casos borde**.
- **Regla Inmutable de Tests:** Queda estrictamente prohibido modificar o borrar un test existente en cualquier fase del flujo sin pedir permiso y recibir un "SÍ" explícito del usuario.
- **Criterios de Finalización (DoD):** Un requerimiento está terminado únicamente si:
    1. Cuenta con tests unitarios e integración (felices y bordes) y todos ejecutan en PASSED.
    2. La aplicación compila y levanta correctamente con la configuración local.
    3. La colección de Postman del proyecto queda actualizada con los endpoints nuevos.

## 5. Gobernanza, Calidad y CI/CD
- **Build & CI Pipeline:** Todo commit/PR integrado en la rama principal debe ejecutar y aprobar la integración continua en GitHub Actions de forma obligatoria (Build SUCCESS).
- **Quality Gate (SonarCloud):** Proyecto vinculado a SonarCloud manteniendo un umbral estricto: **máximo 10 issues abiertos** (sin vulnerabilidades ni smells críticos).
- **Convenciones de Idioma:**
    - Documentación, mensajes de error y explicaciones en **español**.
    - Nombre del dominio en **español** (sin acentos ni 'ñ' en identificadores de código).
    - Términos técnicos y normativos en **inglés**.

## 6. Alcance de la Entrega Nro 1 (Phase 1 Scope)
- **Seguridad:** Autenticación JWT e identificación obligatoria por API Key para acceder a los endpoints privados.
- **Funcionalidades:**
    - Creación de usuario y generación de API Key.
    - Endpoint de catálogo de jugadores (con filtros por liga, equipo y posición).
- **Fuera de Alcance:** Mercado de tokens (compras/ventas), schedulers, estrategias de valuación y cotizaciones históricas.