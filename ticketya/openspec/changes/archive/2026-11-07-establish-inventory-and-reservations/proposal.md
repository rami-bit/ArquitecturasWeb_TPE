# Proposal

## Why

TicketYa ya publica eventos con un snapshot inmutable, pero todavía no puede responder cuántos lugares quedan ni retenerlos mientras un cliente paga. Sin inventario autoritativo y reservas temporales, la compra no puede impedir que el mismo asiento o el último cupo de una zona se venda dos veces cuando muchos clientes compiten o reintentan el pago (`PDF-F4`, `PDF-F5`, `PDF-NF-CONCURRENCY`).

Esta épica abre el incremento `E1-02` del blueprint `establish-ticketya-architecture` y aplica sus decisiones de ownership (§6), comunicación REST (§8), saga de compra (§9), mecanismo contra doble venta (§10), idempotencia (§11) y Definition of Done (§21). Es la base que Purchase consumirá en `E1-03`; por eso no incluye todavía órdenes, pagos ni tickets.

## What Changes

- Incorporar `inventory-service` como microservicio Spring Boot independiente y `inventory_db` como base PostgreSQL de su propiedad exclusiva.
- Inicializar el inventario de cada evento de forma idempotente a partir del snapshot publicado de Catalog, copiando identificadores de sectores y asientos, sin copiar precios.
- Exponer públicamente la disponibilidad de un evento a través del Gateway: capacidad, vendidos, retenidos y disponibles por sector, con mapa de asientos para sectores numerados.
- Implementar reservas temporales internas para asientos numerados y cupos de admisión general, con TTL predeterminado de 10 minutos y configuración por evento.
- Implementar confirmación de venta, liberación y expiración de reservas de forma idempotente y transaccional.
- Garantizar con PostgreSQL que, ante solicitudes concurrentes o reintentos, exista como máximo un éxito por asiento o por último cupo de una zona, incluso con más de una instancia de Inventory.
- Agregar un contrato interno de lectura del snapshot publicado en Catalog para que Inventory pueda inicializarse sin acceder a `catalog_db`.
- Enrutar `/api/inventory/**` por el API Gateway con precedencia sobre la ruta de catálogo, preservando correlation ID y errores `application/problem+json`.
- Actualizar `docker-compose.yml`, Dockerfiles, OpenAPI, colección Postman, README y pruebas de concurrencia.

**Fuera de alcance:** órdenes, Purchase Service, Payment Mock, emisión de tickets/QR, devoluciones, cancelación masiva, límite por cliente, autenticación JWT, fila virtual, cache/Redis, broker, notificaciones y reportes. El límite por cliente y la orquestación de la saga pertenecen a `E1-03`; Inventory sólo expone las operaciones de inventario que esa épica consumirá.

## Capabilities

### New Capabilities

- `inventory-and-reservations`: inventario por evento, disponibilidad pública, reservas temporales, confirmación, liberación y expiración con exclusión mutua respaldada por la base relacional.

### Modified Capabilities

- `api-gateway`: se agrega el routing de disponibilidad de Inventory, su precedencia frente a la ruta de catálogo y la garantía de que los comandos internos de reserva no se publican externamente.
- `event-management`: se agrega un contrato interno e inmutable para leer el snapshot publicado con identificadores de origen y sin precios, destinado a inicializar inventario.
- `containerized-deployment`: el Compose central incorpora `inventory-service` e `inventory_db` y el escenario de levantamiento deja de enumerar sólo Gateway, Catalog y PostgreSQL.

## Impact

- Nuevo proyecto Maven `services/inventory-service` con su `Dockerfile`, configuración, esquema SQL, API y pruebas.
- Nueva base `inventory_db` en PostgreSQL, con ownership exclusivo de Inventory y sin foreign keys hacia otras bases.
- Catalog Service suma un endpoint interno de lectura del snapshot publicado; no cambia el comportamiento público existente.
- API Gateway suma una ruta específica para `/api/inventory/**` y conserva las rutas actuales de catálogo.
- `docker-compose.yml` y README incorporan el nuevo servicio y su base.
- OpenAPI y Postman documentan la disponibilidad pública y los comandos internos; la colección Postman sólo ejercita lo publicado por el Gateway.
- Los contratos internos quedan preparados para que `establish-purchase-and-ticketing` implemente la saga de compra sin modificar el ownership de inventario.
- No hay cambios incompatibles en las APIs públicas existentes.
