# Tasks

## 1. Routing de catálogo por el Gateway

- [x] 1.1 Agregar en `services/api-gateway/src/main/resources/application.yml` la ruta `catalog-service` con predicado `Path=/api/**` y URI `${CATALOG_SERVICE_URL:http://localhost:8081}`, sin `StripPrefix` ni reescritura de path; verificar con un test de integración contra un stub HTTP que `GET /api/events` y `GET /api/management/events` llegan con path, método y query params intactos.
- [x] 1.2 Extender el test de integración para verificar que `/actuator/health` no se enruta a Catalog y que las rutas fuera de `/api/**` no se reenvían; verificar que el test pasa.
- [x] 1.3 Verificar que la ruta toma `CATALOG_SERVICE_URL` cuando está definida y el default local cuando no lo está; cubrir ambos casos en el test de contexto o de integración y confirmar que pasa.

## 2. Correlation ID en el borde

- [x] 2.1 Implementar un `CorrelationIdWebFilter` en `api-gateway` que conserve un `X-Correlation-Id` no vacío o genere un UUID v4, mute la request y fije la response en `beforeCommit`; verificar con tests unitarios los casos suministrado, ausente, vacío y preservación de los demás headers.
- [x] 2.2 Extender el test de integración para verificar que el stub recibe el mismo correlation ID que devuelve el Gateway y que la response contiene un único valor aunque el downstream también devuelva el header; verificar que el test pasa.
- [x] 2.3 Agregar al test de integración el caso de una ruta no encontrada por el Gateway y verificar que la respuesta propia incluye `X-Correlation-Id`; confirmar que el test pasa.

## 3. Identidad provisional y errores de dominio

- [x] 3.1 Agregar al test de integración la verificación de que `X-User-Id` y `X-User-Role` llegan intactos al downstream, que no se inventan cuando faltan y que las consultas públicas sin identidad se enrutan; verificar que el test pasa.
- [x] 3.2 Agregar al test de integración un stub que responda `403` o `404` con `application/problem+json` y verificar que el status, content type, body y `correlationId` llegan sin cambios al cliente; confirmar que el test pasa.

## 4. Verificación del incremento

- [x] 4.1 Ejecutar `mvn verify` desde `services/api-gateway` y desde la raíz del repositorio; verificar que ambas ejecuciones terminan sin errores.
- [x] 4.2 Ejecutar `docker compose config` y verificar que Catalog Service no publica puertos al host y que el Gateway recibe `CATALOG_SERVICE_URL`; confirmar la salida esperada.
- [x] 4.3 Revisar el diff y verificar que no se agregaron dependencias nuevas ni se modificó código, configuración o contrato de Catalog Service; confirmar que el cambio queda concentrado en `api-gateway`.

## Workflow follow-up

- Reconciliar las tareas 6.1 y 6.2 de `establish-event-catalog` con este cambio, dejando 6.3 y 6.5 para la prueba real con Compose y Postman/Newman.
- Archivar este cambio después de la revisión del equipo y de completar todas las tareas.
- Verificar que `api-gateway` quede consolidada bajo `openspec/specs/` luego del archivado.
