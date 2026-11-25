# Implementation Plan: Prometheus y Grafana OSS

**Branch**: `feature/grafana` | **Date**: 2026-11-25 | **Spec**: [spec.md](./spec.md)

## Summary

Un overlay Compose añade Prometheus y Grafana sin alterar el Compose de Actuator. Prometheus será el único puente entre `observability` y `grafana-ui`; Grafana sólo verá Prometheus.

## Technical Context

**Images**: `prom/prometheus:v3.16.0`, `grafana/grafana:13.3.2`.
**Storage**: volúmenes Docker para TSDB y Grafana.
**Validation**: Compose, `promtool`, JSON, provisioning y comprobaciones dinámicas iniciales.
**Constraints**: secretos locales, montajes read-only, sin Java/frontend/base de datos.

## Constitution Check

PASS: sólo infraestructura local; no se modifica código de negocio ni se versionan secretos.

## Architecture

```text
host 127.0.0.1:3000 -> grafana -> grafana-ui -> prometheus -> observability -> backend:8081
                                  -> grafana-access (bridge; publicación loopback)
```

El Compose base no cambia; el overlay evita requerir una credencial en escenarios existentes. Grafana usa `grafana-access` como bridge exclusivo de publicación local: Docker Desktop no activó el port-forward cuando Grafana sólo pertenecía a una red interna. Esta red no incluye backend, PostgreSQL ni Prometheus.

## Files

```text
backend/docker/grafana-compose.yml
backend/docker/prometheus/prometheus.yml
backend/docker/grafana/{provisioning,dashboards}/
specs/008-grafana/
```
