# Progreso del proyecto

## Entrega 2 — épicas E2-02, E2-03 y E2-04 (2026-11-17)

**Estado:** las tres épicas están **cerradas**. Se archivaron como `openspec/changes/archive/2026-11-17-add-request-tracing-and-audit-log/`, `2026-11-17-validate-ticket-access/` y `2026-11-17-add-organizer-event-reports/`, y sus capacidades nuevas (`request-tracing-audit`, `ticket-access-control`, `organizer-reports`) y los agregados a `api-gateway` quedaron sincronizados en `openspec/specs/`.

 - [x] **E2-02:** registro de acceso HTTP en el Gateway y en los seis servicios; `audit-service` con base propia como bitácora centralizada; Purchase, Catalog y Ticket publican de forma asíncrona y resiliente las operaciones críticas; `/api/audit/**` sólo para `ADMIN`.
 - [x] **E2-03:** `POST /api/access/events/{eventId}/validations` (`ACCESS_STAFF`) y `GET /api/access/events/{eventId}/scans` (`ACCESS_STAFF`/`ADMIN`) en Ticket, con marca atómica de uso y registro de intentos.
 - [x] **E2-04:** `GET /api/reports/events/{eventId}` en Purchase (`ORGANIZER` dueño o `ADMIN`) con vendidas, devueltas, ocupación y recaudación por sector.

 - [x] `mvn clean verify` (reactor, Java 21 + Testcontainers): **324 tests, 0 fallos, 0 errores, 0 omitidos** (la línea base era 266).
 - [x] Compose aislado `ticketya_e2_verification`: **13/13 servicios healthy**.
 - [x] Newman por Gateway: **68 requests, 119 aserciones, 0 fallos**.
 - [x] `postman/concurrent-purchases.py`: PASS (1 `CONFIRMED`, 11 `409`, 1 asiento vendido), también con Audit Service detenido.
 - [x] `openspec validate --specs`: 15 specs válidas; los tres cambios, `--strict` válidos.

**Riesgos y pendientes conocidos:** una entrada `USED` no puede revocarse, así que cancelar un evento ya iniciado deja su reintegro en `202` (regla heredada de E1). La copia `order_tickets.status` de Purchase no refleja el uso; el estado autoritativo es el de Ticket. Pendientes de E2: F6, F10 (MongoDB), F11, cache, circuit breaker, integración entre servicios y prueba de carga.

## Entrega 2 — épica E2-01: JWT y roles (`add-jwt-and-role-security`)