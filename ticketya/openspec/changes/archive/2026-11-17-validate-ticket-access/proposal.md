# Proposal

## Why

PDF-F8 pide que el personal de acceso escanee el QR en el ingreso y que el sistema valide que la entrada exista, corresponda al evento y no haya sido usada, marcándola como utilizada: un QR no puede ingresar dos veces. E1 emite QR firmados y modela el estado `USED`, pero no existe la operación de validación y `ACCESS_STAFF` todavía no tiene permisos propios.

## What Changes

- Agregar a Ticket Service `POST /api/access/events/{eventId}/validations` (sólo `ACCESS_STAFF`) y `GET /api/access/events/{eventId}/scans` (`ACCESS_STAFF` o `ADMIN`).
- Verificar la firma HMAC del QR y marcar el uso con un `UPDATE` condicional atómico.
- Registrar cada intento en `access_scans` y en la bitácora centralizada (E2-02).
- Enrutar `/api/access/**` por el Gateway con autorización por rol.

## Capabilities

### New Capabilities

- `ticket-access-control`: validación de entradas en el ingreso, prevención del doble ingreso y registro de intentos.

### Modified Capabilities

- `api-gateway`: enrutar y proteger por rol las rutas de control de acceso.

## Impact

- **Trazabilidad:** PDF-F8, PDF-NF-SECURITY; historia US-E2-02; épica E2-03.
- **Componentes:** Ticket Service (columnas `used_at`, `used_by`, `used_gate`; tabla `access_scans`) y Gateway.
- **Fuera de alcance:** lectores físicos y modo offline de los molinetes; ventanas horarias de ingreso; sincronizar el estado `USED` hacia la copia de la orden en Purchase.
