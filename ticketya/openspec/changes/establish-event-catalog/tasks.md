# Tasks

## 1. Base de la solución

- [x] 1.1 Crear el POM agregador y los proyectos independientes `api-gateway` y `catalog-service`, fijar Java 19 y versiones compatibles de Spring Boot/Spring Cloud, y verificar que el build raíz y cada build individual ejecuten `mvn verify`.
- [x] 1.2 Crear Dockerfiles independientes para `api-gateway` y `catalog-service`, y verificar que cada imagen se construye desde su directorio sin rutas personales ni artefactos externos no declarados.
- [x] 1.3 Crear el `docker-compose.yml` central en la raíz con Gateway, Catalog Service y una imagen oficial versionada de PostgreSQL, y verificar que `docker compose up --build` levanta el incremento completo.
- [x] 1.4 Elegir y documentar el mecanismo mínimo de creación de esquema —SQL, JPA o migrador justificado— y verificar que una base PostgreSQL vacía queda lista para usar de forma reproducible.
- [x] 1.5 Implementar respuestas `application/problem+json` y propagación de `X-Correlation-Id` en Catalog Service, y verificar con pruebas de integración los campos y encabezados devueltos.

## 2. US-E1-01 - Gestión de lugares

- [x] 2.1 Implementar el modelo y esquema de lugares, sectores numerados, asientos y sectores de admisión general con sus constraints, y verificar invariantes y unicidad mediante pruebas unitarias y de persistencia.
- [x] 2.2 Implementar los casos de uso administrativos para crear y modificar lugares, y verificar con pruebas que `ADMIN` puede operar y `ORGANIZER`/`CLIENT` reciben rechazo sin cambios parciales.
- [x] 2.3 Implementar consultas paginadas de lugares para `ORGANIZER` y `ADMIN`, y verificar resultados, paginación y denegación a `CLIENT` mediante pruebas de endpoint.
- [x] 2.4 Documentar endpoints, requests, responses y errores de lugares en OpenAPI, y verificar que el documento generado contiene ejemplos para sectores numerados y generales.

## 3. US-E1-02 y US-E1-03 - Borradores y ofertas

- [x] 3.1 Implementar el modelo y esquema de eventos, participantes y ofertas de sectores con precios decimales, y verificar foreign keys, precios positivos y estados admitidos con pruebas de persistencia.
- [x] 3.2 Implementar creación de borradores para organizadores y administradores, y verificar ownership, asignación de `DRAFT` y rechazo de creación para terceros mediante pruebas unitarias y de integración.
- [x] 3.3 Implementar edición de datos, participantes, lugar y ofertas mientras el evento está en `DRAFT`, y verificar sectores ajenos, precios inválidos y permisos sobre borradores propios y ajenos.
- [x] 3.4 Implementar listado y detalle de gestión filtrados por ownership, y verificar que un organizador no observa eventos ajenos mientras un administrador puede consultar todos.
- [x] 3.5 Documentar los contratos de gestión de borradores y ofertas en OpenAPI, y verificar ejemplos exitosos y errores `400`, `403`, `404` y `409` en el documento generado.

## 4. US-E1-04 - Publicación y estados administrativos

- [x] 4.1 Crear el esquema de snapshots normalizados de evento, participantes, lugar, sectores y asientos, incluyendo unicidad por evento, y verificarlo con pruebas de integración contra PostgreSQL real.
- [x] 4.2 Implementar validaciones de publicación para datos completos, fecha futura, participantes y sectores valorizados, y verificar cada rechazo conserva el evento en `DRAFT` sin snapshot.
- [x] 4.3 Implementar la publicación transaccional y copia del snapshot, y verificar con pruebas de integración que un fallo intermedio hace rollback completo y una publicación válida confirma estado y snapshot juntos.
- [x] 4.4 Hacer idempotente la repetición de `publish` mediante el snapshot único, y verificar que solicitudes repetidas devuelven el mismo evento sin duplicar filas.
- [x] 4.5 Bloquear toda edición fuera de `DRAFT` y verificar que cambios posteriores en la plantilla canónica no modifican snapshots publicados.
- [x] 4.6 Implementar `suspend` y `resume` exclusivos de `ADMIN`, y verificar transiciones válidas, rechazos por rol/estado y conservación del mismo snapshot.
- [x] 4.7 Documentar publicación, suspensión, reanudación y sus errores en OpenAPI, y verificar que los estados futuros aparecen en el modelo sin exponer comandos para `SOLD_OUT`, `CANCELLED` o `FINISHED`.

## 5. US-E1-05 y US-E1-06 - Descubrimiento público

- [ ] 5.1 Incorporar sólo los índices relacionales necesarios para estado, fecha, tipo, organizador, ciudad y participantes, y verificar su existencia y el resultado correcto de las consultas en PostgreSQL.
- [ ] 5.2 Implementar filtros combinables por tipo, organizador, participante, ciudad y rango inclusivo de fechas, y verificar con pruebas de persistencia que sólo se devuelven eventos `PUBLISHED` que cumplen todos los filtros.
- [ ] 5.3 Implementar paginación con límites y metadata, y verificar páginas válidas y rechazo de tamaños o rangos inválidos mediante pruebas de endpoint.
- [ ] 5.4 Implementar orden estable por fecha, menor precio y relevancia textual mediante la solución SQL más simple que cumpla los escenarios, y verificar cada criterio, dirección y desempate con datos controlados antes de considerar full-text o GIN.
- [ ] 5.5 Implementar detalle público basado en snapshot y verificar `404` para eventos inexistentes, `DRAFT` o `SUSPENDED`, sin afirmar disponibilidad de entradas.
- [ ] 5.6 Documentar búsqueda, filtros, ordenamientos, paginación y detalle en OpenAPI, y verificar ejemplos públicos sin encabezados de identidad.

## 6. Gateway y verificación integrada

- [ ] 6.1 Configurar Gateway para exponer las rutas `/api/**` de catálogo sin publicar dependencias a puertos internos, y verificar routing de gestión y consulta mediante pruebas de integración.
- [ ] 6.2 Implementar generación o propagación de `X-Correlation-Id` y forwarding de identidad provisional, y verificar extremo a extremo que Catalog Service registra y responde con el mismo identificador.
- [ ] 6.3 Crear datos de prueba repetibles y una colección Postman que ejecute crear lugar, crear/configurar borrador, publicar, buscar, consultar, suspender y reanudar exclusivamente a través del Gateway; verificar que la colección completa finaliza sin errores.
- [ ] 6.4 Documentar en README requisitos de JDK 19, variables de entorno, builds Maven, construcción de imágenes y comandos del Compose raíz, y verificar las instrucciones desde un checkout limpio sin rutas personales.
- [ ] 6.5 Ejecutar `mvn verify` desde la raíz y cada servicio, construir las imágenes, levantar `docker compose up --build`, validar OpenAPI y ejecutar Postman a través del Gateway; registrar que todas las verificaciones pasan antes de cerrar el cambio.

## Workflow follow-up

- Archivar `establish-event-catalog` después de la revisión del equipo y de completar todas las tareas.
- Verificar que las cuatro capacidades queden consolidadas bajo `openspec/specs/` luego del archivado.



