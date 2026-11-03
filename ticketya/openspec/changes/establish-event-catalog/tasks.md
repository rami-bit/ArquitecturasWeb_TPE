# Tasks

## 1. Base de la solución

- [ ] 1.1 Crear el POM agregador y los proyectos independientes `api-gateway` y `catalog-service`, fijar Java 19 y versiones compatibles de Spring Boot/Spring Cloud, y verificar que el build raíz y cada build individual ejecuten `mvn verify`.
- [ ] 1.2 Incorporar PostgreSQL de catálogo en `infra/compose.yaml`, configurar Catalog Service sin rutas locales y verificar que la conexión funcione usando variables de entorno documentadas.
- [ ] 1.3 Crear las migraciones Flyway iniciales y una prueba Testcontainers que levante PostgreSQL desde cero y verifique que todas las migraciones se aplican correctamente.
- [ ] 1.4 Implementar respuestas `application/problem+json` y propagación de `X-Correlation-Id` en Catalog Service, y verificar con pruebas de integración los campos y encabezados devueltos.

## 2. US-E1-01 - Gestión de lugares

- [ ] 2.1 Implementar el modelo y migraciones de lugares, sectores numerados, asientos y sectores de admisión general con sus constraints, y verificar invariantes y unicidad mediante pruebas unitarias y de persistencia.
- [ ] 2.2 Implementar los casos de uso administrativos para crear y modificar lugares, y verificar con pruebas que `ADMIN` puede operar y `ORGANIZER`/`CLIENT` reciben rechazo sin cambios parciales.
- [ ] 2.3 Implementar consultas paginadas de lugares para `ORGANIZER` y `ADMIN`, y verificar resultados, paginación y denegación a `CLIENT` mediante pruebas de endpoint.
- [ ] 2.4 Documentar endpoints, requests, responses y errores de lugares en OpenAPI, y verificar que el documento generado contiene ejemplos para sectores numerados y generales.

## 3. US-E1-02 y US-E1-03 - Borradores y ofertas

- [ ] 3.1 Implementar el modelo y migraciones de eventos, participantes y ofertas de sectores con precios decimales, y verificar foreign keys, precios positivos y estados admitidos con pruebas de persistencia.
- [ ] 3.2 Implementar creación de borradores para organizadores y administradores, y verificar ownership, asignación de `DRAFT` y rechazo de creación para terceros mediante pruebas unitarias y de integración.
- [ ] 3.3 Implementar edición de datos, participantes, lugar y ofertas mientras el evento está en `DRAFT`, y verificar sectores ajenos, precios inválidos y permisos sobre borradores propios y ajenos.
- [ ] 3.4 Implementar listado y detalle de gestión filtrados por ownership, y verificar que un organizador no observa eventos ajenos mientras un administrador puede consultar todos.
- [ ] 3.5 Documentar los contratos de gestión de borradores y ofertas en OpenAPI, y verificar ejemplos exitosos y errores `400`, `403`, `404` y `409` en el documento generado.

## 4. US-E1-04 - Publicación y estados administrativos

- [ ] 4.1 Crear migraciones para snapshots normalizados de evento, participantes, lugar, sectores y asientos, incluyendo unicidad por evento, y verificar el esquema con pruebas Testcontainers.
- [ ] 4.2 Implementar validaciones de publicación para datos completos, fecha futura, participantes y sectores valorizados, y verificar cada rechazo conserva el evento en `DRAFT` sin snapshot.
- [ ] 4.3 Implementar la publicación transaccional y copia del snapshot, y verificar con pruebas de integración que un fallo intermedio hace rollback completo y una publicación válida confirma estado y snapshot juntos.
- [ ] 4.4 Hacer idempotente la repetición de `publish` mediante el snapshot único, y verificar que solicitudes repetidas devuelven el mismo evento sin duplicar filas.
- [ ] 4.5 Bloquear toda edición fuera de `DRAFT` y verificar que cambios posteriores en la plantilla canónica no modifican snapshots publicados.
- [ ] 4.6 Implementar `suspend` y `resume` exclusivos de `ADMIN`, y verificar transiciones válidas, rechazos por rol/estado y conservación del mismo snapshot.
- [ ] 4.7 Documentar publicación, suspensión, reanudación y sus errores en OpenAPI, y verificar que los estados futuros aparecen en el modelo sin exponer comandos para `SOLD_OUT`, `CANCELLED` o `FINISHED`.

## 5. US-E1-05 y US-E1-06 - Descubrimiento público

- [ ] 5.1 Incorporar índices relacionales y de texto para estado, fecha, tipo, organizador, ciudad, participantes y relevancia, y verificar su creación mediante las migraciones Flyway.
- [ ] 5.2 Implementar filtros combinables por tipo, organizador, participante, ciudad y rango inclusivo de fechas, y verificar con pruebas de persistencia que sólo se devuelven eventos `PUBLISHED` que cumplen todos los filtros.
- [ ] 5.3 Implementar paginación con límites y metadata, y verificar páginas válidas y rechazo de tamaños o rangos inválidos mediante pruebas de endpoint.
- [ ] 5.4 Implementar orden estable por fecha, menor precio del snapshot y relevancia textual, y verificar cada criterio, dirección y desempate con conjuntos de datos controlados.
- [ ] 5.5 Implementar detalle público basado en snapshot y verificar `404` para eventos inexistentes, `DRAFT` o `SUSPENDED`, sin afirmar disponibilidad de entradas.
- [ ] 5.6 Documentar búsqueda, filtros, ordenamientos, paginación y detalle en OpenAPI, y verificar ejemplos públicos sin encabezados de identidad.

## 6. Gateway y verificación integrada

- [ ] 6.1 Configurar Gateway para exponer las rutas `/api/**` de catálogo sin publicar dependencias a puertos internos, y verificar routing de gestión y consulta mediante pruebas de integración.
- [ ] 6.2 Implementar generación o propagación de `X-Correlation-Id` y forwarding de identidad provisional, y verificar extremo a extremo que Catalog Service registra y responde con el mismo identificador.
- [ ] 6.3 Crear datos de prueba repetibles y una colección Postman que ejecute crear lugar, crear/configurar borrador, publicar, buscar, consultar, suspender y reanudar exclusivamente a través del Gateway; verificar que la colección completa finaliza sin errores.
- [ ] 6.4 Documentar en README requisitos de JDK 19, variables de entorno, comandos de build individual/global, arranque de PostgreSQL y ejecución local, y verificar las instrucciones desde un checkout limpio sin rutas personales.
- [ ] 6.5 Ejecutar `mvn verify` desde la raíz y desde cada servicio, validar el OpenAPI y ejecutar la colección Postman contra el entorno integrado; registrar que todas las verificaciones pasan antes de cerrar el cambio.

## Workflow follow-up

- Archivar `establish-event-catalog` después de la revisión del equipo y de completar todas las tareas.
- Verificar que las tres capacidades queden consolidadas bajo `openspec/specs/` luego del archivado.
