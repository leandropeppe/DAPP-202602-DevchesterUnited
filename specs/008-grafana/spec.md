# Feature Specification: Prometheus y Grafana OSS

**Feature Branch**: `feature/grafana`
**Created**: 2026-11-25
**Status**: Draft

## User Scenarios & Testing

### User Story 1 - Consultar el sistema (Priority: P1)

Como responsable de operación quiero un dashboard local autenticado para conocer la salud técnica del backend.

**Independent Test**: Iniciar el stack autorizado y comprobar un target activo, datasource aprovisionado y dashboard disponible.

1. **Given** el stack activo, **When** un operador inicia sesión desde localhost, **Then** visualiza `Devchester United — System Monitoring` con siete indicadores o ausencia de datos explícita.

### User Story 2 - Mantener el aislamiento (Priority: P1)

Como responsable de seguridad quiero que Grafana no amplíe el acceso al backend.

**Independent Test**: Inspeccionar redes y puertos del Compose combinado.

1. **Given** Grafana activo, **When** se inspecciona su red, **Then** sólo pertenece a `grafana-ui`.
2. **Given** Prometheus activo, **When** recolecta, **Then** consulta exclusivamente `backend:8081/actuator/prometheus`.

### User Story 3 - Reproducir la configuración (Priority: P2)

Como responsable técnico quiero datasource y dashboard aprovisionados y persistentes.

**Independent Test**: Recrear contenedores sin borrar volúmenes y verificar la configuración.

## Requirements

- **FR-001**: Incorporar `prom/prometheus:v3.16.0` y `grafana/grafana:13.3.2`, sin `latest`.
- **FR-002**: Prometheus debe recolectar sólo el endpoint administrativo aprobado y retener siete días.
- **FR-003**: Grafana debe publicar sólo `127.0.0.1:3000:3000`, deshabilitar anonimato y registro público.
- **FR-004**: Prometheus no debe publicar puertos ni habilitar APIs administrativas opcionales.
- **FR-005**: Backend permanece sólo en `observability`; Prometheus usa `observability` y `grafana-ui`; Grafana sólo `grafana-ui`.
- **FR-006**: Datasource y dashboard se aprovisionan por archivos de sólo lectura.
- **FR-007**: `GRAFANA_ADMIN_PASSWORD` procede de configuración local ignorada y su ausencia sólo bloquea el overlay.
- **FR-008**: El dashboard tiene hasta siete paneles: disponibilidad, CPU, heap, HTTP, latencia, 5xx y HikariCP.
- **FR-009**: No modificar Java, frontend, PostgreSQL ni `specs/007-actuator-prometheus`.

## Success Criteria

- **SC-001**: Un operador local autenticado puede consultar los indicadores sin configuración manual de Grafana.
- **SC-002**: No hay puertos publicados por backend o Prometheus y Grafana sólo se publica en loopback.
- **SC-003**: Un scrape exitoso identifica al backend con `job=devchester-backend`.

## Assumptions

- La contraseña se configura localmente en `backend/docker/.env` sin versionarla ni compartirla.
- La validación completa de persistencia, aislamiento negativo y regresión queda para G03.
