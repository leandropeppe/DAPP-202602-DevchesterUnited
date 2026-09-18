# Contrato de interfaz: frontend web MVP

**Fecha**: 2026-09-15. SPA React en español. Consume exclusivamente [openapi.json](./openapi.json). No agrega endpoints ni pantalla de sincronización.

## Vistas y transporte

| Vista/acción | Endpoint y credencial | Resultado |
|---|---|---|
| Registro | POST `/api/v1/auth/register`, sin credenciales | 201 confirma y permite login; 400 campos; 409 conflicto |
| Login | POST `/api/v1/auth/login`, sin credenciales | Guarda JWT/expiración; nunca contraseña; 401 genérico |
| Ingresar API Key guardada | Sin petición al guardar | Entrada oculta; se valida al consultar catálogo |
| Generar API Key | POST `/api/v1/auth/apikey`, JWT, sin cuerpo | Acción explícita; no ocurre durante login, navegación o recarga; 200 muestra el secreto una sola vez |
| Catálogo | GET `/api/v1/players`, sin credenciales | Filtros y página conforme al servidor |
| Cerrar sesión | Solo acción local | Borra credenciales y datos protegidos; no revoca clave en backend |

Rutas de vistas: `/registro`, `/login`, `/credenciales`, `/catalogo`; fallback SPA para navegación directa. Registro y catálogo son públicos; generación requiere JWT. La API Key no forma parte obligatoria de la sesión ni se necesita para navegar. Las llamadas usan rutas relativas y únicamente la cabecera indicada. Dev/preview usan proxy Vite `/api/v1` a `http://localhost:8080` sin reescritura; build distribuido se sirve bajo mismo origen con reverse proxy. Nunca incluir `FOOTBALL_DATA_API_KEY`, `JWT_SECRET` ni credenciales PostgreSQL en bundle/VITE_*.

## Ciclo de sesión y visualización de secretos

Guardar `devchester.jwt` y `devchester.jwtExpiresAt` en sessionStorage. Una API Key ingresada o generada para un uso administrativo explícito puede conservarse separadamente, pero no es parte obligatoria de la sesión ni del flujo de catálogo. Al montar, validar estructura/expiración del JWT; el backend sigue siendo autoridad. Mantener el resultado visual completo de la clave recién generada solo en memoria hasta salir de esa vista. Recarga y navegación nunca generan claves ni reconstruyen la pantalla del secreto.

Logout elimina todas las claves de almacenamiento de la aplicación y limpia estado/datos en React. Cierre ordinario de pestaña termina su sesión; nueva pestaña independiente sin opener no hereda credenciales. Abrir ventanas desde la aplicación con noopener. La restauración especial del navegador puede recuperar sessionStorage: no se ofrece borrado irrecuperable ni se borra en beforeunload, que también afectaría recargas. Si almacenamiento está bloqueado, indicar que la sesión funciona en memoria y se pierde al recargar.

JWT expirado/401 de operación JWT → descartar JWT y pedir login para esa acción. El catálogo público no genera ni descarta API Keys. El ID de usuario puede extraerse del sub para estado de presentación, nunca para autorizar.

Generación de API Key: deshabilitar envío mientras espera, no hacer POST en efectos/montaje/navegación. Si la implementación conserva reemplazo explícito, 409 y errores de red no deben provocar reintentos automáticos. Campos de clave ingresada y contraseña son de tipo password; no guardar secretos en URLs, logs, traces o capturas reales.

## Catálogo, estados y accesibilidad

Filtros por liga, equipo y posición con acción Aplicar; resetear page=1 al cambiarlos. No normalizar tildes ni transformar el sentido de los filtros. Los inputs vacíos se omiten. Backend calcula contenido/totales/orden; UI no filtra de nuevo ni recalcula resultados sobre la página. Navegación usa current_page/page_size/total_pages. Cancelar o ignorar respuestas antiguas si cambia la consulta.

Estado inicial público, cargando, error recuperable, lista vacía y resultados diferenciados. Un 503 no se muestra como lista vacía. Dorsal null →«Sin información». Nombre completo combina campos no vacíos sin espacios sobrantes. UI no expone is_active/external_id ni escudos externos; solo muestra perfiles del contrato público.

Controles con labels, errores vinculados a campos, foco visible y navegación por teclado. Resultados/errores anunciados con región accesible; deshabilitar controles pendientes y conservar valores ante validación. Diseño funcional adaptable a móvil/escritorio, sin identidad gráfica avanzada ni biblioteca UI obligatoria.

## Pruebas y scripts previstos

package.json define `dev`, `build`, `preview`, `test` y `test:e2e`; lockfile versionado. Vitest/Testing Library prueban formularios, cliente HTTP, estado y errores. Playwright verifica recarga, logout, pestaña independiente, secreto no mostrado otra vez, ausencia de rotación automática y filtros/paginación/dorsal null. Preparar navegadores antes de suites offline.

SC-010 se ejecuta contra backend local y PostgreSQL descartable del proyecto Compose `devchester-e2e` definido en `frontend/e2e/compose.yml`: backend en loopback 18080, PostgreSQL en tmpfs y fixture normalizado cargado tras Flyway. Playwright inicia preview del build en 4173; `DEV_API_TARGET` configura únicamente el proxy del servidor hacia 18080, nunca secretos del bundle. Ejecutar registro→login→consulta pública y paginación; la generación de API Key es una acción separada y opcional. Probar 400/401/409/503 con simulaciones locales y bloquear hosts ajenos al entorno. Capturas/traces solo con credenciales sintéticas; excluir artefactos sensibles de Git.

Fuentes técnicas: [React](https://react.dev/versions), [Vite proxy](https://vite.dev/config/server-options#server-proxy), [HTML Web Storage](https://html.spec.whatwg.org/multipage/webstorage.html#the-sessionstorage-attribute), [Playwright](https://playwright.dev/docs/mock).
