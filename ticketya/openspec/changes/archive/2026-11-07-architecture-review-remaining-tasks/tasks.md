# Tasks

## 1. Auditoría PDF y blueprint

- [x] 1.1 Contrastar actores, funcionalidades 1–12 y no funcionales del PDF con el blueprint y registrar la matriz de trazabilidad en §1.2 de este diseño y §21 del blueprint.
- [x] 1.2 Verificar que cada funcionalidad del PDF tiene una épica primaria y registrar las colaboradoras sin dejar funcionalidades fuera del roadmap.
- [x] 1.3 Confirmar las guidelines de simplicidad (Java 19, PDF como piso, performance no productiva) y agregarlas a §1 del blueprint.
- [x] 1.4 Confirmar la política de un `Dockerfile` por componente y un único `docker-compose.yml` incremental, y reflejarla en §20 y §21 del blueprint.

## 2. Límites y datos

- [x] 2.1 Validar responsabilidades y exclusiones, corregir `SOLD_OUT` como estado derivado y dejar la cancelación masiva como proceso de Purchase sin ciclo con Catalog.
- [x] 2.2 Revisar ownership y persistencia: fuente de verdad por servicio, sin foreign keys entre bases y base NoSQL diferida a E2-04.
- [x] 2.3 Revisar copias y snapshots: agregar propósito histórico u operativo, quitar el precio de la copia de Inventory y documentar la copia de Purchase y Ticket.

## 3. Flujos críticos

- [x] 3.1 Recorrer la saga y agregar la tabla de ramas: aprobado, rechazado, incierto, reserva vencida, emisión fallida y compra duplicada, con estados recuperables.
- [x] 3.2 Revisar y completar las operaciones SQL de inventario numerado y general: hold, confirmación, liberación y expiración, con constraints y atomicidad multi-instancia.
- [x] 3.3 Corregir el orden de devolución y cancelación a revocar ticket → reintegrar → liberar inventario, y documentar los estados de fallo parcial.
- [x] 3.4 Verificar que la fila virtual no garantiza stock y que el uso único del QR depende de una actualización atómica y de un hash único.

## 4. Consistencia transversal

- [x] 4.1 Agregar la tabla de integraciones E1 con REST síncrono por defecto y la regla de decisión asíncrona con requisito, alternativa, costo y validación.
- [x] 4.2 Agregar la tabla de autoridad de decisión y verificar que cache, resiliencia, seguridad y trazabilidad no deciden venta, pago ni acceso.
- [x] 4.3 Comparar `establish-event-catalog` y `integrate-gateway-routing-and-tracing` con el blueprint y registrar la reconciliación sin modificar cambios archivados.
- [x] 4.4 Auditar cache, Redis, broker, migrador, tracing, service discovery, Resilience4j, saga framework y BFF, y declararlos fuera de baseline en §22.
- [x] 4.5 Incorporar la DoD de épica con componente ejecutable, incluida la construcción de imagen y `docker compose up --build`.

## 5. Correcciones y aprobación

- [x] 5.1 Aplicar las correcciones aceptadas en `design.md` y `proposal.md` del blueprint y verificar la coherencia con el mapa de épicas.
- [x] 5.2 Renderizar con `mmdc` los diagramas modificados y los D1–D5, y registrar comando y resultado.
- [x] 5.3 Ejecutar `openspec validate establish-ticketya-architecture --strict` y `openspec validate architecture-review-remaining-tasks --strict`, y registrar el resultado.
- [x] 5.4 Marcar las tareas 1.1–5.3 de `establish-ticketya-architecture` con su evidencia y verificar que no queden tareas obligatorias abiertas.
- [x] 5.5 Registrar la aprobación en §23 del blueprint, archivar este cambio y archivar `establish-ticketya-architecture` una vez validado.

## Workflow follow-up

- La próxima épica `establish-inventory-and-reservations` debe citar el blueprint y usar §6, §8, §9, §10, §11 y §21.
- Cualquier objeción posterior al blueprint se tramita con un cambio OpenSpec antes de modificar la implementación.
