# Roadmap - Orquestador de Epics/Funcionalidades Post-Merge PDF-F6

> **SUPERADO (2026-11-20).** Este documento describe un estado anterior al cierre de la Fase 0 de reparación post-merge y no debe usarse como fuente de verdad. Contiene endpoints inexistentes (`/api/queue/join`, `/api/queue/position`, `/api/queue/trigger`) y cifras desactualizadas. El estado vigente está en `ROADMAP-Orquestador-F10.md`, `NEXT.md`, `TODO.md` y `docs/verification.md`.

## Contexto Actual (después de merge a main)
- Rama `integracionJwtyF6` fusionada exitosamente a `main`
- Implementado PDF-F6: Fila virtual para eventos de alta demanda
- 17/17 validaciones OpenSpec pasando
- Reactor Maven: 324 pruebas passing
- Compose: 13/13 servicios healthy
- Colección Postman: 68 requests, 119 aserciones

## Roadmap por Epic/Epicidad

### 🟡 EPIC E2: Trazabilidad, Bitácora y Reportes (En progreso)
**Estado:** Commit `078356e` fusiona cambios de `feature/e2-02-03-04`

| Tarea | Descripción | Componente | Status |
|-------|-------------|------------|--------|
| E2-02 | Trazabilidad y bitácora (eventos, acceso QR) | Gateway, Purchase, Inventory | ✅ Completado |
| E2-03 | Reportes de transacciones y estado de fila | Gateway, Reports service | ✅ Completado |
| E2-04 | *[Por definir]* | - | ⏳ Pendiente |

### 🟢 EPIC PDF-F6: Fila Virtual para Eventos de Alta Demanda
**Estado:** Merge completado, OpenSpec validado

| Capability | Descripción | Componentes Afectados | Status |
|------------|-------------|----------------------|--------|
| Virtual-queue (Nueva) | Gestión de fila virtual con posiciones ordenadas y trigger de tandas | Gateway, Inventory, Purchase, Nuevo servicio Cola | ✅ Completado |
| inventory-and-reservations | Validación de posición en cola antes de liberar reservas | Inventory | ✅ Completado |
| purchase-orders | Considerar estado de fila virtual al crear órdenes | Purchase | ✅ Completado |
| api-gateway | Enrutar y proteger endpoints de cola por rol (ACCESS_STAFF, ADMIN) | Gateway | ✅ Completado |

### 🔵 EPIC F7: Mejoras de Compra y Pagos
**Estado:** Pendiente de inicio (revisar fallo Postman HTTP 400)

| Tarea | Descripción | Componente | Status |
|-------|-------------|------------|--------|
| US-F7.3 | Carrito -> Orden con validación de inventario | Purchase, Inventory | ⏳ Pendiente |
| Mejoras de retry | Lógica de reintento para conflictos de alta demanda | Purchase, Gateway | ⏳ Pendiente |

### 🔴 EPIC JWT y Role-Security (Finalizado)
**Estado:** Integrado en main, OpenAPI y Gateway actualizados

| Capability | Descripción | Status |
|------------|-------------|--------|
| JWT role-security | Validación de roles en endpoints críticos | ✅ Completado |
| Permisos por rol | ACCESS_STAFF, ADMIN, CLIENT en Gateway | ✅ Completado |

## Próximos Pasos Recomendados

1. **Validar E2E completo** con stack de Docker:
   ```
   docker compose -p ticketya_verification up --build -d --wait
   postman collection run postman/ticketya.postman_collection.json --env-var baseUrl=http://localhost:8080 --no-report-events
   ```

2. **Resolver HTTP 400 en /api/orders** - El fallo histórico detectado en E1 aún no está completamente resuelto en flujos Postman.

3. **Considerar E2-04** - Revisar qué funcionalidades quedaron pendientes en la epica E2 y priorizarlas según retroalimentación de usarios.

4. **Monitoreo de fila virtual** - Implementar métricas y alertas para la nueva capacidad Virtual-queue en producción.

5. **Refactor de deltas** - La sección `## MODIFIED Capabilities` en el spec.md debe moverse a `proposal.md` o `design.md` según las convenciones de archive.

## Estado de Calidad
- ✅ OpenSpec: 17/17 passing (0 failed)
- ✅ Maven verify: 324 pruebas passing
- ✅ Docker Compose: 13/13 healthy
- ⚠️ Postman: 119 aserciones, pending resolución US-F7.3 HTTP 400