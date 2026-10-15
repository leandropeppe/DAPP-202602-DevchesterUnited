<!--
Sync Impact Report
- Version change: 1.1.0 → 1.2.0
- Principios modificados: calidad de tests; complejidad y diseño; legibilidad; accesibilidad y asincronía frontend; robustez técnica y seguridad; validación previa al cierre de features
- Secciones agregadas: reglas preventivas de calidad en tests, diseño, legibilidad, frontend, robustez técnica y revisión previa al cierre de features
- Secciones removidas: ninguna
- Follow-up TODOs: ninguno
-->

# Constitution: Core & Foundation (Devchester United)

## 1. Stack Tecnológico
- **Lenguaje y Framework Backend:** Java 21 con Spring Boot.
- **Frontend Web:** React.
- **Base de Datos:** PostgreSQL para persistencia de datos.
- **Testing Frameworks Backend:** JUnit 5 + Testcontainers (para entorno de base de datos real en pruebas de integración).
- **Integraciones Externas:** Las fuentes de datos de terceros, incluyendo APIs, endpoints HTTP y scraping web, deben consumirse exclusivamente a través de una capa `Adapter`, desacoplada del dominio y de los controladores.
---
## 2. Principios de Arquitectura y Diseño por Capas

### División estricta de capas

- `Controller`
  - Maneja exclusivamente la capa de entrada HTTP/REST.
  - Solo habla con la capa de `Service`.
  - No puede acceder directamente a `Repository`, `Adapter` ni APIs externas.

- `Service`
  - Orquesta los casos de uso de la aplicación.
  - Coordina objetos de `Modelo`, `Persistence` y `Adapter`.
  - Contiene la lógica de aplicación y la coordinación transaccional cuando corresponda.
  - No debe contener detalles HTTP propios de proveedores externos.

- `Modelo`
  - Contiene la lógica de negocio rica y las invariantes del dominio.
  - No conoce ni interactúa con Controller, Service, Persistence, Adapter, Spring ni APIs externas.
  - Debe mantenerse independiente de infraestructura.

- `Persistence / Repository`
  - Responsable exclusivamente de la persistencia y recuperación de datos.
  - Conoce las entidades necesarias para persistir el estado.
  - No realiza llamadas de lógica de negocio sobre el modelo.
  - No consume APIs externas.

- `Adapter`
  - Encapsula toda comunicación con sistemas o APIs externas.
  - Traduce contratos externos a representaciones internas de la aplicación.
  - No contiene lógica de negocio.
  - Solo puede ser utilizado por la capa `Service`.
  - No debe ser invocado directamente desde Controller, Modelo o Repository.

### Regla de dependencias permitidas

```text
Controller ──→ Service

Service ──→ Modelo
Service ──→ Persistence
Service ──→ Adapter

Adapter ──→ API externa
Persistence ──→ PostgreSQL
```

No están permitidas dependencias directas como:

```text
Controller ──X──→ Repository
Controller ──X──→ Adapter
Controller ──X──→ API externa

Modelo ──X──→ Spring
Modelo ──X──→ Persistence
Modelo ──X──→ Adapter
Modelo ──X──→ API externa

Frontend ──X──→ PostgreSQL
Frontend ──X──→ APIs externas del backend
```

### Persistencia local frente a integraciones externas

- Los datos provenientes de APIs externas que sean necesarios para el funcionamiento del dominio deben persistirse localmente cuando así lo determine el caso de uso.
- Los endpoints de consulta del negocio no deben depender en tiempo real de la disponibilidad de una API externa, salvo que el requisito lo establezca explícitamente.
- Una indisponibilidad temporal del proveedor externo no debe impedir consultar información local previamente persistida.
- Un fallo de sincronización no debe corromper ni eliminar datos locales válidos previamente almacenados.

### OpenAPI v3 / Swagger

- Todos los endpoints públicos expuestos por el backend deben documentarse mediante OpenAPI v3.
- La documentación debe estar disponible mediante Swagger UI en los entornos definidos por la aplicación.
- El contrato OpenAPI debe representar correctamente endpoints, requests, responses, códigos HTTP, mecanismos de autenticación y esquemas de error.

### Complejidad y diseño de interfaces

- Los métodos deben mantener una complejidad cognitiva razonable y una única responsabilidad principal.
- Cuando un método acumule validaciones, parsing, transformación y construcción de errores, deben extraerse responsabilidades en funciones u objetos cohesivos.
- Deben evitarse constructores y métodos con listas largas de parámetros. Cuando preserve la semántica, deben preferirse objetos de entrada, comandos, value objects o builders.
- Las clases inmutables no deben convertirse automáticamente a `record` si el cambio puede alterar `equals`, `hashCode`, serialización, construcción o cualquier comportamiento relevante del dominio.
- Los literales repetidos que representen una misma decisión o concepto deben extraerse a constantes con nombre significativo.

