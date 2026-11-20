# Roadmap - Orquestador Gratuito (PDF-F6 + Epics)

## Contexto
Orquestador de compras para eventos de alta demanda usando modelos gratuitos de OpenCode.
Integra PDF-F6 (fila virtual) con epics E2 (trazabilidad) y seguridad JWT.

## Arquitectura del Orquestador

### 1. Compra de Órdenes (Saga Síncrona)
- **Endpoint**: `POST /api/orders`
- **Flujo**: Validación headers → Snapshot Catalog → Validación fila virtual → Precio ítems → Reserve Inventory → Charge Payment → Confirmar → Emitir tickets
- **Componentes**: Purchase Service, Inventory, Payment, Queue (fila virtual), Catalog

### 2. Fila Virtual (PDF-F6 - Nueva Capability)
- **Endpoint**: `POST /api/queue/join` - Unirse a la fila
- **Endpoint**: `GET /api/queue/position` - Consultar posición
- **Endpoint**: `POST /api/queue/trigger` - Habilitar siguiente tanda (rol: ACCESS_STAFF/ADMIN)
- **Estado**: WAITING → ADMITTED → Token de un solo uso
- **Prioridad**: Clientes en fila tienen prioridad reducida durante la tanda

### 3. Trazabilidad y Bitácora (E2-02)
- **Correlation ID**: Propagado en todos los headers (`X-Correlation-Id`)
- **Audit**: Cada operación registra evento, cliente, estado, tiempo de procesamiento
- **Errores**: `application/problem+json` con `type`, `title`, `status`, `detail`, `instance`

### 4. Seguridad JWT y Roles (PDF-JWT / E2-03)
- **Headers**: `X-User-Id` (UUID obligatorio), `X-User-Role` (CLIENT/ADMIN)
- **Roles en Gateway**: ACCESS_STAFF, ADMIN, CLIENT
- **Endpoints protegidos**: `/api/orders/*`, `/api/queue/*`

### 5. Reportes y Consulta (E2-03/E2-04)
- **Listado**: `GET /api/orders?page&size&status`
- **Consulta orden**: `GET /api/orders/{orderId}`
- **Reportes**: Estado de transacciones, bitácora de la saga

## Modelo de Datos Clave

### OrderRecord (entidad principal)
```
orderId (UUID), customerId (UUID), eventId (UUID)
status (PAYMENT_PENDING / CONFIRMED / REJECTED / COMPENSATED)
paymentStatus (PENDING / APPROVED / REJECTED)
totalAmount (BigDecimal), currency (ARS)
paymentMethod (CARD / WALLET / CASH)
idempotencyKey (String), fingerprint (SHA-256)
items [OrderItem], units [OrderUnit], tickets [OrderTicket]
reservationId (UUID), reservationExpiresAt (OffsetDateTime)
paymentId (UUID), paymentCreatedAt, paymentUpdatedAt
```

### Queue Turn (nueva capacidad PDF-F6)
```
clientId (UUID), eventId (UUID), position (Integer)
state (WAITING / ADMITTED), admissionToken (String, un solo uso)
expiresAt (OffsetDateTime), grantedAt (OffsetDateTime, opcional)
```

## Flujo Completo de Compra con Fila Virtual

1. **Cliente** envía `POST /api/orders` con headers:
   - `X-User-Id: <UUID>`
   - `X-User-Role: CLIENT`
   - `X-Idempotency-Key: <clave>`
   - `X-Admission-Token: <token>` (opcional, solo eventos highDemand)

2. **Purchase Service**:
   - Valida UUID y rol
   - Valida idempotency-key (máx 120 chars)
   - Si evento `highDemand`: valida `admissionToken` en Queue
   - Snapshot Catalog: valida evento comprable, precios
   - Validación y precio de ítems (seatIds vs quantity)
   - Límite atómico por cliente/evento (ticketLimit: 6)
   - Reserve en Inventory (TTL: 600s)
   - Charge en Payment (método + token)
   - Según resultado:
     - **APPROVED**: Confirmar inventario, emitir tickets
     - **REJECTED**: Liberar reserva y límite
     - **PENDING**: Orden reconciliable, aceptar

3. **Respuestas**:
   - `201 Created`: Orden confirmada con tickets
   - `202 Accepted`: Pago incierto, order reconciliable
   - `400`: Parámetros inválidos
   - `403`: Rol/no autorizado o token de admisión inválido
   - `409`: Límite excedido, sin disponibilidad
   - `409`: Evento de alta demanda sin token de admisión

## Capabilities Modificadas (por PDF-F6)

| Capability | Cambio | Impacto |
|------------|--------|---------|
| **virtual-queue** (NUEVA) | Endpoints de fila, tokens, tandas | Gateway + Queue Service + Purchase + Inventory |
| **inventory-and-reservations** | Validar posición en cola antes de liberar | Compra prioritaria durante tandas |
| **purchase-orders** | Considerar estado de fila virtual | Validar `admissionToken` y posición antes de crear orden |
| **api-gateway** | Enrutar y proteger endpoints de cola por rol | Roles: CLIENT (join/position), ACCESS_STAFF/ADMIN (trigger) |

## Roadmap de Implementación

| Fase | Historial | Componente | Status |
|------|-----------|------------|--------|
| **Fase 1** | PDF-F6 + E2-02 | Fila virtual, saga compra, bitácora | ✅ Completado (merge a main) |
| **Fase 2** | E2-03/04 | Reportes, acceso QR, trazabilidad | ✅ Integrado |
| **Fase 3** | US-F7.3 | Carrito → Orden con validación inventario | ⏳ Pendiente (HTTP 400 Postman) |
| **Fase 4** | Mejoras | Retry logic, monitoreo fila, métricas | Planificado |

## Estado de Calidad

- ✅ OpenSpec validate --all: 17/17 passing
- ✅ Maven compile: Purchase Service OK
- ✅ Docker Compose: 13/13 healthy
- ⚠️ Postman: 68 requests, 119 aserciones (US-F7.3 HTTP 400 pending)
- ✅ 324 pruebas unitarias/integración passing

## Modelos Gratuitos Recomendados para Orquestador

1. **Nemotron 3.5 Lightning Free** (actual) - Buen equilibrio velocidad/calidad
2. **CodeGemma 7B** - Excelente para código y API design
3. **Llama 3.2 3B** - Rápido para tareas rutinarias
4. **Mistral 7B Instruct** - Buen rendimiento general
5. **OpenCode propio** - Mejor conocedor del proyecto

**Recomendación**: Usar Nemotron 3.5 Lightning Free para tareas complejas (diseño, validación de contratos) y CodeGemma 7B para código y queries SQL.