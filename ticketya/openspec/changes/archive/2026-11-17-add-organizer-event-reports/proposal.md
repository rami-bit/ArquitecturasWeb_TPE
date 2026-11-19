# Proposal

## Why

PDF-F12 pide que los organizadores consulten reportes de sus eventos: entradas vendidas y devueltas por sector, porcentaje de ocupación y recaudación. Los datos ya existen repartidos (ventas y devoluciones en Purchase, capacidad en Inventory, ownership y precios en Catalog), pero no hay una operación que los combine.

## What Changes

- Agregar `GET /api/reports/events/{eventId}` en Purchase Service para `ORGANIZER` (eventos propios) y `ADMIN`.
- Calcular por sector y en total: vendidas, devueltas, activas, ocupación, recaudación bruta, reintegrada y neta.
- Reusar el ownership de Catalog y degradar con advertencia si Inventory no responde.
- Enrutar `/api/reports/**` por el Gateway con autorización por rol.

## Capabilities

### New Capabilities

- `organizer-reports`: reporte de resultados de un evento para su organizador.

### Modified Capabilities

- `api-gateway`: enrutar y proteger por rol los reportes.

## Impact

- **Trazabilidad:** PDF-F12; historia US-E2-05; épica E2-04.
- **Componentes:** Purchase Service (consulta de solo lectura sobre tablas existentes; sin cambios de esquema) y Gateway.
- **Fuera de alcance:** listado de reportes de todos los eventos, exportación (CSV/PDF), series temporales y cache de agregados.
