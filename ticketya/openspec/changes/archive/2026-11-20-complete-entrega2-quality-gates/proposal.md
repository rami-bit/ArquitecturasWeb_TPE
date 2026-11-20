# Proposal

## Why

La carátula de la Entrega 2 (`docs/entrega2.md`) aún marca cuatro controles como pendientes: informe de prueba de carga, estrategia de caché, arnés de integración automatizado entre servicios y documento único de patrones/consistencia. El enunciado los exige como parte del "Sistema completo": pruebas de integración entre servicios y prueba de carga con informe, resiliencia y cache justificadas, y decisiones arquitectónicas trazables al PDF. F11 queda explícitamente fuera de alcance y se delega a otro compañero.

## What Changes

- Script de prueba de carga sin dependencias nuevas, con perfil por etapas, métricas de latencia y rendimiento, e informe medido en `docs/load-test-report.md`.
- Caché en memoria del catálogo publicado para búsqueda y detalle, con TTL, tamaño acotado e invalidación ante cambios de estado o de ofertas. No se cachea disponibilidad de inventario ni recomendaciones.
- Arnés de integración que levanta o reutiliza el stack aislado, verifica salud, ejecuta la colección Postman por el Gateway y falla con código no cero ante cualquier error, sin borrar volúmenes ajenos.
- Documento único `docs/patterns-and-consistency.md` que justifica los patrones usados y las estrategias de consistencia con referencias al PDF y a la evidencia existente.
- Variables de entorno, README, arquitectura, verificación y carátula actualizados; ningún cambio en las APIs públicas documentadas.

## Capabilities

### New Capabilities

- `load-testing`: perfil de carga reproducible sobre la compra por Gateway e informe con resultados medidos.
- `caching-strategy`: caché acotada del catálogo publicado con TTL e invalidación, sin cachear verdades autoritativas.
- `integration-test-harness`: verificación automatizada de punta a punta del sistema completo.
- `architecture-patterns`: documento único de justificación de patrones y consistencia.

### Modified Capabilities

Ninguna: los cuatro controles se formalizan como capacidades nuevas de calidad y no cambian los contratos vigentes salvo la adición de dos variables de configuración del caché.

## Impact

- `services/catalog-service`: componente de caché en memoria, integración en búsqueda/detalle e invalidación desde la gestión de eventos; variables `CATALOG_CACHE_TTL_SECONDS` y `CATALOG_CACHE_MAX_ENTRIES`.
- `postman/load-test.py`, `postman/integration-harness.py`: scripts de carga y de arnés, con Python 3 estándar.
- `docs/load-test-report.md`, `docs/patterns-and-consistency.md`, `README.md`, `docs/architecture.md`, `docs/verification.md`, `docs/entrega2.md`, `TODO.md` y `NEXT.md`.
- No se modifica OpenAPI ni la colección Postman: los flujos existentes son la evidencia de no regresión.
- F11, sus specs, su código y su despliegue quedan intactos.

## Simplificaciones declaradas

- Caché en proceso con `ConcurrentHashMap` en lugar de Redis: una sola instancia de Catalog en el Compose didáctico; se documenta la desviación respecto de la arquitectura objetivo del blueprint.
- Prueba de carga con `urllib` y `ThreadPoolExecutor` en lugar de k6/JMeter: no hay dependencias nuevas y el perfil es suficiente para el requisito del TPE.
- El arnés reutiliza el stack ya levantado cuando está disponible, para no competir por el puerto 8080 del Gateway.
