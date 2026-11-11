# Proposal

## Why

La Entrega 1 ya dispone de los servicios y contratos del flujo de compra, pero falta dejar una demostración local reproducible del recorrido completo para ambos tipos de sector y una verificación final de regresión. El cierre debe distinguir la evidencia obtenida de las pruebas adicionales de resiliencia y concurrencia que siguen pendientes en `establish-purchase-and-ticketing`.

## What Changes

- Completar la colección Postman con una compra aprobada de asiento numerado por Gateway y verificar el ticket en la respuesta, conservando la compra de admisión general y todos los recorridos existentes.
- Actualizar README y el informe de verificación con instrucciones locales para Java 21, Testcontainers, Compose y Postman, datos de prueba generados por corrida y resultados observados.
- Correlacionar la evidencia Maven existente de 221 pruebas sin fallos ni omisiones y el build Compose de 11 servicios saludables con el código actual; comprobar la salud del stack existente y ejecutar la colección ampliada por Gateway (42 requests, 72 aserciones, sin fallos). Repetir Maven o Compose sólo si cambian código, contratos o configuración que los afecte.
- Registrar en el change de Purchase y en el seguimiento del roadmap las pruebas no realizadas de vencimiento, compra concurrente integral, fallos downstream y reintento de emisión. No declararlas completas.

## Capabilities

### New Capabilities

Ninguna. Este cambio agrega documentación, una demostración de contrato existente y evidencia de pruebas; `skip_specs: true` evita duplicar requisitos funcionales de Purchase, Gateway e Inventory.

### Modified Capabilities

Ninguna. No cambia el comportamiento observable ni los contratos de API.

## Impact

- `postman/ticketya.postman_collection.json`, `README.md`, `docs/verification.md`, `TODO.md` y `NEXT.md`.
- Artefactos de seguimiento de `openspec/changes/establish-purchase-and-ticketing`, sólo para registrar cobertura diferida sin marcar tareas parcialmente verificadas.
- Sin cambios previstos en código, esquema SQL, Dockerfiles, Compose ni OpenAPI; si la verificación revela una regresión, se corrige en el servicio dueño y se repite la comprobación afectada.
