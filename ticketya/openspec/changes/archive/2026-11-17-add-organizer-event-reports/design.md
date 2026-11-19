# Design

## Decisions

### 1. El reporte vive en Purchase

Purchase es dueño de las ventas, los precios congelados y las devoluciones, que concentran el cálculo pesado. Así se evita copiar ventas a otro servicio.

### 2. Autorización delegada en Catalog

Purchase llama a `GET /api/management/events/{id}` con la identidad del solicitante. Catalog aplica la regla de ownership existente (`403` si el evento es ajeno, `404` si no existe) y devuelve nombre, estado y precios. Así no se duplica la regla de ownership.

### 3. Capacidad desde Inventory, con degradación

La disponibilidad pública aporta la capacidad por sector. Si Inventory falla, se devuelven ventas y recaudación con capacidad nula, `capacityAvailable=false` y una advertencia. Si Catalog falla, la respuesta es `503`, porque no se puede autorizar.

### 4. Reglas de conteo

Se cuentan como vendidas las unidades (`order_item_units`) de órdenes `CONFIRMED` o `PAID_PENDING_TICKETS`. Las devoluciones son las filas `COMPLETED` de `refund_requests`, deduplicadas por entrada (`DISTINCT ON (ticket_id)`). El cálculo vive en `OrganizerReportCalculator`, que es puro y tiene pruebas unitarias. La ocupación se redondea `HALF_UP` a dos decimales.

### 5. Sin cache

El reporte se calcula en cada consulta para reflejar al instante devoluciones y cancelaciones; el volumen por evento es acotado.
