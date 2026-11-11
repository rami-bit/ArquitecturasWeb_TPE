# TicketYa - Catálogo de Eventos e Inventario (Entrega 1)

## Compra local (Purchase + Payment Mock)

El flujo de compra se expone en `POST /api/purchases` y `GET /api/purchases/{orderId}` a través del Gateway. Cada compra requiere `X-User-Id` y `Idempotency-Key`; el body incluye `eventId`, `items`, `paymentMethod` y el control local opcional `paymentOutcome` (`APPROVED` por defecto o `REJECTED`). El límite por cliente/evento es cuatro entradas por defecto y se cambia con `PURCHASE_TICKET_LIMIT`.

Ejemplo de compra:

```http
POST http://localhost:8080/api/purchases
X-User-Id: 66bd0a43-1baa-4f7b-8316-930593b84d7c
Idempotency-Key: demo-purchase-001
Content-Type: application/json

{
  "eventId": "e88d7b2d-9019-4c3c-8bc4-e0ecc43c955b",
  "paymentMethod": "CARD",
  "paymentOutcome": "APPROVED",
  "items": [{"sectorId":"a88d7b2d-9019-4c3c-8bc4-e0ecc43c955b","quantity":2}]
}
```

Para demostrar un rechazo, repetir el ejemplo con una clave nueva y `paymentOutcome: "REJECTED"`. La respuesta contiene el estado de la orden; una orden recuperable se responde como `202 Accepted` y se puede volver a procesar repitiendo el POST con la misma clave. `GET /api/purchases/{orderId}` devuelve el detalle y los QR emitidos al titular. Payment Mock y los comandos `/internal/**` sólo son accesibles dentro de Compose.

Incremento actual de TicketYa: administración de lugares, borradores de eventos, publicación con snapshot inmutable, descubrimiento público, inventario de disponibilidad y reservas temporales. Ticket Service también está disponible para emitir entradas QR mediante un contrato interno; la compra que lo invoca todavía no está implementada.

El Catálogo es dueño del snapshot publicado y de los precios. Inventory es dueño de la disponibilidad y de las reservas: materializa el layout desde el snapshot de Catalog (sin copiar precios) y expone `GET /api/inventory/events/{eventId}/availability`. Los comandos de reserva (`/internal/**`) son internos y no se publican por el Gateway; los consumirá el futuro Purchase Service.

## Requisitos

- **JDK 19** (Amazon Corretto 19 recomendado). `JAVA_HOME` debe apuntar a una instalación de Java 19.
- **Maven 3.9+** en el `PATH`.
- **Docker** con **Docker Compose v2**.

Todos los comandos de esta guía se ejecutan desde la carpeta `ticketya/` (raíz de este proyecto OpenSpec), no desde la raíz del repositorio `ArquitecturasWeb_TPE`.

## Variables de entorno

El `docker-compose.yml` de la raíz de `ticketya/` usa los siguientes valores con predeterminados seguros. Sobreescríbelos solo si necesitas cambiar puertos o credenciales de desarrollo.

| Variable | Predeterminado | Propósito |
|---|---|---|
| `POSTGRES_DB` | `ticketya_catalog` | Nombre de la base de datos de catálogo |
| `INVENTORY_POSTGRES_DB` | `ticketya_inventory` | Nombre de la base de datos de inventario |
| `TICKET_POSTGRES_DB` | `ticketya_ticket` | Nombre de la base de datos de tickets |
| `POSTGRES_USER` | `ticketya` | Usuario de ambas bases PostgreSQL |
| `POSTGRES_PASSWORD` | `ticketya` | Contraseña de ambas bases PostgreSQL |
| `GATEWAY_PORT` | `8080` | Puerto publicado del API Gateway en el host |
| `TICKET_QR_SIGNING_SECRET` | Sin valor predeterminado | Clave estable de al menos 32 bytes para firmar los QR |

