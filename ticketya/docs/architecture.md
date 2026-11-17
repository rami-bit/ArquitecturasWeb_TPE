# Arquitectura y diseño — TicketYa

Documento de arquitectura de la Entrega 1 y de las épicas de Entrega 2
E2-01 (JWT y roles), E2-02 (trazabilidad y bitácora centralizada), E2-03
(control de acceso QR) y E2-04 (reportes del organizador). Resume los microservicios, sus
responsabilidades y su API, la comunicación entre ellos, el mecanismo contra la
doble venta y los modelos de datos. El contrato HTTP versionado está en
[`docs/openapi.yaml`](openapi.yaml) y las decisiones de detalle en
[`openspec/changes/archive/2026-11-15-establish-purchase-and-ticketing/design.md`](../openspec/changes/archive/2026-11-15-establish-purchase-and-ticketing/design.md).

## 1. Microservicios y responsabilidades

| Servicio | Puerto | Base de datos | API pública (vía Gateway) | API interna |
|---|---|---|---|---|
| `api-gateway` | 8080 | — | Punto de entrada único. Valida el JWT (Bearer), exige rol en `/api/access/**`, `/api/reports/**` y `/api/audit/**`, registra cada solicitud y enruta `/api/inventory/**`, `/api/orders/**`, `/api/refunds/**`, `/api/events/*/cancellation`, `/api/reports/**` (Purchase), `/api/access/**` (Ticket) y `/api/audit/**` (Audit); el resto de `/api/**` va a Catalog. | — |
| `catalog-service` | 8081 | `ticketya_catalog` | Eventos: `GET /api/events`, `GET /api/events/{id}`; venues: `POST/GET /api/venues`, `PUT /api/venues/{id}`; gestión: `POST/GET /api/management/events`, `GET/PUT /api/management/events/{id}`, `PUT /api/management/events/{id}/offerings`, `POST /api/management/events/{id}/publish`, `/api/management/events/{id}/suspend`, `/api/management/events/{id}/cancel`, `/api/management/events/{id}/resume`. | `GET /internal/events/{eventId}/snapshot`, `GET /internal/events/{eventId}/purchase-snapshot` |
| `inventory-service` | 8082 | `ticketya_inventory` | `GET /api/inventory/events/{eventId}/availability` | `POST/GET /internal/reservations/**`, confirmar/liberar reserva, `POST /internal/reservations/{reservationId}/sold-units/release`, `PUT /internal/events/{eventId}/hold-ttl` y `PUT /internal/events/{eventId}/cancellation` |
| `ticket-service` | 8083 | `ticketya_ticket` | Control de acceso (E2-03): `POST /api/access/events/{eventId}/validations`, `GET /api/access/events/{eventId}/scans`. Las entradas del comprador se leen desde la orden. | `POST /internal/orders/{orderId}/items/{orderItemId}/tickets` (emisión), `POST /internal/tickets/{ticketId}/revoke` |
| `purchase-service` | 8084 | `ticketya_purchase` | `POST/GET /api/orders`, `GET /api/orders/{orderId}`, `POST /api/orders/{orderId}/reconcile`; `POST /api/refunds/tickets/{ticketId}`, `GET /api/refunds/{refundId}`, `POST /api/refunds/{refundId}/decision`; `POST/GET /api/events/{eventId}/cancellation`; reportes (E2-04): `GET /api/reports/events/{eventId}` | — |
| `payment-mock` | 8085 | `ticketya_payment` | — | `POST /internal/payments`, `GET /internal/payments/{paymentId}`, `GET /internal/payments/by-order/{orderId}`, `POST /internal/payments/{paymentId}/refund` (total), `POST /internal/payments/{paymentId}/refunds` (parcial) |
| `audit-service` (E2-02) | 8086 | `ticketya_audit` | `GET /api/audit/events`, `GET /api/audit/traces/{correlationId}` (sólo ADMIN) | `POST /internal/audit-events` (ingesta idempotente) |

Las rutas de cancelación tienen propósitos distintos. Catalog expone
`POST /api/management/events/{id}/cancel`: cambia el estado a `CANCELLED` y
bloquea nuevas ventas en Inventory. Purchase expone
`POST /api/events/{eventId}/cancellation`: procesa los reintegros de entradas
ya vendidas y llama a la ruta de Catalog.

Sólo el Gateway publica un puerto al host (`8080`). Las rutas `/internal/**` no
tienen ruta en el Gateway: cualquier llamada externa a un comando interno
responde `404`.

El Gateway es además el **Resource Server** de TicketYa: valida el JWT firmado
por un proveedor de identidad externo (`JWT_ISSUER_URI` y `JWT_JWK_SET_URI` por
entorno), exige `Authorization: Bearer` en las rutas protegidas y propaga a los
servicios la identidad derivada de los claims (`sub` como UUID y un único rol
de `CLIENT`, `ORGANIZER`, `ADMIN` o `ACCESS_STAFF`). Los encabezados de
identidad que envíe el cliente se descartan y el token no se reenvía. TicketYa
no emite tokens ni mantiene usuarios; la autorización de dominio (rol y
ownership) sigue en Catalog y Purchase.

