# Tasks

## 1. Demostración de compra por Gateway

- [x] 1.1 Agregar a la colección Postman una compra aprobada de un asiento numerado con su ID de Inventory y clave idempotente nueva; verificar el ticket emitido en la respuesta de esa compra, que el JSON sea válido y que los requests previos sigan presentes.
- [x] 1.2 Comprobar la salud actual de los 11 servicios del stack existente y ejecutar la colección completa por Gateway con datos nuevos; verificar las compras aprobadas de admisión general y asiento numerado, 42 requests, 72 aserciones y cero fallos.

## 2. Regresión y evidencia de entrega

- [x] 2.1 Correlacionar los XML Surefire existentes con las clases fuente y la ausencia de cambios pertinentes en código/contratos; verificar 221 pruebas ejecutadas en total, cero fallos, errores y omisiones, y registrar que Maven no se repitió en este cierre documental.
- [x] 2.2 Actualizar README con comandos reproducibles para Maven Java 21, Testcontainers, Compose y Postman; verificar que el flujo actual documentado use el stack existente sin borrar volúmenes ajenos y distinga cuándo corresponde reconstruir.
- [x] 2.3 Actualizar `docs/verification.md` con fechas y comandos de la evidencia Maven/Compose previa y de la salud/Postman actuales; verificar los 221 tests, 11 servicios saludables y 42 requests/72 aserciones/0 fallos contra XML y salidas respectivas, sin atribuirlos a una sola corrida.
- [x] 2.4 Registrar en `establish-purchase-and-ticketing` y TODO/NEXT las pruebas de vencimiento, compra concurrente integral, fallos downstream y reintento QR que siguen pendientes; verificar que la tarea 3.3 y los casilleros no acreditados permanezcan abiertos.
- [x] 2.5 Ejecutar `openspec validate complete-e1-core-delivery --strict` y `git diff --check`; si alguna edición posterior altera código o contrato, repetir Maven/Compose y la verificación afectada antes de cerrar el cambio.

## Workflow follow-up

- Revisar los artefactos y la evidencia con el equipo antes de archivar, según `OpenSpec.md`.
