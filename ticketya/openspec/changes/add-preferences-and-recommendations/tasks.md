# Tasks

## 1. Exploración y decisiones

- [x] 1.1 Revisar `PDF-F10`, `PDF-NF-PERSISTENCE` y el estado vigente de Catalog, Purchase y Gateway, y registrar que `events.event_type` es texto libre, que `purchase_orders` identifica al comprador por `customer_id` y que el Gateway propaga el `sub` derivado del JWT
- [x] 1.2 Confirmar las decisiones D1 a D17 de `design.md` con el equipo: servicio propio, MongoDB 7, contratos internos, fórmula, fallback, ausencia de cache, timeouts, autorización y despliegue
- [x] 1.3 Verificar que ningún requisito vigente de `api-gateway`, `purchase-orders`, `event-discovery` y `containerized-deployment` entre en conflicto con el delta y dejar constancia de los requisitos modificados elegidos
- [x] 1.4 Acordar el puerto interno `8087` y el nombre de la base `preferences-db`, evitando la colisión previa de `8086` entre `queue-service` y `audit-service`

## 2. Propuesta OpenSpec

- [x] 2.1 Crear el cambio `add-preferences-and-recommendations` con `proposal.md`, `design.md`, `tasks.md` y los deltas de `customer-preferences`, `event-recommendations`, `api-gateway`, `purchase-orders`, `event-discovery` y `containerized-deployment`
- [x] 2.2 Ejecutar `npx --yes openspec validate add-preferences-and-recommendations --strict` y corregir todo error o advertencia antes de implementar
- [x] 2.3 Revisar que cada requisito declare comportamiento y al menos un escenario, y que los textos de requisito se mantengan por debajo de 500 caracteres

## 3. Esqueleto del servicio

- [x] 3.1 Crear el módulo Maven `services/preferences-service` con su `pom.xml` (Spring Boot 3.2.5, Java 19, `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-data-mongodb`, `spring-boot-starter-actuator`, `springdoc` y Testcontainers `mongodb` en la versión 1.21.4 del resto del reactor) y su entrada en el `pom.xml` raíz
- [x] 3.2 Crear `application.yml` con `server.port` desde `PREFERENCES_SERVER_PORT`, la URI y base de MongoDB desde `PREFERENCES_MONGODB_URI` y `PREFERENCES_MONGODB_DATABASE`, las URLs internas, el límite de candidatos y los timeouts, y exponer `health` e `info` en Actuator
- [x] 3.3 Crear el `Dockerfile` del servicio siguiendo el patrón de los servicios existentes, con empaquetado `-DskipTests`, y verificar que la imagen se construye de forma individual
- [x] 3.4 Implementar el filtro de registro por request y la propagación de `X-Correlation-Id`, reutilizando el patrón de los demás servicios y sin registrar el contenido de las preferencias

## 4. Dominio y persistencia MongoDB

- [x] 4.1 Modelar el documento `CustomerPreferences` con `_id` como `String`/`UUID` del cliente, `city`, `eventTypes`, `favoriteArtists`, `updatedAt` y `version`, mapeado a la colección `customer_preferences`
- [x] 4.2 Implementar el repositorio Spring Data MongoDB con lectura por `_id` y upsert atómico de un solo documento que actualice `updatedAt` y `version`, sin transacciones multi-documento
- [x] 4.3 Implementar la validación de D14: ciudad de hasta 120 caracteres, hasta 10 tipos de hasta 80, hasta 20 artistas de hasta 200, sin elementos vacíos ni duplicados ignorando mayúsculas, con `400` en `application/problem+json` identificando el campo
- [x] 4.4 Implementar el `PUT /api/customers/me/preferences` idempotente y el `GET /api/customers/me/preferences` con representación vacía y `version` cero cuando el cliente no tiene documento
- [x] 4.5 Rechazar con `400` un cuerpo que incluya `customerId`, `version` o `updatedAt`, y derivar la identidad exclusivamente del `sub` propagado por el Gateway
- [x] 4.6 Escribir las pruebas de integración con Testcontainers MongoDB: alta, reemplazo, lectura de un documento con `_id` correcto, escrituras concurrentes sin duplicados, cliente sin documento y validaciones rechazadas

## 5. Contratos internos en Purchase y Catalog

