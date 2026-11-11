# Próximos pasos — Entrega 1

Los cuatro pendientes de este registro se completaron el 2026-11-11 dentro de `complete-e1-core-delivery` (14/14 tareas). La evidencia y los comandos están en `docs/verification.md`.

1. [x] Pruebas unitarias de compra (`PurchaseBusinessRulesTest`, 2), devoluciones (`RefundPolicyTest`, 4) y búsqueda (`EventDiscoveryServiceTest`, 4), enfocadas en reglas sin repetir pruebas HTTP.
2. [x] `postman/concurrent-purchases.py`: 12 compradores y claves distintas compitieron por el mismo asiento a través del Gateway; una compra `CONFIRMED`, 11 conflictos y una unidad `SOLD`.
3. [x] Reactor `mvn -q verify` con Java 21/Testcontainers: 231 pruebas actuales, cero fallos, errores y omisiones. Compose nuevo: 11 servicios saludables. Colección Postman por Gateway: 42 requests, 72 aserciones, cero fallos.
4. [x] `TODO.md`, `docs/verification.md` y las tareas OpenSpec se actualizaron; validación estricta y `git diff --check` se ejecutan al final de este cierre.

**Pendiente adicional de OpenSpec:** mantener abierta `establish-purchase-and-ticketing` 3.3 para prueba E2E de compra vencida, fallos parciales entre servicios y reintento específico de QR. Estos casos no se acreditan con la evidencia de Entrega 1 ni bloquean sus pruebas expresas del PDF.

**Archivo:** `complete-e1-core-delivery` se archivó en `openspec/changes/archive/2026-11-11-complete-e1-core-delivery/` tras completar 14/14 tareas y pasar la validación estricta. El cambio `establish-purchase-and-ticketing` permanece activo con su cobertura adicional pendiente.