Responsabilidades:

- **Catalog** es dueño del catálogo publicado y de los precios. Publica una
  copia inmutable (`event_snapshots`) que es la que consumen el resto de los
  servicios, de modo que editar el lugar no altera un evento ya publicado.
- El modelo `EventStatus` incluye `DRAFT`, `PUBLISHED`, `SUSPENDED`, `SOLD_OUT`,
  `CANCELLED` y `FINISHED`. E1 implementa las transiciones editoriales actuales;
  `SOLD_OUT` y `FINISHED` están modelados, pero sus transiciones están reservadas
  para flujos posteriores.
- **Inventory** es dueño de la disponibilidad: materializa sectores y asientos
  desde el snapshot de Catalog **sin copiar precios** y administra reservas con
  TTL.
- **Ticket** es dueño de las entradas emitidas y del hash del QR; la emisión es
  idempotente por orden e ítem y Purchase puede revocarlas ante una devolución.
  Desde E2-03 también valida el QR en el acceso: el primer escaneo válido marca
  la entrada `USED` y registra cada intento en `access_scans`.
- **Purchase** es el orquestador de la compra: dueño de la orden, del snapshot
  de precios, del límite por cliente y evento, y del progreso de devoluciones y
  cancelaciones de eventos.
- **Payment Mock** simula la pasarela: dueño del pago y del reintegro, sin datos
  del comprador ni referencias a `purchase_db`; registra reintegros parciales.
- **Purchase** arma además el reporte del organizador (E2-04) con sus propias
  ventas y devoluciones, la autorización y precios de Catalog y la capacidad de
  Inventory.
- **Audit** (E2-02) es dueño de la bitácora centralizada: recibe eventos de
  Catalog, Ticket y Purchase, los guarda append-only y permite seguir una
  operación por su `X-Correlation-Id`.

## 2. Diagrama de módulos

```mermaid
flowchart LR
    Client[Cliente / Postman] -->|HTTP :8080| GW[api-gateway]

    GW -->|/api/** excepto rutas dedicadas| CAT[catalog-service :8081]
    GW -->|/api/inventory| INV[inventory-service :8082]
    GW -->|/api/orders, /api/refunds, cancelación, /api/reports| PUR[purchase-service :8084]
    GW -->|/api/access| TKT
    GW -->|/api/audit| AUD[audit-service :8086]

    PUR -->|snapshot / fecha / cancelación lógica (F7)| CAT
    CAT -->|bloquear ventas de evento cancelado| INV
    PUR -->|reservar / confirmar / liberar unidad vendida (F7)| INV
    PUR -->|cobrar / reintegrar total o parcialmente (F7)| PAY[payment-mock :8085]
    PUR -->|emitir / revocar tickets (F7)| TKT[ticket-service :8083]
    PUR -->|reporte: autorización y precios (F12)| CAT
    PUR -->|reporte: capacidad (F12)| INV
    CAT -.->|eventos de auditoría async| AUD
    TKT -.->|eventos de auditoría async| AUD
    PUR -.->|eventos de auditoría async| AUD

    CAT --- DB1[(ticketya_catalog)]
    INV --- DB2[(ticketya_inventory)]
    TKT --- DB3[(ticketya_ticket)]
    PUR --- DB4[(ticketya_purchase)]
    PAY --- DB5[(ticketya_payment)]
    AUD --- DB6[(ticketya_audit)]
```

Las flechas punteadas son envíos asíncronos *fire-and-forget* (E2-02): la
operación de negocio nunca espera a Audit Service.

Cada servicio es un proyecto Spring Boot independiente, con su propio `pom.xml`,
su `Dockerfile`, su esquema y su proceso. El POM raíz sólo agrega los módulos
para construir todo junto.

## 3. Comunicación

- **REST síncrono** sobre HTTP/JSON para el negocio; no hay broker ni bus de
  eventos.
- **Asíncrono fire-and-forget** sólo para la bitácora (E2-02): cada productor
  encola el evento en un pool acotado y lo envía a `audit-service` con timeouts
  cortos (1 s / 2 s) y hasta 3 intentos. Si Audit no responde, el evento queda
  en el log local del productor y la operación sigue sin error.
- Todas las llamadas propagan `X-Correlation-Id`; si falta, el servicio que
  recibe la solicitud genera un UUID y siempre lo devuelve en el header de
  respuesta, incluidas las respuestas de error.
- Los errores usan `application/problem+json` (`type`, `title`, `status`,
  `detail`, `instance` y `correlationId`).
- Purchase aplica timeouts por dependencia y persiste el estado de la saga
  antes de cada paso, de modo que un reintento con la misma `Idempotency-Key`
  recupera la orden en lugar de volver a cobrar o reservar.
