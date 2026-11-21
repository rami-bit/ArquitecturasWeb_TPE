# Entrega 2 — Sistema completo

> **Actualización 20/11/2026 — PDF-F11 implementada.** `notification-service` (puerto 8088, base `ticketya_notification`) envía email y push simulados al confirmar o cancelar una compra, al suspender, reanudar o cancelar un evento, y cuando un artista favorito publica un evento en la ciudad del cliente. Verificación: ver la primera sección de [`verification.md`](verification.md).

**Fecha de entrega:** 20/11/2026
**Repositorio:** `https://github.com/rami-bit/ArquitecturasWeb_TPE.git`, rama `main`
**Commit de cierre de la Fase 0 y PDF-F10:** `fb56dfa`

Este documento es la carátula del entregable 2 del enunciado (`TPE-ArqWeb-TicketYa-2026.md`). Consolida el estado real del repositorio, los entregables exigidos y la evidencia de verificación. La fuente de estado operativo es [`NEXT.md`](../NEXT.md); la checklist por historia está en [`TODO.md`](../TODO.md); los comandos y resultados medidos están en [`verification.md`](verification.md).

## Alcance implementado

| Requisito del enunciado | Estado | Evidencia |
|---|---|---|
| F6 — Fila virtual de alta demanda | Implementado | `queue-service` + PostgreSQL; Postman `F6-01`…`F6-17` |
| F8 — Control de acceso con QR | Implementado | `/api/access/**`; Postman 32 y 37–42 |
| F10 — Preferencias y recomendaciones | Implementado | `preferences-service` + MongoDB 7; Postman `F10-01`…`F10-09` |
| F11 — Notificaciones | Implementado | `notification-service` + PostgreSQL; email y push simulados; Postman `F11-01`…`F11-12`; cambio archivado `2026-11-20-add-customer-notifications` |
| F12 — Reportes para organizadores | Implementado | `/api/reports/**`; Postman 28–31 y 43 |
| Base NoSQL justificada | Implementado | MongoDB 7 en `preferences-db`; decisión D2 del cambio archivado |
| JWT con roles y aislamiento | Implementado | E2-01 + validación por rol en Gateway y servicios |
| Consistencia y compensaciones | Implementado | Saga de compra con reintentos idempotentes y compensaciones observables |
| Resiliencia (timeouts y degradación asimétrica) | Implementado | Timeouts 2 s/5 s hacia Catalog y Purchase; `HISTORY_UNAVAILABLE`, `503`, `UPCOMING_FALLBACK` |
| Circuit breaker y fallback (Payment y notificaciones) | Implementado | `CircuitBreaker` de tres estados, fallo rápido `503` sin cobrar y fallback local de notificaciones; cambio archivado `2026-11-20-add-circuit-breaker-and-fallback` |
| Cache | Cumplido | `EventDiscoveryCache` en Catalog con TTL e invalidación; `caching-strategy/spec.md`; la decisión "sin caché de recomendaciones" se mantuvo justificada en D10 |
| Bitácora y log centralizado | Implementado | `audit-service`, `/api/audit/**` sólo ADMIN |
| Contenedor por microservicio y Compose central | Implementado | 19 contenedores, `docker-compose.yml` raíz |

## Entregables exigidos

| Entregable | Estado | Referencia |
|---|---|---|
| Repositorio Git actualizado con código fuente | Cumplido | rama `main`, commit `fb56dfa` |
| `docker-compose.yml` con todas las bases, gateway y microservicios | Cumplido | 19/19 servicios `healthy` en verificación aislada |
| Especificaciones y documentación de diseño actualizadas | Cumplido | 23 specs OpenSpec vigentes, 0 cambios activos; `docs/architecture.md`, `docs/Documentacion-de-diseño.md` |
| README con instrucciones de ejecución y diseño | Cumplido | [`README.md`](../README.md) |
| Colección de requests actualizada | Cumplido | 106 items, ejecutada por el Gateway con Newman |
| Informe de la prueba de carga | Cumplido | [`docs/load-test-report.md`](load-test-report.md), medido el 20/11/2026 |
| Integración automatizada entre servicios | Cumplido | `postman/integration-harness.py`, corrida PASS con 17 servicios |
| Documento único de patrones y consistencia | Cumplido | [`docs/patterns-and-consistency.md`](patterns-and-consistency.md) |
| Instrucciones de interfaz gráfica | No aplica | El proyecto no desarrolló interfaz gráfica |

## Criterios de aceptación

Los criterios de aceptación de las doce historias están en [`docs/Documentacion-de-diseño.md`](Documentacion-de-diseño.md). En esta entrega se revisaron y actualizaron los de F10 (puntaje explicable, motivos, exclusión de comprados, fallback, degradación y seguridad) y los de F11, que quedó implementada (confirmación y cancelación de compras, suspensión, reanudación y cancelación de eventos, y aviso por artista favorito en la ciudad del cliente). Los comportamientos especificados están formalizados en `openspec/specs/`, sin deltas pendientes de sincronizar.

## Evidencia de verificación

- `mvn clean verify` (con F11): 466 pruebas, 0 fallos, 0 errores, 0 omitidos (Java 19, Testcontainers 1.21.4, PostgreSQL 16.4 y MongoDB 7).
- `docker compose -p ticketya_f11_verification up --build -d --wait`: 19/19 contenedores `healthy`.
- Newman por el Gateway: 117 requests, 196 aserciones, 0 fallos (incluye `F11-01`…`F11-12`).
- `python postman/concurrent-purchases.py`: 12 compras concurrentes, 1 confirmada y 11 conflictos, sin doble venta.
- `openspec validate --all --strict`: 24/24 specs y 0 cambios activos.
- `git diff --check`: limpio.

Detalle en [`verification.md`](verification.md).

## Pendiente verificable

No quedan historias funcionales del enunciado pendientes. F11 se implementó con `notification-service` (ver `docs/architecture.md` §9). Queda fuera de alcance, y documentado, el envío real por un proveedor externo de email o push: la pasarela es simulada, como Payment Mock.
