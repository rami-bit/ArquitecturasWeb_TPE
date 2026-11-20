# Design

## Context

El sistema tiene 19 specs vigentes, 17 contenedores verificados y una colección Postman con 94 pasos. Quedan cuatro controles de la Entrega 2. El enunciado exige una prueba de carga con informe, integración entre servicios reales, resiliencia y cache justificadas, y decisiones de arquitectura trazables. El blueprint menciona Redis como arquitectura objetivo, pero el Compose actual ejecuta una sola instancia de Catalog y no hay dependencia de caché en el reactor.

## Goals / Non-Goals

**Goals:** medir la compra bajo carga con datos reales del stack; cachear sólo lecturas de catálogo con caducidad e invalidación demostrables; automatizar la verificación completa del sistema; dejar un único documento de patrones y consistencia.

**Non-Goals:** F11 y cualquier notificación real; introducir Redis o k6; cachear disponibilidad, precios de compra ni recomendaciones; cambiar contratos HTTP; optimizar para producción.

## Decisions

### D1 — Prueba de carga con Python estándar

`postman/load-test.py` usa `urllib` y `ThreadPoolExecutor`, con etapas configurables (por omisión 10/20/40 usuarios) y una duración corta por etapa. Mide latencia por request, calcula p50/p95/p99, throughput y tasa de errores. No agrega dependencias y puede ejecutarse contra un stack ya saludable.

### D2 — El informe se genera con datos medidos

`docs/load-test-report.md` registra fecha, entorno, perfil, resultados por etapa, interpretación y comandos de reproducción. Se escribe después de ejecutar el script contra el stack de verificación; no se inventan cifras.

### D3 — Caché en memoria sólo para catálogo publicado

`EventDiscoveryCache` guarda respuestas de `search` y `detail` de eventos `PUBLISHED` en un `ConcurrentHashMap` con expiración por entrada y tope de entradas. La clave incluye todos los filtros, paginación y orden. Se descartan entradas vencidas y las que exceden el tope ( FIFO simple ). La disponibilidad de Inventory nunca se cachea porque es la verdad autoritativa para vender; las recomendaciones tampoco (decisión D10 de PDF-F10).

### D4 — Invalidación en el mismo cambio de estado

`EventManagementService` invalida la entrada del evento y el índice de búsqueda después de publicar, suspender, cancelar, reanudar o reemplazar ofertas. La invalidación ocurre tras el commit de la base; si falla, la entrada expira por TTL como máximo. No se replica entre instancias.

### D5 — Consistencia explícita

La compra usa consistencia fuerte sobre la base de Purchase y reservas atómicas en Inventory. El catálogo publicado es una instantánea inmutable: la caché puede servirla hasta `CATALOG_CACHE_TTL_SECONDS` sin afectar la venta, porque Purchase vuelve a leer el snapshot por contrato interno. Las compensaciones y los reintentos idempotentes se documentan en el documento único.

### D6 — Arnés de integración

`postman/integration-harness.py` comprueba o levanta el stack, espera `healthy` de los 17 servicios, obtiene tokens del IdP local, ejecuta Newman por el Gateway y finaliza con código distinto de cero si falla salud o aserciones. Modo `--reuse` para no publicar otro Gateway en el mismo puerto; `down` sin `-v` para conservar volúmenes. Imprime un resumen con cantidades.

### D7 — Documento único de patrones

`docs/patterns-and-consistency.md` reúne Gateway, Saga, Circuit Breaker, BFF, Service Discovery, caché y consistencia, con el problema que resuelve, la alternativa descartada, el costo y la evidencia. `docs/architecture.md` y el README enlazan al documento en lugar de duplicarlo.

### D8 — Trazabilidad y alcance

Cada patrón y control referencia `PDF-F<N>` o `PDF-NF-<TOPIC>`. F11 no se menciona como implementado y no se tocan sus artefactos.

## Risks / Trade-offs

| Riesgo | Mitigación |
|---|---|
| La caché sirva un evento suspendido unos segundos | Invalidación inmediata en el cambio de estado y TTL acotado; la venta no depende de la caché |
| La prueba de carga sature el stack y rompa el E2E posterior | Perfil corto, datos propios y ejecución posterior del arnés con la colección completa |
| el arnés choque con un stack ya levantado | Modo `--reuse` por omisión y nombre de proyecto aislado cuando levanta uno nuevo |
| el informe quede desactualizado | Incluir fecha, commit y comando exacto de reproducción |
