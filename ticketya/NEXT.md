# Progreso del proyecto

## Entrega 2 - Épica E2-02: Fila virtual (`add-virtual-queue`)

**Estado:** épica **cerrada** el 2026-11-17. El cambio quedó archivado como
`openspec/changes/archive/2026-11-17-add-virtual-queue/` (35/35 tareas) y sus
capacidades (`virtual-queue` nueva; `event-management`, `event-discovery`,
`purchase-orders` y `api-gateway` modificadas) sincronizadas en `openspec/specs/`.

- [x] Catálogo: `high_demand` con `NOT NULL DEFAULT FALSE`, editable sólo en `DRAFT` y publicado en snapshots/consultas públicas (`HighDemandPostgresIntegrationTest` con PostgreSQL real).
- [x] Fila: posiciones con `pg_advisory_xact_lock`, entrada idempotente, consulta sin disponibilidad, abandono con reingreso al final, tandas en orden y cierre reactivo por expiración.
- [x] Token de admisión HMAC ligado a evento y comprador, de un solo uso y con TTL corto; validación atómica en `/internal/admissions/validate`.
- [x] Gateway: `/api/queue/**` antes del comodín, JWT obligatorio y `401` en `problem+json`; contrato OpenAPI con 39 paths y `X-Admission-Token` en la compra.
- [x] Purchase: exige admisión sólo si el snapshot marca alta demanda, tras idempotencia y antes de reservar/cobrar; 7 casos de contrato (válido, ausente, ajeno, vencido, reutilizado, no-high-demand y replay).
- [x] `mvn -o verify`: **330 tests, 0 fallos, 0 errores, 0 omitidos** (Gateway 51, Catalog 54, Inventory 73, Ticket 17, Payment 24, Purchase 64, Queue 47). XML revisados: ninguno con `skipped > 0`.
- [x] Compose aislado `ticketya_e2_02_verification` con IdP local: **13/13 servicios healthy**; `COMPOSE_EXIT=0`.
- [x] Newman por Gateway: **65 requests, 110 aserciones, 0 fallos**; 48 requests de Entrega 1 + 17 de E2-02 (flujo de fila y rechazos ausente/ajeno/vencido/reutilizado/replay).
- [x] `postman/concurrent-purchases.py`: **12 compras, 1 `CONFIRMED`, 11 `409`, 1 asiento `SOLD`**; sin regresión frente a E2-01.
- [x] `openspec validate add-virtual-queue --strict` (EXIT=0), `openspec validate --specs` (12 válidas) y `git diff --check` sin errores.

**Sobre el HTTP 400 heredado:** la request `09` de la colección respondió `201 Created` en el stack de verificación; la colección y el script de concurrencia no cambiaron desde `c26a1f4`, por lo que no era una regresión de esta épica. No se aplicó la opción B (cambio separado) porque no hubo cambio de semántica ni de contrato.

**Workspace preservado:** se conservaron los ajustes preexistentes en `.opencode/agents/*`, `opencode.jsonc`, los cuatro `*-free.md` y `../TP2/.factorypath`; se borró `postman_dump.txt`. El worktree de línea base se retiró sin borrar volúmenes ajenos.


## Entrega 2 — épica E2-01: JWT y roles (`add-jwt-and-role-security`)

**Estado:** épica **cerrada** el 2026-11-16. El cambio quedó archivado como `openspec/changes/archive/2026-11-16-add-jwt-and-role-security/` (10/10 tareas) y sus capacidades (`jwt-role-security` nueva; `api-gateway` y `purchase-orders` modificadas) sincronizadas en `openspec/specs/`.

