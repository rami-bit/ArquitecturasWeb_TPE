# Proposal

## Why

La implementación actual de PDF-F11 ya registra avisos de compra, cancelación, suspensión, reanudación y publicación relevante, pero el canal `EMAIL` sólo escribe en un log y queda marcado `SENT`. Catalog tampoco permite modificar la fecha u horario de un evento publicado, por lo que no puede avisar ese cambio. Son las dos brechas que aborda este cambio.

## What Changes

- Permitir que cada `CLIENT` registre y consulte su email. Entregar las notificaciones `EMAIL` existentes por SMTP a Mailpit en Compose; `SENT` significará aceptación SMTP. Conservar la bandeja y el canal `PUSH` simulado actuales.
- Permitir al dueño `ORGANIZER` o a `ADMIN` postergar sólo `startsAt` de un evento `PUBLISHED` aún no iniciado. La fecha nueva debe ser estrictamente posterior a la vigente y al instante de gestión más 72 horas. La operación será idempotente y rechazará revisiones obsoletas.
- Emitir `EVENT_POSTPONED` después del commit hacia la ingesta F11 existente. Cada comprador recibirá el aviso con fecha anterior y nueva, sin repetirlo por el mismo cambio.
- Mostrar y usar la fecha vigente en búsqueda, detalle y candidatos de recomendaciones, invalidando la caché tras el commit. Mantener el snapshot publicado, las entradas y QR, y la regla actual de reintegro.
- Actualizar contratos y pruebas de estos dos flujos, incluido Mailpit y el paso por Gateway.

## Capabilities

### Modified Capabilities

- `customer-notifications`: contacto propio, entrega SMTP del canal `EMAIL` y aviso por postergación, reutilizando la ingesta, deduplicación, reintentos, bandeja y consulta de compradores actuales.
- `event-management`: postergación autorizada e idempotente de fecha/hora de un evento publicado.
- `event-discovery`: fecha vigente en búsqueda, detalle y recomendaciones.
- `caching-strategy`: invalidación de búsqueda y detalle después de una postergación confirmada.
- `api-gateway`: autorización explícita de la nueva operación de postergación; la ruta de notificaciones ya existe.
- `containerized-deployment`: Mailpit como receptor SMTP local; Notification Service y su PostgreSQL ya existen.

## Impact

Se modifican Catalog, Notification Service y Gateway; Purchase y Preferences conservan sus contratos internos actuales. Se agregan datos de contacto y revisión/historial de fecha, además de Mailpit en Compose. OpenAPI, Postman y documentación se actualizan sólo para los endpoints y comportamientos nuevos. No se implementa una interfaz gráfica ni se reemplaza el canal `PUSH` simulado.
