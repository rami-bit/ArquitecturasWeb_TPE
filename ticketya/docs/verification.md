# Verificación de Entrega 1 y Entrega 2

## Verificación de las épicas E2-02, E2-03 y E2-04 (2026-11-17)

Estado: **cerradas**. Entorno: Linux, Docker Engine 29.9.2, OpenJDK 21 (compilación con `release=19`), Maven 3.9.11, Testcontainers con PostgreSQL 16.4 real.

| Verificación | Comando / entorno | Resultado |
|---|---|---|
| Línea base antes de los cambios | `mvn -q verify` | 266 pruebas, 0 fallos, 0 errores, 0 omitidos. |
| Reactor completo | `mvn -q clean verify` | **BUILD SUCCESS. 324 pruebas, 0 fallos, 0 errores, 0 omitidos**: Gateway 55, Catalog 55, Inventory 75, Ticket 32, Payment Mock 26, Purchase 72, Audit 9. XML Surefire recientes revisados. |
| E2-02 bitácora | `AuditServiceContractTest` (Testcontainers) | 7 pruebas: ingesta idempotente por `eventId`, validación `400`, traza cronológica multi-servicio, `404` sin eventos, filtros y paginación, `403` para roles no ADMIN, eco de `X-Correlation-Id`. |
| E2-02 publicador | `AuditPublisherTest` en Purchase, Ticket y Catalog | 5 pruebas por módulo: envío con correlation id del MDC, reintento de fallos transitorios con el mismo `eventId`, sin reintento de `400`, servicio caído sin afectar al llamador, modo sólo-log. |
| E2-02 registro HTTP | `RequestLoggingFilterTest` (6 servicios) y `GatewayEntrega2RoleAuthorizationTest` | Líneas con método, path, status, duración, usuario, rol y correlation id; el Gateway registra `200`, `403` (con usuario) y `401` (anónimo). |
| E2-03 acceso QR | `AccessControlContractTest`, `TicketQrVerificationTest` | 6 + 2 pruebas: ingreso, reingreso `409 ALREADY_USED`, `422 WRONG_EVENT` sin consumir, `409 REVOKED`, `404 UNKNOWN_TICKET` (firma alterada o ajena), roles; **25 escaneos simultáneos → 1 `200`, 24 `409`, un único registro `ADMITTED`**. |
| E2-04 reportes | `OrganizerReportCalculatorTest`, `OrganizerReportIntegrationTest` | 4 + 6 pruebas: fórmulas y redondeo, dueño/ADMIN, organizador ajeno `403`, evento inexistente `404`, roles no autorizados sin llamar a Catalog, exclusión de órdenes no pagadas, devolución duplicada contada una vez, Inventory caído (degradación) y Catalog caído (`503`). |
| Gateway: roles y contrato | `GatewayEntrega2RoleAuthorizationTest`, `ConsolidatedOpenApiDocumentContractTest` | Cada ruta nueva llega al servicio correcto sólo con sus roles; el resto recibe `403` problem+json sin upstream; sin token, `401`. OpenAPI: 42 rutas / 47 operaciones, operaciones E2 con códigos mínimos y seguridad. |
| Compose aislado | `JWT_ISSUER_URI=http://host.docker.internal:19998 JWT_JWK_SET_URI=http://host.docker.internal:19998/.well-known/jwks.json docker compose -p ticketya_e2_verification up --build -d --wait` | **13/13 servicios healthy** (incluye `audit-db` y `audit-service`). |
| Postman vía Gateway | Newman 6.2.1 con tokens del IdP local (`tools/local-test-idp`) | **68 requests, 119 aserciones, 0 fallos** (antes: 48/82). Pasos 28-47 cubren reportes, acceso QR y bitácora. |
| Compras simultáneas | `BASE_URL=http://localhost:8080 IDP_TOKEN_URL=http://localhost:19998/token python postman/concurrent-purchases.py` | PASS: 12 compras, 1 `CONFIRMED`, 11 conflictos, 1 asiento `SOLD`. |
| Resiliencia de la auditoría | `docker compose stop audit-service` y repetir la prueba anterior | PASS igual; los productores registran `audit service unavailable after 3 attempts; event kept in local log`. |
| Log centralizado en runtime | `docker compose logs audit-service` | 64 ingestas `POST /internal/audit-events` durante la corrida E2E; trazas consultadas por `GET /api/audit/traces/{id}`. |
| OpenSpec | `openspec validate --specs` y `openspec validate <cambio> --strict` | 15 specs válidas (avisos de longitud preexistentes en `api-gateway`); los tres cambios válidos en modo estricto antes de archivarse. |

**Limitación del entorno de verificación (no del repositorio):** en el sandbox donde se ejecutó esta verificación, la etapa `maven` de los Dockerfiles multi-stage no pudo descargar dependencias de Maven Central, porque el proxy TLS del entorno no es de confianza dentro de los contenedores de build (error PKIX). Las imágenes se construyeron con un override local, fuera del repositorio, que copia los JAR generados por `mvn verify` sobre la misma imagen base `amazoncorretto:19-alpine`. Los Dockerfiles del repositorio no cambiaron, y el nuevo `services/audit-service/Dockerfile` replica el patrón de Purchase y Payment Mock. En una máquina con acceso normal a Maven Central, `docker compose up --build` construye las imágenes directamente.


## Verificación de la épica E2-02 - Fila virtual (2026-11-17)

