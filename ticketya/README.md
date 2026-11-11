# TicketYa - Plataforma de venta de entradas (Entrega 1)

## Solicitud de devolución (US-F7.1)

El titular de un ticket pagado puede pedir su devolución hasta antes de las 72 horas previas al evento. La solicitud y la consulta pasan por el Gateway; repetir el POST devuelve la misma solicitud.

```http
POST http://localhost:8080/api/refunds/tickets/{ticketId}
X-User-Id: 66bd0a43-1baa-4f7b-8316-930593b84d7c
```

```http
GET http://localhost:8080/api/refunds/{refundId}
X-User-Id: 66bd0a43-1baa-4f7b-8316-930593b84d7c
```

La respuesta inicial indica `PENDING`, decisión nula y progreso `NOT_STARTED`. Otro usuario recibe `404`.

## Decidir una devolución (US-F7.2)

Un administrador aprueba o rechaza una solicitud mediante el Gateway. El rol se simula con el encabezado `X-User-Role`; no hay autenticación en esta entrega.

```http
POST http://localhost:8080/api/refunds/{refundId}/decision
X-User-Role: ADMIN
Content-Type: application/json

{"decision":"APPROVED"}
```

Para rechazar, usa `{"decision":"REJECTED"}`. El rechazo conserva el ticket y su lugar vendido. Al aprobar, Purchase revoca el ticket, solicita el reintegro idempotente a Payment Mock y libera la unidad vendida en Inventory. Un `200` indica proceso completo; `202` indica que una dependencia falló y se puede repetir la misma decisión para continuar. Una decisión opuesta responde `409`.


## Cancelar un evento con reintegros (US-F7.3)

ADMIN inicia la cancelación por el Gateway. Purchase pide a Catalog que marque el evento `CANCELLED` y bloquee nuevas reservas en Inventory; luego procesa los tickets pagados por lotes. Cada ticket se revoca, se reintegra por su importe y libera su unidad vendida. La respuesta `202` indica que queda trabajo; repetir el mismo POST continúa el lote sin duplicar reintegros. `200` y `status: COMPLETED` indican que terminó. La consulta GET muestra el progreso sin procesar otro lote. El rol y la identidad son encabezados simulados para E1.

```http
POST http://localhost:8080/api/events/{eventId}/cancellation
X-User-Id: a1b2c3d4-1111-2222-3333-444455556666
X-User-Role: ADMIN
```

```http
GET http://localhost:8080/api/events/{eventId}/cancellation
X-User-Role: ADMIN
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

Cada solicitud requiere `X-User-Id` (UUID) y, para crear una orden, `Idempotency-Key` (1 a 120 caracteres). El encabezado opcional `X-User-Role: ADMIN` permite listar y consultar órdenes de cualquier cliente. El cuerpo **no incluye precios**: `eventId`, `items` y `payment`. Cada ítem informa exactamente uno de `seatIds` (sector numerado) o `quantity` (admisión general), usando los identificadores de Inventory que devuelve la consulta de disponibilidad. El límite de entradas por cliente y evento es seis y se cambia con `PURCHASE_TICKET_LIMIT`.

Ejemplo de compra:

```http
POST http://localhost:8080/api/orders
X-User-Id: 66bd0a43-1baa-4f7b-8316-930593b84d7c
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
| `400`, `403`, `404`, `409` | Solicitud inválida, identidad inválida, evento no comprable o conflicto, respectivamente |
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
| `POSTGRES_USER` | `ticketya` | Usuario de ambas bases PostgreSQL |
| `POSTGRES_PASSWORD` | `ticketya` | Contraseña de ambas bases PostgreSQL |
| `GATEWAY_PORT` | `8080` | Puerto publicado del API Gateway en el host |
| `PURCHASE_TICKET_LIMIT` | `6` | Máximo de entradas por cliente y evento |
| `TICKET_QR_SIGNING_SECRET` | Clave local definida en `.env` | Clave estable de al menos 32 bytes para firmar los QR |

Ticket Service requiere `TICKET_QR_SIGNING_SECRET`; el `.env` incluye una clave local de desarrollo para arrancar el stack directamente. Conserva el mismo valor entre reinicios si quieres validar QR ya emitidos. Al cambiarlo, los QR existentes dejarán de validarse. No reutilices esta clave fuera del entorno didáctico local.

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
| purchase-service | `PURCHASE_TICKET_LIMIT` | `6` |
| payment-mock | `SERVER_PORT` | `8085` |
| payment-mock | `DB_URL` | `jdbc:postgresql://payment-db:5432/ticketya_payment` |
| payment-mock | `DB_USER` | `ticketya` |
| payment-mock | `DB_PASSWORD` | `ticketya` |
| api-gateway | `SERVER_PORT` | `8080` |
| api-gateway | `CATALOG_SERVICE_URL` | `http://catalog-service:8081` |
| api-gateway | `INVENTORY_SERVICE_URL` | `http://inventory-service:8082` |
| api-gateway | `PURCHASE_SERVICE_URL` | `http://purchase-service:8084` |

