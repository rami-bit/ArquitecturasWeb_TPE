# Tasks

## 1. Entrega real del canal EMAIL

- [x] 1.1 Agregar contacto de email propio en PostgreSQL de Notification y `PUT/GET /api/notifications/me/email` con validación, identidad derivada, aislamiento y documentación OpenAPI. Probar esquema real, email válido/inválido, ausencia de contacto y roles por Gateway.
- [ ] 1.2 Sustituir la simulación sólo para `EMAIL` por SMTP configurable; conservar `PUSH` simulado. Marcar `SENT` sólo tras aceptación SMTP y `SKIPPED_NO_CONTACT` cuando falte dirección. Probar contenido, destinatario, deduplicación, caída/recuperación SMTP y reintentos contra PostgreSQL y Mailpit.
- [ ] 1.3 Añadir Mailpit al Compose central con SMTP interno, interfaz/API en `127.0.0.1:8025`, healthcheck y variables documentadas. Construir la imagen afectada y verificar el stack saludable y un correo capturado.

## 2. Postergación y aviso a compradores

- [x] 2.1 Agregar `schedule_revision` e historial/idempotencia a Catalog, compatibles con datos existentes. Verificar constraints y concurrencia contra PostgreSQL real.
- [x] 2.2 Implementar `PATCH /api/management/events/{id}/schedule` con rol, ownership, revisión esperada, `Idempotency-Key` y reloj inyectado. Probar éxito, 72 horas exactas, fecha adelantada/igual, evento iniciado, estado inválido, actor ajeno, replay y carrera de revisiones.
- [x] 2.3 Hacer que búsqueda, detalle y candidatos de recomendaciones usen `events.starts_at` para filtro, orden y fecha mostrada; conservar el snapshot y los contratos de venta/QR. Invalidar caché después del commit. Probar lectura nueva, rechazo sin invalidación y límite de devolución con la fecha vigente.
- [ ] 2.4 Publicar `EVENT_POSTPONED` después del commit con `changeId` estable y fechas anterior/nueva. Ampliar ingesta, tipo, plantilla, esquema y procesador de Notification reutilizando compradores existentes. Probar un aviso por comprador/canal, replay sin duplicados y ningún aviso tras rechazo.

## 3. Contratos y verificación

- [x] 3.1 Actualizar OpenAPI consolidado y de servicios, Postman, README y documentación de diseño para email propio, significado de `SENT`, Mailpit, postergación y límite de recuperación del publicador actual. Comparar controlador, Gateway, clientes HTTP y JSON de productor/consumidor.
- [ ] 3.2 Ejecutar pruebas de módulos afectados y `mvn verify` del reactor tras la última edición. Confirmar en XML recientes que las pruebas de integración con PostgreSQL tienen `tests > 0` y cero fallos, errores y omisiones.
- [ ] 3.3 Levantar Compose con un nombre de proyecto nuevo, verificar servicios `healthy` y ejecutar Postman por Gateway con compra, postergación y publicación relevante. Comprobar por la API de Mailpit destinatario, asunto y contenido de los correos; probar permisos y errores relevantes.
- [ ] 3.4 Verificar SMTP caído y recuperado sin cambiar el resultado de negocio, y registrar el límite de los productores volátiles sin atribuirles recuperación durable. Ejecutar `openspec validate complete-f11-notifications-and-event-postponement --strict` y `git diff --check`; actualizar evidencia en `docs/verification.md`, `NEXT.md`, `TODO.md` y `docs/entrega2.md` antes de cerrar el cambio.