Estado: **cerrada**. El cambio `add-virtual-queue` quedó archivado como
`openspec/changes/archive/2026-11-17-add-virtual-queue/` (35/35 tareas) y sus
capacidades sincronizadas en `openspec/specs/`.

Entorno: Windows, Docker Desktop 29.7.2, JDK 19 (el `pom.xml` fija
`maven.compiler.release=19`), Maven 3.9.15, Testcontainers con PostgreSQL 16.4
real, IdP de prueba local `tools/local-test-idp/LocalTestIdp.java` y Newman
6.2.3 mediante `npx --yes newman`.

| Verificación | Comando / entorno | Resultado |
|---|---|---|
| Reactor completo | `mvn -o verify` desde la raíz | **BUILD SUCCESS**. Gateway **51**, Catalog **54**, Inventory **73**, Ticket **17**, Payment Mock **24**, Purchase **64**, Queue **47** = **330 pruebas, 0 fallos, 0 errores, 0 omitidos**. XML Surefire recientes revisados clase por clase; ninguno con `skipped > 0`. |
| Marco de alta demanda | `HighDemandPostgresIntegrationTest` (Testcontainers, PostgreSQL real) | `high_demand` existe con `NOT NULL DEFAULT FALSE`; los `INSERT` crudos siguen siendo válidos y el campo sólo se puede activar en `DRAFT`. |
| Unidad de la fila | `BatchCalculatorTest`, `AdmissionWindowTest`, `AdmissionTokenCodecTest` | Tandas en orden por posición, vigencia del token y código HMAC del token de admisión. |
| Integración de la fila | `QueueJoinFlowIntegrationTest`, `QueuePromotionIntegrationTest`, `QueueAdmissionExpiryIntegrationTest`, `QueueValidateIntegrationTest`, `QueueCatalogFailureIntegrationTest` | PostgreSQL real (`queue-db`): idempotencia de entrada, unicidad de posición, orden de llegada, reingreso al final tras abandono, expiración, token ajeno/reutilizado y caída del proveedor de snapshot. |
| Admisión en la compra | `PurchaseAdmissionContractTest` (7 casos) | Compra con token válido, sin token, de otro cliente, vencido, reutilizado con otra `Idempotency-Key`, evento sin alta demanda sin cambios y replay idempotente que no consume de nuevo el token; `503` si la fila no responde. |
| Contrato OpenAPI | `ConsolidatedOpenApiDocumentContractTest`, `CatalogPurchaseSnapshotDocumentContractTest`, `PurchaseOpenApiDocumentContractTest` | 39 paths; `highDemand` en los esquemas públicos y en los dos snapshots, `X-Admission-Token` declarado en `POST /api/orders`, paths `/api/queue/**` y `/internal/admissions/validate` documentados. |
| Ruteo del Gateway | `GatewayDefaultRouteTest`, `GatewayRoutingIntegrationTest`, `GatewayJwtSecurityIntegrationTest` | `/api/queue/**` precede al comodín `/api/**`; las operaciones de la fila exigen JWT (sin token → `401` en `application/problem+json`) y el Gateway no reenvía el Bearer. |
| Compose aislado | `$env:JWT_ISSUER_URI='http://host.docker.internal:19998'; $env:JWT_JWK_SET_URI='http://host.docker.internal:19998/.well-known/jwks.json'; $env:QUEUE_BATCH_INTERVAL_SECONDS='2'; docker compose -p ticketya_e2_02_verification up --build -d --wait` | **13/13 servicios healthy**, incluidos `queue-db` y `queue-service`. `COMPOSE_EXIT=0`. El intervalo de tandas se acortó sólo en el entorno de verificación; el valor por defecto sigue siendo 15 s. |
| Colección Postman vía Gateway | `npx --yes newman run postman/ticketya.postman_collection.json --env-var baseUrl=http://localhost:8080 ... --delay-request 2000` | **65 requests, 110 aserciones, 0 fallos**. Los 48 requests de Entrega 1 siguen en verde y los 17 nuevos (`E2-02-01` … `E2-02-17`) cubren el flujo de fila y los rechazos por admisión ausente, ajena, vencida, reutilizada y por replay idempotente. `NEWMAN_EXIT=0`. |
| Compras simultáneas por Gateway | `$env:BASE_URL='http://localhost:8080'; $env:IDP_TOKEN_URL='http://localhost:19998/token'; python postman/concurrent-purchases.py` | **PASS**: 12 compras, 1 `CONFIRMED`, 11 `409`, 1 asiento `SOLD`; evento `0fb59ae0-1206-4ee2-83ef-9de1e532af8a`. `CONC_EXIT=0`. |
| OpenSpec y diff | `openspec validate add-virtual-queue --strict`, `openspec validate --specs`, `git diff --check` | `Change 'add-virtual-queue' is valid` (EXIT=0); 12 specs válidas, 0 fallidos; diff sin errores de whitespace. |

### Investigación del HTTP 400 del primer `POST /api/orders`

El estado heredado de `AGENTS.md` registraba un `400` en la request `09` de la
colección. Antes de tocar código se verificó:

1. La request `09` cumple `docs/openapi.yaml`: `eventId`, un ítem con
   `sectorId` de Inventory y `quantity`, y `payment.method=CARD`. La cadena
   `01 → 04 → 05 → 08` exporta los IDs correctos.