Ticket Service requiere `TICKET_QR_SIGNING_SECRET`; Compose falla explícitamente si falta. Para una sesión local de PowerShell, genera una clave de desarrollo antes de levantar el sistema:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:TICKET_QR_SIGNING_SECRET = [Convert]::ToBase64String($bytes)
$rng.Dispose()
docker compose up --build
```

La clave debe mantenerse igual entre reinicios e instancias. Si se cambia, los QR existentes ya no podrán validarse. No la guardes en el repositorio ni uses una clave de desarrollo en producción.

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
| api-gateway | `SERVER_PORT` | `8080` |
| api-gateway | `CATALOG_SERVICE_URL` | `http://catalog-service:8081` |
| api-gateway | `INVENTORY_SERVICE_URL` | `http://inventory-service:8082` |

Ninguna variable contiene rutas personales ni secretos de producción.

## Compilación y verificación con Maven

Desde la carpeta `ticketya/`:

```bash
# Compilación y verificación completa desde la raíz agregadora
mvn verify

# Compilación y verificación de un servicio individual
mvn verify -pl services/api-gateway
mvn verify -pl services/catalog-service
mvn verify -pl services/inventory-service
mvn verify -pl services/ticket-service
```

El proyecto usa Java 19 y Spring Boot 3.2.5. No se incluye Maven Wrapper (`mvnw`); se asume Maven 3.9+ instalado en el entorno.

Los tests de contrato de Inventory y de persistencia contra PostgreSQL usan Testcontainers: requieren Docker disponible. Si Docker no está disponible, esas clases se omiten automáticamente y el resto de los tests sigue ejecutándose.

La verificación del nuevo Ticket Service, incluidos los tests omitidos y los
pasos que siguen pendientes por falta de Docker, está registrada en
[`services/ticket-service/docs/ticket-verification.md`](services/ticket-service/docs/ticket-verification.md).

## Construcción de imágenes y Compose

Desde la carpeta `ticketya/`:

```bash
# Construir todas las imágenes (Gateway, Catalog, Inventory y Ticket Service)
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

El Gateway expone el único puerto publicado (`${GATEWAY_PORT:-8080}:8080`). Catalog Service, Inventory Service, Ticket Service y sus bases no publican puertos al host; se comunican por nombres de servicio de la red de Compose.

## Salud de los servicios

Cada servicio tiene una comprobación de estado basada en `wget` contra su endpoint de actuator:

- `postgres` (Catálogo) e `inventory-db` (Inventory): `pg_isready`
- `ticket-db`: `pg_isready`
- Catalog Service: `http://localhost:8081/actuator/health`
- Inventory Service: `http://localhost:8082/actuator/health`
- Ticket Service: `http://localhost:8083/actuator/health`
- API Gateway: `http://localhost:8080/actuator/health`

Espera a que todos informen `healthy` antes de ejecutar la colección Postman.

## Ejecutar la colección Postman (vía Gateway)

La colección está en `ticketya/postman/ticketya.postman_collection.json`. Ejecútala exclusivamente contra el Gateway (puerto 8080):

```bash
npx --yes newman run postman/ticketya.postman_collection.json \
  --env-var baseUrl=http://localhost:8080
```

La colección es repetible: cada corrida genera un `runId` único para nombre de lugar y participante, evitando colisiones entre ejecuciones. Cubre: crear lugar, crear/configurar borrador, publicar, buscar, consultar, suspender y reanudar, más la consulta pública de disponibilidad de Inventory a través del Gateway. Inventory materializa el inventario de forma perezosa a partir del snapshot publicado de Catalog en la primera consulta.

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

## Emisión interna de entradas QR

Ticket Service escucha en `ticket-service:8083`, dentro de la red de Compose. Purchase invocará este contrato **después** de confirmar pago e inventario:

| Método | Ruta | Propósito |
|---|---|---|
| `POST` | `/internal/orders/{orderId}/items/{orderItemId}/tickets` | Emitir o recuperar idempotentemente las entradas QR de un item confirmado |

