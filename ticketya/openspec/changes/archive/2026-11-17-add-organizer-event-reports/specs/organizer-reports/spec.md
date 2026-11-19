# Spec Delta

## Purpose

Permitir a cada organizador conocer las ventas, devoluciones, ocupación y recaudación de sus eventos.

## ADDED Requirements

### Requirement: Consultar el reporte de un evento

Purchase SHALL entregar, para un evento, las entradas vendidas, devueltas y activas por sector, el porcentaje de ocupación y la recaudación bruta, reintegrada y neta, junto con los totales del evento. Trazabilidad: PDF-F12.

#### Scenario: Evento con ventas y devoluciones

- **GIVEN** un evento con 4 unidades vendidas en Campo (capacidad 100, una devuelta) y 2 en Platea (capacidad 4)
- **WHEN** su organizador consulta el reporte
- **THEN** Campo informa `soldTickets=4`, `refundedTickets=1`, `activeTickets=3` y `occupancyPercent=3.00`, Platea `occupancyPercent=50.00`, y los totales suman ambos sectores

#### Scenario: Sector sin ventas

- **WHEN** un sector ofertado no tiene ventas
- **THEN** aparece con ceros y ocupación `0.00`

### Requirement: Contar sólo ventas pagadas y devoluciones completadas

El reporte SHALL contar como vendidas sólo las unidades de órdenes `CONFIRMED` o `PAID_PENDING_TICKETS`, y como devueltas sólo las entradas con reintegro `COMPLETED`, una vez por entrada aunque tengan solicitud del cliente y de cancelación. Trazabilidad: PDF-F12 y PDF-F7.

#### Scenario: Órdenes no pagadas

- **WHEN** existen órdenes `REJECTED`, `COMPENSATED` o `PAYMENT_PENDING`
- **THEN** no suman ventas ni recaudación

#### Scenario: Evento cancelado

- **GIVEN** un evento cancelado con todas sus entradas reintegradas
- **WHEN** se consulta el reporte
- **THEN** `refundedTickets` iguala a `soldTickets`, `activeTickets=0` y `netRevenue=0`

### Requirement: Restringir el reporte al organizador del evento

Sólo `ORGANIZER` y `ADMIN` SHALL consultar reportes. Un `ORGANIZER` SHALL acceder sólo a sus eventos, según el ownership de Catalog. Trazabilidad: PDF-F12 y PDF-NF-SECURITY.

#### Scenario: Organizador ajeno

- **WHEN** un `ORGANIZER` consulta el reporte de un evento de otro organizador
- **THEN** recibe `403`

#### Scenario: Rol no autorizado

- **WHEN** un `CLIENT` o `ACCESS_STAFF` consulta un reporte
- **THEN** recibe `403` sin que Purchase consulte a Catalog

### Requirement: Degradar el reporte ante fallas de Inventory

Si Inventory no responde, el reporte SHALL entregarse con ventas y recaudación, con capacidad y ocupación nulas, `capacityAvailable=false` y una advertencia. Si Catalog no responde, SHALL responder `503`. Trazabilidad: PDF-NF-RESILIENCE.

#### Scenario: Inventory no disponible

- **WHEN** Inventory falla durante la consulta
- **THEN** el reporte responde `200` con `capacityAvailable=false` y los totales de ventas correctos