2. El worktree aislado en `c26a1f4` confirmó que ni la colección ni
   `concurrent-purchases.py` cambiaron desde la línea base, de modo que no
   era una regresión de la admisión de esta épica.
3. El análisis estático señaló `PurchaseService.priceItems` como único punto
   del camino feliz capaz de devolver `400` según datos de runtime.
4. **No se reproduce**: en el stack `ticketya_e2_02_verification` la request
   `09` respondió `201 Created` y la colección completa pasó 48/48 antes de
   agregar el flujo de fila. No se modificó ninguna regla de negocio ni se
   cambió el contrato; la opción B (cambio OpenSpec separado) no fue necesaria.

### Nota sobre el caso de token vencido en Postman

La request `E2-02-17` construye un token con marca de tiempo pasada usando el
secreto de desarrollo de `QUEUE_ADMISSION_SECRET`. Es una firma HMAC válida
pero fuera de vigencia y ejerce la misma respuesta `403` que un token expirado,
sin depender del TTL configurado en el servidor. La expiración real por TTL
está cubierta con PostgreSQL real en `AdmissionWindowTest` y
`QueueAdmissionExpiryIntegrationTest`.

### Preservación del workspace

Se conservaron sin cambios los ajustes preexistentes en `.opencode/agents/*`,
`opencode.jsonc`, los cuatro `*-free.md` y `../TP2/.factorypath`. Se eliminó
`postman_dump.txt` según lo acordado. El worktree de línea base se creó en
`C:\Users\ramir\AppData\Local\Temp\opencode\ticketya-baseline` y se retiró
sin borrar volúmenes ajenos.


## Verificación de la épica E2-01 - JWT y roles (2026-11-16)

Estado: **cerrada**. El cambio `add-jwt-and-role-security` quedó archivado como `openspec/changes/archive/2026-11-16-add-jwt-and-role-security/` (10/10 tareas) y sus capacidades sincronizadas en `openspec/specs/`.

Entorno: Windows, Docker Desktop 29.7.2, JDK 19 (el `pom.xml` fija `maven.compiler.release=19`), Maven 3.9.15, Testcontainers con PostgreSQL 16.4 real.

| Verificación | Comando / entorno | Resultado |
|---|---|---|
| Módulos afectados | `mvn -pl services/api-gateway,services/catalog-service,services/purchase-service -am verify` | **BUILD SUCCESS**. Gateway **49**, Catalog **48**, Purchase **55** = **152 pruebas, 0 fallos, 0 errores, 0 omitidos**. XML Surefire recientes revisados clase por clase. |
| Reactor completo | `mvn verify` desde la raíz | **BUILD SUCCESS**. Gateway 49, Catalog 48, Inventory 73, Ticket 17, Payment Mock 24, Purchase 55 = **266 pruebas, 0 fallos, 0 errores, 0 omitidos** (la línea base de E1 era 260; la épica agrega 6 pruebas netas de seguridad en el Gateway). |
| Seguridad del Gateway | `GatewayJwtSecurityIntegrationTest` (IdP de prueba con firma RS256 real y JWKS local) | 5 pruebas: token válido para `CLIENT`, `ORGANIZER`, `ADMIN` y `ACCESS_STAFF` llega al upstream con `X-User-Id` = `sub` y `X-User-Role` = único rol; 13 variantes inválidas (ausente, malformado, vencido, firma ajena, issuer incorrecto, `sub` ausente, `sub` no UUID, `roles` ausente/vacía/no array/rol no permitido/dos roles, `exp` ausente) responden `401` sin llamar al upstream; headers de identidad falsificados solos no autentican; las tres consultas públicas y `/actuator/health` siguen anónimas. |
| Contrato OpenAPI | `ConsolidatedOpenApiDocumentContractTest` | 16 pruebas: esquema HTTP `BearerJWT` declarado, requisito global heredado, `security: []` explícito en las tres consultas públicas y en las operaciones internas, `401` declarado en toda operación protegida, ausencia de `X-User-Id`/`X-User-Role` como parámetros del documento. |
| Compose aislado | `$env:JWT_ISSUER_URI='http://host.docker.internal:19998'; $env:JWT_JWK_SET_URI='http://host.docker.internal:19998/.well-known/jwks.json'; docker compose -p ticketya_jwt_verification up --build -d --wait` | **11/11 servicios healthy**, incluidos el Gateway y las cinco imágenes recompiladas. El stack quedó detenido sin borrar volúmenes. |
| Comportamiento en runtime | Requests manuales contra `http://localhost:8080` | Públicas sin token: `GET /api/events` 200, `GET /api/events/{id}` 404 (recurso inexistente, ruta alcanzable), `GET .../availability` 404, `/actuator/health` 200. Protegidas sin token: `POST /api/venues`, `GET /api/management/events` y `GET /api/orders` → **401**. `GET /api/management/events` con token ADMIN → 200. `POST /api/venues` con `X-User-Role: ADMIN` pero sin JWT → **401**. |
| Roles y ownership por Gateway | Colección Postman con tokens reales | `ACCESS_STAFF` en operación ADMIN → **403**; `ORGANIZER` sobre borrador ajeno → **403**; `CLIENT` decidiendo una devolución → **403**; `CLIENT` consultando orden ajena → **404**; las denegaciones conservan `X-Correlation-Id`. |
| Colección Postman vía Gateway | `npx --yes newman run postman/ticketya.postman_collection.json --env-var baseUrl=http://localhost:8080 --env-var organizerToken=... --env-var clientToken=... --env-var numberedBuyerToken=... --env-var adminToken=... --env-var accessStaffToken=...` | **48 requests, 82 aserciones, 0 fallos**. Las rutas públicas se recorren sin token y todas las protegidas con Bearer. Incluye `00a` (ruta protegida sin token → `401` en `application/problem+json` con `X-Correlation-Id`) y `00b` (headers `X-User-Id`/`X-User-Role` sin JWT → `401`). |
| Compras simultáneas por Gateway | `BASE_URL=http://localhost:8080 IDP_TOKEN_URL=http://localhost:19998/token python postman/concurrent-purchases.py` | **PASS**: 12 compras, 1 `CONFIRMED`, 11 `409`, 1 asiento `SOLD`, `reserved=0`; evento `681a375c-de64-4e4f-9396-843bb4322024`. La corrida es repetible: cada ejecución crea su propio evento y sus propias identidades. |
| OpenSpec y diff | `openspec validate --specs` (12 specs válidas), `openspec validate add-jwt-and-role-security --strict` antes de archivar, `git diff --check` | Sin errores. Los avisos de texto largo en `api-gateway` son preexistentes y no bloqueantes. |

