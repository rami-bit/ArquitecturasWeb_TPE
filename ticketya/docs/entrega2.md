# Entrega 2 — Sistema completo

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
| F11 — Notificaciones | Criterios de aceptación vigentes; implementación pendiente | [`Documentacion-de-diseño.md`](Documentacion-de-diseño.md#feature-f11---notificaciones-al-cliente-entrega-2) |
| F12 — Reportes para organizadores | Implementado | `/api/reports/**`; Postman 28–31 y 43 |
| Base NoSQL justificada | Implementado | MongoDB 7 en `preferences-db`; decisión D2 del cambio archivado |
| JWT con roles y aislamiento | Implementado | E2-01 + validación por rol en Gateway y servicios |
| Consistencia y compensaciones | Implementado | Saga de compra con reintentos idempotentes y compensaciones observables |
| Resiliencia (timeouts y degradación asimétrica) | Implementado | Timeouts 2 s/5 s hacia Catalog y Purchase; `HISTORY_UNAVAILABLE`, `503`, `UPCOMING_FALLBACK` |
| Circuit breaker y fallback (Payment y notificaciones) | Implementado | `CircuitBreaker` de tres estados, fallo rápido `503` sin cobrar y fallback local de notificaciones; cambio archivado `2026-11-20-add-circuit-breaker-and-fallback` |
| Cache | Pendiente | SSD-5.4 del enunciado; decisión "sin caché de recomendaciones" justificada en D10 |
| Bitácora y log centralizado | Implementado | `audit-service`, `/api/audit/**` sólo ADMIN |
| Contenedor por microservicio y Compose central | Implementado | 17 contenedores, `docker-compose.yml` raíz |

## Entregables exigidos

| Entregable | Estado | Referencia |
|---|---|---|
| Repositorio Git actualizado con código fuente | Cumplido | rama `main`, commit `fb56dfa` |
| `docker-compose.yml` con todas las bases, gateway y microservicios | Cumplido | 17/17 servicios `healthy` en verificación aislada |
| Especificaciones y documentación de diseño actualizadas | Cumplido | 19 specs OpenSpec vigentes, 0 cambios activos; `docs/architecture.md`, `docs/Documentacion-de-diseño.md` |
| README con instrucciones de ejecución y diseño | Cumplido | [`README.md`](../README.md) |
| Colección de requests actualizada | Cumplido | 94 items, ejecutada por el Gateway con Newman |
| Informe de la prueba de carga | Pendiente | Ver sección "Pendiente verificable" |
| Instrucciones de interfaz gráfica | No aplica | El proyecto no desarrolló interfaz gráfica |

## Criterios de aceptación

Los criterios de aceptación de las doce historias están en [`docs/Documentacion-de-diseño.md`](Documentacion-de-diseño.md). En esta entrega se revisaron y actualizaron los de F10 (puntaje explicable, motivos, exclusión de comprados, fallback, degradación y seguridad) y se dejaron vigentes y completos los de F11, cuya implementación queda para la siguiente iteración. Los comportamientos especificados están formalizados en `openspec/specs/`, sin deltas pendientes de sincronizar.

## Evidencia de verificación

- `mvn verify`: 430 pruebas, 0 fallos, 0 errores, 0 omitidos (Java 19, Testcontainers 1.21.4, PostgreSQL 16.4 y MongoDB 7).
- `docker compose -p ticketya_f10_verification up --build -d --wait`: 17/17 contenedores `healthy`.
- Newman por el Gateway: 103 requests, 94 test-scripts, 174 aserciones, 0 fallos.
- `python postman/concurrent-purchases.py`: 12 compras concurrentes, 1 confirmada y 11 conflictos, sin doble venta.
- `openspec validate --all --strict`: 19/19 specs y 0 cambios activos.
- `git diff --check`: limpio.

Detalle en [`verification.md`](verification.md).

## Pendiente verificable

No forma parte del cierre de documentación, pero sigue abierto en `TODO.md` y debe resolverse antes de dar la entrega por terminada en su totalidad:

1. Prueba de carga del flujo de compra con informe de usuarios concurrentes, tiempos de respuesta y errores. La prueba de concurrencia actual no sustituye al informe de carga.
2. Estrategia de caché del catálogo y las búsquedas, con TTL e invalidación justificada.
3. Arnés de integración entre servicios reales que ejecute el flujo completo de forma automatizada.
4. Documento único de justificación de patrones y estrategias de consistencia.
5. Implementación de F11 y su despliegue en Compose (el despacho y el fallback local ya están disponibles; falta el adaptador y el servicio de entrega).
