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
