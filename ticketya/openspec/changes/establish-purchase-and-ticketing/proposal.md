# Proposal

## Why

TicketYa ya publica eventos, administra disponibilidad y emite entradas QR, pero no existe un comprador que pueda recorrer el flujo completo: reservar, pagar, confirmar la venta y recibir sus entradas. Sin Purchase Service ni una pasarela simulada, la arquitectura definida en `establish-ticketya-architecture` no puede demostrarse de punta a punta ni cubrir los entregables de diseño (diagramas de módulos, secuencia y ER) exigidos por la Entrega 1.

## What Changes

- Agregar `purchase-service` como microservicio Spring Boot independiente, dueño de órdenes, ítems, límite por cliente/evento y estado de la saga de compra.
- Exponer la compra pública a través del Gateway: crear orden, consultar orden y reconciliar una orden pendiente.
- Orquestar la saga síncrona: snapshot de compra de Catalog, reserva en Inventory, cobro en Payment Mock, confirmación de inventario y emisión idempotente de tickets en Ticket Service.
- Compensar pago rechazado, pago incierto, reserva vencida y fallo de emisión con liberación, reintegro y reintento idempotentes.
- Agregar `payment-mock` como microservicio independiente con cobro, consulta y reintegro idempotentes y PostgreSQL propio.
- Extender el contrato interno de Catalog con un snapshot de compra que incluye precios por sector, sin alterar el snapshot sin precios que consume Inventory.
- Extender la respuesta de reserva de Inventory con los identificadores de origen y los datos de sector/fila/asiento que Purchase necesita para valorizar la orden y emitir tickets.
- Enrutar `/api/orders/**` por el Gateway y mantener `/internal/**` fuera de la superficie pública.
- Incorporar ambos servicios y sus bases al Compose raíz, con Dockerfiles, healthchecks y variables de entorno.
- Documentar la arquitectura exigida en `docs/architecture.md`: servicios y API pública, diagrama de módulos, secuencia de compra con pago rechazado y reserva vencida, ER por microservicio y entidades solapadas.
- Versionar el contrato OpenAPI consolidado de Purchase y Payment en `docs/openapi.yaml`, con ejemplos y códigos de error.
- Agregar pruebas unitarias de compra, pruebas de contrato e integración, una prueba de concurrencia de compra completa y actualizar la colección Postman.

Quedan fuera de este cambio: devoluciones solicitadas por el cliente, cancelación masiva de un evento, notificaciones, control de acceso con QR y autenticación JWT.

## Capabilities

### New Capabilities

- `purchase-orders`: ciclo de vida de la orden, idempotencia de la compra, límite de entradas por cliente y evento, orquestación de la saga y compensaciones observables.
- `payment-processing`: pasarela de pagos simulada con cobro, consulta y reintegro idempotentes, utilizada exclusivamente dentro de la red de servicios.

### Modified Capabilities

- `event-management`: se agrega un contrato interno de snapshot de compra con precios por sector, separado del snapshot sin precios que consume Inventory.
- `inventory-and-reservations`: la respuesta de reserva incorpora identificadores de origen y datos de presentación para que Purchase pueda valorizar la orden y emitir tickets.
- `api-gateway`: se agregan las rutas públicas de órdenes y se mantiene Payment Mock fuera de la superficie externa.
- `containerized-deployment`: se agregan Purchase Service, Payment Mock y sus bases PostgreSQL al Compose raíz.

## Impact

- Nuevos módulos `services/purchase-service` y `services/payment-mock`, con sus POM, Dockerfiles, `schema.sql`, configuración y pruebas.
- Nuevas bases `purchase_db` y `payment_db`, cada una con su volumen y sin acceso cruzado.
- Cambios aditivos en los contratos internos de Catalog e Inventory y en el snapshot publicado de eventos.
- Cambios en `services/api-gateway`, `docker-compose.yml`, `pom.xml` raíz, `README.md`, `docs/`, `postman/` y la colección Postman.
- El flujo de compra queda disponible sólo a través del Gateway; los comandos internos de Inventory, Ticket y Payment no se publican.
- La documentación de arquitectura se vuelve la fuente para los diagramas exigidos por el PDF y deberá actualizarse cuando cambie un límite de servicio.
