# Tasks

## 1. Base del servicio y despliegue

- [ ] 1.1 Crear `services/inventory-service` con su `pom.xml` (Spring Boot 3.2.5, Java 19, web, validation, data-jpa, PostgreSQL, actuator, springdoc, test y Testcontainers), la estructura de paquetes y la clase `InventoryServiceApplication`; agregar el módulo al `pom.xml` raíz y verificar que `mvn -pl services/inventory-service verify` y el build raíz lo resuelven.
- [ ] 1.2 Configurar `application.yml` con datasource por variables de entorno, `ddl-auto: validate`, inicialización SQL, actuator health y puerto interno 8082; verificar el arranque del contexto y el endpoint de salud con un test.
- [ ] 1.3 Implementar `CorrelationIdFilter`, `ApiProblemException` y `GlobalExceptionHandler` con `application/problem+json`; verificar con tests de endpoint que se propaga o genera `X-Correlation-Id` y que los errores no filtran trazas ni credenciales.
- [ ] 1.4 Crear el `Dockerfile` de Inventory y agregar `inventory-service` e `inventory-db` al `docker-compose.yml` con PostgreSQL 16.4-alpine, volumen propio, healthchecks, variables de entorno y sin puerto publicado al host; verificar `docker compose config` y la construcción individual de la imagen.
- [ ] 1.5 Documentar en README los requisitos, las variables de Inventory y Compose (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `CATALOG_SERVICE_URL`, `INVENTORY_HOLD_TTL_SECONDS`, `SERVER_PORT`), el build y los comandos de ejecución; verificar que las instrucciones funcionan desde un checkout limpio al menos para build y `docker compose config`.

## 2. Modelo, snapshot e inicialización

- [ ] 2.1 Implementar `schema.sql` idempotente con `event_inventories`, `inventory_sectors`, `inventory_items`, `reservations` y `reservation_items`, incluyendo checks, foreign keys internas, unicidad e índices; verificar desde una base PostgreSQL vacía con Testcontainers que las constraints rechazan duplicados y valores inválidos y que una falla revierte la transacción.
- [ ] 2.2 Implementar entidades JPA y repositorios alineados con `schema.sql` y validar el mapeo con `ddl-auto: validate`; verificar con un test de persistencia contra PostgreSQL real que el esquema creado desde cero es compatible con las entidades.
- [ ] 2.3 Exponer `GET /internal/events/{eventId}/snapshot` en Catalog con un DTO que incluya `eventId`, `snapshotId`, datos de ubicación, sectores con `sourceSectorId`, tipo, capacidad y asientos con `sourceSeatId`, fila y número, sin precios; verificar `200` para evento publicado, `404` para inexistente o sin snapshot, inmutabilidad tras cambiar la plantilla y que el Gateway no enruta `/internal/**`.
- [ ] 2.4 Implementar el cliente REST de Catalog con timeouts, propagación de `X-Correlation-Id` y mapeo de `404`/`503`; verificar con `MockRestServiceServer` o WireMock los casos de éxito, recurso inexistente y Catalog no disponible.
- [ ] 2.5 Implementar la inicialización idempotente de `EventInventory` desde el snapshot, sin guardar precios y sin reescribir un inventario existente; verificar primera inicialización, repetición, dos solicitudes concurrentes y ausencia de precios en tablas y respuestas.

## 3. Reservas temporales

- [ ] 3.1 Implementar la normalización y validación de ítems (sector, asiento, cantidad, pertenencia, duplicados y mezclas inválidas) y el fingerprint canónico del payload; verificar con pruebas unitarias payloads válidos, inválidos y cambios de orden que no alteran el fingerprint.
- [ ] 3.2 Implementar `POST /internal/reservations` con TTL del evento u override, inserción idempotente por `orderId`, actualizaciones condicionales y rollback total; verificar reserva de asientos, reserva general, último cupo, duplicado idéntico, duplicado con otro payload y fallo parcial sin retención.
- [ ] 3.3 Implementar `GET /internal/reservations/by-order/{orderId}` y `GET /internal/reservations/{reservationId}`; verificar que devuelven estado, vencimiento e ítems, que no son accesibles por el Gateway y que responden `404` para reservas inexistentes.
- [ ] 3.4 Implementar `PUT /internal/events/{eventId}/hold-ttl` con rango de 60 a 3600 segundos; verificar el valor predeterminado de 600, la actualización a 300 y el rechazo de valores fuera de rango sin modificar el valor anterior.

## 4. Confirmación, liberación y expiración