---

## 3. Estructura de Paquetes Obligatoria

El backend DEBE seguir estrictamente una organización Package by Layer a partir del paquete raíz, por ejemplo:

```text
src/main/java/com/devchester/united/
├── configuration/
├── controller/
│   ├── dto/
│   └── exceptionHandler/
├── modelo/
│   ├── exception/
│   └── utils/
├── persistence/
│   └── dto/
├── adapter/
│   ├── dto/
│   └── impl/
└── service/
    ├── config/
    ├── impl/
    └── interfaces/
```

### Regla de DTOs externos

- Los DTOs pertenecientes a APIs externas deben vivir dentro de `adapter/dto`.
- Los DTOs externos no deben reutilizarse como DTOs públicos del Controller.
- Los DTOs externos no deben convertirse en objetos de dominio de forma implícita.
- La traducción entre contrato externo y representación interna debe ocurrir dentro de la capa `Adapter` o mediante mecanismos de mapeo asociados a ella.

### Correspondencia entre paquetes y rutas

- Toda declaración `package` debe corresponder exactamente con la ruta física del archivo fuente.
- Las nuevas clases, tests y recursos deben ubicarse en el paquete que represente su responsabilidad real.

---

## 4. Políticas de Validación en Capas

- **Validación en DTO Request:** forma, tipo, requeridos, formato, longitudes, trimming, sanitización y validaciones como `@NotNull`, `@NotBlank`, etc.
- **Validación en Service:** existencia de recursos, resolución de IDs, viabilidad operacional, reglas que requieran persistencia y coordinación del caso de uso.
- **Validación en Modelo:** invariantes de dominio, estados inválidos y reglas de negocio puras mediante excepciones propias del dominio.

Las validaciones realizadas en capas superiores no reemplazan las invariantes del Modelo.

---

## 5. Integraciones con Fuentes Externas

- Toda integración con terceros DEBE implementarse mediante la capa `Adapter`.
- Queda prohibido realizar llamadas HTTP a APIs externas desde Controller, Modelo o Repository.
- Los Services no deben depender de detalles HTTP específicos del proveedor.

### Credenciales y secretos

- API Keys, tokens y credenciales externas deben proporcionarse mediante variables de entorno o mecanismos seguros equivalentes.
- Queda estrictamente prohibido versionar secretos reales, incluir credenciales reales en código o configuración versionada, o registrar secretos completos en logs.

### Mapeo de datos

- Los contratos externos deben encapsularse dentro del Adapter.
- Las estructuras entregadas por un proveedor externo no definen directamente el modelo de dominio de Devchester United.
- El Adapter debe traducir los datos externos a representaciones internas controladas por la aplicación.

### Abstracción de fuentes externas

- Toda fuente externa de datos DEBE estar desacoplada de la capa `Service`
  mediante una interfaz definida por la aplicación.
- El `Service` debe depender de dicha interfaz y no de una implementación
  concreta del mecanismo de obtención de datos.
- Las implementaciones concretas pueden utilizar APIs HTTP, endpoints JSON,
  scraping HTML u otros mecanismos de integración, siempre encapsulados
  dentro de la capa `Adapter`.
- Cambiar el mecanismo de obtención de datos no debe requerir modificar la
  lógica de negocio ni el caso de uso de sincronización.
- Los detalles específicos del proveedor, incluyendo URLs, selectores HTML,
  parámetros HTTP, identificadores externos y estructuras de respuesta,
  deben permanecer encapsulados dentro del Adapter.

### Sincronización

- Los procesos de sincronización deben ser idempotentes siempre que el caso de uso lo permita.
- Deben evitarse duplicados al volver a importar información previamente sincronizada.
- Las entidades sincronizadas deben conservar, cuando sea necesario, el identificador externo del proveedor para permitir futuras actualizaciones.
- Una nueva sincronización debe actualizar entidades existentes cuando corresponda en lugar de crear duplicados.

### Manejo de errores externos

- Los errores de proveedores externos deben traducirse a errores propios de la aplicación.
- Los detalles internos de la respuesta del proveedor no deben filtrarse directamente al consumidor de la API.
- Timeouts, errores HTTP o respuestas inválidas no deben dejar el sistema en un estado inconsistente.
- Un fallo externo no debe provocar la eliminación de información local válida previamente sincronizada.

### Procesamiento de grandes volúmenes

- Las integraciones externas deben evitar cargar en memoria cantidades de
  datos mayores a las necesarias para completar el caso de uso.
- Cuando la fuente externa permita paginación, lotes o procesamiento
  incremental, estos mecanismos deben utilizarse cuando resulte apropiado.
- El tamaño de página o lote debe ser una decisión de infraestructura y no
  debe formar parte de la lógica de negocio.
