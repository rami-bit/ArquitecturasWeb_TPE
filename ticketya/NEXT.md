# Progreso del proyecto

## Fase 0 - Reparación de integración post-merge (2026-11-20)

**Estado:** cerrada y verificada sobre `main` (`6f1ed0b` + cambios locales). Detalle de la deuda y del plan en `ROADMAP-Orquestador-F10.md`.

- [x] **0.1 OpenAPI reconciliado.** Se recuperaron de `2649f08` los 6 paths y 13 schemas de E2-02/03/04 conservando F6: `docs/openapi.yaml` quedó con **45 paths / 51 operaciones / 75 schemas**, incluyendo `/api/access/**`, `/api/reports/**`, `/api/audit/**` y `/internal/audit-events`.
- [x] **0.2 Test de contrato corregido.** La aserción que prohibía capacidades ya implementadas se redujo a `NOT_YET_IMPLEMENTED_CAPABILITY_FRAGMENTS = ["notification"]`; el test pasó a llamarse `theContractDoesNotDeclareCapabilitiesOutsideTheImplementedScope`. **18/18 pruebas** en verde.
- [x] **0.3 Flujo Postman de F6 restaurado.** Se reconstruyeron los 17 pasos desde `4efdf23` como `F6-01`…`F6-17` (prefijo nuevo para no colisionar con E2-02). La colección quedó en **85 requests**. `F6-08` ahora sondea `GET /entries/me` hasta que la tanda de 15 s admite al cliente, y `F6-17` prueba un token con firma adulterada en lugar de un "token vencido" que el flujo no produce.
- [x] **0.4 Cambio huérfano eliminado.** `openspec/changes/fila-virtual` ya no existe; `virtual-queue` sigue archivada como única fuente.
- [x] **0.5 `api-gateway` en modo estricto.** Se dividieron los dos requisitos que superaban 500 caracteres en cuatro requisitos con escenarios propios.
- [x] **0.6 Documentación de estado.** `TODO.md`, `NEXT.md`, `README.md` y los dos roadmaps viejos quedaron alineados con lo medido.
- [x] **0.7 Verificación completa.** `mvn verify` del reactor: **388 pruebas, 0 fallos, 0 errores, 0 omitidos** (84 clases, Testcontainers incluido). `docker compose -p ticketya_f10_verification up --build -d --wait`: **15/15 contenedores healthy**. Newman por el Gateway: **94 requests, 85 test-scripts, 147 aserciones, 0 fallos**. `openspec validate --all --strict`: **17/17**. `git diff --check` limpio.

Lección registrada: pasar tokens del IdP a Newman desde PowerShell requiere interpolar con `"organizerToken=$($tokens['organizer'])"`; sin `$()` el argumento llega como texto y el Gateway responde `401` en cascada.

## PDF-F10 - Preferencias y recomendaciones (2026-11-20)

**Estado:** cerrada e integrada en `main`. El cambio OpenSpec se archivó como `openspec/changes/archive/2026-11-20-add-preferences-and-recommendations/` y sus deltas se sincronizaron a las specs vigentes (18/18 con `--strict`); no quedan cambios activos.

- [x] **Servicio y base NoSQL:** `services/preferences-service` con MongoDB 7 (`preferences-db`, base `ticketya_preferences`), justificación documentada en `design.md` D1–D17.
- [x] **API pública:** `PUT`/`GET /api/customers/me/preferences` y `GET /api/customers/me/recommendations`; sólo `CLIENT`, identidad derivada del `sub`, sin `customerId` en la ruta.
- [x] **Contratos internos:** señales de compra en Purchase y candidatos publicados en Catalog; Preferences no accede a bases ajenas.
- [x] **Algoritmo y resiliencia:** puntaje explicable (máximo 17), motivos, exclusión de comprados, fallback `UPCOMING_FALLBACK`, `HISTORY_UNAVAILABLE` si falla Purchase y `503` si falla Catalog; sin cache (decisión justificada).
- [x] **Contratos y despliegue:** 5 operaciones y 8 esquemas nuevos en OpenAPI, 9 pasos Postman `F10-01`…`F10-09`, ruta del Gateway antes del comodín, Compose con 17 contenedores y documentación actualizada.
- [x] **Verificación:** `mvn verify` 421 pruebas, 0 fallos/errores/omisiones; Compose 17/17 healthy; Newman 103 requests y 174 aserciones sin fallos; `openspec validate --all --strict` 17/17; `git diff --check` limpio. Detalle en `docs/verification.md`.

Pendiente para el cierre de entrega: F11 (notificaciones), cache, circuit breaker, prueba de carga, pruebas de integración automatizadas entre servicios y documento único de patrones/consistencia.

## Entrega 2 - épicas E2-02, E2-03 y E2-04 (2026-11-17)

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