- Los comandos que modifican estado son idempotentes por clave de negocio:
  reserva por `orderId`, pago por `orderId`, emisión por `(orderId, orderItemId)`
  y orden por `(customerId, idempotencyKey)`.

## 4. Secuencia de compra de una entrada numerada

Incluye pago rechazado, pago incierto y vencimiento de la reserva.

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant G as Gateway
    participant P as Purchase
    participant Cat as Catalog
    participant I as Inventory
    participant Pay as Payment
    participant T as Ticket

    C->>G: POST /api/orders (Authorization: Bearer, Idempotency-Key)
    G->>G: validar JWT (firma, exp, sub UUID, un rol)
    G->>P: crear orden (identidad derivada del sub)
    P->>P: recuperar o crear orden idempotente
    P->>Cat: GET /internal/events/{id}/purchase-snapshot
    Cat-->>P: evento, sectores, asientos y precios
    P->>P: validar ítems y límite atómico cliente/evento
    P->>I: POST /internal/reservations (orderId, items, ttl)
    alt sin disponibilidad
        I-->>P: 409
        P-->>G: 409 problem+json
        G-->>C: lugares no disponibles
    else reserva creada
        I-->>P: reservationId + expiresAt + datos de origen
        P->>Pay: POST /internal/payments (monto, token)
        alt pago rechazado
            Pay-->>P: REJECTED
            P->>I: POST /internal/reservations/{id}/release
            P->>P: liberar límite y orden REJECTED
            P-->>G: 402 problem+json
        else pago incierto (PENDING o timeout)
            Pay-->>P: PENDING
            P->>P: orden PAYMENT_PENDING
            P-->>G: 202
            Note over I: la reserva sigue activa hasta expiresAt
        else pago aprobado
            Pay-->>P: APPROVED
            P->>I: POST /internal/reservations/{id}/confirm
            alt reserva vencida o confirmación rechazada
                I-->>P: 409
                P->>Pay: POST /internal/payments/{paymentId}/refund
                P->>I: liberar si sigue activa
                P->>P: orden COMPENSATED
                P-->>G: 409 problem+json
            else inventario confirmado
                I-->>P: CONFIRMED
                P->>T: POST /internal/orders/{id}/items/{item}/tickets
                alt emisión fallida
                    T-->>P: error transitorio
                    P->>P: orden PAID_PENDING_TICKETS
                    P-->>G: 202
                else tickets emitidos
                    T-->>P: tickets con QR
                    P->>P: orden CONFIRMED
                    P-->>G: 201
                    G-->>C: entradas
                end
            end
        end
    end

    Note over I: si expiresAt vence sin confirmación, Inventory<br/>libera la reserva y sus asientos
    C->>G: POST /api/orders/{id}/reconcile
    G->>P: reintentar saga
    P-->>C: 200 con el estado resultante (o 409 si es terminal)
```

Estados persistidos de la orden: `CONFIRMED`, `REJECTED`, `PAYMENT_PENDING`,
`PAID_PENDING_TICKETS` y `COMPENSATED`. Los estados terminales son `CONFIRMED`,
`REJECTED` y `COMPENSATED`; reconciliar una orden terminal responde `409`.

### Devolución individual y cancelación de evento (F7)

Una devolución individual se solicita en `POST /api/refunds/tickets/{ticketId}` y
un ADMIN la aprueba o rechaza en `POST /api/refunds/{refundId}/decision`. La
cancelación masiva comienza en Purchase; Purchase solicita a Catalog la
cancelación lógica del evento y Catalog bloquea el inventario. Por cada ticket
pagado, Purchase persiste y ejecuta los mismos pasos de revocación, reintegro
parcial y liberación de unidad vendida. Cada invocación procesa un lote de forma
síncrona y persiste el avance para reintentar; no agrega broker ni servicio.

```mermaid
sequenceDiagram
    actor A as ADMIN
    participant G as Gateway
    participant P as Purchase
    participant C as Catalog
    participant I as Inventory
    participant T as Ticket
    participant Pay as Payment

    A->>G: POST /api/events/{eventId}/cancellation
    G->>P: iniciar/procesar cancelación
    P->>C: POST /api/management/events/{id}/cancel
    C->>I: PUT /internal/events/{eventId}/cancellation
    I-->>C: 204 inventario bloqueado
    C-->>P: evento CANCELLED
    loop por lotes de tickets pagados
        P->>T: POST /internal/tickets/{ticketId}/revoke
        P->>Pay: POST /internal/payments/{paymentId}/refunds (monto, idempotencyKey)
        P->>I: POST /internal/reservations/{reservationId}/sold-units/release
    end
    P-->>G: avance de reintegros
    G-->>A: estado PROCESSING o COMPLETED
