# Tasks

## 1. Marca de alta demanda en el catálogo

- [ ] 1.1 Agregar la columna `high_demand` al bootstrap de Catalog (`ALTER TABLE events ADD COLUMN IF NOT EXISTS high_demand BOOLEAN NOT NULL DEFAULT FALSE`) y verificarla contra PostgreSQL real, comprobando que el `DEFAULT FALSE` deja válidos los `INSERT` crudos de los tests existentes
- [ ] 1.2 Modelar la marca en la entidad `Event` (campo, getter y setter) y exponerla en `EventRequest` y `EventResponse`, con valor por omisión `false` cuando el cliente no la informa
- [ ] 1.3 Aplicar la marca al crear y al editar el evento, y rechazarla cuando el evento ya no está en `DRAFT`, con pruebas de integración de los tres casos
- [ ] 1.4 Publicar la marca en los dos contratos internos de snapshot (`CatalogEventSnapshot` y `CatalogPurchaseSnapshot`) leyéndola de `events` en los SELECT existentes, manteniendo los dos esquemas idénticos salvo precios
- [ ] 1.5 Publicar la marca en las respuestas públicas del catálogo (`SearchItem` y `EventDetail`) en los SELECT de discovery, con pruebas de integración
- [ ] 1.6 Actualizar `docs/openapi.yaml` con `highDemand` en los esquemas afectados, con `required` simétrico en los dos snapshots, y ajustar `CatalogPurchaseSnapshotDocumentContractTest` y `ConsolidatedOpenApiDocumentContractTest`

## 2. Servicio de fila virtual

- [ ] 2.1 Crear el módulo Maven `services/queue-service` con su `pom.xml`, su entrada en el `pom.xml` raíz, su `Dockerfile` y su `application.yml` con las propiedades `queue.batch-size`, `queue.batch-interval-seconds`, `queue.admission-ttl-seconds` y `queue.catalog-base-url`
- [ ] 2.2 Crear el esquema propio `queue_db` (`queue_counters`, `queue_entries`) en el bootstrap idempotente del servicio, con `UNIQUE (event_id, client_id)`, `UNIQUE (event_id, position)` y el chequeo de estados
- [ ] 2.3 Implementar el repositorio JDBC con la asignación de posición bajo `pg_advisory_xact_lock` por evento y `SELECT … FOR UPDATE` sobre el contador, reutilizando el patrón vigente de `acquireLimit` y `lockEvent`
- [ ] 2.4 Implementar el cliente del snapshot interno de Catalog para conocer la marca de alta demanda y distinguir evento no publicado (`404`) de evento publicado
- [ ] 2.5 Implementar la entrada a la fila: turno idempotente por cliente y evento, posición asignada al final y errores `401`, `403`, `404` y `409` con `application/problem+json`
- [ ] 2.6 Implementar la consulta del turno propio con posición, cantidad de clientes que lo preceden, tamaño de tanda, estado y, cuando corresponde, el token de admisión con su vencimiento
- [ ] 2.7 Implementar el abandono del turno, el cierre por expiración y la asignación de una posición nueva al volver a entrar, sin reutilizar posiciones cerradas
- [ ] 2.8 Implementar el cálculo de tandas por tiempo y la admisión en orden, con la promoción de los turnos dentro de la tanda vigente
- [ ] 2.9 Implementar la emisión del token de admisión ligado a evento y cliente, su verificación y el consumo atómico de un solo uso en `POST /internal/admissions/validate`
- [ ] 2.10 Implementar la propagación del `X-Correlation-Id` y el registro por request con endpoint, cliente, estado y tiempo de procesamiento, sin exponer el token de admisión en los logs
- [ ] 2.11 Escribir las pruebas unitarias del cálculo de tandas, de la vigencia del token y de la máquina de estados
- [ ] 2.12 Escribir las pruebas de integración con PostgreSQL real: orden de llegada entre clientes concurrentes, idempotencia de la entrada, unicidad de la posición con dos instancias, tokens ajeno, vencido y reutilizado, y evento no publicado

## 3. Ruteo en el API Gateway

- [ ] 3.1 Declarar la ruta `/api/queue/**` antes del comodín `/api/**` en la tabla de rutas del Gateway, con la URL base configurable por entorno
- [ ] 3.2 Verificar que las operaciones de la fila exigen JWT y que responden `401` sin token, con la prueba de precedencia de la ruta sobre el comodín de catálogo
- [ ] 3.3 Documentar los paths, esquemas y el encabezado `X-Admission-Token` de la fila en `docs/openapi.yaml` y ajustar la tabla de operaciones del test de contrato consolidado

## 4. Exigencia de admisión en la compra

- [ ] 4.1 Declarar el encabezado `X-Admission-Token` en `PurchaseOrderController` y propagarlo a `createOrder`, actualizando las llamadas directas de `PurchaseBusinessRulesTest` sin perder cobertura
- [ ] 4.2 Implementar el cliente de la fila para validar y consumir la admisión, con `503` cuando la fila no responde y `403` cuando la admisión no habilita la compra
- [ ] 4.3 Exigir la admisión sólo cuando el snapshot de compra informa `highDemand`, después del chequeo de idempotencia y antes de reservar o cobrar, sin efectos secundarios en caso de rechazo
- [ ] 4.4 Escribir las pruebas de contrato de Purchase: compra con token válido, compra sin token, token de otro cliente, token vencido, token reutilizado con otra `Idempotency-Key`, evento que no es de alta demanda sin cambios y replay idempotente que no consume de nuevo el token

## 5. Despliegue y documentación

- [ ] 5.1 Agregar `queue-db` y `queue-service` al `docker-compose.yml` con puerto interno, volumen propio, healthcheck y red, y declarar las variables de entorno correspondientes en `.env`
- [ ] 5.2 Documentar en el `README.md` las variables nuevas, el build, el arranque del servicio de fila y el flujo de compra con admisión
- [ ] 5.3 Actualizar `docs/architecture.md` con el servicio de fila, su diagrama de módulos, el modelo de datos de `queue_db` y la secuencia de la compra con admisión
- [ ] 5.4 Actualizar `docs/Documentacion-de-diseño.md` con los criterios de aceptación ampliados de `US-E2-02` y su estado, y registrar el cambio en `TODO.md` y `NEXT.md`
- [ ] 5.5 Agregar a la colección Postman el flujo de la fila (entrar, consultar, abandonar y comprar con admisión) y los casos de rechazo por admisión ausente, ajena, vencida y reutilizada

## 6. Verificación y cierre

- [ ] 6.1 Ejecutar `mvn verify` del reactor con Java 21 y Testcontainers, y confirmar cero fallos, cero errores y cero omisiones
- [ ] 6.2 Levantar un Compose aislado con nombre de proyecto nuevo, comprobar que todos los servicios quedan `healthy` y recorrer los flujos de la fila y de compra con admisión por el Gateway
- [ ] 6.3 Reejecutar la colección Postman completa y `postman/concurrent-purchases.py` para comprobar que los flujos de la Entrega 1 siguen comportandose igual
- [ ] 6.4 Registrar la evidencia en `docs/verification.md` y ejecutar `openspec validate add-virtual-queue --strict` y `git diff --check`
- [ ] 6.5 Archivar el cambio con `openspec archive -y add-virtual-queue` y confirmar que las capacidades `virtual-queue`, `event-management`, `event-discovery`, `purchase-orders` y `api-gateway` quedaron sincronizadas en `openspec/specs/`
