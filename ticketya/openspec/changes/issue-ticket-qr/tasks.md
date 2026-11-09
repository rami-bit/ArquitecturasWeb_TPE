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
  Compose; indicar que Purchase aun no existe y queda pendiente la integracion
  del flujo de compra. Evidencia y limitaciones en `docs/ticket-verification.md`.

Las verificaciones de este servicio pasaron con Docker disponible. La integracion
de compra de punta a punta sigue pendiente porque Purchase Service todavia no existe.

## Workflow follow-up

- Integrar el contrato desde `establish-purchase-and-ticketing` cuando Purchase
  Service este implementado.
- Implementar revocacion, validacion de acceso y uso unico del QR en las epicas
  que correspondan; no forman parte de esta emision.
