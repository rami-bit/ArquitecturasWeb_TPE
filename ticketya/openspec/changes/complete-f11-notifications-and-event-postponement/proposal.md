# Proposal

## Why

F11 es la única funcionalidad obligatoria de Entrega 2 sin entrega real: Purchase registra eventos de notificación, pero ningún servicio envía avisos al cliente. El enunciado también exige avisar ante cambios de evento y Catalog sólo permite editar borradores; hace falta una postergación acotada que no altere asientos, precios ni la regla de devoluciones.

## What Changes

- Incorporar email al cliente mediante un servicio de notificaciones y Mailpit como receptor SMTP verificable en Compose. El cliente registra su propia dirección; el servicio conserva estado de entrega y evita duplicados.
- Enviar avisos por compra confirmada, cancelación del evento y publicación de un artista favorito en la ciudad del cliente. La compensación de una orden conserva su evento `ORDER_CANCELLED`, distinto de la cancelación de un evento.
- Permitir que el organizador dueño o ADMIN postergue sólo la fecha/hora de un evento `PUBLISHED`. La nueva fecha debe ser posterior tanto a la vigente como al instante de gestión más 72 horas. Mantener intacta la regla actual de devolución, que consulta la fecha vigente en Catalog.
- Mostrar la fecha vigente en catálogo, recomendaciones y consulta de entradas compradas, preservando la fecha original como dato histórico. El QR, lugar, sectores, asientos, capacidad y precios permanecen estables.
- Añadir contratos, seguridad, trazabilidad, despliegue y casos de prueba para los nuevos flujos. La entrega de emails no bloquea compra, publicación, postergación ni cancelación.

## Capabilities

### New Capabilities

- `customer-notifications`: registro de email propio, avisos de F11, deduplicación, estados de entrega y degradación ante fallo SMTP.

### Modified Capabilities

- `event-management`: excepción específica a la inmutabilidad para postergar fecha/hora de un evento publicado, con autorización y límites temporales.
- `event-discovery`: fecha vigente en búsquedas, detalle y candidatos de recomendaciones tras una postergación.
- `customer-preferences`: búsqueda interna y acotada de clientes que siguen a un artista en la ciudad de un evento publicado.
- `purchased-tickets`: fecha vigente visible al titular junto al dato histórico de compra, sin modificar el QR.
- `caching-strategy`: invalidación de búsqueda y detalle luego de confirmar una postergación.
- `api-gateway`: ruta protegida de email propio y preservación de los contratos internos fuera del Gateway.
- `containerized-deployment`: servicio de notificaciones, base propia y Mailpit en el Compose central.

## Impact

Catalog, Purchase, Preferences, API Gateway y un nuevo `notification-service`; PostgreSQL de Catalog/Purchase/Notification y MongoDB de Preferences. Se amplían `docs/openapi.yaml`, la colección Postman, Compose, README, diagramas y pruebas de contrato/integración. Mailpit captura correo en el entorno local; un SMTP externo puede configurarse en otro entorno sin cambiar los contratos de F11.