### Defectos encontrados y corregidos durante la verificación

1. **`postman/concurrent-purchases.py` incompatible con la seguridad JWT.** El script seguía autenticando con `X-User-Id`/`X-User-Role`, que el Gateway ahora descarta: la primera llamada (`POST /api/venues`) devolvía `401` y la prueba de concurrencia documentada en el README quedaba rota. Se corrigió para emitir un token por identidad contra el endpoint de token del IdP (`IDP_TOKEN_URL`) y enviar `Authorization: Bearer`. Cobertura equivalente: sigue verificando 12 compras concurrentes, una sola venta y la liberación del cupo.
2. **Sincronización manual de especificaciones incompleta.** Las specs principales se habían editado a mano en lugar de usar el archivado de OpenSpec, por lo que dos requisitos quedaron desactualizados frente al delta archivado: `purchase-orders` → *Consultar órdenes propias* seguía describiendo la identidad por header y le faltaba el escenario *Administrador consulta órdenes*; `api-gateway` → *Enrutar las órdenes de compra por el Gateway* seguía describiendo el reenvío de headers sin JWT. Se revirtieron las ediciones manuales y `openspec archive add-jwt-and-role-security` aplicó el sync canónico (`+ 5, ~ 6`).
3. **README desactualizado.** Decía "esas cuatro variables" para cinco tokens y no documentaba el IdP de prueba local ni el nuevo requisito de `IDP_TOKEN_URL` para el script de concurrencia.

### Herramienta de verificación agregada

`tools/local-test-idp/LocalTestIdp.java` es un IdP de prueba mínimo, sólo con el JDK y **fuera del producto y del Compose**: publica el JWKS en `/.well-known/jwks.json` y emite tokens RS256 (`sub` UUID, `roles` con un único valor, `iss`, `iat`, `exp`). Expone además `GET /token?sub=<uuid>&role=<ROL>` para los scripts que necesitan identidades distintas por iteración, y `--key-file` para persistir el par de claves: el Gateway cachea el JWKS que ya leyó, de modo que reiniciar el IdP sin esa clave haría que los tokens nuevos dejaran de validar. Sustituye la dependencia de un IdP externo durante la verificación, en línea con el diseño (no se integra ni despliega un proveedor de identidad concreto).

## Verificación final del core E1 (2026-11-15)

Entorno: Windows, Docker Desktop, reactor ejecutado en `maven:3.9.9-eclipse-temurin-21-alpine` (Java 21); los tests de integración usaron Testcontainers con PostgreSQL 16.4.

| Verificación | Comando / entorno | Resultado |
|---|---|---|
| Reactor completo | `docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "${PWD}:/workspace" -w /workspace -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal maven:3.9.9-eclipse-temurin-21-alpine mvn -q clean verify` | Éxito; **260 tests, 0 fallos, 0 errores, 0 omitidos**. Gateway 43, Catalog 48, Inventory 73, Purchase 55, Payment Mock 24 y Ticket 17. XML Surefire recientes revisados. |
| OpenAPI consolidado | `python -m openapi_spec_validator docs/openapi.yaml` y test `ConsolidatedOpenApiDocumentContractTest` | OpenAPI 3.0.3 válido; 36 rutas / 41 `operationId` únicos; 15 tests estáticos en Gateway pasan, incluidos roles E1, errores, correlación y ausencia de capacidades E2. |
| Compose limpio | `$env:GATEWAY_PORT='18080'; docker compose -p ticketya_e1_final_verification_20261115 up --build -d --wait` | **11/11 servicios healthy**; seis imágenes de servicios construidas. Gateway publicado en `http://localhost:18080`. |
| OpenAPI runtime | `docker compose -p ticketya_e1_final_verification_20261115 exec -T <catalog-service|inventory-service|ticket-service|purchase-service|payment-mock> wget -qO- http://localhost:<8081-8085>/v3/api-docs` | Las cinco APIs internas devolvieron `/v3/api-docs` correctamente. |
| Postman vía Gateway | `npx --yes newman run postman/ticketya.postman_collection.json --env-var baseUrl=http://localhost:18080` | **42 requests, 72 aserciones, 0 fallos**; incluye compra general y numerada, pago rechazado, devoluciones, reintegros y cancelación de evento. |
| Compras completas simultáneas | `$env:BASE_URL='http://localhost:18080'; python postman/concurrent-purchases.py` | **12 compras**, 1 `CONFIRMED`, 11 `409`, 1 asiento `SOLD`, `sold=1`, `reserved=0`. |
| OpenSpec y diff | `openspec validate --specs`, `openspec validate establish-purchase-and-ticketing --strict`, `git diff --check` | 11 specs principales válidas; cambio Purchase validado con 14/14 tareas antes de archivarse; sin errores de whitespace. |
| Detención del stack | `docker compose -p ticketya_e1_final_verification_20261115 down` | Contenedores/red eliminados; volúmenes conservados (no se usó `-v`). |

