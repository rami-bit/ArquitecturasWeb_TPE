# Tasks

## 1. Notification Service y contacto del cliente

- [ ] 1.1 Crear `services/notification-service` con POM, Dockerfile, configuración, healthcheck y esquema PostgreSQL propio para contactos, eventos y mensajes; verificar `mvn -pl services/notification-service -am verify` con pruebas del esquema en PostgreSQL real.
- [ ] 1.2 Implementar `PUT/GET /api/notifications/me/email` con validación de dirección, identidad por encabezado derivado y rechazo de campos ajenos; verificar casos `200`, `400`, `401`, `403` y aislamiento entre clientes en pruebas de contrato.
- [ ] 1.3 Implementar ingesta idempotente `POST /internal/notifications`, unicidad por evento y mensaje, estados e intentos persistidos; verificar duplicados y reanudación tras reinicio con PostgreSQL real.
- [ ] 1.4 Implementar envío SMTP con timeout/reintentos configurables y estados `SENT`, `FAILED`, `SKIPPED_NO_CONTACT`; verificar contenido, destinatario y ausencia de QR/pago contra Mailpit, más SMTP caído sin excepción al productor.
- [ ] 1.5 Documentar API propia, modelo de datos y significado de `SENT` en OpenAPI/README; verificar que los ejemplos corresponden a las respuestas de contrato del módulo.

## 2. Catalog: postergación y fecha vigente

- [ ] 2.1 Agregar revisión, historial de postergaciones y outbox a PostgreSQL de Catalog de manera compatible con datos existentes; verificar constraints, unicidad e inicialización en PostgreSQL real.
- [ ] 2.2 Implementar `PATCH /api/management/events/{id}/schedule` con ownership, rol, revisión esperada, clave idempotente y reloj inyectado; verificar fecha igual/adelantada, igualdad y exceso de 72 h, evento iniciado, estado inválido, replay y dos cambios concurrentes.
- [ ] 2.3 Hacer que búsquedas, detalle, snapshots internos y candidatos de recomendación usen `events.starts_at` vigente sin alterar la configuración publicada; agregar `GET /internal/events/{eventId}/schedule` para estados no públicos y verificar filtros, orden, precio, IDs de sector, evento cancelado y snapshot histórico en integración de Catalog.
- [ ] 2.4 Invalidar caché de búsqueda y detalle tras el commit de postergación; verificar lectura posterior con fecha nueva y ausencia de invalidación/aviso tras rechazo.
- [ ] 2.5 Documentar la operación y sus errores en OpenAPI de Catalog y consolidado, la excepción de inmutabilidad y el historial en diagramas; verificar un ejemplo por Gateway en la colección Postman.

## 3. Audiencias y compradores

- [ ] 3.1 Implementar en Preferences la búsqueda interna paginada de clientes por ciudad y artista favorito, con campos normalizados/indexados y cursor estable, incluyendo datos previos; verificar coincidencias, duplicados, migración y varias páginas contra MongoDB real.
- [ ] 3.2 Implementar en Purchase la consulta interna paginada de compradores confirmados distintos por evento, sin QR ni datos de pago; verificar un cliente con varias entradas, varios clientes y cursor contra PostgreSQL real.
- [ ] 3.3 Documentar ambos contratos internos y sus límites de datos en OpenAPI de servicios/diseño; verificar que Gateway no los enruta en una prueba de ruta.

## 4. Productores durables y visualización de entradas

- [ ] 4.1 Registrar outbox de Purchase en la transición atómica a `CONFIRMED` tanto al crear como al reconciliar la orden; verificar exactamente un evento por orden, ausencia en estados no confirmados y recuperación tras caída de Notification Service con PostgreSQL real.
- [ ] 4.2 Registrar outbox de Catalog en primera publicación, primera cancelación y cada postergación confirmada con ID y revisión estables; verificar repetición idempotente, rollback y no bloqueo ante Notification Service caído con PostgreSQL real.
- [ ] 4.3 Implementar workers acotados de envío al contrato interno con timeout, circuito existente en Purchase, log local y reintentos persistidos; verificar entrega tras recuperación y que una caída no cambia la respuesta de compra o gestión.
- [ ] 4.4 Ampliar la vista de orden con `currentEventStartsAt` desde Catalog y advertencia si no está disponible, conservando `eventStartsAt` y QR; verificar titular/tercero y misma fecha vigente aplicada al límite de devolución.
- [ ] 4.5 Documentar en OpenAPI, README y diseño la diferencia entre `ORDER_CANCELLED` por compensación y aviso de cancelación de evento; verificar que cada tipo de evento corresponde a su transición real.

## 5. Fan-out, seguridad y despliegue

- [ ] 5.1 Procesar en Notification los eventos recibidos: cliente de la orden, compradores distintos para cancelación/postergación y audiencia de Preferences para publicación; verificar mensajes únicos, contenido de fecha anterior/nueva y páginas reanudables con pruebas de integración.
- [ ] 5.2 Enrutar `/api/notifications/**` a Notification y protegerlo para `CLIENT`; proteger el `PATCH` de programación para `ORGANIZER`/`ADMIN`, conservar ownership en Catalog y excluir `/internal/**`; verificar `401`, `403`, `409` y éxito por Gateway.
- [ ] 5.3 Incorporar Notification Service, su base y Mailpit al Compose raíz, variables y healthchecks; construir imagen afectada y verificar stack saludable, SMTP interno e interfaz de Mailpit sólo en `127.0.0.1:8025`.
- [ ] 5.4 Actualizar README, arquitectura, OpenAPI consolidado y Postman con contacto, postergación y tres avisos; verificar que la colección usa sólo el Gateway para la API pública y consulta Mailpit para comprobar el correo capturado.

## 6. Integración final del cambio

- [ ] 6.1 Ejecutar pruebas de cada módulo afectado y `mvn verify` del reactor tras la última edición; verificar en XML recientes de Testcontainers que las clases de integración tienen pruebas >0 y 0 fallos, errores y omisiones.
- [ ] 6.2 Levantar un proyecto Compose nuevo con `up --build -d --wait`, verificar todos los servicios `healthy` y ejecutar Postman por Gateway con datos nuevos: compra directa/reconciliada, cancelación, postergación, publicación relevante, duplicados y roles; comprobar destinatario y contenido mediante la API de Mailpit.
- [ ] 6.3 Repetir E2E con Notification/SMTP caído y luego recuperado; verificar operaciones de negocio sin regresión, eventos pendientes reenviados y un registro por tipo/operación/cliente.
- [ ] 6.4 Ejecutar `openspec validate complete-f11-notifications-and-event-postponement --strict` y `git diff --check`; registrar cantidades, versión/stack, riesgos y resultados reales en `docs/verification.md`, `NEXT.md`, `TODO.md` y `docs/entrega2.md` antes de marcar el cambio como completo.
