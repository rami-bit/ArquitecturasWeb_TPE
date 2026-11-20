# Informe de prueba de carga - flujo de compra

- **Fecha:** 2026-11-20 10:00 UTC
- **Commit:** `52e8c96`
- **Objetivo:** medir `POST /api/orders` por el Gateway con compradores concurrentes (PDF-E2-TESTING).
- **Entorno:** stack `ticketya_f10_verification` (17 contenedores), Java 19, PostgreSQL 16 y MongoDB 7.
- **Perfil:** 10, 20, 40 compradores concurrentes por etapa.
- **Datos:** evento de carga `914a1a3f78` creado en cada corrida, sector de admisión general con cupo 10000.
- **Reproducción:** `python postman/load-test.py` contra un stack saludable y el IdP local en `:19998`.

| Etapa (usuarios) | Requests | OK | Errores | Duración (s) | RPS | p50 (ms) | p95 (ms) | p99 (ms) | Máx (ms) |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 10 | 10 | 10 | 0 | 1.07 | 9.39 | 943.7 | 1050.1 | 1050.1 | 1050.1 |
| 20 | 20 | 20 | 0 | 1.67 | 11.95 | 1374.1 | 1643.0 | 1648.0 | 1648.0 |
| 40 | 40 | 40 | 0 | 2.44 | 16.39 | 1790.3 | 2373.1 | 2404.0 | 2404.0 |

## Interpretación

- Cada comprador usa un `sub` distinto y una clave de idempotencia nueva: no hay competencia por el límite de seis entradas por cliente.
- Se considera éxito un `201 CONFIRMED`; cualquier otro código se cuenta como error. Códigos observados: ninguno.
- La latencia se mide en el cliente desde el envío de la orden hasta la respuesta completa; incluye red, Gateway y la saga completa.
- Esta prueba complementa pero no sustituye la colección Postman ni la prueba de concurrencia sobre un mismo asiento.
- El propósito es didáctico: no se fijan umbrales de rendimiento productivo, se reporta lo medido para el entorno indicado.