- [x] El Gateway valida el JWT firmado por un IdP externo (`issuer-uri` + `jwk-set-uri` por entorno): `sub` UUID, expiración vigente y exactamente un rol de `CLIENT`, `ORGANIZER`, `ADMIN` o `ACCESS_STAFF`.
- [x] Las rutas protegidas exigen `Authorization: Bearer` y responden `401` en `application/problem+json` conservando `X-Correlation-Id`, sin llegar al upstream. Las consultas públicas (`GET /api/events`, `GET /api/events/{id}`, `GET /api/inventory/events/{eventId}/availability`) y los healthchecks siguen anónimos.
- [x] El Gateway descarta `X-User-Id`/`X-User-Role` del cliente y no reenvía el Bearer; propaga al servicio la identidad derivada de los claims validados. Los headers falsificados solos no autentican.
- [x] Catalog y Purchase conservan las reglas de rol y ownership: `403` por rol insuficiente, `404` para recursos ajenos.
- [x] `docs/openapi.yaml` declara el esquema HTTP `BearerJWT` por operación, con `security: []` explícito en las públicas; `ConsolidatedOpenApiDocumentContractTest` lo verifica.
- [x] `mvn -pl services/api-gateway,services/catalog-service,services/purchase-service -am verify`: **152 tests, 0 fallos, 0 errores, 0 omitidos** (Gateway 49, Catalog 48, Purchase 55).
- [x] Compose aislado `ticketya_jwt_verification`: **11/11 servicios healthy** con el IdP de prueba local.
- [x] Newman por Gateway: **48 requests, 82 aserciones, 0 fallos** con tokens reales; incluye las denegaciones por rol y ownership y los rechazos `401` por token ausente o headers de identidad falsificados.
- [x] `postman/concurrent-purchases.py`: **12 compras simultáneas, 1 `CONFIRMED`, 11 `409`, 1 asiento `SOLD`**.
- [x] `openspec validate --specs`: 12 specs válidas; `git diff --check` sin errores.

**Defectos corregidos al verificar:** (1) `postman/concurrent-purchases.py` seguía autenticando con `X-User-*` y quedaba roto con la seguridad JWT — ahora emite un token por identidad contra el endpoint del IdP; (2) la sincronización manual de specs había dejado dos requisitos desactualizados (`purchase-orders` → *Consultar órdenes propias* y `api-gateway` → *Enrutar las órdenes de compra por el Gateway*) — se revirtió y el archivado aplicó el sync canónico; (3) el README no documentaba el IdP de prueba local ni `IDP_TOKEN_URL`.

**Fuera de alcance, sin cambios:** el Gateway sigue sin emitir tokens ni mantener usuarios, no hay base de identidad propia y `ACCESS_STAFF` todavía no habilita las operaciones de control de acceso QR (pertenecen a E2-05).

## Cierre final — Entrega 1

**Estado:** Entrega 1 cerrada el 2026-11-15.

- [x] `issue-ticket-qr` archivado y su capacidad sincronizada a `openspec/specs/ticket-issuance/`.
- [x] `establish-purchase-and-ticketing` archivado con 14/14 tareas; sus capacidades quedaron sincronizadas en `openspec/specs/`.
- [x] `mvn -q clean verify` con Java 21/Testcontainers y PostgreSQL real: **260 tests, 0 fallos, 0 errores, 0 omitidos**.
- [x] Compose aislado `ticketya_e1_final_verification_20261115`: **11/11 servicios healthy**; se detuvo sin borrar volúmenes.
- [x] Newman por Gateway: **42 requests, 72 aserciones, 0 fallos**.
- [x] `postman/concurrent-purchases.py`: **12 compras simultáneas, 1 `CONFIRMED`, 11 `409`, 1 asiento `SOLD`**.
- [x] `docs/openapi.yaml`: 36 rutas/41 operaciones, validación OpenAPI y pruebas de contrato aprobadas.
- [x] `openspec validate --specs`: 11 specs válidas; `git diff --check` sin errores.

La evidencia, los comandos y el detalle por módulo están en [`docs/verification.md`](docs/verification.md). Las historias F6, F8 y F10–F12 siguen documentadas únicamente como backlog de Entrega 2; no se crearon cambios OpenSpec ni código para ellas.

**Workspace preexistente preservado:** las cuatro copias untracked `README_*_1361.md` se eliminaron únicamente después de verificar que sus hashes coincidían con los borrados staged ya presentes; los borrados staged se conservaron. También se conservaron los cambios preexistentes en `.opencode/agents/*` y `../TP2/.factorypath`.
