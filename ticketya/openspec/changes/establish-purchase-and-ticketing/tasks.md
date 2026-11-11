# Tasks

## 1. Contratos y documentación de arquitectura

- [ ] 1.1 Definir `docs/openapi.yaml` con la API pública de Purchase, los contratos internos de Payment, el snapshot de compra de Catalog y la respuesta enriquecida de Inventory, incluyendo ejemplos y errores. Verificar que el YAML parsea y que los contract tests de Purchase y Payment lo leen.
- [ ] 1.2 Crear `docs/architecture.md` con la tabla de microservicios y API pública, el diagrama de módulos, la secuencia de compra con pago rechazado y reserva vencida, el ER de Purchase y Payment, el ER de Catalog, Inventory y Ticket, y la tabla de entidades solapadas. Verificar que todos los bloques Mermaid están completos y que el README referencia el documento.
- [ ] 1.3 Actualizar el README con los nuevos servicios, puertos internos, variables de entorno, flujo de compra y comandos de verificación. Verificar que un lector puede levantar el sistema y ejecutar la compra sólo con el README.

## 2. Catalog: snapshot de compra con precios

- [ ] 2.1 Agregar `GET /internal/events/{eventId}/purchase-snapshot` reutilizando el snapshot publicado e incluyendo el precio de cada sector, sin modificar el contrato sin precios que consume Inventory. Verificar con tests de contrato: evento publicado, evento en borrador `404`, inmutabilidad y ausencia de ruta en el Gateway.
- [ ] 2.2 Documentar el nuevo contrato en `docs/openapi.yaml` y en el OpenAPI runtime de Catalog. Verificar que `GET /v3/api-docs` de Catalog contiene el path y que el snapshot de inventario sigue sin precios.

## 3. Inventory: respuesta de reserva enriquecida

- [ ] 3.1 Extender `ReservationItem` con `sourceSectorId`, `sourceSeatId`, `sectorName`, `sectorType`, `rowLabel` y `seatNumber`, manteniendo `sectorId`, `seatId` y `quantity`. Verificar con tests de contrato para asientos numerados y admisión general, incluida la repetición idempotente.
- [ ] 3.2 Ajustar consultas y mapeos para obtener los datos de origen sin nuevas tablas ni precios. Verificar que la respuesta no incluye precios ni datos del comprador y que los tests existentes de reserva siguen pasando.

## 4. Payment Mock

- [ ] 4.1 Crear `services/payment-mock` como proyecto Spring Boot independiente con `api`, `application`, `domain` e `infrastructure`, `schema.sql`, `payment_db` y Dockerfile. Verificar build aislado con `mvn verify -pl services/payment-mock` y healthcheck en Compose.
- [ ] 4.2 Implementar `POST /internal/payments` idempotente por `orderId` y resultado determinista por token (`tok_approved`, `tok_rejected`, `tok_pending`). Verificar `201/200/400/409` con tests de contrato y fingerprint persistido.
- [ ] 4.3 Implementar consulta por `paymentId` y por `orderId` y el reintegro idempotente de un pago aprobado. Verificar `200/404/409`, que un pago rechazado o pendiente no se reintegra y que repetir el reintegro no duplica movimientos.
- [ ] 4.4 Propagar `X-Correlation-Id` y usar `application/problem+json` en todos los errores. Verificar con tests de contrato y con la ausencia de ruta en el Gateway.

## 5. Purchase Service

- [ ] 5.1 Crear `services/purchase-service` como proyecto Spring Boot independiente con `purchase_db`, `schema.sql`, Dockerfile y configuración de timeouts hacia Catalog, Inventory, Payment y Ticket. Verificar build aislado y healthcheck.
- [ ] 5.2 Implementar la creación idempotente de órdenes con `X-User-Id` e `Idempotency-Key`, fingerprint canónico y conflicto por contenido distinto. Verificar con tests unitarios y de contrato `201/200/403/400/409`.
- [ ] 5.3 Implementar la validación de ítems contra el snapshot de compra de Catalog: evento publicado, precio histórico, asientos por sector numerado y cantidad por admisión general. Verificar `404/409/400` y que el cliente no puede imponer precios.
- [ ] 5.4 Implementar la reserva en Inventory previa al cobro y la persistencia de los datos de origen de cada ítem. Verificar que sin disponibilidad no se invoca Payment y que el reintento recupera la misma reserva.
- [ ] 5.5 Implementar el cobro en Payment y la confirmación de inventario. Verificar la rama confirmada: lugares vendidos y orden `CONFIRMED`.
- [ ] 5.6 Implementar la emisión idempotente de tickets por ítem y el estado `PAID_PENDING_TICKETS` ante fallo transitorio. Verificar que un reintento devuelve los mismos tickets y no vuelve a cobrar.
- [ ] 5.7 Implementar las compensaciones: pago rechazado (`402` + liberación), pago incierto (`202` + `PAYMENT_PENDING`), reserva vencida (`409` + reintegro + liberación) y liberación del límite. Verificar cada rama con tests unitarios y dobles de los clientes.
- [ ] 5.8 Implementar el límite atómico por cliente y evento, seis por defecto y configurable. Verificar el límite, la liberación al rechazar y la competencia concurrente del mismo cliente.
- [ ] 5.9 Implementar consulta de orden propia, listado paginado y reconciliación de órdenes pendientes. Verificar `200/404/409` y que un cliente no puede ver órdenes ajenas.
- [ ] 5.10 Propagar `X-Correlation-Id`, registrar transiciones sin datos sensibles y responder `application/problem+json`. Verificar con tests de contrato y revisión de logs.

## 6. Gateway, Compose y colección Postman

- [ ] 6.1 Enrutar `/api/orders/**` a Purchase antes de `/api/**` y confirmar que `/internal/**` sigue sin publicarse. Verificar con tests del Gateway y con una compra a través de `localhost:8080`.
- [ ] 6.2 Agregar `purchase-service`, `payment-mock`, `purchase_db` y `payment_db` al Compose raíz y al POM agregador, con volúmenes, healthchecks y variables de entorno. Verificar `docker compose config` y `docker compose up --build`.
- [ ] 6.3 Actualizar la colección Postman con el flujo completo: crear lugar, evento y publicación, consultar disponibilidad, compra aprobada, compra rechazada, consulta de orden y reconciliación. Verificar con `npx newman` contra el Gateway.

## 7. Verificación integral

- [ ] 7.1 Ejecutar `mvn verify` desde la raíz y en cada servicio afectado. Verificar que no hay tests omitidos por falta de Docker en los flujos nuevos.
- [ ] 7.2 Ejecutar la prueba de concurrencia de compra completa: muchas compras simultáneas del mismo asiento, exactamente una `CONFIRMED`, y verificar que Inventory registra una única venta y que no hay tickets duplicados.
- [ ] 7.3 Ejecutar `docker compose down -v && docker compose up -d --wait`, recorrer la colección Postman y validar OpenAPI en Catalog, Inventory, Ticket, Purchase y Payment. Registrar la evidencia en `docs/verification.md`.

## Workflow follow-up

- Archivar este cambio con `openspec archive establish-purchase-and-ticketing` sólo después de completar las tareas y actualizar `TODO.md`.
- Las devoluciones, la cancelación masiva y las notificaciones se implementarán en sus propios cambios.
