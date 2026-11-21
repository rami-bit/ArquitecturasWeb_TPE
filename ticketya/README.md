# TicketYa - Plataforma de venta de entradas

> **Autenticación:** desde la épica E2-01 todas las rutas protegidas exigen
> `Authorization: Bearer <JWT>` emitido por un proveedor de identidad externo.
> `X-User-Id` y `X-User-Role` ya no autentican: el Gateway los descarta y deriva
> la identidad de los claims validados. Ver
> [Autenticación con JWT](#autenticación-con-jwt-e2-01).
>
> **Entrega 2 implementada hasta hoy:** E2-01 (JWT y roles), E2-02
> (trazabilidad HTTP y bitácora centralizada), E2-03 (control de acceso con QR),
> E2-04 (reportes del organizador), F6 (fila virtual), F10 (preferencias y
> recomendaciones con MongoDB 7) y los controles transversales de caché,
> circuit breaker, pruebas de integración y carga, y F11 (notificaciones por
> email y push simulados). Ver
> [Notificaciones](#notificaciones-al-cliente-pdf-f11),
> [Trazabilidad y bitácora](#trazabilidad-y-bitácora-centralizada-e2-02),
> [Control de acceso QR](#control-de-acceso-con-qr-us-f8-e2-03),
> [Reportes del organizador](#reportes-del-organizador-us-f12-e2-04),
> [Fila virtual](#fila-virtual-de-eventos-de-alta-demanda),
> [Caché](#caché-del-catálogo-pdf-nf-cache),
> [Preferencias](#preferencias-y-recomendaciones-pdf-f10) y
> [Resiliencia](#resiliencia-circuit-breaker-y-fallback-pdf-nf-resilience).
>
> **Carátula de la Entrega 2:** estado de los entregables, criterios de aceptación y evidencia en [`docs/entrega2.md`](docs/entrega2.md).
>
> **Patrones y consistencia:** [`docs/patterns-and-consistency.md`](docs/patterns-and-consistency.md) es el documento único de decisiones. **Prueba de carga:** [`docs/load-test-report.md`](docs/load-test-report.md).

## Control de acceso con QR (US-F8, E2-03)

El personal de acceso (`ACCESS_STAFF`) escanea el QR que el comprador recibió en su orden (`qrPayload`) y lo envía por el Gateway:

```http
POST http://localhost:8080/api/access/events/{eventId}/validations
Authorization: Bearer <JWT con roles=["ACCESS_STAFF"]>
Content-Type: application/json

{"qrPayload":"v1.2a2383ad-0396-4465-820f-5d75710c0206.<firma>","gate":"Puerta 3"}
```

| Código | `result` / `reason` | Significado |
|---|---|---|
| `200` | `ADMITTED` | Entrada válida; queda `USED` con fecha, puerta y validador |
| `409` | `ALREADY_USED` | Ya ingresó; incluye `usedAt` y `usedGate` |
| `409` | `REVOKED` | Revocada por devolución o cancelación |
| `422` | `WRONG_EVENT` | Corresponde a otro evento; no se consume |
| `404` | `UNKNOWN_TICKET` | Firma inválida, formato incorrecto o entrada inexistente |
| `403` | — | El rol no es `ACCESS_STAFF` (lo responde el Gateway) |

El ingreso se marca con un único `UPDATE ... WHERE status='ISSUED'`: ante escaneos simultáneos del mismo QR, aun desde varias instancias, exactamente uno ingresa. `ACCESS_STAFF` o `ADMIN` pueden consultar los intentos de un evento con `GET /api/access/events/{eventId}/scans`, que devuelve los totales por resultado y los intentos paginados.

## Reportes del organizador (US-F12, E2-04)

```http
GET http://localhost:8080/api/reports/events/{eventId}
Authorization: Bearer <JWT con roles=["ORGANIZER"] (dueño del evento) o ["ADMIN"]>
```

Devuelve por sector y en total `soldTickets`, `refundedTickets`, `activeTickets`, `capacity`, `occupancyPercent`, `grossRevenue`, `refundedAmount` y `netRevenue`. Sólo cuentan como vendidas las órdenes pagadas, y las devoluciones cuentan cuando el reintegro está completado. Un organizador ajeno recibe `403`; `CLIENT` y `ACCESS_STAFF` también. Si Inventory no responde, el reporte llega igual con `capacityAvailable: false`, la capacidad y la ocupación en `null` y una advertencia en `warnings`.

## Trazabilidad y bitácora centralizada (E2-02)

- **Cada solicitud HTTP** queda en el log del Gateway y del servicio que la atiende (logger `ticketya.http`), con método, endpoint, usuario, rol, código, duración y correlation id. El Gateway registra también los `401`/`403` que no llegan a los servicios:

  ```
  http method=POST path=/api/orders status=201 durationMs=281 userId=4bd7… role=CLIENT correlationId=17e9…
  ```

  Para verlos todos juntos, ejecuta `docker compose logs -f | grep ticketya.http`.
- **Cada operación crítica** (compra, reconciliación, devolución, cancelación, cambios de estado del evento, emisión/revocación de entradas e ingreso al evento) se envía de forma asíncrona a `audit-service`, con el mismo `X-Correlation-Id` que recorre la operación. Si Audit Service no responde, la operación sigue normalmente y el evento queda en el log local (`ticketya.audit`).
- **Consulta (sólo `ADMIN`):**

  ```http
  GET http://localhost:8080/api/audit/traces/{correlationId}
  GET http://localhost:8080/api/audit/events?operation=PURCHASE_ORDER&outcome=CONFIRMED&page=0&size=50
  Authorization: Bearer <JWT con roles=["ADMIN"]>
  ```

  Para seguir una operación propia, envíala con `X-Correlation-Id: mi-traza-1` y consulta `GET /api/audit/traces/mi-traza-1`. Filtros disponibles: `correlationId`, `service`, `operation`, `outcome`, `resourceId`, `actorId`, `from` y `to`.

## Solicitud de devolución (US-F7.1)

El titular de un ticket pagado puede pedir su devolución hasta antes de las 72 horas previas al evento. La solicitud y la consulta pasan por el Gateway; repetir el POST devuelve la misma solicitud.

```http
POST http://localhost:8080/api/refunds/tickets/{ticketId}
Authorization: Bearer <JWT con sub=66bd0a43-1baa-4f7b-8316-930593b84d7c y roles=["CLIENT"]>
```

```http
GET http://localhost:8080/api/refunds/{refundId}
Authorization: Bearer <JWT con sub=66bd0a43-1baa-4f7b-8316-930593b84d7c y roles=["CLIENT"]>
```

La respuesta inicial indica `PENDING`, decisión nula y progreso `NOT_STARTED`. Otro usuario recibe `404`.

## Decidir una devolución (US-F7.2)

Un administrador con rol `ADMIN` aprueba o rechaza una solicitud mediante el Gateway.

```http
POST http://localhost:8080/api/refunds/{refundId}/decision
Authorization: Bearer <JWT con roles=["ADMIN"]>
Content-Type: application/json

{"decision":"APPROVED"}
```

Para rechazar, usa `{"decision":"REJECTED"}`. El rechazo conserva el ticket y su lugar vendido. Al aprobar, Purchase revoca el ticket, solicita el reintegro idempotente a Payment Mock y libera la unidad vendida en Inventory. Un `200` indica proceso completo; `202` indica que una dependencia falló y se puede repetir la misma decisión para continuar. Una decisión opuesta responde `409`. Un token sin rol `ADMIN` recibe `403`.


## Cancelar un evento con reintegros (US-F7.3)

ADMIN inicia la cancelación por el Gateway. Purchase pide a Catalog que marque el evento `CANCELLED` y bloquee nuevas reservas en Inventory; luego procesa los tickets pagados por lotes. Cada ticket se revoca, se reintegra por su importe y libera su unidad vendida. La respuesta `202` indica que queda trabajo; repetir el mismo POST continúa el lote sin duplicar reintegros. `200` y `status: COMPLETED` indican que terminó. La consulta GET muestra el progreso sin procesar otro lote.

```http
POST http://localhost:8080/api/events/{eventId}/cancellation
Authorization: Bearer <JWT con roles=["ADMIN"]>
```

```http
GET http://localhost:8080/api/events/{eventId}/cancellation
Authorization: Bearer <JWT con roles=["ADMIN"]>
```

Una respuesta de ejemplo es `{"eventId":"...","status":"PROCESSING","totalTickets":4,"completedTickets":2,"pendingTickets":2}`. Catalog también expone `POST /api/management/events/{eventId}/cancel` para cambiar el estado del evento; esa ruta por sí sola no procesa ventas existentes. Las notificaciones a compradores corresponden a US-F11.

## Compra local (Purchase + Payment Mock)

El flujo de compra se expone a través del Gateway:

| Método | Ruta | Propósito |
|---|---|---|
| `POST` | `/api/orders` | Crear una orden y ejecutar la saga de compra |
| `GET` | `/api/orders` | Listar las órdenes del cliente (`page`, `size`, `status`) |
| `GET` | `/api/orders/{orderId}` | Consultar una orden propia con ítems, unidades, pago y tickets |
| `POST` | `/api/orders/{orderId}/reconcile` | Reintentar una orden `PAYMENT_PENDING` o `PAID_PENDING_TICKETS` |

Cada solicitud requiere `Authorization: Bearer <JWT>` cuyo `sub` es el UUID del cliente, y para crear una orden, `Idempotency-Key` (1 a 120 caracteres). Un token con rol `ADMIN` permite listar y consultar órdenes de cualquier cliente. El cuerpo **no incluye precios**: `eventId`, `items` y `payment`. Cada ítem informa exactamente uno de `seatIds` (sector numerado) o `quantity` (admisión general), usando los identificadores de Inventory que devuelve la consulta de disponibilidad. El límite de entradas por cliente y evento es seis y se cambia con `PURCHASE_TICKET_LIMIT`.

Cuando el evento está marcado como de alta demanda, `POST /api/orders` exige además el encabezado `X-Admission-Token` con el token vigente que emite la fila virtual; su ausencia o un token ajeno, vencido o ya consumido responde `403`. El token se consume en el intento de compra y no reserva inventario.

Ejemplo de compra:

```http
POST http://localhost:8080/api/orders
Authorization: Bearer <JWT con sub=66bd0a43-1baa-4f7b-8316-930593b84d7c y roles=["CLIENT"]>
Idempotency-Key: demo-purchase-001
Content-Type: application/json

{
  "eventId": "e88d7b2d-9019-4c3c-8bc4-e0ecc43c955b",
  "items": [
    {"sectorId": "0b5e9b5e-7b0e-4c0a-9f5e-2f7b8c1d2e3f", "seatIds": ["f1d2c3b4-a5e6-4f70-8a91-b2c3d4e5f601", "f1d2c3b4-a5e6-4f70-8a91-b2c3d4e5f602"]},
    {"sectorId": "9c8d7e6f-5a4b-4c3d-8e2f-1a0b9c8d7e6f", "quantity": 2}
  ],
  "payment": {"method": "CARD", "token": "tok_approved"}
}
```

El token de simulación decide el cobro: `tok_approved` aprueba, `tok_rejected` rechaza y `tok_pending` deja el pago pendiente. Los códigos de respuesta son:

| Código | Resultado |
|---|---|
| `201` | Compra confirmada, inventario vendido y tickets emitidos |
| `200` | Reintento idempotente de una orden existente |
| `202` | Pago pendiente; consultar o reconciliar la orden |
| `402` | Pago rechazado; la reserva se libera |
| `400`, `403`, `404`, `409` | Solicitud inválida, rol insuficiente, evento no comprable o conflicto, respectivamente |
| `401` | Falta el Bearer o el token no es válido; lo responde el Gateway sin llegar al servicio |
| `503` | Una dependencia no está disponible; reintentar con la misma clave |

El flujo completo y los errores están en [`docs/openapi.yaml`](docs/openapi.yaml). La colección Postman ejercita compras aprobadas, devolución y cancelación por el Gateway.

## Requisitos

- **JDK 19 o superior**. `JAVA_HOME` debe apuntar a una instalación compatible.
- **Maven 3.9+** en el `PATH`.
- **Docker** con **Docker Compose v2**.

Todos los comandos de esta guía se ejecutan desde la carpeta `ticketya/` (raíz de este proyecto OpenSpec), no desde la raíz del repositorio `ArquitecturasWeb_TPE`.

## Variables de entorno

Docker Compose carga automáticamente el archivo `.env` de esta carpeta. Mantenlo sincronizado con las variables `${...}` de `docker-compose.yml` cuando se agreguen o renombren opciones. Los valores son para desarrollo local.

| Variable | Predeterminado | Propósito |
|---|---|---|
| `POSTGRES_DB` | `ticketya_catalog` | Nombre de la base de datos de catálogo |
| `INVENTORY_POSTGRES_DB` | `ticketya_inventory` | Nombre de la base de datos de inventario |
| `TICKET_POSTGRES_DB` | `ticketya_ticket` | Nombre de la base de datos de tickets |
| `PURCHASE_POSTGRES_DB` | `ticketya_purchase` | Nombre de la base de datos de compras |
| `PAYMENT_POSTGRES_DB` | `ticketya_payment` | Nombre de la base de datos de pagos simulados |
| `QUEUE_POSTGRES_DB` | `ticketya_queue` | Nombre de la base de datos de la fila virtual |
| `AUDIT_POSTGRES_DB` | `ticketya_audit` | Nombre de la base de la bitácora centralizada (E2-02) |
| `AUDIT_SERVICE_URL` | `http://audit-service:8086` | URL a la que Catalog, Ticket y Purchase envían los eventos de auditoría; vacía = sólo log local |
| `POSTGRES_USER` | `ticketya` | Usuario de todas las bases PostgreSQL |
| `POSTGRES_PASSWORD` | `ticketya` | Contraseña de todas las bases PostgreSQL |
| `GATEWAY_PORT` | `8080` | Puerto publicado del API Gateway en el host |
| `JWT_ISSUER_URI` | `https://identity.example.invalid/issuer` | Emisor externo esperado por el Gateway |
| `JWT_JWK_SET_URI` | `https://identity.example.invalid/.well-known/jwks.json` | JWKS público del proveedor de identidad |
| `PURCHASE_TICKET_LIMIT` | `6` | Máximo de entradas por cliente y evento |
| `QUEUE_BATCH_SIZE` | `10` | Turnos admitidos por tanda de la fila virtual |
| `QUEUE_BATCH_INTERVAL_SECONDS` | `15` | Duración de cada tanda de la fila virtual |
| `QUEUE_ADMISSION_TTL_SECONDS` | `120` | Vigencia del token de admisión |
| `QUEUE_ADMISSION_SECRET` | Clave local definida en `.env` | Clave HMAC estable que firma los tokens de admisión |
| `PREFERENCES_MONGODB_DATABASE` | `ticketya_preferences` | Base MongoDB de preferencias (PDF-F10) |
| `PREFERENCES_CANDIDATE_LIMIT` | `200` | Máximo de candidatos pedidos al catálogo por recomendación |
| `PREFERENCES_HTTP_CONNECT_TIMEOUT_MS` | `2000` | Timeout de conexión hacia Catalog y Purchase |
| `PREFERENCES_HTTP_READ_TIMEOUT_MS` | `5000` | Timeout de lectura hacia Catalog y Purchase |
| `PURCHASE_CIRCUIT_FAILURE_THRESHOLD` | `5` | Fallas consecutivas que abren el circuito de Payment y notificaciones |
| `PURCHASE_CIRCUIT_OPEN_SECONDS` | `30` | Duración de la apertura antes de permitir una llamada de prueba |
| `NOTIFICATION_SERVICE_URL` | `http://notification-service:8088` | Notification Service al que Purchase y Catalog envían sus hechos; vacío = sólo log local |
| `NOTIFICATION_PROVIDER_MODE` | `SIMULATED` | Pasarela simulada de email/push; `FAILING` simula la caída para probar reintentos |
| `NOTIFICATION_MAX_ATTEMPTS` / `NOTIFICATION_RETRY_BASE_DELAY_MS` | `6` / `2000` | Intentos y espera base (exponencial, tope 60 s) de destinatarios y entregas |
| `TICKET_QR_SIGNING_SECRET` | Clave local definida en `.env` | Clave estable de al menos 32 bytes para firmar los QR |

Ticket Service requiere `TICKET_QR_SIGNING_SECRET`; el `.env` incluye una clave local de desarrollo para arrancar el stack directamente. Conserva el mismo valor entre reinicios si quieres validar QR ya emitidos. Al cambiarlo, los QR existentes dejarán de validarse. No reutilices esta clave fuera del entorno didáctico local.

### Autenticación con JWT (E2-01)

TicketYa no emite tokens ni mantiene usuarios: el cliente obtiene un JWT de un proveedor de identidad externo y lo envía como `Authorization: Bearer <JWT>`. Antes de usar rutas protegidas, reemplaza los dos valores de ejemplo terminados en `.invalid` por el `issuer` y el JWKS reales del proveedor. Sin esa configuración los endpoints públicos y los healthchecks siguen disponibles, pero las rutas protegidas fallan cerradas.

El token debe incluir `iss` coincidente con `JWT_ISSUER_URI`, `sub` como UUID, `exp` vigente y `roles` como array con exactamente uno de `CLIENT`, `ORGANIZER`, `ADMIN` o `ACCESS_STAFF`. El Gateway valida firma e identidad, descarta `X-User-Id`/`X-User-Role` enviados por el cliente y deriva los encabezados internos de los claims. No se registran ni versionan tokens.

Para ejecutar la colección Postman, configura `organizerToken`, `clientToken`, `numberedBuyerToken`, `adminToken` y `accessStaffToken` con JWTs emitidos por el IdP. Sus claims `sub` deben coincidir respectivamente con `userId`, `userId`, `numberedBuyerId`, `adminUserId` y `accessStaffUserId`; sus roles son `ORGANIZER`, `CLIENT`, `CLIENT`, `ADMIN` y `ACCESS_STAFF`. La colección usa además `foreignOrganizerId` para probar que un organizador no acceda a un borrador ajeno. No agregues tokens reales a la colección ni al repositorio.

#### IdP de prueba local (herramienta de verificación)

Para verificar el stack sin depender de un proveedor externo, el repositorio incluye un IdP de prueba mínimo en `tools/local-test-idp/LocalTestIdp.java`. No forma parte del producto ni del Compose: sólo publica el JWKS y emite tokens de prueba.

```bash
# Terminal 1: IdP de prueba (sin dependencias, sólo el JDK)
java tools/local-test-idp/LocalTestIdp.java --port 19998 \
  --issuer http://host.docker.internal:19998 \
  --key-file .local/idp-key.pem \
  --token organizer:e88d7b2d-9019-4c3c-8bc4-e0ecc43c955b:ORGANIZER \
  --token client:e88d7b2d-9019-4c3c-8bc4-e0ecc43c955b:CLIENT \
  --token numberedBuyer:9f44c33f-35eb-4764-becd-02f6e2414701:CLIENT \
  --token admin:a1b2c3d4-1111-2222-3333-444455556666:ADMIN \
  --token accessStaff:d160e54c-5f88-49a1-9e9a-2c901ac70a01:ACCESS_STAFF
```

El IdP publica el JWKS en `/.well-known/jwks.json` y expone un endpoint de token en `/token?sub=<uuid>&role=<ROL>` que devuelve `{"access_token": "<jwt>", ...}`. `--key-file` persiste el par de claves: el Gateway cachea el JWKS que ya leyó, así que reiniciar el IdP sin esa clave haría que los tokens nuevos dejaran de validar.

Levanta el Compose apuntando al IdP de prueba (el `issuer` debe coincidir exactamente con el claim `iss`):

```bash
JWT_ISSUER_URI=http://host.docker.internal:19998 \
JWT_JWK_SET_URI=http://host.docker.internal:19998/.well-known/jwks.json \
docker compose up --build -d --wait
```

El issuer usa `host.docker.internal` porque el Gateway corre dentro de Docker y el IdP en el host. En Docker Engine para Linux el nombre se resuelve gracias a `extra_hosts: host.docker.internal:host-gateway` del servicio `api-gateway`.

Los servicios internos reciben su configuración a través de variables de Compose (no necesitas definirlas a menos que quieras sobreescribirlas):

| Servicio | Variable | Predeterminado |
|---|---|---|
| catalog-service | `SERVER_PORT` | `8081` |
| catalog-service | `DB_URL` | `jdbc:postgresql://postgres:5432/ticketya_catalog` |
| catalog-service | `DB_USER` | `ticketya` |
| catalog-service | `DB_PASSWORD` | `ticketya` |
| inventory-service | `SERVER_PORT` | `8082` |
| inventory-service | `DB_URL` | `jdbc:postgresql://inventory-db:5432/ticketya_inventory` |
| inventory-service | `DB_USER` | `ticketya` |
| inventory-service | `DB_PASSWORD` | `ticketya` |
| inventory-service | `CATALOG_SERVICE_URL` | `http://catalog-service:8081` |
| inventory-service | `INVENTORY_HOLD_TTL_SECONDS` | `600` (TTL por defecto de reservas, rango 60-3600) |
| ticket-service | `SERVER_PORT` | `8083` |
| ticket-service | `DB_URL` | `jdbc:postgresql://ticket-db:5432/ticketya_ticket` |
| ticket-service | `DB_USER` | `ticketya` |
| ticket-service | `DB_PASSWORD` | `ticketya` |
| ticket-service | `TICKET_QR_SIGNING_SECRET` | Obligatoria; clave HMAC estable |
| purchase-service | `SERVER_PORT` | `8084` |
| purchase-service | `DB_URL` | `jdbc:postgresql://purchase-db:5432/ticketya_purchase` |
| purchase-service | `DB_USER` | `ticketya` |
| purchase-service | `DB_PASSWORD` | `ticketya` |
| purchase-service | `CATALOG_SERVICE_URL` | `http://catalog-service:8081` |
| purchase-service | `INVENTORY_SERVICE_URL` | `http://inventory-service:8082` |
| purchase-service | `PAYMENT_SERVICE_URL` | `http://payment-mock:8085` |
| purchase-service | `TICKET_SERVICE_URL` | `http://ticket-service:8083` |
| purchase-service | `QUEUE_SERVICE_URL` | `http://queue-service:8086` |
| purchase-service | `PURCHASE_TICKET_LIMIT` | `6` |
| payment-mock | `SERVER_PORT` | `8085` |
| payment-mock | `DB_URL` | `jdbc:postgresql://payment-db:5432/ticketya_payment` |
| payment-mock | `DB_USER` | `ticketya` |
| payment-mock | `DB_PASSWORD` | `ticketya` |
| queue-service | `SERVER_PORT` | `8086` |
| queue-service | `DB_URL` | `jdbc:postgresql://queue-db:5432/ticketya_queue` |
| queue-service | `DB_USER` | `ticketya` |
| queue-service | `DB_PASSWORD` | `ticketya` |
| queue-service | `CATALOG_SERVICE_URL` | `http://catalog-service:8081` |
| queue-service | `QUEUE_BATCH_SIZE` | `10` (turnos admitidos por tanda) |
| queue-service | `QUEUE_BATCH_INTERVAL_SECONDS` | `15` (duración de cada tanda) |
| queue-service | `QUEUE_ADMISSION_TTL_SECONDS` | `120` (vigencia del token de admisión) |
| queue-service | `QUEUE_ADMISSION_SECRET` | Obligatoria; clave HMAC estable del token de admisión |
| notification-service | `SERVER_PORT` | `8088` |
| notification-service | `DB_URL` | `jdbc:postgresql://notification-db:5432/ticketya_notification` |
| notification-service | `NOTIFICATION_PURCHASE_SERVICE_URL` | `http://purchase-service:8084` (compradores del evento) |
| notification-service | `NOTIFICATION_PREFERENCES_SERVICE_URL` | `http://preferences-service:8087` (fans por artista y ciudad) |
| notification-service | `NOTIFICATION_CHANNELS` | `EMAIL,PUSH` |
| catalog-service, purchase-service | `NOTIFICATION_SERVICE_URL` | `http://notification-service:8088` |
| audit-service | `SERVER_PORT` | `8086` |
| audit-service | `DB_URL` | `jdbc:postgresql://audit-db:5432/ticketya_audit` |
| preferences-service | `SERVER_PORT` | `8087` |
| preferences-service | `MONGODB_URI` | `mongodb://preferences-db:27017` |
| preferences-service | `MONGODB_DATABASE` | `ticketya_preferences` |
| preferences-service | `CATALOG_SERVICE_URL` / `PURCHASE_SERVICE_URL` | `http://catalog-service:8081` / `http://purchase-service:8084` |
| api-gateway | `GATEWAY_PREFERENCES_SERVICE_URL` | `http://preferences-service:8087` |
| audit-service | `DB_USER` / `DB_PASSWORD` | `ticketya` / `ticketya` |
| catalog-service, ticket-service, purchase-service | `AUDIT_SERVICE_URL` | `http://audit-service:8086` |
| api-gateway | `SERVER_PORT` | `8080` |
| api-gateway | `CATALOG_SERVICE_URL` | `http://catalog-service:8081` |
| api-gateway | `INVENTORY_SERVICE_URL` | `http://inventory-service:8082` |
| api-gateway | `PURCHASE_SERVICE_URL` | `http://purchase-service:8084` |
| api-gateway | `QUEUE_SERVICE_URL` | `http://queue-service:8086` |
| api-gateway | `TICKET_SERVICE_URL` | `http://ticket-service:8083` (control de acceso, E2-03) |
| api-gateway | `AUDIT_SERVICE_URL` | `http://audit-service:8086` (bitácora, E2-02) |
| api-gateway | `JWT_ISSUER_URI` | `https://identity.example.invalid/issuer` |
| api-gateway | `JWT_JWK_SET_URI` | `https://identity.example.invalid/.well-known/jwks.json` |

Ninguna variable contiene rutas personales ni secretos de producción.

## Verificación de sistema completo, carga e integración

Con el stack saludable y el IdP de prueba en `:19998`:

```bash
# Prueba de carga del flujo de compra (etapas 10/20/40 por omisión)
python postman/load-test.py

# Arnés de integración: reutiliza el stack si ya está levantado;
# con --down detiene sólo el stack que él mismo creó (sin borrar volúmenes)
python postman/integration-harness.py
```

La prueba de carga es complementaria a la colección Postman: el arnés comprueba ambas y falla si cualquiera no pasa. Los resultados medidos están en [`docs/load-test-report.md`](docs/load-test-report.md) y las decisiones de arquitectura en [`docs/patterns-and-consistency.md`](docs/patterns-and-consistency.md).

## Compilación y verificación con Maven

Desde la carpeta `ticketya/`:

```bash
# Compilación y verificación completa desde la raíz agregadora
mvn verify

# Compilación y verificación de un servicio individual
mvn verify -pl services/api-gateway -am
mvn verify -pl services/catalog-service -am
mvn verify -pl services/inventory-service -am
mvn verify -pl services/ticket-service -am
mvn verify -pl services/purchase-service -am
mvn verify -pl services/queue-service -am
mvn verify -pl services/payment-mock -am
mvn verify -pl services/audit-service -am
mvn verify -pl services/preferences-service -am
mvn verify -pl services/notification-service -am
```

Pruebas específicas de las épicas E2-02, E2-03, E2-04 y PDF-F11 (requieren Docker para Testcontainers):

```bash
# PDF-F11: notificaciones (servicio, productores y destinatarios)
mvn -pl services/notification-service -am verify
mvn -pl services/catalog-service,services/purchase-service,services/preferences-service -am verify -Dtest='EventChangeNotificationIntegrationTest,EventNotificationPublisherTest,EventBuyersIntegrationTest,NotificationDispatcher*Test,PreferenceMatchesMongoIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false

# E2-03: control de acceso QR (incluye 25 escaneos simultáneos del mismo QR)
mvn -pl services/ticket-service -am verify -Dtest='AccessControlContractTest,TicketQrVerificationTest' -Dsurefire.failIfNoSpecifiedTests=false

# E2-04: reportes del organizador
mvn -pl services/purchase-service -am verify -Dtest='OrganizerReportCalculatorTest,OrganizerReportIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false

# E2-02: bitácora, publicador resiliente y registro de acceso HTTP
mvn -pl services/audit-service -am verify
mvn -pl services/purchase-service,services/ticket-service,services/catalog-service -am verify -Dtest='AuditPublisherTest,RequestLoggingFilterTest' -Dsurefire.failIfNoSpecifiedTests=false

# Gateway: roles por ruta, 401/403 y log de acceso; contrato OpenAPI consolidado
mvn -pl services/api-gateway -am verify -Dtest='GatewayEntrega2RoleAuthorizationTest,ConsolidatedOpenApiDocumentContractTest' -Dsurefire.failIfNoSpecifiedTests=false
```

El proyecto compila para Java 19 y usa Spring Boot 3.2.5. No se incluye Maven Wrapper (`mvnw`); se asume Maven 3.9+ instalado en el entorno. En Windows, si el host sólo tiene JDK 17, se puede ejecutar la verificación con Java 21 y Testcontainers desde PowerShell:

```powershell
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "${PWD}:/workspace" -w /workspace -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal maven:3.9.9-eclipse-temurin-21-alpine mvn -q verify
```

Después hay que revisar los XML Surefire recientes: `skipped=0` en las clases Testcontainers, además de cero fallos y errores. Los comandos y resultados de la última corrida están en [`docs/verification.md`](docs/verification.md). Si sólo cambian documentación o la colección Postman, se puede reutilizar un stack ya saludable y ejecutar la colección con datos nuevos; al cambiar código o configuración, reconstruir el proyecto Compose aislado.

Los tests de contrato de Inventory, Purchase y Payment Mock, y de persistencia contra PostgreSQL, usan Testcontainers: requieren Docker disponible. Si Docker no está disponible, esas clases se omiten automáticamente y el resto de los tests sigue ejecutándose; un test omitido no cuenta como verificado.

La verificación del Ticket Service está registrada en
[`services/ticket-service/docs/ticket-verification.md`](services/ticket-service/docs/ticket-verification.md) y el resumen de toda la entrega en [`docs/verification.md`](docs/verification.md).

## Construcción de imágenes y Compose

Desde la carpeta `ticketya/`:

```bash
# Construir todas las imágenes (Gateway, Catalog, Inventory, Ticket, Purchase, Queue, Payment Mock y Audit)
docker compose build

# Levantar el incremento completo (reconstruye imágenes y espera las comprobaciones de estado)
docker compose up --build

# Levantar en segundo plano
docker compose up -d

# Ver estado de los servicios
docker compose ps

# Ver logs
docker compose logs -f

# Detener y eliminar contenedores (conserva volúmenes)
docker compose down

# Detener y eliminar todo, incluidos volúmenes de datos de PostgreSQL
docker compose down -v
```

El Gateway expone el único puerto publicado (`${GATEWAY_PORT:-8080}:8080`). Catalog, Inventory, Ticket, Purchase, Queue, Payment Mock, Audit y sus bases no publican puertos al host; se comunican por nombres de servicio de la red de Compose.

## Salud de los servicios

Cada servicio tiene una comprobación de estado basada en `wget` contra su endpoint de actuator:

- `postgres` (Catálogo), `inventory-db`, `ticket-db`, `purchase-db`, `queue-db`, `payment-db` y `audit-db`: `pg_isready`; `preferences-db`: `mongosh --eval "db.adminCommand('ping')"`
- Preferences Service: healthcheck interno en `http://preferences-service:8087/actuator/health` (no publica puerto en el host; compruébalo con `docker compose ps`)
- Catalog Service: `http://localhost:8081/actuator/health`
- Inventory Service: `http://localhost:8082/actuator/health`
- Ticket Service: `http://localhost:8083/actuator/health`
- Purchase Service: `http://localhost:8084/actuator/health`
- Queue Service: `http://localhost:8086/actuator/health`
- Payment Mock: `http://localhost:8085/actuator/health`
- Audit Service: `http://localhost:8086/actuator/health`
- API Gateway: `http://localhost:8080/actuator/health`

Espera a que los 19 contenedores informen `healthy` antes de ejecutar la colección Postman.

## Ejecutar la colección Postman (vía Gateway)

La colección está en `ticketya/postman/ticketya.postman_collection.json`. Ejecútala exclusivamente contra el Gateway (puerto 8080):

```bash
npx --yes newman run postman/ticketya.postman_collection.json \
  --env-var baseUrl=http://localhost:8080 \
  --env-var organizerToken="$ORGANIZER_TOKEN" \
  --env-var clientToken="$CLIENT_TOKEN" \
  --env-var numberedBuyerToken="$NUMBERED_BUYER_TOKEN" \
  --env-var adminToken="$ADMIN_TOKEN" \
  --env-var accessStaffToken="$ACCESS_STAFF_TOKEN"
```

Exporta esas cinco variables con tokens vigentes del IdP. `organizerToken` debe tener `sub=userId` y rol `ORGANIZER`; `clientToken`, `sub=userId` y rol `CLIENT`; `numberedBuyerToken`, `sub=numberedBuyerId` y rol `CLIENT`; `adminToken`, `sub=adminUserId` y rol `ADMIN`; `accessStaffToken`, `sub=accessStaffUserId` y rol `ACCESS_STAFF`. Los tokens son datos sensibles: pásalos al proceso de Newman desde el entorno y no los guardes en archivos del repositorio.

La colección es repetible: cada corrida genera un `runId` único para evitar colisiones. Cubre Catalog e Inventory, compras aprobadas de admisión general y asiento numerado con QR, reintento idempotente, consulta de tickets por su titular, pago rechazado con liberación del cupo y límite de seis entradas por cliente y evento. También prueba solicitud y resolución de devoluciones, cancelación con reintegros, suspensión y reanudación del evento, y el flujo de la fila virtual con compra usando el token de admisión. Los pasos 28 a 47 cubren E2-02/E2-03/E2-04. Los pasos `F10-01` a `F10-09` cubren preferencias, recomendaciones, aislamiento por cliente, roles y rechazo de identidad en el cuerpo. Los pasos `F6-01` a `F6-17` cubren la fila virtual: alta del evento de alta demanda, ingreso e reingreso idempotente, espera de la tanda con sondeo de `GET /entries/me`, compras sin token, con token ajeno, con token válido, reutilización y token adulterado, y abandono con reingreso al final de la fila. Incluyen el reporte del evento cancelado (7 vendidas, 7 devueltas, neto 0) y el de un evento activo, los rechazos por rol, el ingreso con QR, el reingreso, el QR de otro evento, la entrada revocada y el QR adulterado, la consulta de intentos, y las trazas en la bitácora de una compra y de una cancelación. Todas las rutas públicas se recorren sin token y todas las operaciones protegidas por el Gateway. Inventory materializa el inventario de forma perezosa a partir del snapshot publicado de Catalog en la primera consulta.

Para comprobar muchas compras completas simultáneas del mismo asiento con compradores y claves distintas, sobre un stack saludable ejecuta `python postman/concurrent-purchases.py`. La prueba crea sus propios datos, espera una compra confirmada y once conflictos, y comprueba que Inventory registra un solo asiento vendido.

Desde la épica E2-01 el Gateway autentica con JWT, así que el script ya no envía `X-User-Id`/`X-User-Role`: emite un token por identidad contra el endpoint de token del IdP. Configura `IDP_TOKEN_URL` (predeterminado `http://localhost:19998/token`) apuntando al IdP configurado en el Gateway:

```bash
BASE_URL=http://localhost:8080 IDP_TOKEN_URL=http://localhost:19998/token \
  python postman/concurrent-purchases.py
```

Si el IdP no expone un endpoint de token, el script falla al inicio con un mensaje explícito en lugar de producir compras anónimas rechazadas por el Gateway.

## Flujo interno de inventario (no publicado por el Gateway)

Los comandos de reserva existen sólo dentro de la red de Compose y no tienen ruta en el Gateway:

| Método | Ruta | Propósito |
|---|---|---|
| `POST` | `/internal/reservations` | Crear o recuperar una reserva temporal (idempotente por `orderId`) |
| `GET` | `/internal/reservations/by-order/{orderId}` | Recuperar por orden |
| `GET` | `/internal/reservations/{reservationId}` | Recuperar por reserva |
| `POST` | `/internal/reservations/{reservationId}/confirm` | Confirmar y vender los lugares retenidos |
| `POST` | `/internal/reservations/{reservationId}/release` | Liberar los lugares retenidos |
| `PUT` | `/internal/events/{eventId}/hold-ttl` | Configurar el TTL de retención (60-3600 s) |
| `GET` | `/internal/events/{eventId}/snapshot` | Snapshot publicado de Catalog (contrato interno) |

Smoke test interno desde el host, sin publicar puertos:

```bash
# Disponibilidad pública a través del Gateway
curl -H "X-Correlation-Id: smoke-1" \
  http://localhost:8080/api/inventory/events/<eventId>/availability

# Comandos internos dentro de la red de Compose
docker compose exec -T inventory-service wget -qO- \
  http://localhost:8082/v3/api-docs
docker compose exec -T inventory-service wget -qO- \
  http://localhost:8082/internal/reservations/by-order/<orderId>
```

## Fila virtual de eventos de alta demanda

Queue Service escucha en `queue-service:8086`, dentro de la red de Compose, y el Gateway publica su API en `/api/queue/**`. Un evento admite fila sólo cuando fue creado o editado como borrador con `highDemand: true`; la marca se puede cambiar únicamente en `DRAFT` y los eventos publicados sin ella siguen comprándose como siempre.

| Método | Ruta pública (Gateway) | Propósito |
|---|---|---|
| `POST` | `/api/queue/events/{eventId}/entries` | Entrar a la fila; `201` la primera vez y `200` con el mismo turno si el cliente ya tenía uno activo |
| `GET` | `/api/queue/events/{eventId}/entries/me` | Consultar posición, estado y, si está admitido, el token de admisión |
| `DELETE` | `/api/queue/events/{eventId}/entries/me` | Abandonar el turno activo (`204`) |

La fila asigna posiciones con un contador por evento bajo bloqueo de PostgreSQL, nunca reutiliza posiciones y abre tandas por tiempo (`QUEUE_BATCH_SIZE` turnos cada `QUEUE_BATCH_INTERVAL_SECONDS`). El turno pasa de `WAITING` a `ADMITTED` y desde ahí a `CONSUMED`, `EXPIRED` o `LEFT`. El token de admisión es opaco, está firmado con `QUEUE_ADMISSION_SECRET`, ligado al evento y al comprador, vence a los `QUEUE_ADMISSION_TTL_SECONDS` y no se persiste: se recalcula desde el turno.

Compra con admisión, sólo para eventos de alta demanda:

```bash
# 1. Entrar a la fila
curl -X POST -H "Authorization: Bearer $CLIENT_TOKEN" \
  http://localhost:8080/api/queue/events/<eventId>/entries

# 2. Sondear hasta quedar ADMITTED y guardar admissionToken
curl -H "Authorization: Bearer $CLIENT_TOKEN" \
  http://localhost:8080/api/queue/events/<eventId>/entries/me

# 3. Comprar con el token de admisión
curl -X POST -H "Authorization: Bearer $CLIENT_TOKEN" \
  -H "Idempotency-Key: queue-demo-1" \
  -H "X-Admission-Token: <admissionToken>" \
  -H "Content-Type: application/json" \
  -d '{"eventId":"<eventId>","items":[{"sectorId":"<sectorId>","quantity":1}],"payment":{"method":"CARD","token":"tok_approved"}}' \
  http://localhost:8080/api/orders
```

Purchase valida y consume el token llamando al contrato interno `POST /internal/admissions/validate` (`200` con `admissionId`, `403` si el token es ajeno, venció o ya se usó, `404` si no hay turno, `503` si la fila no responde) **antes** de reservar y cobrar. Si falta el token en un evento de alta demanda la compra responde `403`; si la fila cerró, `409`. El token habilita el intento pero no reserva ni garantiza inventario: Inventory sigue siendo la única autoridad de stock. Una caída de la fila no bloquea la cancelación del evento, porque el cierre de la fila es reactivo al estado del catálogo.

Smoke test interno, sin publicar puertos:

```bash
docker compose exec -T queue-service wget -qO- http://localhost:8086/v3/api-docs
docker compose exec -T queue-service wget -qO- http://localhost:8086/actuator/health
```

## Caché del catálogo (PDF-NF-CACHE)

Catalog mantiene una caché en memoria de búsquedas y detalles de eventos publicados con `CATALOG_CACHE_TTL_SECONDS` (30 por omisión) y `CATALOG_CACHE_MAX_ENTRIES` (200). La clave incluye filtros, paginación y orden. Publicar, suspender, cancelar, reanudar o reemplazar ofertas invalida la caché después del commit; si la invalidación falla, la entrada expira por TTL. **Nunca se cachea la disponibilidad de Inventory**, que se consulta en cada petición, ni las recomendaciones. El detalle y las alternativas descartadas están en `caching-strategy/spec.md` y en [`docs/patterns-and-consistency.md`](docs/patterns-and-consistency.md).

## Preferencias y recomendaciones (PDF-F10)

`preferences-service` guarda un documento por cliente en **MongoDB 7** (`preferences-db`, base `ticketya_preferences`, colección `customer_preferences`) y calcula recomendaciones bajo demanda. Es la base NoSQL del sistema (`PDF-NF-PERSISTENCE`); el resto de los servicios sigue usando PostgreSQL. La identidad se deriva del `sub` del JWT: ninguna ruta recibe un `customerId`.

| Método | Ruta (por Gateway) | Descripción |
|---|---|---|
| `PUT` | `/api/customers/me/preferences` | Registrar o reemplazar ciudad, tipos de evento y artistas favoritos. Responde `200` con `version` |
| `GET` | `/api/customers/me/preferences` | Consultar preferencias propias; sin documento responde `200` con `version` `0` |
| `GET` | `/api/customers/me/recommendations?limit=10` | Recomendaciones explicables; `limit` de 1 a 20 |

Las únicas operaciones permitidas requieren rol `CLIENT`. El Gateway publica `/api/customers/**` hacia Preferences **antes** del comodín `/api/**` de Catalog y no publica los contratos internos: `GET /internal/customers/{customerId}/recommendation-signals` (Purchase) y `GET /internal/events/recommendation-candidates?limit=200` (Catalog). Preferences no accede a esas bases.

El puntaje es lineal y determinista: `3` por tipo favorito, `2` por artista favorito en el cartel (tope tres), `2` por ciudad, `min(5, co-compras)` y `1` si el evento es de los próximos 30 días; máximo 17. Cada componente que suma aparece en `reasons` (`FAVORITE_EVENT_TYPE`, `FAVORITE_ARTIST`, `SAME_CITY`, `CO_PURCHASE`, `UPCOMING_FALLBACK`). Se ordena por puntaje descendente, fecha ascendente e identificador ascendente, y se excluyen eventos ya comprados. Sin preferencias ni historial se devuelven los próximos eventos con puntaje cero y motivo `UPCOMING_FALLBACK`.

No hay cache: cada consulta lee un documento, un agregado acotado en Purchase y hasta 200 candidatos en Catalog. Si Purchase falla, la respuesta sigue con `warnings: ["HISTORY_UNAVAILABLE"]`; si falla Catalog, responde `503`. Para reproducir la degradación en un stack ya levantado, detén `purchase-service` (`docker compose stop purchase-service`) y consulta recomendaciones, luego reinícialo; no detengas contenedores del stack de verificación ajeno.

## Resiliencia: circuit breaker y fallback (PDF-NF-RESILIENCE)

Purchase protege sus dependencias con un circuito de tres estados (`CLOSED`, `OPEN`, `HALF_OPEN`) implementado en `CircuitBreaker`, sin dependencias nuevas:

- **Payment:** tras `PURCHASE_CIRCUIT_FAILURE_THRESHOLD` fallas consecutivas de transporte o respuestas `5xx`, el circuito se abre y `charge` responde `503` sin invocar a Payment. Purchase libera la reserva y no marca la orden como pagada. Un `402` (pago rechazado) **no** abre el circuito porque es una respuesta de negocio. Vencidos `PURCHASE_CIRCUIT_OPEN_SECONDS`, se permite una sola llamada de prueba; si falla, el circuito se reabre.
- **Notificaciones:** cada evento (`ORDER_CONFIRMED`, `ORDER_CANCELLED`) se escribe siempre en el log local `ticketya.notification` con `correlationId`, `orderId` y `customerId`. Con `NOTIFICATION_SERVICE_URL` vacía no hay envío remoto (fallback local). Si hay URL, el envío es asíncrono y usa el mismo circuito: ante fallo, el evento queda en el log y la compra continúa. Este componente nunca lanza excepciones al flujo de negocio.

Los cambios de estado y los eventos en fallback se registran con SLF4J. La entrega por email y push la hace `notification-service` (ver [Notificaciones](#notificaciones-al-cliente-pdf-f11)).

## Notificaciones al cliente (PDF-F11)

`notification-service` (puerto interno 8088, base `ticketya_notification`) envía un email y un push (simulados) por cada hecho:

| Hecho | Origen | Quién recibe |
|---|---|---|
| Compra confirmada (`ORDER_CONFIRMED`), también al reconciliar | Purchase | El comprador |
| Orden compensada (`ORDER_CANCELLED`) | Purchase | El comprador |
| Evento suspendido / reanudado (`EVENT_SUSPENDED`, `EVENT_RESUMED`) | Catalog | Compradores con órdenes pagadas |
| Evento cancelado (`EVENT_CANCELLED`) | Catalog | Compradores con órdenes pagadas |
| Evento publicado (`EVENT_PUBLISHED`) | Catalog | Clientes con un artista del evento como favorito y la misma ciudad (preferencias F10) |

El cliente consulta su bandeja por el Gateway:

```http
GET http://localhost:8080/api/notifications/me?type=ORDER_CONFIRMED&page=0&size=20
Authorization: Bearer <JWT con roles=["CLIENT"]>
```

Cada notificación informa `channel` (`EMAIL`/`PUSH`) y `status` (`PENDING`, `SENT`, `FAILED`). La entrega es asíncrona: Purchase y Catalog responden sin esperar, y el servicio procesa cada segundo. Los reenvíos del mismo hecho no duplican avisos. Si Purchase, Preferences o la pasarela fallan, reintenta con espera exponencial. Para ver las entregas simuladas:

```bash
docker compose logs -f notification-service | grep ticketya.delivery
```

## Pasarela de pagos simulada (interna)

Payment Mock escucha en `payment-mock:8085`, dentro de la red de Compose. Purchase la invoca durante la saga y nunca se publica por el Gateway:

| Método | Ruta | Propósito |
|---|---|---|
| `POST` | `/internal/payments` | Cobrar de forma idempotente por `orderId`; el token decide `APPROVED`, `REJECTED` o `PENDING` |
| `GET` | `/internal/payments/{paymentId}` | Consultar un pago |
| `GET` | `/internal/payments/by-order/{orderId}` | Recuperar el pago de una orden |
| `POST` | `/internal/payments/{paymentId}/refund` | Reintegrar un pago `APPROVED`; repetir el reintegro no duplica el movimiento |

Reutilizar un `orderId` con el mismo contenido devuelve el pago existente; con otro contenido responde `409`. Reintegrar un pago `PENDING` o `REJECTED` responde `409` y conserva el estado. La base `ticketya_payment` sólo guarda el pago: ni el comprador ni datos de `purchase_db`.

## Emisión interna de entradas QR

Ticket Service escucha en `ticket-service:8083`, dentro de la red de Compose. Purchase invoca este contrato **después** de confirmar pago e inventario:

| Método | Ruta | Propósito |
|---|---|---|
| `POST` | `/internal/orders/{orderId}/items/{orderItemId}/tickets` | Emitir o recuperar idempotentemente las entradas QR de un item confirmado |

La solicitud incluye al comprador, el snapshot del evento y las unidades del item; para asientos numerados cada unidad lleva fila y número. La primera emisión devuelve `201`; un reintento idéntico devuelve `200` con los mismos tickets y QR. Reutilizar la pareja `orderId + orderItemId` con distinto contenido devuelve `409`.

La respuesta contiene `qrPayload` y `qrPngBase64`. La imagen PNG se genera al responder; PostgreSQL conserva el hash del payload, no el token. La API es interna y no tiene ruta en el Gateway. Purchase invoca esta emisión al completar una compra aprobada.

Valida la documentación y el estado del servicio desde el contenedor:

```powershell
docker compose exec -T ticket-service wget -qO- http://localhost:8083/v3/api-docs
docker compose exec -T ticket-service wget -qO- http://localhost:8083/actuator/health
```

El contrato versionado está en `services/ticket-service/docs/ticket-openapi.yaml`. Ticket permite revocar internamente una entrada vendida (una entrada ya `USED` no puede revocarse) y, desde E2-03, valida las entradas en el acceso (ver [Control de acceso con QR](#control-de-acceso-con-qr-us-f8-e2-03)).

## Validar OpenAPI (interno, vía contenedor)

OpenAPI y Swagger UI están expuestos en Catalog Service (puerto interno 8081), Inventory Service (8082), Ticket Service (8083), Purchase Service (8084), Queue Service (8086), Payment Mock (8085) y Audit Service (8086); no se publican por el Gateway. Valida desde dentro de cada contenedor:

```bash
docker compose exec -T catalog-service wget -qO- http://localhost:8081/v3/api-docs
docker compose exec -T inventory-service wget -qO- http://localhost:8082/v3/api-docs
docker compose exec -T ticket-service wget -qO- http://localhost:8083/v3/api-docs
docker compose exec -T purchase-service wget -qO- http://localhost:8084/v3/api-docs
docker compose exec -T queue-service wget -qO- http://localhost:8086/v3/api-docs
docker compose exec -T payment-mock wget -qO- http://localhost:8085/v3/api-docs
docker compose exec -T audit-service wget -qO- http://localhost:8086/v3/api-docs
```

Deben contener los paths de cada flujo. El contrato consolidado de la entrega está en `docs/openapi.yaml`; Ticket mantiene además `services/ticket-service/docs/ticket-openapi.yaml`.

## Verificación desde una copia limpia del repositorio

Desde una copia limpia del proyecto (sin rutas personales ni configuración del autor):

1. Clona o haz checkout del repositorio en una carpeta nueva.
2. Entra en `ticketya/`.
3. Confirma que `JAVA_HOME` apunta a JDK 19 o superior y que `mvn -v` reporta esa versión.
4. Ejecuta `mvn verify` desde la raíz de `ticketya/`.
5. Ejecuta `docker compose -p ticketya_e1_verification up --build -d --wait`. Ese nombre crea volúmenes separados de los de otros proyectos Compose.
6. Comprueba que `docker compose -p ticketya_e1_verification ps` muestre los 15 servicios saludables.
7. Ejecuta la colección con `npx --yes newman run postman/ticketya.postman_collection.json --env-var baseUrl=http://localhost:8080`.
8. Valida OpenAPI con `docker compose -p ticketya_e1_verification exec -T catalog-service wget -qO- http://localhost:8081/v3/api-docs` y el mismo comando para Inventory, Ticket, Purchase, Queue, Payment Mock y Audit en sus puertos internos respectivos.
9. Ejecuta `docker compose -p ticketya_e1_verification down` al terminar; los volúmenes quedan conservados.

Ningún paso requiere rutas absolutas de la máquina del autor ni secretos versionados.

## Estructura del proyecto

```
ticketya/
+-- pom.xml
+-- docker-compose.yml
+-- .env
+-- README.md
+-- postman/
|   +-- ticketya.postman_collection.json
+-- docs/
|   +-- openapi.yaml
|   +-- architecture.md
|   +-- verification.md
|   +-- Documentacion-de-diseño.md
+-- services/
|   +-- api-gateway/
|   |   +-- pom.xml, Dockerfile, src/
|   +-- catalog-service/
|   |   +-- pom.xml, Dockerfile, src/
|   +-- inventory-service/
|   |   +-- pom.xml, Dockerfile, src/
|   +-- ticket-service/
|   |   +-- pom.xml, Dockerfile, docs/, src/
|   +-- purchase-service/
|   |   +-- pom.xml, Dockerfile, src/
|   +-- queue-service/          (E2-02: fila virtual de alta demanda)
|   |   +-- pom.xml, Dockerfile, src/
|   +-- payment-mock/
|   |   +-- pom.xml, Dockerfile, src/
|   +-- audit-service/          (E2-02: bitácora centralizada)
|       +-- pom.xml, Dockerfile, src/
+-- openspec/
+-- OpenSpec.md
```

## Reset de la base de datos

Para partir desde bases PostgreSQL vacías en un proyecto de verificación aislado (sin tocar otros proyectos):

```bash
docker compose -p ticketya_e1_verification down -v
docker compose -p ticketya_e1_verification up --build -d --wait
```

Esto elimina sólo los siete volúmenes PostgreSQL del proyecto `ticketya_e1_verification` y los recrea vacíos. Los esquemas se inicializan automáticamente con el `schema.sql` de cada servicio al arrancar Catalog, Inventory, Ticket, Purchase, Queue, Payment Mock y Audit. Los cambios de esquema de E2-03 (`used_at`, `used_by`, `used_gate`, `access_scans`) usan `IF NOT EXISTS`, por lo que también se aplican sobre volúmenes existentes sin necesidad de reset.
