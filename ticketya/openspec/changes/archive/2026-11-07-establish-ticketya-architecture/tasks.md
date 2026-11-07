# Tasks

## 1. Revisión del blueprint

- [x] 1.1 Revisar con el equipo los diagramas de contexto, contenedores y despliegue, y verificar que todos renderizan correctamente y distinguen Entrega 1 de Entrega 2.
  **Criterios de aceptación:**
  - D1 contexto (transversal, sin E1/E2), D2 C4 contenedores E1, D3 C4 contenedores E2, D4 despliegue E1, D5 despliegue E2 — cada uno con un solo alcance.
  - Renderizado verificado con `mmdc` (comando y resultado registrados).
  - Tabla de estado actual aparte de los diagramas.
  - `payment_db` marcado como "a decidir"; PSP fuera de alcance; nombres unificados entre diagramas y tablas.
  - §20 distingue mínimo del PDF (E1 local + bases Docker; E2 todo Docker) de la decisión del proyecto (Docker desde E1 como adelanto voluntario).
  - 1.5 sigue siendo la tarea que confirma la política de Dockerfile por componente y Compose único.
  **Evidencia:** cambio archivado `2026-11-07-architecture-review-task-1-1`; D3 y D4 corregidos y D1–D5 re-renderizados en `2026-11-07-architecture-review-remaining-tasks` §Verificación de renderizado.
- [x] 1.2 Contrastar actores, funcionalidades y no funcionales del blueprint con `TPE-ArqWeb-TicketYa-2026.pdf`, y verificar que cada punto del enunciado tiene un servicio dueño o una decisión arquitectónica asociada.
  **Evidencia:** `2026-11-07-architecture-review-remaining-tasks` §1.2 y §21 del blueprint (cobertura funcional, no funcional y entregables del PDF).
- [x] 1.3 Revisar el mapa de épicas y verificar que cada funcionalidad del PDF pertenece a una única épica primaria sin quedar fuera del roadmap.
  **Evidencia:** `2026-11-07-architecture-review-remaining-tasks` §1.3 y tabla de cobertura funcional en §21 del blueprint; nota de reconciliación de `integrate-gateway-routing-and-tracing` como sub-cambio de E1-01.
- [x] 1.4 Revisar las guidelines de simplicidad y verificar que el equipo acuerda Java 19, el PDF como piso y la ausencia de performance productiva como driver.
  **Evidencia:** `2026-11-07-architecture-review-remaining-tasks` §1.4 y tabla de guidelines en §1 del blueprint.
- [x] 1.5 Revisar el requisito de despliegue y verificar que el equipo acuerda un `Dockerfile` por componente desarrollado y un único `docker-compose.yml` incremental en la raíz.
  **Evidencia:** `2026-11-07-architecture-review-remaining-tasks` §1.5 y §20–§21 del blueprint (política de despliegue y DoD de épica).

## 2. Validación de límites y datos

- [x] 2.1 Validar responsabilidades y exclusiones de cada microservicio con el equipo, y verificar que no existan capacidades con dos dueños transaccionales.
  **Evidencia:** `2026-11-07-architecture-review-remaining-tasks` §2.1; §5 del blueprint con `SOLD_OUT` derivado y cancelación orquestada por Purchase.
- [x] 2.2 Revisar la tabla de ownership y persistencia, y verificar que cada servicio implementado tenga fuente de verdad propia, que no haya foreign keys entre bases y que sólo se seleccione la base NoSQL obligatoria al refinar su épica.
  **Evidencia:** `2026-11-07-architecture-review-remaining-tasks` §2.2; §6 del blueprint.
- [x] 2.3 Revisar las copias y snapshots entre Catalog, Inventory, Purchase y Ticket, y verificar que cada copia tenga un propósito histórico u operativo explícito.
  **Evidencia:** `2026-11-07-architecture-review-remaining-tasks` §2.3; tabla de propósito de copias en §6 del blueprint.

## 3. Validación de flujos críticos

- [x] 3.1 Recorrer la saga de compra para pago aprobado, rechazado, incierto, reserva vencida y emisión fallida, y verificar que cada rama termine en un estado recuperable o una compensación idempotente.
  **Evidencia:** `2026-11-07-architecture-review-remaining-tasks` §3.1; tabla de ramas de la saga en §9 del blueprint.