Ninguna variable contiene rutas personales ni secretos de producción.

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
mvn verify -pl services/payment-mock -am
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
# Construir todas las imágenes (Gateway, Catalog, Inventory, Ticket, Purchase y Payment Mock)
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

El Gateway expone el único puerto publicado (`${GATEWAY_PORT:-8080}:8080`). Catalog, Inventory, Ticket, Purchase, Payment Mock y sus bases no publican puertos al host; se comunican por nombres de servicio de la red de Compose.

## Salud de los servicios

Cada servicio tiene una comprobación de estado basada en `wget` contra su endpoint de actuator:

- `postgres` (Catálogo), `inventory-db`, `ticket-db`, `purchase-db` y `payment-db`: `pg_isready`
- Catalog Service: `http://localhost:8081/actuator/health`
- Inventory Service: `http://localhost:8082/actuator/health`
- Ticket Service: `http://localhost:8083/actuator/health`
- Purchase Service: `http://localhost:8084/actuator/health`
- Payment Mock: `http://localhost:8085/actuator/health`
- API Gateway: `http://localhost:8080/actuator/health`

Espera a que todos informen `healthy` antes de ejecutar la colección Postman.

## Ejecutar la colección Postman (vía Gateway)

La colección está en `ticketya/postman/ticketya.postman_collection.json`. Ejecútala exclusivamente contra el Gateway (puerto 8080):

```bash
npx --yes newman run postman/ticketya.postman_collection.json \
  --env-var baseUrl=http://localhost:8080
```

La colección es repetible: cada corrida genera un `runId` único para evitar colisiones. Cubre Catalog e Inventory, compras aprobadas de admisión general y asiento numerado con QR, reintento idempotente, consulta de tickets por su titular, pago rechazado con liberación del cupo y límite de seis entradas por cliente y evento. También prueba solicitud y resolución de devoluciones, cancelación con reintegros, suspensión y reanudación del evento. Todas las rutas públicas se recorren por el Gateway. Inventory materializa el inventario de forma perezosa a partir del snapshot publicado de Catalog en la primera consulta.

Para comprobar muchas compras completas simultáneas del mismo asiento con compradores y claves distintas, sobre un stack saludable ejecuta `python postman/concurrent-purchases.py`. La prueba crea sus propios datos, espera una compra confirmada y once conflictos, y comprueba que Inventory registra un solo asiento vendido.

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

El contrato versionado está en `services/ticket-service/docs/ticket-openapi.yaml`. Ticket permite revocar internamente una entrada vendida; la validación en acceso queda para US-F8.

## Validar OpenAPI (interno, vía contenedor)

OpenAPI y Swagger UI están expuestos en Catalog Service (puerto interno 8081), Inventory Service (8082), Ticket Service (8083), Purchase Service (8084) y Payment Mock (8085); no se publican por el Gateway. Valida desde dentro de cada contenedor:

```bash
docker compose exec -T catalog-service wget -qO- http://localhost:8081/v3/api-docs
docker compose exec -T inventory-service wget -qO- http://localhost:8082/v3/api-docs
docker compose exec -T ticket-service wget -qO- http://localhost:8083/v3/api-docs
docker compose exec -T purchase-service wget -qO- http://localhost:8084/v3/api-docs
docker compose exec -T payment-mock wget -qO- http://localhost:8085/v3/api-docs
```

Deben contener los paths de cada flujo. El contrato consolidado de la entrega está en `docs/openapi.yaml`; Ticket mantiene además `services/ticket-service/docs/ticket-openapi.yaml`.

## Verificación desde una copia limpia del repositorio

Desde una copia limpia del proyecto (sin rutas personales ni configuración del autor):

1. Clona o haz checkout del repositorio en una carpeta nueva.
2. Entra en `ticketya/`.
3. Confirma que `JAVA_HOME` apunta a JDK 19 o superior y que `mvn -v` reporta esa versión.
4. Ejecuta `mvn verify` desde la raíz de `ticketya/`.
5. Ejecuta `docker compose -p ticketya_e1_verification up --build -d --wait`. Ese nombre crea volúmenes separados de los de otros proyectos Compose.
6. Comprueba que `docker compose -p ticketya_e1_verification ps` muestre los 11 servicios saludables.
7. Ejecuta la colección con `npx --yes newman run postman/ticketya.postman_collection.json --env-var baseUrl=http://localhost:8080`.
8. Valida OpenAPI con `docker compose -p ticketya_e1_verification exec -T catalog-service wget -qO- http://localhost:8081/v3/api-docs` y el mismo comando para Inventory, Ticket, Purchase y Payment Mock en sus puertos internos respectivos.
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
|   +-- payment-mock/
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

Esto elimina sólo los cinco volúmenes PostgreSQL del proyecto `ticketya_e1_verification` y los recrea vacíos. Los esquemas se inicializan automáticamente con el `schema.sql` de cada servicio al arrancar Catalog, Inventory, Ticket, Purchase y Payment Mock.