La primera invocación de Newman incluyó `--no-report-events`, opción que el ejecutable `npx newman` no reconoce; se repitió el comando sin esa opción y la colección pasó. Los avisos `503` registrados dentro de los tests de reintento de devoluciones son fallos inyectados esperados; los tests finalizaron en verde.

OpenSpec archivó `issue-ticket-qr` y `establish-purchase-and-ticketing`. Las capacidades F6, F8 y F10–F12 continúan sólo como backlog E2; no se inició su implementación.

## Cierre de pruebas expresas del PDF (2026-11-11)

Se agregaron pruebas unitarias de reglas de compra (`PurchaseBusinessRulesTest`, 2), devoluciones (`RefundPolicyTest`, 4) y búsqueda/ordenamiento (`EventDiscoveryServiceTest`, 4). La regla de devolución de 72 horas y la inmutabilidad de la decisión se extrajeron a `RefundPolicy` sin cambiar las rutas ni las respuestas. La concurrencia se verifica con `postman/concurrent-purchases.py`, que crea un evento y asiento propios por corrida y dispara 12 compras completas por Gateway, cada una con comprador y clave de idempotencia distintos.

| Verificación | Comando / entorno | Resultado |
|---|---|---|
| Módulo Catalog | `docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "${PWD}:/workspace" -w /workspace -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal maven:3.9.9-eclipse-temurin-21-alpine mvn -q -pl services/catalog-service -am verify` | 48 pruebas de Catalog, 0 fallos, 0 errores, 0 omisiones; 4 pruebas nuevas de búsqueda. |
| Módulo Purchase | Mismo contenedor Java 21 con `mvn -q -pl services/purchase-service -am verify` | XML recientes de las clases actuales; la corrida inicial falló al compilar un test nuevo, se corrigió antes del reactor final. |
| Reactor completo | Mismo contenedor Java 21 con `mvn -q verify` | Código 0; 231 pruebas en los seis módulos, 0 fallos, 0 errores, 0 omisiones. Los XML de las clases fuente actuales están presentes; se excluyó un XML antiguo de `PurchaseFlowIntegrationTest` porque ya no existe su fuente. |
| Compose limpio | `docker compose -p ticketya_e1_core_20261111 up --build -d --wait` y `ps --format json` | 11/11 servicios `healthy`, incluidas las imágenes recompiladas de Catalog y Purchase. |
| Colección completa por Gateway | `postman collection run postman/ticketya.postman_collection.json --env-var baseUrl=http://localhost:8080 --no-report-events` | 42 requests, 72 aserciones, 0 fallos. Incluye compra aprobada y rechazada, compra numerada, devoluciones y cancelación. |
| Muchas compras del mismo asiento por Gateway | `python postman/concurrent-purchases.py` | 12 compras simultáneas, una `201 CONFIRMED`, 11 respuestas `409`, un asiento `SOLD` y contadores `sold=1`, `reserved=0`. Evento nuevo `bbe6adb6-f090-4f2f-8f71-76fd61b7e8bf`. |

La prueba multi-instancia de Inventory sigue siendo evidencia complementaria de la exclusión entre procesos. El stack Compose de esta corrida usa un nombre de proyecto nuevo y conserva sus volúmenes; no se borraron volúmenes existentes. Las pruebas adicionales de compra vencida E2E, fallos parciales downstream y reintento QR siguen abiertas en `establish-purchase-and-ticketing`.

## Evidencia de liberación por vencimiento (US-E1-05, tarea 3.1)

Fecha: 2026-11-11. Se revisaron implementación, pruebas fuente y XML Surefire de Inventory, sin modificar código ni ejecutar Maven nuevamente. Los XML son posteriores al último commit del módulo (`56b4c55`, 15:42 -0300) y no hay diferencias locales en su código o tests.

| Clase | Tests | Fallos | Errores | Omitidos | Evidencia |
|---|---:|---:|---:|---:|---|
| `ReservationLifecycleContractTest` | 9 | 0 | 0 | 0 | Una reserva vencida no se confirma; la consulta de disponibilidad libera el asiento. La liberación posterior devuelve `EXPIRED` y mantiene los contadores de venta en cero. |
| `HoldTtlContractTest` | 5 | 0 | 0 | 0 | El vencimiento se configura por evento: 600 s por defecto y 300 s tras configurarlo. Esta clase comprueba el TTL, no la liberación. |
| `InventoryAvailabilityContractTest` | 8 | 0 | 0 | 0 | Tras forzar el vencimiento, el asiento queda `AVAILABLE` y el cupo general recupera su disponibilidad completa. |
| `InventoryConcurrencyContractTest` | 4 | 0 | 0 | 0 | Dos instancias disparan la expiración concurrente; la reserva queda `EXPIRED`, el asiento `AVAILABLE` y los contadores generales en cero. |

