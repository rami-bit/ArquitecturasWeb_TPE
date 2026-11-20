# Proposal

## Why

PDF-F6 describe la necesidad de implementar una fila virtual para eventos de alta demanda. Cuando se abre la venta de un evento masivo, decenas de miles de personas intentan comprar en los mismos minutos. El sistema debe:
- Seguir funcionando bajo carga extrema
- Ser justo con quienes llegaron primero
- Garantizar que una misma entrada nunca se venda dos veces

Actualmente no hay mecanismo para manejar la demanda simultánea más allá de la reserva temporal en Inventory. Se necesita una cola ordenada que permita a los clientes esperar su turno de compra sin sobrecargar el sistema.

## What Changes

- Agregar endpoint \POST /api/queue/join\ para que los clientes se incorporen a la fila virtual de un evento
- Agregar endpoint \GET /api/queue/position\ para consultar la posición en la fila
- Agregar endpoint \POST /api/queue/trigger\ para habilitar la siguiente tanda de clientes (puede ser manual o automática)
- Modificar el flujo de compra para que los clientes en fila tengan prioridad para reservar asientos durante un periodo reducido
- Agregar tablas/collections de cola virtual con estado de posición, tiempo de ingreso y token de sesión

## Capabilities

### New Capabilities

- irtual-queue: gestión de fila virtual para eventos de alta demanda, con posiciones ordenadas y trigger de tandas

### Modified Capabilities

- inventory-and-reservations: integrar validación de posición en cola antes de liberar reservas
- purchase-orders: considerar estado de fila virtual al crear órdenes
- pi-gateway: enrutar y proteger endpoints de cola por rol \ACCESS_STAFF\ o \ADMIN\

## Impact

- **Funcionalidad:** PDF-F6, mejora la experiencia en eventos de alta demanda
- **Componentes:** Gateway, Inventory, Purchase, y el nuevo servicio de cola virtual
- **Fuera de alcance:** No incluye motor de recomendaciones (F10), notificaciones push (F11) ni cache de posiciones (a menos que se justifique separadamente)

## Exclusions

- No modifica el mecanismo de reserva temporal (TTL, expiración) existente en Inventory
- No cambia la lógica de doble venta (ya protegida por constraints de DB y idempotencia)
- No incluye persistencia histórica de posiciones una vez completada la compra
