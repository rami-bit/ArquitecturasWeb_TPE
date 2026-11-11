# Proposal

## Why

El roadmap de Entrega 1 necesita completar el flujo que conecta una selección del cliente con una compra persistida, un pago simulado y entradas utilizables. Hoy Catalog, Inventory y la emisión interna de Ticket existen por separado, pero el cliente no puede ejecutar ni consultar una compra completa desde el Gateway.

## What Changes

- Crear Purchase como dueño de órdenes, snapshots de compra, estado de la operación e idempotencia.
- Crear un Payment Mock local que registre pagos y reintegros con resultados aprobados o rechazados e idempotencia.
- Orquestar reservas de Inventory, pago y confirmación; compensar rechazos y expiraciones liberando la reserva.
- Emitir tickets QR solo después de confirmar el pago y la reserva, y asociarlos a la orden y al cliente.
- Exponer operaciones de compra y consulta desde el Gateway, manteniendo privados los endpoints internos.
- Agregar bases de datos, Dockerfiles, configuración Compose, OpenAPI, Postman y pruebas para los nuevos componentes.

## Capabilities

### New Capabilities
- `purchase-orders`: Crear órdenes con snapshot de precio, límite por cliente/evento, idempotencia y orquestación de compra.
- `payment-processing`: Procesar pagos simulados y reintegros idempotentes con respuestas configurables.
- `purchased-tickets`: Emitir y consultar tickets asociados a una compra confirmada.

### Modified Capabilities
- `api-gateway`: Publicar las rutas externas de compra y consulta de órdenes/tickets mediante el Gateway.
- `containerized-deployment`: Incorporar Purchase y Payment Mock con sus bases al Compose local.
- `inventory-and-reservations`: Permitir que una reserva se confirme solo tras pago aprobado y se libere ante rechazo o expiración desde Purchase.
- `event-discovery`: Hacer que el detalle público use los mismos IDs de sector estables que Inventory.

## Impact

- Nuevos servicios Spring Boot `purchase-service` y `payment-mock`, cada uno con base PostgreSQL propia.
- Cambios en `docker-compose.yml`, el POM agregador y configuración de API Gateway.
- Integración REST interna con Catalog, Inventory y Ticket usando los contratos existentes.
- Nuevos contratos OpenAPI, colección Postman, documentación y pruebas unitarias, de integración y de flujo.
