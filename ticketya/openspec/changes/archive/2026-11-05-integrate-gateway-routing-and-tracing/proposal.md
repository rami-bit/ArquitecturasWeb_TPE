# Proposal

## Why

El API Gateway todavía no enruta tráfico hacia Catalog Service y no genera ni propaga `X-Correlation-Id`; por eso el catálogo sólo puede usarse llamando directamente a su puerto interno y no se cumple el punto de entrada único definido por la arquitectura. Este cambio cierra las tareas 6.1 y 6.2 de `establish-event-catalog` antes de continuar con la verificación integral y el resto de la Entrega 1.

## What Changes

- Configurar una ruta declarativa del Gateway para exponer `/api/**` hacia Catalog Service usando `CATALOG_SERVICE_URL`, sin reescribir ni quitar el prefijo `/api`.
- Incorporar un filtro de correlation ID en el Gateway que conserve un identificador entrante válido o genere un UUID v4 cuando falte, lo propague en la request y lo devuelva en la response.
- Mantener el forwarding de los encabezados provisionales `X-User-Id` y `X-User-Role` sin agregar autorización en el Gateway; Catalog Service sigue siendo el dueño de las reglas de rol y ownership.
- Agregar pruebas unitarias del filtro y pruebas de integración del Gateway contra un downstream HTTP de prueba, sin requerir Docker ni dependencias nuevas.
- Mantener Catalog Service sin exposición directa al host en el Compose central y sin cambios en su comportamiento de correlación, errores o autorización.
- Dejar la prueba extremo a extremo real Gateway -> Catalog -> PostgreSQL para las tareas 6.3 y 6.5 de `establish-event-catalog`.
- Reemplazar funcionalmente las tareas 6.1 y 6.2 de `establish-event-catalog` con este cambio; el cambio anterior conserva esas referencias hasta el archivado.

## Capabilities

### New Capabilities

- `api-gateway`: define el punto de entrada HTTP único para las rutas de catálogo bajo `/api/**`, la generación o propagación de `X-Correlation-Id` y el forwarding de identidad provisional durante la Entrega 1.

### Modified Capabilities

Ninguna. Las capacidades funcionales de catálogo no cambian; sólo se agrega el comportamiento de entrada del Gateway.

## Impact

- Cambios concentrados en `services/api-gateway`: configuración de ruta, filtro de correlation ID y pruebas.
- Sin cambios de código en `services/catalog-service`; su filtro de correlación y su autorización de dominio se reutilizan tal como existen.
- Sin dependencias nuevas de runtime ni de test.
- Sin cambios en el contrato OpenAPI de Catalog Service.
- Sin cambios en `docker-compose.yml`: Catalog Service ya no publica su puerto y el Gateway ya recibe `CATALOG_SERVICE_URL`.
- La validación real mediante Compose, OpenAPI y Postman/Newman queda para 6.3 y 6.5 del cambio `establish-event-catalog`.