- El procesamiento por lotes no debe comprometer la consistencia global de
  la sincronización.

### Atomicidad de sincronizaciones

- Una sincronización que represente una única operación lógica DEBE ser
  atómica respecto de la persistencia local.
- No deben quedar actualizaciones parciales visibles si la sincronización
  falla antes de completarse.
- Ante un error durante la persistencia, los cambios correspondientes a la
  sincronización deben revertirse y conservarse el último estado local válido.
- La obtención de datos desde proveedores externos debe separarse, cuando
  resulte posible, de la transacción de persistencia para evitar mantener
  transacciones de base de datos abiertas durante operaciones de red.
- Los datos externos deben validarse antes de reemplazar o actualizar el
  estado local persistido.

---
## 6. Estrategia de Testing y Definition of Done

### Niveles de pruebas

- **Unitarias:** dominio puro, sin Spring ni conexión a base de datos.
- **Integración:** Services y Repositories contra PostgreSQL real mediante Testcontainers.
- **End-to-End (E2E):** controladores con MockMvc, ubicados exclusivamente en su paquete correspondiente.
- **Adapter:** integraciones externas probadas sin depender del proveedor real, contemplando éxito, errores HTTP, respuestas inválidas o incompletas, fallos de comunicación, timeout y mapeo.
- **Cobertura funcional:** todos los tests deben contemplar casos felices y casos borde.

### Calidad y legibilidad de tests

- Las lambdas utilizadas para verificar excepciones deben contener únicamente la operación cuya excepción se espera.
- La preparación de argumentos, mocks, fixtures y estado previo debe realizarse fuera de dichas lambdas.
- Deben preferirse APIs de testing vigentes, idiomáticas y semánticas, evitando APIs obsoletas o patrones de prueba desaconsejados.
- Los imports duplicados o sin uso deben eliminarse.
- Las declaraciones de variables deben mantenerse claras, evitando agrupar variables independientes en una misma declaración.
- Los tests deben expresar una intención única y verificable, con una estructura clara de preparación, ejecución y validación.

### Regla inmutable de tests

Queda estrictamente prohibido modificar o borrar un test existente en cualquier fase del flujo sin pedir permiso y recibir un **"SÍ" explícito del usuario**.

### Restricción de APIs reales en tests

- Las suites automatizadas no deben consumir cuotas reales de proveedores externos.
- Los tests no deben depender de conectividad a Internet.
- Los tests no deben requerir credenciales reales de terceros.

### Criterios de Finalización (DoD)

Un requerimiento está terminado únicamente si:

1. Cuenta con tests unitarios y/o de integración según corresponda, incluyendo casos felices y borde, y todos ejecutan en PASSED.
2. La aplicación compila correctamente.
3. La aplicación levanta correctamente con la configuración local definida.
4. La colección de Postman queda actualizada cuando el requerimiento modifica o agrega endpoints.

Cuando el requerimiento incluya una integración externa:

5. El Adapter cuenta con pruebas automatizadas.
6. Se verifica el comportamiento ante indisponibilidad o respuestas inválidas del proveedor.
7. Ningún secreto real queda versionado.
8. La sincronización puede ejecutarse nuevamente sin generar duplicados indebidos.

Cuando el requerimiento incluya frontend:

9. El frontend compila correctamente.
10. El frontend consume únicamente endpoints documentados del backend.
11. Las validaciones del frontend son complementarias y no reemplazan las validaciones del backend.
12. Los tests nuevos o modificados respetan las reglas de calidad y legibilidad definidas en esta Constitution.
13. Las APIs deprecated y los patrones obsoletos se eliminan o se justifican explícitamente cuando no exista una alternativa compatible.

## 7. Frontend Web

- El frontend web se implementará utilizando React.
- El frontend es consumidor de la API REST de Devchester United.
- El frontend no accede directamente a PostgreSQL.
- El frontend no debe consumir directamente APIs externas utilizadas internamente por el backend.
- Toda comunicación con el backend debe respetar los contratos HTTP/OpenAPI publicados.

### Responsabilidades del frontend

El frontend puede contener lógica de presentación, navegación, estado de interfaz, validaciones de experiencia de usuario y transformación de datos para representación visual.

El frontend no debe contener lógica de negocio del dominio, acceso directo a persistencia, credenciales de proveedores externos ni secretos de backend.

### Seguridad

- Los secretos de infraestructura y proveedores externos nunca deben exponerse al frontend.
- Ninguna validación realizada en React reemplaza la validación equivalente del backend.
- La autorización efectiva siempre es responsabilidad del backend.

### Contratos

- El frontend debe consumir exclusivamente endpoints soportados y documentados por la API.
- Cuando una necesidad del frontend requiera información no disponible, primero debe evolucionarse el contrato backend correspondiente.