- [x] 3.2 Revisar las operaciones SQL conceptuales de inventario numerado y general, y verificar que la estrategia impida doble venta con múltiples instancias.
  **Evidencia:** `2026-11-07-architecture-review-remaining-tasks` §3.2; SQL de hold, confirmación, liberación y expiración en §10 del blueprint.
- [x] 3.3 Revisar devolución individual y cancelación masiva, y verificar el orden de reintegro, revocación y liberación de inventario ante fallos parciales.
  **Evidencia:** `2026-11-07-architecture-review-remaining-tasks` §3.3; orden revocar → reintegrar → liberar y estados de fallo parcial en §12 del blueprint.
- [x] 3.4 Revisar fila virtual y validación QR, y verificar que la cola no garantice stock y que el uso único del ticket dependa de una actualización atómica.
  **Evidencia:** `2026-11-07-architecture-review-remaining-tasks` §3.4; §13–§14 del blueprint con token de admisión sin stock y `UPDATE` atómico por `qr_hash`.

## 4. Consistencia transversal

- [x] 4.1 Revisar cada integración partiendo de REST síncrono y verificar que cualquier propuesta de broker, Outbox/Inbox o comunicación asíncrona documente requisito, alternativa simple, costo y forma de validación.
  **Evidencia:** `2026-11-07-architecture-review-remaining-tasks` §4.1; tabla de integraciones y regla de decisión asíncrona en §8 del blueprint.
- [x] 4.2 Revisar cache, resiliencia, seguridad y trazabilidad, y verificar que ninguna estrategia permita decidir venta, pago o acceso desde datos no autoritativos.
  **Evidencia:** `2026-11-07-architecture-review-remaining-tasks` §4.2; tabla de autoridad de decisión en §16 y refuerzos en §17–§18 del blueprint.
- [x] 4.3 Comparar `establish-event-catalog` con este blueprint y registrar mediante `$openspec-update-change establish-event-catalog` cualquier referencia o reconciliación que el equipo apruebe.
  **Evidencia:** `2026-11-07-architecture-review-remaining-tasks` §4.3. El cambio está archivado y no es editable; la reconciliación se registra en ese diseño y en la nota de §21, sin cambios de comportamiento ni delta de specs.
- [x] 4.4 Auditar tecnologías opcionales —cache, Redis, broker, migrador de esquema, tracing y service discovery— y verificar que las no justificadas queden explícitamente fuera de la baseline.
  **Evidencia:** `2026-11-07-architecture-review-remaining-tasks` §4.4; tabla de tecnologías opcionales en §22 del blueprint.
- [x] 4.5 Verificar que la Definition of Done de cada épica que agregue un componente incluya construir su imagen y levantar el sistema actualizado con `docker compose up --build`.
  **Evidencia:** `2026-11-07-architecture-review-remaining-tasks` §4.5; checklist de DoD de épica en §21 del blueprint.

## 5. Aprobación arquitectónica

- [x] 5.1 Incorporar al blueprint las observaciones aceptadas por el equipo y verificar que propuesta, diseño y mapa de épicas permanezcan coherentes.
  **Evidencia:** `2026-11-07-architecture-review-remaining-tasks` §5.1 y hallazgos F-01 a F-21; correcciones aplicadas en `proposal.md` y `design.md` del blueprint.
- [x] 5.2 Ejecutar `openspec validate establish-ticketya-architecture --strict` y verificar que el cambio no presente errores ni advertencias.
  **Evidencia:** `2026-11-07-architecture-review-remaining-tasks` §5.2; salida registrada en la verificación final del cambio.
- [x] 5.3 Registrar la aprobación del equipo y verificar que el blueprint sea la referencia utilizada al proponer la siguiente épica `establish-inventory-and-reservations`.
  **Evidencia:** `2026-11-07-architecture-review-remaining-tasks` §5.3 y §23 del blueprint. Aprobación registrada el 2026-11-07; la próxima épica debe citar §6, §8, §9, §10, §11 y §21. A la fecha no existe un proposal de esa épica; la verificación de uso se hará al abrirlo.

## Workflow follow-up

- [x] Archivar el cambio cuando el equipo apruebe el blueprint y todas las tareas estén completas.
- [x] Mantener el blueprint actualizado cuando una épica modifique ownership, límites de servicio o protocolos críticos; la regla queda en §23.