```

La misma secuencia de revocación, reintegro y liberación se usa tras aprobar una
devolución individual. E1 emite y revoca el QR; no implementa su validación en el
acceso.

## 5. Doble venta

El mecanismo no depende de una única instancia del servicio:

1. **Un asiento se vende una sola vez.** `inventory_items` tiene una restricción
   única por asiento de origen (`event_inventory_id, source_seat_id`) y la
   retención es un `UPDATE` condicional
   (`SET status='HELD', reservation_id=?, expires_at=? WHERE id=? AND sector_id=? AND status='AVAILABLE'`).
   Si dos instancias compiten, sólo una actualiza la fila; la otra obtiene `0`
   filas y responde `409`.
2. **La capacidad de admisión general se descuenta de forma atómica** con
   `UPDATE inventory_sectors SET reserved = reserved + ? WHERE id=? AND sold + reserved + ? <= capacity`.
3. **Una orden sólo se confirma si Inventory confirma la reserva**, y la
   confirmación vuelve a validar el estado y el vencimiento de la reserva.
4. **La reserva tiene TTL**: al vencer, Inventory la marca `EXPIRED` y libera
   asientos y cupos. Un pago aprobado sobre una reserva vencida se compensa con
   reintegro.
5. **La idempotencia cierra la ventana de reintentos**: la misma orden reutiliza
   la misma reserva y el mismo pago.

Las pruebas de `InventoryConcurrencyContractTest` ejercitan compras simultáneas
del mismo asiento y el último cupo de una zona, verificando exactamente una
confirmación.

## 6. Modelo de datos por microservicio

Cada servicio tiene su propia base; las referencias entre servicios son valores
UUID sin claves foráneas.

### Catalog

```mermaid
erDiagram
    venues ||--o{ venue_sectors : has
    venue_sectors ||--o{ venue_seats : has
    venues ||--o{ events : hosts
    events ||--o{ event_participants : includes
    events ||--o{ event_sector_offerings : offers
    events ||--|| event_snapshots : publishes
    event_snapshots ||--o{ snapshot_participants : copies
    event_snapshots ||--o{ snapshot_sectors : copies
    snapshot_sectors ||--o{ snapshot_seats : copies

    venues {
        uuid id PK
        varchar name
        varchar address
        varchar city
        numeric latitude
        numeric longitude
    }
    venue_sectors {
        uuid id PK
        uuid venue_id FK
        varchar name
        varchar sector_type
        int general_capacity
    }
    venue_seats {
        uuid id PK
        uuid venue_sector_id FK
        varchar row_label
        int seat_number
    }
    events {
        uuid id PK
        uuid organizer_id
        uuid venue_id FK
        varchar name
        varchar event_type
        timestamptz starts_at
        varchar status
    }
    event_participants {
        uuid id PK
        uuid event_id FK
        varchar name
    }
    event_sector_offerings {
        uuid id PK
        uuid event_id FK
        uuid venue_sector_id FK
        numeric price
    }
    event_snapshots {
        uuid id PK
        uuid event_id FK
        varchar name
        varchar venue_name
        varchar venue_city
        timestamptz starts_at
    }
    snapshot_sectors {
        uuid id PK
        uuid snapshot_id FK
        uuid source_sector_id
        varchar name
        varchar sector_type
        int capacity
        numeric price
    }
    snapshot_seats {
        uuid id PK
        uuid sector_id FK
        uuid source_seat_id
        varchar row_label
        int seat_number
    }
```

### Inventory

```mermaid
erDiagram
    event_inventories ||--o{ inventory_sectors : materializes
    event_inventories ||--o{ inventory_items : contains
    event_inventories ||--o{ reservations : receives
    inventory_sectors ||--o{ inventory_items : groups
    inventory_sectors ||--o{ reservation_items : holds
    reservations ||--o{ reservation_items : contains
    inventory_items ||--o{ reservation_items : reserved_by
    reservation_items ||--o{ sold_unit_releases : records

    event_inventories {
        uuid id PK
        uuid event_id
        uuid snapshot_id
        int hold_ttl_seconds
        timestamptz initialized_at
        timestamptz cancelled_at
    }
    inventory_sectors {
        uuid id PK
        uuid event_inventory_id FK
        uuid source_sector_id
        varchar name
        varchar type
        int capacity
        int sold
        int reserved
    }
    reservations {
        uuid id PK
        uuid order_id
        uuid event_inventory_id FK
        varchar status
        varchar request_fingerprint
        timestamptz expires_at
        timestamptz created_at
        timestamptz confirmed_at
        timestamptz released_at
        timestamptz expired_at
    }
    inventory_items {
        uuid id PK
        uuid event_inventory_id FK
        uuid sector_id FK
        uuid source_seat_id
        varchar row_label
        int seat_number
        varchar status
        uuid reservation_id FK
        timestamptz expires_at
        timestamptz sold_at
    }
    reservation_items {
        uuid id PK
        uuid reservation_id FK
        uuid sector_id FK
        uuid inventory_item_id FK
        int quantity
    }
    event_cancellations {
        uuid event_id PK
        timestamptz cancelled_at
    }
    sold_unit_releases {
        uuid reservation_item_id PK, FK
        int unit_number PK
        timestamptz released_at
    }
```

Restricciones clave: `UNIQUE (event_inventory_id, source_seat_id)` en
`inventory_items`, `UNIQUE (event_id)` en `event_inventories`,
`UNIQUE (order_id)` en `reservations`, y
`CHECK (sold + reserved <= capacity)` en `inventory_sectors`. `event_cancellations`
usa `event_id` como UUID sin FK; `sold_unit_releases` referencia localmente
`reservation_items(id)` y tiene clave primaria `(reservation_item_id, unit_number)`.

### Ticket

```mermaid
erDiagram
    ticket_issuances ||--o{ tickets : issues

    ticket_issuances {
        uuid id PK
        uuid order_id
        uuid order_item_id
        varchar request_fingerprint
        timestamptz created_at
    }
    tickets {
        uuid id PK
        uuid issuance_id FK
        int unit_number
        uuid customer_id
        uuid event_id
        varchar event_name
        timestamptz event_starts_at
        varchar venue_name
        varchar venue_city
        varchar sector_name
        varchar row_label
        int seat_number
        varchar qr_hash
        varchar status
        timestamptz issued_at
        timestamptz used_at
        uuid used_by
        varchar used_gate
    }
    tickets ||--o{ access_scans : "intentos de ingreso"
    access_scans {
        uuid id PK
        uuid event_id
        uuid ticket_id
        uuid staff_id
        varchar gate
        varchar result
        varchar correlation_id
        timestamptz scanned_at
    }
```

E2-03 agrega `used_at`, `used_by` y `used_gate` (con `ALTER TABLE ... ADD
COLUMN IF NOT EXISTS`, compatible con volúmenes existentes) y la tabla
`access_scans`. `access_scans.ticket_id` no tiene FK porque también registra QR
falsificados (`UNKNOWN_TICKET`, ticket nulo); un índice único parcial
`(ticket_id) WHERE result = 'ADMITTED'` garantiza a lo sumo un ingreso por
entrada.

`UNIQUE (order_id, order_item_id)` hace idempotente la emisión y
`UNIQUE (qr_hash)` impide duplicar una entrada. La base guarda el hash del
payload, no el token del QR. `order_id`, `order_item_id`, `customer_id` y
`event_id` son referencias UUID sin FK a otras bases; el único vínculo relacional
local es `tickets.issuance_id`.

### Purchase

```mermaid
erDiagram
    purchase_orders ||--o{ purchase_order_items : contains
    purchase_order_items ||--o{ order_item_units : describes
    purchase_orders ||--o{ order_tickets : issues
    purchase_order_items ||--o{ order_tickets : groups
    purchase_orders ||--o{ refund_requests : receives
    purchase_order_items ||--o{ refund_requests : identifies

    purchase_orders {
        uuid id PK
        uuid customer_id
        uuid event_id
        varchar event_name
        timestamptz event_starts_at
        varchar venue_name
        varchar venue_city
        varchar currency
        numeric total_amount
        varchar status
        uuid payment_id
        varchar payment_status
        varchar payment_method
        timestamptz payment_created_at
        timestamptz payment_updated_at
        varchar idempotency_key
        varchar request_fingerprint
        uuid reservation_id
        timestamptz reservation_expires_at
        timestamptz created_at
        timestamptz updated_at
    }
    purchase_order_items {
        uuid id PK
        uuid order_id FK
        uuid inventory_sector_id
        uuid source_sector_id
        varchar sector_name
        varchar sector_type
        int quantity
        numeric unit_price
        numeric subtotal
    }
    order_item_units {
        uuid id PK
        uuid order_item_id FK
        int unit_number
        uuid inventory_seat_id
        uuid source_seat_id
        varchar row_label
        int seat_number
    }
    order_tickets {
        uuid id PK
        uuid order_id FK
        uuid order_item_id FK
        uuid ticket_id
        int unit_number
        varchar status
        text qr_payload
        text qr_png_base64
        varchar row_label
        int seat_number
    }
    customer_event_limits {
        uuid id PK
        uuid customer_id
        uuid event_id
        int committed_units
        int pending_units
    }
    refund_requests {
        uuid id PK
        uuid ticket_id
        uuid order_id FK
        uuid order_item_id FK
        uuid customer_id
        uuid event_id
        numeric amount
        varchar status
        varchar decision
        varchar progress
        varchar source
        timestamptz created_at
    }
    event_cancellations {
        uuid event_id PK
        uuid last_ticket_id
        timestamptz created_at
    }
```

Tablas y columnas de `schema.sql`: las FK de `purchase_order_items`,
`order_item_units`, `order_tickets` y `refund_requests` sólo apuntan a tablas
Purchase. `customer_event_limits` no tiene FK a órdenes; su índice único es
`(customer_id, event_id)`. Restricciones adicionales: único
`(customer_id, idempotency_key)` en `purchase_orders`, `(order_item_id,
unit_number)` en `order_item_units`, `ticket_id` y `(order_id, order_item_id,
unit_number)` en `order_tickets`, y `(ticket_id, source)` en `refund_requests`.
`refund_requests.amount` tiene `CHECK (amount > 0)`.
`ticket_id`, `last_ticket_id`, `event_id`, `payment_id`, `reservation_id` e
`inventory_*` son referencias UUID sin FK entre servicios. Purchase guarda el
**snapshot de precios** porque Inventory nunca los persiste.

### Payment

```mermaid
erDiagram
    payments ||--o{ refunds : records

    payments {
        uuid id PK
        uuid order_id
        varchar status
        numeric amount
        char currency
        varchar method
        varchar simulation_token
        timestamptz created_at
        timestamptz updated_at
        timestamptz refunded_at
    }
    refunds {
        uuid id PK
        uuid payment_id FK
        numeric amount
        varchar idempotency_key
        char request_fingerprint
        timestamptz created_at
    }
```

`payments.order_id` es único y no tiene FK a Purchase. Estados del pago:
`APPROVED`, `REJECTED`, `PENDING` y `REFUNDED`. Cada reintegro total o parcial
queda en `refunds`, con clave de idempotencia única y FK local a `payments`.
El token de simulación se persiste para detectar reintentos con contenido
distinto, pero nunca se expone en la respuesta.

### Audit (E2-02)

```mermaid
erDiagram
    audit_events {
        uuid id PK "eventId del productor"
        varchar correlation_id
        varchar service
        varchar operation
        varchar outcome
        varchar actor_id
        varchar actor_role
        varchar resource_type
        varchar resource_id
        jsonb details
        timestamptz occurred_at
        timestamptz received_at
    }
```

Tabla append-only. La clave primaria es el `eventId` que genera el productor,
por lo que un reintento del envío no duplica el registro (`ON CONFLICT DO
NOTHING`). `resource_id` y `actor_id` son referencias de texto a otras bases,
sin FK. Índices por `(correlation_id, occurred_at)`, `(operation,
occurred_at)`, `resource_id` y `occurred_at`.

## 7. Entidades solapadas entre bases

| Entidad | Dueño | Copias / referencias | Propósito y regla |
|---|---|---|---|
| `event` | Catalog (`events`, `event_snapshots`) | Inventory (`event_inventories.event_id`, `event_cancellations.event_id`), Purchase (`purchase_orders.event_id`, `refund_requests.event_id`, `event_cancellations.event_id`), Ticket (`tickets.event_id`) | Referencias UUID sin FK. Cada servicio conserva sólo los atributos que necesita. |
| `sector` | Catalog (`snapshot_sectors.source_sector_id`) | Inventory (`inventory_sectors.source_sector_id`), Purchase (`purchase_order_items.source_sector_id`) | Inventory materializa el sector con su propio ID y conserva `source_sector_id`; Purchase guarda ambos identificadores. Ningún servicio copia el precio salvo Purchase. |
| `seat` | Catalog (`snapshot_seats.source_seat_id`) | Inventory (`inventory_items.source_seat_id`), Purchase (`order_item_units.source_seat_id`) | Inventory es dueño de la disponibilidad y estado vendido/retenido; Catalog describe el layout. |
| `customer` | Identidad externa (proveedor de identidad externo; claim `sub` del JWT) | Purchase (`purchase_orders.customer_id`, `customer_event_limits.customer_id`, `refund_requests.customer_id`), Ticket (`tickets.customer_id`) | No hay servicio de identidad propio: el Gateway valida el JWT y propaga a los servicios el `sub` como UUID. |
| `price` | Catalog (`snapshot_sectors.price`) | Purchase (`purchase_order_items.unit_price`, `purchase_orders.total_amount`, `refund_requests.amount`) | Inventory nunca almacena precios. Purchase congela el precio del snapshot al crear la orden. |
| `payment` | Payment (`payments`, `refunds`) | Purchase (`purchase_orders.payment_id`, `purchase_orders.payment_status`) | Purchase guarda el identificador y estado del pago. `payments.order_id` y `refunds.payment_id` no crean FK entre bases. |
| `ticket` | Ticket (`tickets`, hash QR y estado) | Purchase (`order_tickets.ticket_id`, `qr_payload`, `qr_png_base64`; `refund_requests.ticket_id`) | Ticket es dueño del ticket y su revocación. Purchase conserva la copia del payload/PNG que devuelve al titular al consultar su orden; no hay FK hacia `ticket_db`. |
| `reservation` | Inventory (`reservations`) | Purchase (`purchase_orders.reservation_id`) | Referencia UUID entre bases; Purchase no consulta tablas de Inventory. |
| `correlation` / operación | Audit (`audit_events.correlation_id`) | Ticket (`access_scans.correlation_id`) y el log de cada servicio | El `X-Correlation-Id` es el identificador común de una operación; no es una entidad con dueño sino la clave para unir registros de varios servicios. |

## 8. Contrato y documentación

- Contrato consolidado: [`docs/openapi.yaml`](openapi.yaml) — 47 operaciones
  sobre 42 rutas, con ejemplos, errores `problem+json` y esquemas de Purchase,
  Payment, Catalog, Inventory, Ticket (acceso) y Audit.
- OpenAPI en runtime: `/v3/api-docs` en los puertos internos de Catalog (8081),
  Inventory (8082), Ticket (8083), Purchase (8084), Payment (8085) y Audit (8086).
- Colección de pruebas: `postman/ticketya.postman_collection.json`.
- Evidencia de ejecución: [`docs/verification.md`](verification.md).

## 9. Ejecución local

`docker compose up -d --build` levanta el Gateway en `http://localhost:8080`, los
seis servicios (Catalog, Inventory, Ticket, Purchase, Payment Mock y Audit) y
sus seis bases: 13 contenedores. Los puertos `8081`–`8086` no se publican al
host; el `docker-compose.yml` y `.env` documentan las variables de cada
servicio. El detalle paso a paso está en el [README](../README.md).

## 10. Trazabilidad y bitácora centralizada (E2-02)

El PDF pide dos niveles de registro; cada uno tiene un mecanismo propio.

**Registro de cada solicitud HTTP.** El Gateway (`AccessLogWebFilter`) y cada
servicio (`RequestLoggingFilter`) escriben una línea en el logger
`ticketya.http`:

```
http method=POST path=/api/orders status=201 durationMs=281 userId=4bd7…6c1 role=CLIENT correlationId=17e9…211
```

- En el Gateway el filtro se ejecuta antes de la seguridad, por lo que también
  registra los `401` y `403` que no llegan a los servicios. El usuario y el rol
  los copia del JWT ya validado un filtro dentro de la cadena de seguridad
  (`AuthenticatedIdentityCaptureFilter`), antes de la autorización.
- En los servicios, `userId` y `role` son la identidad derivada que propaga el
  Gateway (`X-User-Id`/`X-User-Role`).
- Todas las líneas de log de los servicios llevan el servicio y el
  `X-Correlation-Id` en el patrón (MDC):
  `INFO [purchase-service,17e9…211]`.
- Los healthchecks (`/actuator/**`) se registran en DEBUG para no inundar el log.
- `docker compose logs -f` reúne los logs de todos los contenedores en un solo
  flujo.

**Bitácora centralizada de operaciones críticas.** `audit-service` guarda en
`ticketya_audit` un evento por operación crítica, con el mismo
`X-Correlation-Id` que recorre la operación entre servicios:

| Servicio | Operación (`operation`) | Resultados (`outcome`) |
|---|---|---|
| Purchase | `PURCHASE_ORDER`, `PURCHASE_RECONCILE` | `CONFIRMED`, `PAYMENT_PENDING`, `PAID_PENDING_TICKETS`, `REJECTED`, `COMPENSATED`, `FAILED`, `ERROR` |
| Purchase | `REFUND_REQUEST`, `REFUND_DECISION`, `REFUND_EXECUTION` | `PENDING`, `FAILED`, `APPROVED`, `REJECTED`, `COMPLETED`, `PAUSED` |
| Purchase | `EVENT_CANCELLATION` | `PROCESSING`, `COMPLETED`, `FAILED` |
| Catalog | `EVENT_PUBLICATION`, `EVENT_SUSPENSION`, `EVENT_RESUMPTION`, `EVENT_CATALOG_CANCELLATION` | estado resultante del evento |
| Ticket | `TICKETS_ISSUED`, `TICKET_REVOCATION` | `ISSUED`, `REPLAYED`, `REVOKED`, `REJECTED_USED` |
| Ticket | `ACCESS_VALIDATION` | `ADMITTED`, `ALREADY_USED`, `REVOKED`, `WRONG_EVENT`, `UNKNOWN_TICKET` |

Una cancelación de evento produce, con un único `X-Correlation-Id`, eventos de
Purchase (`EVENT_CANCELLATION`, `REFUND_EXECUTION`), Catalog
(`EVENT_CATALOG_CANCELLATION`) y Ticket (`TICKET_REVOCATION`).
`GET /api/audit/traces/{correlationId}` los devuelve en orden cronológico.

```mermaid
sequenceDiagram
    autonumber
    participant A as ADMIN
    participant G as api-gateway
    participant P as purchase-service
    participant C as catalog-service
    participant T as ticket-service
    participant AU as audit-service
    A->>G: POST /api/events/{id}/cancellation (X-Correlation-Id: cancel-1)
    G->>P: misma ruta + X-Correlation-Id: cancel-1
    P->>C: POST /api/management/events/{id}/cancel (cancel-1)
    C-->>AU: async EVENT_CATALOG_CANCELLATION (cancel-1)
    P->>T: POST /internal/tickets/{ticketId}/revoke (cancel-1)
    T-->>AU: async TICKET_REVOCATION (cancel-1)
    P-->>AU: async REFUND_EXECUTION / EVENT_CANCELLATION (cancel-1)
    A->>G: GET /api/audit/traces/cancel-1
    G->>AU: GET /api/audit/traces/cancel-1
    AU-->>A: eventos de los tres servicios en orden
```

**Decisiones.**

- *¿Por qué un servicio propio y no sólo logs?* Los logs de contenedor son
  efímeros y no se pueden consultar por operación. Un servicio con base propia
  permite filtrar por correlation id, operación, recurso o actor, y restringir
  el acceso a ADMIN. Un stack de logs (ELK o Loki) es la alternativa
  productiva; para el alcance didáctico agrega más contenedores que valor.
- *¿Por qué fire-and-forget?* La auditoría no puede volverse un punto de falla
  de la compra. El envío no bloquea la solicitud. Los reintentos son seguros
  porque el `eventId` vuelve idempotente la ingesta, y si el envío finalmente
  falla el evento queda en el log local. Así la consistencia de la bitácora es
  eventual y no transaccional con el negocio: un evento puede perderse sólo si
  el proceso productor termina con eventos en cola, y aun así queda la línea en
  su log.
- *¿Por qué PostgreSQL?* Se mantiene una tecnología conocida para el registro
  append-only con `JSONB` en `details`. La base NoSQL del TP queda para
  preferencias y recomendaciones (F10).

## 11. Control de acceso con QR (E2-03)

```mermaid
sequenceDiagram
    autonumber
    participant S as ACCESS_STAFF
    participant G as api-gateway
    participant T as ticket-service
    participant DB as ticketya_ticket
    S->>G: POST /api/access/events/{eventId}/validations {qrPayload, gate}
    G->>G: JWT válido y rol ACCESS_STAFF (si no, 403)
    G->>T: misma ruta + X-User-Id / X-User-Role
    T->>T: verificar firma HMAC de v1.<ticketId>.<firma>
    alt firma inválida
        T->>DB: INSERT access_scans (UNKNOWN_TICKET)
        T-->>S: 404 reason=UNKNOWN_TICKET
    else firma válida
        T->>DB: UPDATE tickets SET status='USED' WHERE id=? AND event_id=? AND qr_hash=? AND status='ISSUED'
        alt 1 fila actualizada
            T->>DB: INSERT access_scans (ADMITTED), misma transacción
            T-->>S: 200 ADMITTED
        else 0 filas
            T->>DB: leer estado y registrar el intento rechazado
            T-->>S: 409 ALREADY_USED / 409 REVOKED / 422 WRONG_EVENT / 404 UNKNOWN_TICKET
        end
    end
```

**Mecanismo contra el doble ingreso.** Es un `UPDATE` condicional sobre la
fila del ticket. PostgreSQL serializa las escrituras sobre la misma fila: con
varios escaneos simultáneos, incluso desde varias instancias de Ticket Service,
el primero cambia `ISSUED → USED` y los demás reevalúan el `WHERE` tras su
commit y no afectan filas. El índice único parcial de `access_scans` es una
segunda barrera. `AccessControlContractTest` lanza 25 escaneos concurrentes del
mismo QR y verifica un solo `200`, 24 `409` y un único registro `ADMITTED`.

**Reglas.** Una entrada de otro evento no se consume (`422`). Una revocada por
devolución o cancelación se rechaza (`409 REVOKED`). Una ya usada no puede
revocarse: la revocación responde `409`, regla que ya existía en E1. La
respuesta no expone datos del comprador.

## 12. Reportes del organizador (E2-04)

`GET /api/reports/events/{eventId}` en Purchase combina tres fuentes:

1. **Catalog** (`GET /api/management/events/{id}` con la identidad del
   solicitante): aplica el ownership existente (`ORGANIZER` sólo sus eventos,
   `403` si es ajeno; `ADMIN` cualquiera) y aporta nombre, estado y precio
   vigente por sector.
2. **Purchase** (base propia): unidades de órdenes pagadas (`CONFIRMED` o
   `PAID_PENDING_TICKETS`) e importe por sector, y devoluciones con reintegro
   `COMPLETED`. Una entrada con solicitud del cliente y de cancelación cuenta
   una sola vez (`DISTINCT ON (ticket_id)`).
3. **Inventory** (disponibilidad pública): capacidad de cada sector.

Fórmulas: `activeTickets = sold - refunded`, `occupancyPercent = active /
capacity * 100` con 2 decimales y `netRevenue = gross - refunded`. Si Inventory
no responde, el reporte se entrega igual (ventas y recaudación no dependen de
ese servicio) con capacidad y ocupación nulas, `capacityAvailable: false` y una
advertencia. Es una degradación controlada en lugar de un `503`. Si Catalog no
responde, en cambio, responde `503`: sin Catalog no se puede autorizar.

El reporte se calcula en cada consulta, sin cache ni tabla de agregados. El
volumen por evento es acotado y así refleja al instante devoluciones y
cancelaciones. Si hiciera falta, una vista materializada por evento es la
evolución natural.