- [ ] 4.1 Implementar `POST /internal/reservations/{reservationId}/confirm` con transición condicional, conversión de asientos a `SOLD`, paso de cupos generales de retenidos a vendidos y verificación de cantidades; verificar éxito, repetición idempotente, reserva vencida, reserva liberada y reserva inexistente.
- [ ] 4.2 Implementar `POST /internal/reservations/{reservationId}/release` con transición condicional y liberación de asientos y cupos generales; verificar éxito, repetición idempotente, reserva confirmada, reserva vencida y reserva inexistente.
- [ ] 4.3 Implementar la expiración perezosa antes de disponibilidad y reserva y un barrido periódico cada 60 segundos en un bean separado con transacción por reserva; verificar liberación sin tráfico, que sólo se tocan reservas `ACTIVE` vencidas y que `SOLD` nunca se modifica.
- [ ] 4.4 Implementar la prueba de expiración concurrente con dos instancias o contextos apuntando a la misma base; verificar que sólo una transición libera la reserva y que los contadores generales no se descuentan dos veces.

## 5. Disponibilidad pública y Gateway

- [ ] 5.1 Implementar `GET /api/inventory/events/{eventId}/availability` con sectores, capacidad, vendidos, retenidos, disponibles y mapa de asientos, con orden estable y sin precios ni datos sensibles; verificar sectores numerados, generales, asientos retenidos, reservas vencidas y `404` para eventos sin snapshot.
- [ ] 5.2 Configurar la ruta `/api/inventory/**` en el Gateway con precedencia sobre `/api/**`, variable `INVENTORY_SERVICE_URL` y propagación de `X-Correlation-Id`; verificar routing, precedencia, preservación de errores `application/problem+json` y que `/internal/**` no se publica.
- [ ] 5.3 Verificar que la disponibilidad pública se consume exclusivamente a través del Gateway y que Inventory no publica puerto al host; ejecutar una prueba de integración Gateway → Inventory o un smoke test con `docker compose`.

## 6. Contratos, documentación y Postman

- [ ] 6.1 Definir o actualizar `docs/openapi.yaml` con disponibilidad, comandos internos, configuración de TTL, esquemas de problema y correlation ID; verificar que los paths, códigos y esquemas principales coinciden con el OpenAPI generado por springdoc.
- [ ] 6.2 Ampliar la colección Postman con la disponibilidad pública a través del Gateway y datos repetibles; verificar con Newman que el flujo público finaliza sin errores.
- [ ] 6.3 Documentar en README el flujo de inventario, la separación entre API pública y comandos internos, el smoke test interno y las variables de Compose; verificar que los comandos documentados se ejecutan tal como están escritos.
- [ ] 6.4 Agregar pruebas de contrato que fallen si cambian paths, códigos o esquemas de `docs/openapi.yaml` sin actualizar la implementación; verificar que `mvn verify` y la validación OpenAPI pasan.

## 7. Concurrencia multi-instancia

- [ ] 7.1 Implementar una prueba con dos instancias de Inventory sobre la misma `inventory_db` compitiendo por el mismo asiento; verificar exactamente un éxito, un único asiento `HELD` y el resto de solicitudes rechazadas.
- [ ] 7.2 Implementar una prueba de competencia por el último cupo de un sector de admisión general; verificar exactamente un éxito y que `sold + reserved` nunca supera la capacidad.
- [ ] 7.3 Implementar pruebas de confirmación concurrente de la misma reserva y de reintento de reserva con el mismo `orderId`; verificar una única venta o retención y respuestas idempotentes.
- [ ] 7.4 Registrar en `docs/verification.md` el comando, resultado y conclusión de las pruebas de concurrencia; verificar que la evidencia corresponde a la corrida actual y no a una ejecución anterior.

## 8. Verificación integrada y cierre

- [ ] 8.1 Ejecutar `mvn verify` desde la raíz y desde `services/inventory-service`, `services/catalog-service` y `services/api-gateway`; verificar que todos los tests pasan.
- [ ] 8.2 Construir las imágenes individuales y ejecutar `docker compose down -v && docker compose up -d --build --wait`; verificar healthchecks de Gateway, Catalog, Inventory, `catalog_db` e `inventory_db`.
- [ ] 8.3 Ejecutar Postman/Newman contra el Gateway, validar `docs/openapi.yaml` y el OpenAPI de Inventory, y ejecutar el smoke test interno de reservas; registrar resultados y evidencia.
- [ ] 8.4 Revisar el DoD de §21 del blueprint, actualizar `docs/verification.md` y README con la corrida final; verificar que no quedan tareas obligatorias ni desviaciones del blueprint.

## Workflow follow-up

- Archivar `establish-inventory-and-reservations` después de la revisión del equipo y de completar todas las tareas.
- Verificar que las specs delta se consoliden en `openspec/specs/` y que el próximo cambio `establish-purchase-and-ticketing` consuma los contratos internos definidos aquí.
- En la implementación, seguir el pipeline acordado: `@spec-designer` define/valida `docs/openapi.yaml`, `@contract-tester` escribe y ejecuta los tests de contrato, y `@api-builder` implementa hasta que los tests estén verdes.
