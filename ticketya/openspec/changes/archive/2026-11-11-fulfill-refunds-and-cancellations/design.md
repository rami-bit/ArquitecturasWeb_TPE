# Design

## Context

Purchase persiste órdenes y coordina Inventory, Payment Mock y Ticket mediante HTTP síncrono. Catalog ya permite cancelar eventos y bloquea reservas nuevas en Inventory, pero no toca ventas existentes. Payment Mock ya implementa reintegros idempotentes. Ver `proposal.md` y los deltas de `specs/`.

## Goals / Non-Goals

**Goals:** Completar devoluciones individuales y cancelaciones masivas con datos propios por servicio, operaciones idempotentes y estados consultables desde Gateway.

**Non-Goals:** Pasarela real, mensajería, autenticación fuerte y notificaciones (US-F11).

## Decisions

1. **Purchase orquesta y posee la solicitud.** Una tabla `refund_requests` referencia orden, ítem y ticket por valor, conserva estado/decisión/importe y claves estables para comandos internos. Alternativa: poner la orquestación en Ticket; descartada porque Purchase conoce precio, pago y orden.
2. **Ventana de 72 horas según fecha de Catalog.** Purchase consulta la fecha del evento al crear la solicitud; una fecha faltante o dependencia caída impide aceptar una solicitud nueva. La hora de comparación se toma de un reloj inyectable. Alternativa: usar solo snapshot local; se evita por posible cambio de fecha posterior a la compra.
3. **Decisión y ejecución son pasos separados y reintentables.** ADMIN fija una decisión irreversible; Purchase persiste avance, revoca Ticket, pide reintegro a Payment Mock y libera exactamente el ticket vendido en Inventory. Cada comando lleva una clave derivada de `refundRequestId`. Payment Mock acepta el importe de un ticket por reintegro, permite varios reintegros idempotentes por cobro y rechaza una suma superior al cobro original. Alternativa: transacción distribuida; innecesaria para Compose didáctico.
4. **Cancelación usa el mismo procesamiento por ticket.** Catalog marca `CANCELLED`, Inventory bloquea reservas y Purchase asegura una operación aprobada por cada ticket vendido. Reutiliza una solicitud individual pendiente o aprobada; si fue rechazada, crea otra operación con origen `CANCELLATION`. La unicidad por ticket y origen conserva el rechazo anterior sin duplicar reintegros. El lote se recorre de manera paginada/reintentable. Una venta parcialmente procesada conserva estado visible. El endpoint de inicio exige ADMIN.
5. **Liberación granular en Inventory.** La reserva confirmada conserva sus otros ítems vendidos; un registro único por reserva e ítem evita dos decrementos del mismo cupo. Nunca se usa `release` de una reserva confirmada para una devolución.
6. **Sin notificación en E1.** El resultado se consulta por API; US-F11 agregará el adaptador de aviso en E2.

## Risks / Trade-offs

- [Fallo entre reintegro y liberación] → guardar avance y reintentar con claves estables; mostrar estado incompleto.
- [Cancelación con muchas ventas tarda] → paginar y permitir repetir el lote; no bloquear la respuesta hasta terminar todos los tickets.
- [Compra concurrente a cancelación] → Inventory bloquea reservas nuevas; Purchase comprueba estado del evento antes de iniciar la compra.

## Migration Plan

Agregar tablas/columnas idempotentes, luego contratos internos y finalmente rutas públicas. En Compose se reconstruyen servicios con sus bases existentes; ante rollback se detienen rutas nuevas y las operaciones ya confirmadas conservan sus estados.