- [x] 5.1 Implementar en `purchase-service` el `GET /internal/customers/{customerId}/recommendation-signals` con `purchasedEventIds` (órdenes `CONFIRMED` o `PAID_PENDING_TICKETS`) y `coPurchases` agregadas por cantidad de clientes distintos, ordenadas por `count` descendente y `eventId` ascendente, con tope de 200
- [x] 5.2 Escribir las pruebas de integración de las señales contra PostgreSQL real: cliente con compras, cliente sin compras con `200` y listas vacías, órdenes rechazadas o compensadas excluidas, y tope de 200 respetado
- [x] 5.3 Implementar en `catalog-service` el `GET /internal/events/recommendation-candidates?limit=200` con eventos `PUBLISHED` y `startsAt` futuro, ordenados por `startsAt` y `eventId`, informando `eventId`, `name`, `eventType`, `startsAt`, `city`, `participants` y `minPrice`, sin disponibilidad
- [x] 5.4 Escribir las pruebas de integración de los candidatos contra PostgreSQL real: exclusión de estados no publicados, exclusión de fechas pasadas, orden estable y límite máximo
- [x] 5.5 Implementar en `preferences-service` los clientes HTTP de ambos contratos con timeouts de conexión de 2 s y lectura de 5 s, sin reintentos automáticos, propagando `X-Correlation-Id`

## 6. Algoritmo de recomendación

- [x] 6.1 Implementar el cálculo de puntaje de D7 como componente puro y determinista: `3` por tipo favorito, `2` por artista con tope de tres, `2` por ciudad, `min(5, coCompras)` y `1` por evento dentro de 30 días
- [x] 6.2 Implementar la generación de `reasons` (`FAVORITE_EVENT_TYPE`, `FAVORITE_ARTIST`, `SAME_CITY`, `CO_PURCHASE`, `UPCOMING_FALLBACK`), con comparación de tipo y artista sin distinguir mayúsculas ni espacios extremos
- [x] 6.3 Implementar la exclusión de eventos ya comprados, de eventos no publicados y de eventos pasados, y el descarte de candidatos con `score` cero en modo normal
- [x] 6.4 Implementar el orden `score` descendente, `startsAt` ascendente y `eventId` ascendente, con el límite aplicado después de ordenar
- [x] 6.5 Implementar el fallback de D9: sin preferencias ni historial se devuelven los próximos eventos con `score` cero y motivo `UPCOMING_FALLBACK`; sin candidatos se responde `200` con lista vacía
- [x] 6.6 Escribir las pruebas unitarias del scorer y del orquestador: cada componente por separado, suma máxima `17`, tope de artistas, tope de co-compra, empates por fecha y por `eventId`, exclusión de comprados y fallback

## 7. API pública y Gateway

- [x] 7.1 Implementar `GET /api/customers/me/recommendations` con `limit` de `1` a `20` y `10` por omisión, `400` fuera de rango, `200` con `items`, `warnings` y `generatedAt`, y `503` cuando Catalog no responde
- [x] 7.2 Implementar la degradación ante fallas de Purchase: `200` con recomendaciones por preferencias y `warnings: ["HISTORY_UNAVAILABLE"]`, sin reintentos
- [x] 7.3 Exigir en el servicio la identidad derivada y el rol `CLIENT`, responder `403` a cualquier otro rol y `401` sin identidad, sin leer ni escribir preferencias en esos casos
- [x] 7.4 Declarar la ruta `preferences-service` con `Path=/api/customers/**` antes del comodín `/api/**` en el Gateway, con URL configurable por `GATEWAY_PREFERENCES_SERVICE_URL` y autorización `hasRole("CLIENT")`
- [x] 7.5 Verificar que los contratos internos de Preferences, Purchase y Catalog no se enrutan por el Gateway y responden como ruta no encontrada
- [x] 7.6 Escribir las pruebas del Gateway: precedencia de la ruta sobre el comodín, `401` sin token, `403` con rol distinto de `CLIENT`, propagación de identidad y de `X-Correlation-Id`

## 8. Contrato OpenAPI

- [x] 8.1 Documentar en `docs/openapi.yaml` las operaciones `replaceCustomerPreferences`, `getCustomerPreferences`, `getCustomerRecommendations`, `getRecommendationSignals` y `getRecommendationCandidates`, con `operationId` único, parámetro y header `X-Correlation-Id`, `security: []` en las internas y `application/problem+json` en todos los errores
- [x] 8.2 Documentar los esquemas `CustomerPreferencesRequest`, `CustomerPreferencesResponse`, `EventRecommendationPage`, `RecommendedEvent`, `RecommendationSignals` y `CoPurchaseSignal` con `required` explícito, `limit` acotado y ejemplos de éxito, error y advertencia
- [x] 8.3 Incorporar las cinco operaciones al conjunto de Entrega 2 de `ConsolidatedOpenApiDocumentContractTest` con los estados de D13 y ejecutar el test sin fallos
- [x] 8.4 Verificar que el candado de capacidades del test consolidado sigue intacto y que las rutas nuevas cumplen el prefijo `/api/` o `/internal/`

