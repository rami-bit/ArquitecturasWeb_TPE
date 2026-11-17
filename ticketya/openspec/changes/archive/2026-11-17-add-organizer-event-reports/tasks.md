# Tasks

## 1. Purchase Service

- [x] 1.1 Implementar `OrganizerReportCalculator` (vendidas, devueltas, activas, ocupación y recaudación por sector y totales); probar redondeo, capacidad desconocida, sectores sin ventas y devoluciones acotadas (`OrganizerReportCalculatorTest`).
- [x] 1.2 Implementar `OrganizerReportService` y `OrganizerReportController` con autorización vía Catalog y capacidad de Inventory; probar con PostgreSQL real: dueño, ADMIN, organizador ajeno, evento inexistente, roles no autorizados sin llamar a Catalog, órdenes no pagadas, devolución duplicada, Inventory caído y Catalog caído (`OrganizerReportIntegrationTest`).

## 2. Gateway y contratos

- [x] 2.1 Enrutar `/api/reports/**` a Purchase con autorización por rol en el Gateway y probarla.
- [x] 2.2 Documentar la operación en `docs/openapi.yaml` y ampliar Postman con el reporte del evento cancelado (7 vendidas, 7 devueltas, neto 0), el de un evento activo (ocupación 0.2 %) y los rechazos por rol.