`ReservationExpiryService` cambia a `EXPIRED` sólo reservas activas vencidas y, en la misma transacción, libera asientos y cupos generales. `AvailabilityService` y `ReservationService.create` disparan esa expiración al consultar disponibilidad o intentar otra reserva. El SQL permite retener de nuevo un asiento `AVAILABLE` y reservar cupo general cuando el contador recupera capacidad. Esto respalda que el lugar vuelve a estar disponible para otra compra; no se ejecutó una compra E2E de vencimiento, que no es una prueba específica exigida por el PDF para Entrega 1. No se requirió corregir el comportamiento.

## Verificación de US-E1-05

Fecha: 2026-11-11. Se reutilizó el stack Compose existente `ticketya_verification`; no se creó uno nuevo en esta corrida. El usuario interrumpió la repetición del reactor Maven porque los cambios de este turno se limitaron a documentación y Postman.

| Comprobación | Resultado de este turno |
|---|---|
| Compose existente `ticketya_verification` | 11 servicios saludables |
| Colección Postman por Gateway | 42 requests, 72 aserciones, 0 fallos; incluye compra aprobada de asiento numerado con QR |

La última verificación completa de Maven sigue siendo la del cierre F7: `mvn -q verify` con Java 21, Testcontainers y PostgreSQL real ejecutó 221 pruebas actuales, sin fallos, errores ni omisiones. **No se repitió en este turno** y no debe presentarse como validación posterior a los cambios de US-E1-05.

El flujo E1 crea un lugar y un evento, publica ofertas, consulta disponibilidad y compra entradas generales y un asiento numerado con pago aprobado y tickets con QR. La colección también conserva los recorridos existentes de pago rechazado, idempotencia, devoluciones y cancelación con reintegros. Esos recorridos son la base de regresión: deben seguir pasando aunque el trabajo nuevo de US-E1-05 se cubra sólo en el camino feliz.

Para verificar en un entorno local sin modificar los volúmenes de otros proyectos, ejecutar desde la raíz:

```bash
mvn verify
docker compose -p ticketya_e1_verification up --build -d --wait
docker compose -p ticketya_e1_verification ps
npx --yes newman run postman/ticketya.postman_collection.json --env-var baseUrl=http://localhost:8080
docker compose -p ticketya_e1_verification down
```

Comprobar los XML recientes de Surefire para confirmar que las clases Testcontainers ejecutaron pruebas y no quedaron omitidas. El informe histórico de Inventory que sigue registra una corrida anterior; sus 116 pruebas y cinco servicios no representan el stack actual.

## Informe histórico: Establish Inventory and Reservations

Fecha: 2026-11-07  
Cambio: `establish-inventory-and-reservations` (E1-02, PDF-F4, PDF-F5)  
Estado: TODAS LAS VERIFICACIONES PASAN ✓

## Resumen

Se ejecutó la verificación integrada del segundo incremento de TicketYa: inventario de disponibilidad y reservas temporales. Inventory materializa su inventario desde el snapshot publicado de Catalog (sin copiar precios), expone la disponibilidad pública a través del Gateway y mantiene los comandos de reserva en `/internal/**`, fuera del Gateway. La garantía de no doble venta se apoya en PostgreSQL: actualizaciones condicionales, constraints y transacciones, no en caché ni en estado de proceso.

## 1. Build con Maven

Comando: `mvn verify` desde la raíz de `ticketya/`

Resultado: **BUILD SUCCESS**

| Módulo | Tests | Fallos | Errores | Omitidos |
|---|---|---|---|---|
| api-gateway | 25 | 0 | 0 | 0 |
| catalog-service | 32 | 0 | 0 | 0 |
| inventory-service | 59 | 0 | 0 | 0 |
| ticketya (agregador) | — | — | — | — |

Total: **116 tests, 0 fallos, 0 errores, 0 omitidos**.

Entorno:
- JDK 19 (Amazon Corretto 19.0.2)
- Maven 3.9.15
- Docker 29.7.2 (requerido por los tests de Testcontainers)

### 1.1 Desglose de Inventory

| Clase de test | Tests | Qué verifica |
|---|---|---|
| `InventoryAvailabilityContractTest` | 8 | Disponibilidad pública, orden estable, asientos retenidos, vencidos, `404` sin snapshot, ausencia de precios |
| `ReservationContractTest` | 10 | Creación de reservas: asientos, general, último cupo, idempotencia, conflictos, rollback total |
| `ReservationLifecycleContractTest` | 9 | Confirmación, liberación y expiración: transiciones, repetición idempotente, estados inválidos, `404` |
| `ReservationQueryContractTest` | 4 | Consulta por `orderId` y por `reservationId`, vencimiento e ítems |
| `ReservationFingerprintContractTest` | 1 | Fingerprint canónico: el orden de ítems no altera la idempotencia; otro payload con el mismo `orderId` es `409` |
| `HoldTtlContractTest` | 5 | TTL predeterminado 600, actualización a 300, rango 60–3600, `404` sin inventario materializado |
| `InventoryHealthContractTest` | 1 | Contexto completo con PostgreSQL real y actuator `UP` con correlation ID |
| `InventorySchemaConstraintTest` | 3 | Constraints del esquema: duplicados de sector y asiento, tipos inválidos, contadores por encima de capacidad |
| `OpenApiDocumentContractTest` | 9 | `docs/openapi.yaml`: paths, códigos, esquemas, problem+json y ausencia de precios |
| `OpenApiRuntimeParityTest` | 5 | Paridad entre springdoc y `docs/openapi.yaml`, incluido el contrato interno de snapshot |
| `InventoryConcurrencyContractTest` | 4 | Dos instancias sobre la misma base compitiendo por asiento, último cupo, confirmación y expiración |
| **Total** | **59** | |

