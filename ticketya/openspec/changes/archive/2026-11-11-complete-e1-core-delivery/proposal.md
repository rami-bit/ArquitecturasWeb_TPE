# Proposal

## Why

La demostración local de compra general y numerada ya funciona. Al contrastar el cierre con `TPE-ArqWeb-TicketYa-2026.pdf` (páginas 2 a 5), todavía faltan evidencias expresamente pedidas para Entrega 1: pruebas unitarias de la lógica de compra, devoluciones y búsqueda, y una prueba de muchas compras simultáneas del mismo asiento o último cupo donde sólo una tenga éxito. La liberación de lugares al vencer la reserva es un requisito funcional; el PDF no exige una prueba E2E específica de vencimiento.

## What Changes

- Completar la colección Postman con una compra aprobada de asiento numerado por Gateway y verificar el ticket en la respuesta, conservando la compra de admisión general y todos los recorridos existentes.
- Actualizar README y el informe de verificación con instrucciones locales para Java 21, Testcontainers, Compose y Postman, datos de prueba generados por corrida y resultados observados.
- Conservar la evidencia obtenida: 221 pruebas Maven sin fallos ni omisiones en la corrida anterior, 11 servicios saludables y la colección ampliada por Gateway con 42 requests y 72 aserciones sin fallos. Repetir las verificaciones afectadas cuando se agreguen las pruebas requeridas.
- Comprobar la liberación por vencimiento con la implementación y las pruebas existentes de Inventory, y agregar o ajustar sólo lo necesario si el comportamiento exigido no está cubierto.
- Agregar pruebas unitarias de la lógica de compra, devoluciones y búsqueda; simular muchas compras concurrentes por el mismo asiento o último cupo y verificar que sólo una termine exitosa, usando el mecanismo de exclusión mutua entre instancias ya documentado.
- Mantener diferenciadas las pruebas adicionales de fallos parciales entre servicios y reintento de emisión QR, que no son condiciones expresas del Entregable 1. Conservarlas abiertas en `establish-purchase-and-ticketing` sin acreditarlas.

## Capabilities

### New Capabilities

Ninguna. Este cambio completa pruebas y evidencia de requisitos funcionales ya especificados; `skip_specs: true` evita duplicar contratos de Purchase, Gateway e Inventory.

### Modified Capabilities

Ninguna. No cambia el comportamiento observable ni los contratos de API.

## Impact

- Pruebas de los módulos Purchase, Catalog y del flujo concurrente de compra; `postman/ticketya.postman_collection.json`, `README.md`, `docs/verification.md`, `TODO.md` y `NEXT.md`.
- Artefactos de seguimiento de `openspec/changes/establish-purchase-and-ticketing`, sólo para registrar cobertura diferida sin marcar tareas parcialmente verificadas.
- No se prevén nuevos endpoints, esquema SQL ni infraestructura. Si una prueba revela un defecto funcional, se corrige en el servicio dueño y se repite la comprobación afectada.
