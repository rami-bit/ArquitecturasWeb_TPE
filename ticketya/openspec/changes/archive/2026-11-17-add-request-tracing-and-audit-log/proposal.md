# Proposal

## Why

PDF-NF-TRACE exige dos cosas. Cada solicitud HTTP debe quedar registrada con endpoint, usuario, código de respuesta y tiempo de procesamiento. Cada operación crítica (compra, devolución, cancelación, ingreso al evento) debe quedar en un log centralizado con un identificador que permita seguirla a través de los microservicios. Hoy el `X-Correlation-Id` se propaga, pero nadie registra las solicitudes ni existe un lugar común donde consultar una operación.

## What Changes

- Agregar un registro de acceso HTTP en el Gateway y en cada servicio, con usuario, rol, código, duración y `X-Correlation-Id`, e incluir el correlation id en el patrón de log de los servicios.
- Agregar `audit-service` (Spring Boot + PostgreSQL propio) como bitácora centralizada append-only, con ingesta interna idempotente y consulta para `ADMIN`.
- Publicar desde Purchase, Catalog y Ticket los eventos de las operaciones críticas, de forma asíncrona y resiliente.
- Enrutar `/api/audit/**` por el Gateway, sólo para `ADMIN`.
- Actualizar Compose, OpenAPI, Postman y documentación.

## Capabilities

### New Capabilities

- `request-tracing-audit`: registro de solicitudes HTTP y bitácora centralizada de operaciones críticas, consultable por correlation id.

### Modified Capabilities

- `api-gateway`: enrutar y proteger por rol la consulta de la bitácora.

## Impact

- **Trazabilidad:** PDF-NF-TRACE, PDF-NF-RESILIENCE; épica E2-02.
- **Componentes:** Gateway, Catalog, Inventory, Ticket, Purchase y Payment Mock (registro de acceso); Catalog, Ticket y Purchase (productores); `audit-service` y `audit-db` nuevos.
- **Fuera de alcance:** stack de agregación de logs (ELK/Loki), métricas y tracing distribuido con spans (OpenTelemetry).
