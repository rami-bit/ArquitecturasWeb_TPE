# Tasks

## 1. Especificación

- [x] 1.1 Crear el cambio `complete-entrega2-quality-gates` con la CLI de OpenSpec y artefactos proposal/specs/design/tasks
- [x] 1.2 Validar el cambio con `openspec validate complete-entrega2-quality-gates --strict`

## 2. Prueba de carga e informe

- [x] 2.1 Implementar `postman/load-test.py` con perfil por etapas, métricas p50/p95/p99, throughput y errores
- [x] 2.2 Ejecutar la prueba contra el stack de verificación y guardar `docs/load-test-report.md` con datos medidos
- [x] 2.3 Documentar en README el comando de reproducción y que la carga no sustituye a la prueba funcional

## 3. Estrategia de caché

- [x] 3.1 Implementar `EventDiscoveryCache` en Catalog con TTL, tope de entradas y clave que incluya filtros, paginación y orden
- [x] 3.2 Integrar la caché en búsqueda y detalle sin cachear disponibilidad ni recomendaciones
- [x] 3.3 Invalidar la caché al publicar, suspender, cancelar, reanudar y reemplazar ofertas
- [x] 3.4 Declarar `CATALOG_CACHE_TTL_SECONDS` y `CATALOG_CACHE_MAX_ENTRIES` en configuración, Compose y README
- [x] 3.5 Escribir pruebas unitarias de hit/miss/expiración/invalidación y una prueba de integración contra PostgreSQL real

## 4. Arnés de integración

- [x] 4.1 Implementar `postman/integration-harness.py` con modos levantar/reutilizar, espera de salud y salida no cero ante fallos
- [x] 4.2 Obtener tokens del IdP local y ejecutar Newman por el Gateway desde el arnés
- [x] 4.3 Verificar que el arnés no borra volúmenes ajenos y que imprime un resumen con cantidades
- [x] 4.4 Ejecutar el arnés contra el stack de verificación y documentar el resultado

## 5. Documento único de patrones y consistencia

- [x] 5.1 Escribir `docs/patterns-and-consistency.md` con Gateway, Saga, Circuit Breaker, BFF, Service Discovery, caché y consistencia
- [x] 5.2 Referenciar cada patrón a su requisito PDF y a la evidencia existente
- [x] 5.3 Enlazar el documento desde README y `docs/architecture.md` sin duplicar contenido

## 6. Cierre

- [x] 6.1 Actualizar `TODO.md`, `NEXT.md`, `docs/entrega2.md` y `docs/verification.md` con los resultados medidos
- [x] 6.2 Ejecutar `mvn verify`, Compose aislado, Newman, la prueba de carga y el arnés sin regresiones
- [x] 6.3 Sincronizar y archivar el cambio OpenSpec, y ejecutar `openspec validate --all --strict` y `git diff --check`
- [x] 6.4 Confirmar que no se modificó ningún artefacto de F11