### 1.2 Desglose de Catalog y Gateway

Catalog suma 32 tests; 5 corresponden a `CatalogSnapshotContractTest` (snapshot publicado, sin precios, inmutable tras cambiar la plantilla, `404` para inexistente o no publicado).

Gateway suma 25 tests; 6 corresponden a `GatewayInventoryContractTest` (routing de `/api/inventory/**`, precedencia sobre `/api/**`, `/internal/**` no publicado, propagación de `X-Correlation-Id` y preservación de `application/problem+json`).

## 2. Concurrencia multi-instancia

`InventoryConcurrencyContractTest` levanta dos contextos de Spring Boot de Inventory sobre el mismo PostgreSQL de Testcontainers y dispara solicitudes simultáneas alternando ambas instancias. Resultados de la corrida:

| Escenario | Solicitudes | Resultado |
|---|---|---|
| Mismo asiento | 8 concurrentes (4 por instancia) | 1 × `201`, 7 × `409`; un único asiento `HELD` y una única reserva `ACTIVE` |
| Último cupo general | 6 concurrentes, con 2 cupos ya retenidos sobre capacidad 3 | 1 × `201`, 5 × `409`; `sold + reserved = 3 = capacity`; disponible `0` |
| Confirmación concurrente | 6 confirmaciones de la misma reserva | 6 × `200` con estado `CONFIRMED`; asiento `SOLD`, `sold=1`, `reserved=0`, 2 ítems |
| Reintento de creación | 6 repeticiones del mismo `orderId` tras confirmar | 6 × `200` con el mismo `reservationId`; una sola fila en `reservations` y `sold` sin cambios |
| Expiración perezosa concurrente | 6 consultas de disponibilidad desde ambas instancias | 6 × `200`; reserva `EXPIRED` una sola vez, asiento `AVAILABLE`, contadores en cero |

Conclusión: la no doble venta no depende del proceso. Las actualizaciones condicionales (`UPDATE ... WHERE status = 'AVAILABLE'` y contadores con guardas de capacidad) más las constraints del esquema serializan la competencia entre instancias.

## 3. Contrato OpenAPI

- Archivo versionado: `docs/openapi.yaml` (8 paths, 14 schemas).
- Validación: `npx --yes @redocly/cli@latest lint docs/openapi.yaml` → **API description is valid**, con 4 warnings esperados de `no-ambiguous-paths` (convivencia de `by-order/{orderId}` con `{reservationId}/...`).
- Runtime de Inventory: `/v3/api-docs` expone los 8 paths. El path `/internal/events/{eventId}/snapshot` es documentación del contrato de Catalog dentro del runtime de Inventory (Inventory no lo sirve).
- Tests de contrato: `OpenApiDocumentContractTest` y `OpenApiRuntimeParityTest` fallan si cambian paths, códigos o esquemas sin actualizar el YAML.

| Path | Método | Exposición |
|---|---|---|
| `/api/inventory/events/{eventId}/availability` | GET | Pública, vía Gateway |
| `/internal/reservations` | POST | Interna |
| `/internal/reservations/by-order/{orderId}` | GET | Interna |
| `/internal/reservations/{reservationId}` | GET | Interna |
| `/internal/reservations/{reservationId}/confirm` | POST | Interna |
| `/internal/reservations/{reservationId}/release` | POST | Interna |
| `/internal/events/{eventId}/hold-ttl` | PUT | Interna |
| `/internal/events/{eventId}/snapshot` | GET | Interna (Catalog) |

## 4. Construcción de imágenes Docker

Comando: `docker compose build` desde la raíz de `ticketya/`

| Imagen | Estado |
|---|---|
| `ticketya-api-gateway:latest` | Built |
| `ticketya-catalog-service:latest` | Built |
| `ticketya-inventory-service:latest` | Built |

Cada imagen ejecuta `mvn verify` en su etapa de build. El Dockerfile de Inventory usa el contexto raíz para copiar `docs/openapi.yaml`, de modo que `OpenApiDocumentContractTest` corre también dentro de la imagen. Los tests con `@Testcontainers(disabledWithoutDocker = true)` se omiten en el build porque no hay socket Docker disponible dentro del contenedor de build; se ejecutan completos en `mvn verify` desde el host.

## 5. Levantamiento con Docker Compose

Comando: `docker compose down -v && docker compose up -d --build --wait`

Resultado: los 5 servicios quedan `Healthy`.

| Servicio | Puerto | Healthcheck | Estado |
|---|---|---|---|
| postgres | 5432 (interno) | `pg_isready` | Healthy |
| inventory-db | 5432 (interno) | `pg_isready` | Healthy |
| catalog-service | 8081 (interno) | `wget /actuator/health` | Healthy |
| inventory-service | 8082 (interno) | `wget /actuator/health` | Healthy |
| api-gateway | 8080 (publicado) | `wget /actuator/health` | Healthy |

