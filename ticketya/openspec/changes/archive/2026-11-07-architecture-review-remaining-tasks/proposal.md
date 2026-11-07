# Proposal

## Why

El cambio `establish-ticketya-architecture` cerró la tarea 1.1 mediante el cambio archivado `2026-11-07-architecture-review-task-1-1`, pero quedan diecinueve tareas de revisión (1.2 a 5.3) y el blueprint todavía tiene ambigüedades que impiden aprobarlo: no existe una trazabilidad explícita PDF→servicio→épica, `SOLD_OUT` no tiene dueño claro, el orden de devolución reintegra antes de revocar el ticket, el límite por cliente no explica cómo se aplica bajo concurrencia, `payment_db` falta en D3, §4 conserva un diagrama E1 duplicado y las tecnologías opcionales no están consolidadas en una tabla única. Cerrar el blueprint sin resolver esos puntos dejaría decisiones críticas escondidas en las tareas o en la memoria del equipo.

## What Changes

- Auditar los puntos funcionales 1–12 y los no funcionales del PDF contra el blueprint, y registrar una matriz de trazabilidad con servicio dueño, épica primaria y entrega.
- Verificar que cada funcionalidad del PDF pertenece a una única épica primaria, con épicas colaboradoras explícitas y sin funcionalidades fuera del roadmap.
- Confirmar las guidelines de simplicidad: Java 19, PDF como piso, ausencia de performance productiva como driver y justificación obligatoria de toda tecnología adicional.
- Confirmar la política de despliegue: un `Dockerfile` por componente desarrollado y un único `docker-compose.yml` incremental en la raíz.
- Validar responsabilidades y exclusiones, y corregir los puntos donde dos capacidades podían interpretarse como dueñas transaccionales (estado editorial vs. `SOLD_OUT`, cancelación vs. reintegro, precio copiado a Inventory).
- Validar la tabla de ownership y persistencia, el propósito de cada copia/snapshot y la ausencia de foreign keys entre bases.
- Recorrer la saga de compra y documentar las ramas de pago aprobado, rechazado, incierto, reserva vencida, emisión fallida y compra duplicada, con estados recuperables y compensaciones idempotentes.
- Reforzar las operaciones SQL conceptuales de inventario (hold, confirm, release, expiración) y el mecanismo de uso único de QR.
- Corregir el orden de devolución y cancelación: bloquear/revocar el ticket antes de reintegrar y liberar inventario, con estados de fallo parcial.
- Consolidar la tabla de integraciones síncronas y la regla de decisión asíncrona (requisito, alternativa simple, costo y validación).
- Consolidar la tabla de autoridad de decisión para cache, resiliencia, seguridad y trazabilidad.
- Auditar cache, Redis, broker, migrador de esquema, tracing y service discovery, y declarar fuera de baseline los que no tengan justificación.
- Incorporar la Definition of Done de épica con componente ejecutable, incluyendo construir la imagen y verificar `docker compose up --build`.
- Marcar las tareas 1.1–5.3 de `establish-ticketya-architecture` con su evidencia y archivar el blueprint una vez aprobado.

## Capabilities

### New Capabilities

Ninguna. Este cambio sólo revisa y corrige documentación de arquitectura; no incorpora comportamiento observable.

### Modified Capabilities

Ninguna. Las capacidades funcionales se especifican y evolucionan en los cambios de cada épica.

## Impact

- `openspec/changes/establish-ticketya-architecture/design.md`: correcciones en §1, §4, §5, §6, §8–§22 y nuevo registro de aprobación en §23.
- `openspec/changes/establish-ticketya-architecture/proposal.md`: alcance de la revisión y correcciones aceptadas.
- `openspec/changes/establish-ticketya-architecture/tasks.md`: tareas 1.1–5.3 marcadas con evidencia.
- Este cambio agrega `design.md`, `proposal.md` y `tasks.md` de revisión; no modifica código, contratos, bases, Dockerfiles ni Compose.
- El blueprint queda como referencia obligatoria para `establish-inventory-and-reservations`; cualquier objeción posterior requiere un cambio OpenSpec.
