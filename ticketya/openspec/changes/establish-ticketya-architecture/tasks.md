# Tasks

## 1. Revisión del blueprint

- [ ] 1.1 Revisar con el equipo los diagramas de contexto, contenedores y despliegue, y verificar que todos renderizan correctamente y distinguen Entrega 1 de Entrega 2.
- [ ] 1.2 Contrastar actores, funcionalidades y no funcionales del blueprint con `TPE-ArqWeb-TicketYa-2026.pdf`, y verificar que cada punto del enunciado tiene un servicio dueño o una decisión arquitectónica asociada.
- [ ] 1.3 Revisar el mapa de épicas y verificar que cada funcionalidad del PDF pertenece a una única épica primaria sin quedar fuera del roadmap.
- [ ] 1.4 Revisar las guidelines de simplicidad y verificar que el equipo acuerda Java 19, el PDF como piso y la ausencia de performance productiva como driver.
- [ ] 1.5 Revisar el requisito de despliegue y verificar que el equipo acuerda un `Dockerfile` por componente desarrollado y un único `docker-compose.yml` incremental en la raíz.

## 2. Validación de límites y datos

- [ ] 2.1 Validar responsabilidades y exclusiones de cada microservicio con el equipo, y verificar que no existan capacidades con dos dueños transaccionales.
- [ ] 2.2 Revisar la tabla de ownership y persistencia, y verificar que cada servicio implementado tenga fuente de verdad propia, que no haya foreign keys entre bases y que sólo se seleccione la base NoSQL obligatoria al refinar su épica.
- [ ] 2.3 Revisar las copias y snapshots entre Catalog, Inventory, Purchase y Ticket, y verificar que cada copia tenga un propósito histórico u operativo explícito.

## 3. Validación de flujos críticos

- [ ] 3.1 Recorrer la saga de compra para pago aprobado, rechazado, incierto, reserva vencida y emisión fallida, y verificar que cada rama termine en un estado recuperable o una compensación idempotente.
- [ ] 3.2 Revisar las operaciones SQL conceptuales de inventario numerado y general, y verificar que la estrategia impida doble venta con múltiples instancias.
- [ ] 3.3 Revisar devolución individual y cancelación masiva, y verificar el orden de reintegro, revocación y liberación de inventario ante fallos parciales.
- [ ] 3.4 Revisar fila virtual y validación QR, y verificar que la cola no garantice stock y que el uso único del ticket dependa de una actualización atómica.

## 4. Consistencia transversal

- [ ] 4.1 Revisar cada integración partiendo de REST síncrono y verificar que cualquier propuesta de broker, Outbox/Inbox o comunicación asíncrona documente requisito, alternativa simple, costo y forma de validación.
- [ ] 4.2 Revisar cache, resiliencia, seguridad y trazabilidad, y verificar que ninguna estrategia permita decidir venta, pago o acceso desde datos no autoritativos.
- [ ] 4.3 Comparar `establish-event-catalog` con este blueprint y registrar mediante `$openspec-update-change establish-event-catalog` cualquier referencia o reconciliación que el equipo apruebe.
- [ ] 4.4 Auditar tecnologías opcionales —cache, Redis, broker, migrador de esquema, tracing y service discovery— y verificar que las no justificadas queden explícitamente fuera de la baseline.
- [ ] 4.5 Verificar que la Definition of Done de cada épica que agregue un componente incluya construir su imagen y levantar el sistema actualizado con `docker compose up --build`.

## 5. Aprobación arquitectónica

- [ ] 5.1 Incorporar al blueprint las observaciones aceptadas por el equipo y verificar que propuesta, diseño y mapa de épicas permanezcan coherentes.
- [ ] 5.2 Ejecutar `openspec validate establish-ticketya-architecture --strict` y verificar que el cambio no presente errores ni advertencias.
- [ ] 5.3 Registrar la aprobación del equipo y verificar que el blueprint sea la referencia utilizada al proponer la siguiente épica `establish-inventory-and-reservations`.

## Workflow follow-up

- Archivar el cambio cuando el equipo apruebe el blueprint y todas las tareas estén completas.
- Mantener el blueprint actualizado cuando una épica modifique ownership, límites de servicio o protocolos críticos.