El Gateway es el único servicio con puerto publicado al host (`${GATEWAY_PORT:-8080}:8080`). Catalog, Inventory, `postgres` e `inventory-db` sólo son accesibles por la red de Compose. `/internal/**` no tiene ruta en el Gateway.

## 6. Ejecución de Postman vía Gateway

Comando: `npx --yes newman run postman/ticketya.postman_collection.json --env-var baseUrl=http://localhost:8080`

Resultado: **13 requests, 31 aserciones, 0 fallos**

| # | Request | Método | Ruta | Estado |
|---|---|---|---|---|
| 01 | Crear lugar | POST | `/api/venues` | 201 |
| 02 | Crear borrador | POST | `/api/management/events` | 201 |
| 03 | Editar borrador | PUT | `/api/management/events/{id}` | 200 |
| 04 | Configurar ofertas | PUT | `/api/management/events/{id}/offerings` | 200 |
| 05 | Publicar | POST | `/api/management/events/{id}/publish` | 200 |
| 06 | Buscar publicados | GET | `/api/events` | 200 |
| 07 | Detalle público | GET | `/api/events/{id}` | 200 |
| 08 | Disponibilidad de Inventory | GET | `/api/inventory/events/{eventId}/availability` | 200 |
| 09 | Suspender | POST | `/api/management/events/{id}/suspend` | 200 |
| 10 | Detalle tras suspensión | GET | `/api/events/{id}` | 404 |
| 11 | Reanudar | POST | `/api/management/events/{id}/resume` | 200 |
| 12 | Detalle tras reanudación | GET | `/api/events/{id}` | 200 |
| 13 | Eco CorrelationId | POST | `/api/management/events/{id}/suspend` | 200 |

La request 08 valida que Inventory materializa perezosamente el inventario desde el snapshot publicado, devuelve sectores numerados y generales con sus asientos, no expone `price` y propaga `X-Correlation-Id`. Todos los requests van exclusivamente por el Gateway (puerto 8080).

## 7. Smoke test interno de reservas

Ejecutado dentro de la red de Compose (sin publicar puertos), sobre el evento publicado por la colección Postman:

1. `GET /api/inventory/events/{eventId}/availability` → 2 sectores: numerado (3 asientos) y general (capacidad 500).
2. `POST /internal/reservations` con 1 asiento numerado + 2 generales → `201`, estado `ACTIVE`, 2 ítems.
3. `POST /internal/reservations/{reservationId}/confirm` → `200`, estado `CONFIRMED`.
4. `GET /v3/api-docs` → expone `/api/inventory/events/{eventId}/availability`, `/internal/reservations`, `/internal/events/{eventId}/hold-ttl` y `/internal/events/{eventId}/snapshot`.

También se verificó que el Gateway no publica `/internal/**` (sin ruta configurada para ese prefijo).

## 8. Limpieza

Comando: `docker compose down` (sin `-v` para conservar volúmenes) o `docker compose down -v` para reset completo de `catalog-postgres-data` e `inventory-postgres-data`.

## 9. Checklist de Definition of Done (§21 del blueprint)

| Criterio | Evidencia |
|---|---|
| Dockerfile propio e imagen construida individualmente | `services/inventory-service/Dockerfile`; `docker compose build` construye `ticketya-inventory-service` |
| Compose raíz declara el componente y sus dependencias | `inventory-service`, `inventory-db`; depende de `inventory-db` y `catalog-service` sanos |
| `docker compose up --build` levanta el incremento con healthchecks sanos | 5/5 servicios `Healthy` |
| Configuración por variables de entorno, sin rutas ni secretos | `DB_URL`, `DB_USER`, `DB_PASSWORD`, `CATALOG_SERVICE_URL`, `INVENTORY_HOLD_TTL_SECONDS`, `SERVER_PORT` |
| README documenta build, ejecución y pruebas | Secciones de Inventory, variables, Compose, flujo interno, OpenAPI y reset de volúmenes |
| OpenAPI y Postman actualizados y ejecutados por el Gateway | `docs/openapi.yaml` validado; Newman 13 requests / 31 aserciones por el puerto 8080 |
| Tests unitarios, de integración y de concurrencia aplicables | 116 tests verdes, incluidos los 4 escenarios multi-instancia |
| `correlationId` propagado y sin datos sensibles | Tests de correlation ID en los 3 servicios; disponibilidad y errores sin precios ni trazas |

## Conclusión

Todas las verificaciones del cambio `establish-inventory-and-reservations` pasan:

- ✅ `mvn verify` desde la raíz: 116 tests, 0 fallos (25 Gateway + 32 Catalog + 59 Inventory)
- ✅ Concurrencia multi-instancia: 4 escenarios, sin doble venta ni doble liberación
- ✅ `docs/openapi.yaml` válido con Redocly y en paridad con el runtime de springdoc
- ✅ `docker compose build` para las 3 imágenes
- ✅ `docker compose up -d --build --wait`: 5 servicios `Healthy`, sólo el Gateway publicado
- ✅ Newman vía Gateway: 13 requests, 31 aserciones, 0 fallos
- ✅ Smoke test interno de reserva y confirmación, y OpenAPI interno de Inventory
- ✅ DoD de §21 del blueprint cubierto y sin desviaciones

El cambio queda listo para archivado tras la revisión del equipo.