## 9. Colección Postman

- [x] 9.1 Agregar el flujo de preferencias: alta, consulta, reemplazo y verificación de que el reemplazo cambia el orden de las recomendaciones
- [x] 9.2 Agregar el flujo de recomendaciones: consulta con `limit` por omisión y explícito, `400` fuera de rango y verificación de que un evento comprado no se recomienda
- [x] 9.3 Agregar los casos de aislamiento y seguridad: cliente B no ve las preferencias de A, `401` sin token y `403` con rol distinto de `CLIENT`
- [x] 9.4 Agregar los casos de degradación: `warnings: ["HISTORY_UNAVAILABLE"]` con Purchase detenido y `503` con Catalog detenido, documentando cómo reproducirlos
- [x] 9.5 Ejecutar la colección completa por el Gateway con Newman y confirmar cero fallos, sin regresiones en los flujos de compra, fila virtual, reportes, acceso y auditoría

## 10. Compose y configuración

- [x] 10.1 Agregar `preferences-db` con `image: mongo:7`, volumen `preferences-mongo-data`, red interna y healthcheck `mongosh`, sin publicar puertos al host
- [x] 10.2 Agregar `preferences-service` con `depends_on` de su base en estado `healthy`, healthcheck contra `/actuator/health`, variables de entorno propias y sin puertos publicados
- [x] 10.3 Declarar las variables nuevas en `.env` y documentarlas en el `README.md` (`PREFERENCES_SERVER_PORT`, `PREFERENCES_MONGODB_URI`, `PREFERENCES_MONGODB_DATABASE`, `PREFERENCES_CATALOG_SERVICE_URL`, `PREFERENCES_PURCHASE_SERVICE_URL`, `PREFERENCES_CANDIDATE_LIMIT`, `PREFERENCES_HTTP_CONNECT_TIMEOUT_MS`, `PREFERENCES_HTTP_READ_TIMEOUT_MS`, `GATEWAY_PREFERENCES_SERVICE_URL`)
- [x] 10.4 Verificar que la imagen de `preferences-service` se construye de forma individual y que el servicio queda `healthy` al levantarlo con Compose

## 11. Pruebas

- [x] 11.1 Ejecutar las pruebas unitarias del scorer, del orquestador de recomendaciones, de la validación de preferencias y de los clientes internos con stubs, incluyendo timeouts y degradación
- [x] 11.2 Ejecutar las pruebas de integración con Testcontainers MongoDB de persistencia, upsert atómico y aislamiento por cliente, comprobando en los XML de `surefire-reports` que las clases esperadas tengan `tests > 0`, `failures = 0`, `errors = 0` y `skipped = 0`
- [x] 11.3 Ejecutar las pruebas de contrato MockMvc de `PUT` y `GET` de preferencias y de recomendaciones: `200`, `400`, `401`, `403`, `503`, formato `problem+json` y `X-Correlation-Id`
- [x] 11.4 Ejecutar las pruebas de integración de los contratos internos en Purchase y Catalog contra PostgreSQL real, incluyendo cliente sin historial y límites de la consulta
- [x] 11.5 Ejecutar las pruebas del Gateway de precedencia, autorización por rol y no publicación de rutas internas
- [x] 11.6 Ejecutar el flujo E2E por Gateway con la colección Postman y `postman/concurrent-purchases.py`, verificando que la compra no cambia de comportamiento

## 12. Cierre

- [x] 12.1 Ejecutar `mvn verify` del reactor con Java 19 y Docker disponible, y confirmar cero fallos, cero errores y cero omisiones en los módulos afectados
- [x] 12.2 Levantar un stack aislado con `docker compose -p ticketya_f10_verification up --build -d --wait`, comprobar que todos los servicios quedan `healthy` y recorrer preferencias y recomendaciones por el Gateway
- [x] 12.3 Actualizar `docs/architecture.md` con el servicio, su diagrama de módulos, el modelo del documento MongoDB y la secuencia de recomendación; actualizar `docs/Documentacion-de-diseño.md`, `README.md`, `TODO.md` y `NEXT.md`
- [x] 12.4 Registrar la evidencia en `docs/verification.md` (comandos, cantidad de pruebas, fallos y omisiones, stack usado) y ejecutar `git diff --check`
- [x] 12.5 Ejecutar `npx --yes openspec validate add-preferences-and-recommendations --strict` sin errores y dejar el cambio listo para archivar sólo cuando el Definition of Done esté completo
