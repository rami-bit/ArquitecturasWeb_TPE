# Spec Delta

## MODIFIED Requirements

### Requirement: Ownership y snapshots históricos de la orden

Purchase SHALL ser el único dueño de `purchase_db`. SHALL guardar un snapshot de nombre, fecha, lugar, sector, precio y unidad al crear la orden, SHALL referenciar otros servicios sólo por identificadores y SHALL NOT acceder directamente a sus bases ni crear foreign keys entre servicios. SHALL exponer a Preferences un contrato interno de señales agregadas de historial, sin órdenes individuales ni datos de pago. Trazabilidad: PDF-F10.

#### Scenario: Conservar el precio de la compra

- **GIVEN** un evento cuyo precio cambia después de la compra
- **WHEN** se consulta la orden
- **THEN** Purchase devuelve el precio y los datos históricos guardados al crear la orden

#### Scenario: Referencias sin foreign keys cruzadas

- **WHEN** se inspecciona `purchase_db`
- **THEN** las referencias a evento, sector, asiento, pago y tickets son valores UUID sin constraints hacia otras bases

#### Scenario: Señales de historial de un cliente

- **GIVEN** un cliente con compras pagadas de dos eventos
- **WHEN** Preferences solicita `GET /internal/customers/{customerId}/recommendation-signals`
- **THEN** Purchase responde `200` con esos eventos en `purchasedEventIds` y las co-compras agregadas del cliente, sin montos, pagos ni tickets

#### Scenario: Sólo compras pagadas cuentan

- **GIVEN** órdenes del cliente en estado `REJECTED`, `PAYMENT_PENDING` o `COMPENSATED`
- **WHEN** se solicitan las señales del cliente
- **THEN** esos eventos no aparecen en `purchasedEventIds` ni alimentan las co-compras

#### Scenario: Cliente sin compras

- **WHEN** se solicitan las señales de un cliente sin órdenes pagadas
- **THEN** Purchase responde `200` con `purchasedEventIds` y `coPurchases` vacías, sin responder `404`

#### Scenario: Señales acotadas

- **GIVEN** un cliente con historial que produce más de 200 eventos candidatos de co-compra
- **WHEN** se solicitan sus señales
- **THEN** Purchase devuelve como máximo 200 co-compras ordenadas por `count` descendente y `eventId` ascendente
