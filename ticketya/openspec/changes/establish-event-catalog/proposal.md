# Proposal

## Why

TicketYa necesita un primer incremento vertical para que administradores definan lugares, organizadores preparen y publiquen eventos, y clientes puedan descubrirlos. Esta épica establece el catálogo base exigido por los puntos funcionales 1, 2, 3 y 9 del TPE y prepara datos estables para las futuras reservas y compras.

La épica sigue las guidelines de `establish-ticketya-architecture`: Java 19 es el estándar, el PDF define el alcance mínimo y toda herramienta adicional debe justificarse frente a una alternativa más simple.

## What Changes

- Incorporar la administración de lugares canónicos, con ubicación, sectores numerados o de admisión general y sus capacidades físicas.
- Permitir que organizadores creen y configuren eventos propios en borrador, y que administradores operen sobre cualquier evento.
- Publicar eventos mediante una transición transaccional que genere un snapshot inmutable de lugar, sectores, asientos, capacidades y precios.
- Incorporar los estados `DRAFT`, `PUBLISHED`, `SUSPENDED`, `SOLD_OUT`, `CANCELLED` y `FINISHED`, implementando en esta épica publicación, suspensión y reanudación.
- Exponer búsqueda paginada de eventos publicados por tipo, organizador, participante, ciudad y rango de fechas, con orden por fecha, precio mínimo o relevancia.
- Exponer el detalle público de eventos publicados y vistas de gestión para organizadores y administradores.
- Incorporar un API Gateway como único punto de entrada y aplicar identificación provisional mediante encabezados durante la Entrega 1.
- Documentar contratos OpenAPI y cubrir reglas de dominio, persistencia y endpoints con pruebas automatizadas.

## Capabilities

### New Capabilities

- `venue-management`: Administración y consulta de lugares canónicos, sectores, capacidades, filas y asientos.
- `event-management`: Creación y edición de borradores, configuración de precios, publicación con snapshot inmutable, ownership y transiciones de suspensión.
- `event-discovery`: Búsqueda paginada, ordenamiento y consulta pública de eventos visibles.

### Modified Capabilities

Ninguna. El proyecto todavía no posee especificaciones principales.

## Impact

- Nuevos proyectos Spring Boot independientes para API Gateway y Catalog Service, compilados con Java 19 y Maven.
- Nueva base PostgreSQL de propiedad exclusiva de Catalog Service, con creación de esquema reproducible desde una base vacía.
- Nuevas APIs REST bajo `/api`, documentación OpenAPI y respuestas de error `application/problem+json`.
- Identificación temporal con `X-User-Id` y `X-User-Role`; JWT queda fuera de esta épica.
- Base arquitectónica para que Inventory Service inicialice inventario desde snapshots publicados en una épica posterior.
- Sin implementación todavía de reservas, disponibilidad, compras, pagos, QR, cancelaciones, reintegros, cache o mensajería.
- Sin obligación de adoptar migradores de esquema, infraestructura de testing o optimizaciones de búsqueda específicas mientras una alternativa simple cumpla los escenarios.