La solicitud incluye al comprador, el snapshot del evento y las unidades del item; para asientos numerados cada unidad lleva fila y número. La primera emisión devuelve `201`; un reintento idéntico devuelve `200` con los mismos tickets y QR. Reutilizar la pareja `orderId + orderItemId` con distinto contenido devuelve `409`.

La respuesta contiene `qrPayload` y `qrPngBase64`. La imagen PNG se genera al responder; PostgreSQL conserva el hash del payload, no el token. La API es interna y no tiene ruta en el Gateway. **Como Purchase Service todavía no existe, la emisión se puede verificar por contrato, pero aún no hay flujo real de compra que la invoque.**

Valida la documentación y el estado del servicio desde el contenedor:

```powershell
docker compose exec -T ticket-service wget -qO- http://localhost:8083/v3/api-docs
docker compose exec -T ticket-service wget -qO- http://localhost:8083/actuator/health
```

El contrato versionado está en `services/ticket-service/docs/ticket-openapi.yaml`. Este alcance emite QR; validación en acceso, revocación y devoluciones siguen pendientes.

## Validar OpenAPI (interno, vía contenedor)

OpenAPI y Swagger UI están expuestos en Catalog Service (puerto interno 8081), Inventory Service (puerto interno 8082) y Ticket Service (puerto interno 8083); no se publican por el Gateway. Valida desde dentro de cada contenedor:

```bash
docker compose exec -T catalog-service wget -qO- http://localhost:8081/v3/api-docs
docker compose exec -T inventory-service wget -qO- http://localhost:8082/v3/api-docs
docker compose exec -T ticket-service wget -qO- http://localhost:8083/v3/api-docs
```

Deben contener los paths de cada flujo. Los contratos versionados de Inventory y Ticket están en `docs/openapi.yaml` y `services/ticket-service/docs/ticket-openapi.yaml`.

## Verificación desde una copia limpia del repositorio

Desde una copia limpia del proyecto (sin rutas personales ni configuración del autor):

1. Clona o haz checkout del repositorio en una carpeta nueva.
2. Entra en `ticketya/`.
3. Confirma que `JAVA_HOME` apunta a JDK 19 y que `mvn -v` reporta Java 19.
4. Ejecuta `mvn verify` desde la raíz de `ticketya/`.
5. Ejecuta `docker compose build`.
6. Ejecuta `docker compose down -v && docker compose up -d --wait`.
7. Ejecuta la colección Postman con `npx newman`.
8. Valida OpenAPI con `docker compose exec -T catalog-service wget -qO- http://localhost:8081/v3/api-docs`, `docker compose exec -T inventory-service wget -qO- http://localhost:8082/v3/api-docs` y `docker compose exec -T ticket-service wget -qO- http://localhost:8083/v3/api-docs`.
9. Ejecuta `docker compose down` al terminar.

Ningún paso requiere rutas absolutas de la máquina del autor ni secretos versionados.

## Estructura del proyecto

```
ticketya/
+-- pom.xml
+-- docker-compose.yml
+-- README.md
+-- postman/
|   +-- ticketya.postman_collection.json
+-- docs/
|   +-- verification.md
+-- services/
|   +-- api-gateway/
|   |   +-- pom.xml, Dockerfile, src/
|   +-- catalog-service/
|   |   +-- pom.xml, Dockerfile, src/
|   +-- inventory-service/
|   |   +-- pom.xml, Dockerfile, src/
|   +-- ticket-service/
|       +-- pom.xml, Dockerfile, docs/, src/
+-- openspec/
+-- OpenSpec.md
```

## Reset de la base de datos

Para partir desde bases PostgreSQL vacías:

```bash
docker compose down -v
docker compose up -d
```

Esto elimina los volúmenes `catalog-postgres-data`, `inventory-postgres-data` y `ticket-postgres-data` y los recrea vacíos. Los esquemas se inicializan automáticamente con el `schema.sql` de cada servicio al arrancar Catalog, Inventory y Ticket Service.