### Accesibilidad y asincronía explícita

- Deben utilizarse elementos HTML semánticos e interactivos apropiados para cada acción.
- No deben simularse links o botones mediante elementos no interactivos cuando exista un elemento HTML adecuado.
- Toda Promise debe manejarse explícitamente mediante `await`, `catch`, `then` con rejection handler o `void` cuando se descarte intencionalmente.
- Deben evitarse expresiones anidadas difíciles de leer cuando puedan expresarse mediante variables, funciones auxiliares o condiciones más claras.

## 8. Gobernanza, Calidad y CI/CD

### Build & CI Pipeline

- Todo commit o Pull Request integrado en la rama principal debe ejecutar y aprobar la integración continua mediante GitHub Actions.
- El pipeline debe mantener estado `BUILD SUCCESS`.

### Quality Gate

- El proyecto debe estar vinculado a SonarCloud.
- Se mantiene como criterio máximo:
  - 10 issues abiertos;
  - 0 vulnerabilidades;
  - 0 smells críticos.

### Robustez técnica y seguridad

- Las expresiones regulares deben evitar patrones con riesgo de backtracking superlineal, especialmente sobre entradas controladas externamente.
- No debe capturarse `Error` salvo que exista una razón técnica explícita, documentada y limitada al caso necesario.
- Las operaciones transaccionales que requieran el proxy de Spring no deben depender de self-invocation.
- El comportamiento de rollback ante checked exceptions debe declararse explícitamente cuando corresponda al caso de uso.

### Revisión previa al cierre de una feature

- Antes de considerar terminada una feature, debe revisarse la presencia de los patrones de calidad históricos del proyecto.
- Debe ejecutarse SonarCloud como parte de la validación final.
- Las nuevas issues de severidad alta o crítica deben resolverse antes del cierre de la feature o, excepcionalmente, quedar justificadas explícitamente cuando exista una limitación técnica, falso positivo o restricción externa verificable.
- Las issues Medium o Low que permanezcan abiertas deben evaluarse, justificarse y quedar documentadas.
- La revisión debe considerar tanto backend como frontend cuando la feature afecte a ambos.

### Legibilidad y mantenimiento

- Debe declararse una variable por sentencia cuando existan variables independientes o cuando agruparlas reduzca la claridad.
- El código comentado obsoleto debe eliminarse; los comentarios deben reservarse para explicar decisiones, restricciones o contexto que no resulte evidente del código.
- La estructura e indentación deben hacer inequívoco el flujo de control, especialmente en condicionales, callbacks, excepciones y bloques anidados.
- Las constantes deben utilizar nombres que expresen el significado del valor y no únicamente su representación literal.

### Convenciones de idioma

- Documentación, mensajes de error y explicaciones: español.
- Nombre del dominio: español.
- Los identificadores del código no utilizan acentos ni `ñ`.
- Términos técnicos, estándares y conceptos normativos pueden mantenerse en inglés.

---

## 9. Estándares Permanentes del Frontend

Estas reglas son obligatorias para todas las futuras páginas, componentes y
modificaciones del frontend. Su objetivo es mantener un código legible,
predecible y consistente sin alterar el comportamiento funcional.

- La indentación debe ser consistente y utilizar exactamente 2 espacios.
- El estilo JavaScript existente debe conservarse: comillas simples y ausencia
  de punto y coma.
- No se deben escribir múltiples instrucciones en una misma línea.
- Las funciones, condicionales, bloques try/catch y callbacks complejos deben
  utilizar bloques multilínea.
- Los elementos JSX anidados deben ubicarse en líneas independientes, con
  indentación jerárquica.
- Los atributos JSX deben distribuirse en varias líneas cuando eso mejore la
  legibilidad.
- Se deben evitar operadores ternarios extensos en una sola línea.
- El código debe separar visualmente imports, constantes, hooks, funciones
  auxiliares y JSX.
- Prettier debe utilizarse como herramienta de formato, respetando las
  convenciones del proyecto.
- Toda implementación o modificación frontend debe aplicar estas reglas y
  preservar comportamiento, validaciones, accesibilidad y contratos existentes.

## 10. Instrucciones de Desarrollo Frontend para Codex

Codex debe tratar la sección 10 como una regla permanente al crear o modificar
cualquier archivo del frontend. Antes de entregar cambios debe revisar que no
haya instrucciones múltiples en una línea, que el JSX mantenga estructura
jerárquica y que el formato siga las convenciones indicadas. Los cambios de
formato no deben modificar validaciones, accesibilidad, llamadas HTTP ni
contratos del backend.

**Version**: 1.2.0 | **Ratified**: TODO(RATIFICATION_DATE): conservar la fecha
original de adopción cuando se documente | **Last Amended**: 2026-10-14
