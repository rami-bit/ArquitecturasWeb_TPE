# Tasks

## 1. Contrato y servicio

- [x] 1.1 Agregar `ticket-service` Java 19 / Spring Boot con estructura `api`,
  `application`, `domain` e `infrastructure/persistence`; verificar build aislado
  y desde el POM agregador. Verificado con Maven Wrapper y `-am`.
- [x] 1.2 Definir entidades y `schema.sql` idempotente para emision y tickets,
  con unicidad por orden/item/unidad y hash QR unico; verificar constraints en
  PostgreSQL real. Verificado por Testcontainers con PostgreSQL 16.4.
- [x] 1.3 Implementar el comando interno y validar snapshot, asientos y unidades;
  verificar respuestas 201/200/400/409 y errores problem+json.

## 2. QR y consistencia

- [x] 2.1 Generar payload firmado HMAC estable y PNG QR, guardando solo el hash;
  verificar firma estable, PNG y ausencia del payload en persistencia.
- [x] 2.2 Implementar idempotencia atomica por `orderId + orderItemId` y
  fingerprint; verificar repeticion identica, conflicto de contenido distinto,
  rollback y competencia concurrente.
- [x] 2.3 Propagar `X-Correlation-Id`, evitar rutas Gateway para `/internal/**`
  y documentar el contrato OpenAPI; verificar con tests de contrato.

## 3. Compose y validacion

- [x] 3.1 Integrar `ticket-service` y `ticket-db` al Compose sin publicar puertos,
  con Dockerfile, variables y healthcheck; verificar `docker compose config`,
  construccion de imagen y estado saludable.
- [x] 3.2 Actualizar README y registrar la verificacion Maven, de contrato y
  Compose. La integracion posterior con Purchase se registro en
  `establish-purchase-and-ticketing` y su evidencia integrada esta en
  `docs/verification.md`; la verificacion original de Ticket queda como evidencia
  historica en `services/ticket-service/docs/ticket-verification.md`.

Las verificaciones originales de este servicio pasaron con Docker disponible.
Purchase se integro posteriormente para emitir tickets despues de confirmar pago
e inventario; esa integracion no cambia el alcance de esta historia de emision.

## Workflow follow-up

- [x] Integrar el contrato desde `establish-purchase-and-ticketing` cuando Purchase
  Service este implementado; la integracion se completo y se probo por Gateway.
- La revocacion se incorporo posteriormente en la capacidad de devoluciones
  (`ticket-refunds`). La validacion de acceso y el uso unico del QR corresponden a
  US-F8 de Entrega 2 y siguen fuera del alcance de esta historia.
