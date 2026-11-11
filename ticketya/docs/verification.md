# Verificación de Entrega 1

## Cierre de pruebas expresas del PDF (2026-11-11)

Se agregaron pruebas unitarias de reglas de compra (`PurchaseBusinessRulesTest`, 2), devoluciones (`RefundPolicyTest`, 4) y búsqueda/ordenamiento (`EventDiscoveryServiceTest`, 4). La regla de devolución de 72 horas y la inmutabilidad de la decisión se extrajeron a `RefundPolicy` sin cambiar las rutas ni las respuestas. La concurrencia se verifica con `postman/concurrent-purchases.py`, que crea un evento y asiento propios por corrida y dispara 12 compras completas por Gateway, cada una con comprador y clave de idempotencia distintos.

| Verificación | Comando / entorno | Resultado |
|---|---|---|
| Módulo Catalog | `docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "${PWD}:/workspace" -w /workspace -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal maven:3.9.9-eclipse-temurin-21-alpine mvn -q -pl services/catalog-service -am verify` | 48 pruebas de Catalog, 0 fallos, 0 errores, 0 omisiones; 4 pruebas nuevas de búsqueda. |
| Módulo Purchase | Mismo contenedor Java 21 con `mvn -q -pl services/purchase-service -am verify` | XML recientes de las clases actuales; la corrida inicial falló al compilar un test nuevo, se corrigió antes del reactor final. |
| Reactor completo | Mismo contenedor Java 21 con `mvn -q verify` | Código 0; 231 pruebas en los seis módulos, 0 fallos, 0 errores, 0 omisiones. Los XML de las clases fuente actuales están presentes; se excluyó un XML antiguo de `PurchaseFlowIntegrationTest` porque ya no existe su fuente. |
| Compose limpio | `docker compose -p ticketya_e1_core_20261111 up --build -d --wait` y `ps --format json` | 11/11 servicios `healthy`, incluidas las imágenes recompiladas de Catalog y Purchase. |
| Colección completa por Gateway | `postman collection run postman/ticketya.postman_collection.json --env-var baseUrl=http://localhost:8080 --no-report-events` | 42 requests, 72 aserciones, 0 fallos. Incluye compra aprobada y rechazada, compra numerada, devoluciones y cancelación. |
| Muchas compras del mismo asiento por Gateway | `python postman/concurrent-purchases.py` | 12 compras simultáneas, una `201 CONFIRMED`, 11 respuestas `409`, un asiento `SOLD` y contadores `sold=1`, `reserved=0`. Evento nuevo `bbe6adb6-f090-4f2f-8f71-76fd61b7e8bf`. |

La prueba multi-instancia de Inventory sigue siendo evidencia complementaria de la exclusión entre procesos. El stack Compose de esta corrida usa un nombre de proyecto nuevo y conserva sus volúmenes; no se borraron volúmenes existentes. Las pruebas adicionales de compra vencida E2E, fallos parciales downstream y reintento QR siguen abiertas en `establish-purchase-and-ticketing`.

## Evidencia de liberación por vencimiento (US-E1-05, tarea 3.1)

Fecha: 2026-11-11. Se revisaron implementación, pruebas fuente y XML Surefire de Inventory, sin modificar código ni ejecutar Maven nuevamente. Los XML son posteriores al último commit del módulo (`56b4c55`, 15:42 -0300) y no hay diferencias locales en su código o tests.

| Clase | Tests | Fallos | Errores | Omitidos | Evidencia |
|---|---:|---:|---:|---:|---|
| `ReservationLifecycleContractTest` | 9 | 0 | 0 | 0 | Una reserva vencida no se confirma; la consulta de disponibilidad libera el asiento. La liberación posterior devuelve `EXPIRED` y mantiene los contadores de venta en cero. |
| `HoldTtlContractTest` | 5 | 0 | 0 | 0 | El vencimiento se configura por evento: 600 s por defecto y 300 s tras configurarlo. Esta clase comprueba el TTL, no la liberación. |
| `InventoryAvailabilityContractTest` | 8 | 0 | 0 | 0 | Tras forzar el vencimiento, el asiento queda `AVAILABLE` y el cupo general recupera su disponibilidad completa. |
| `InventoryConcurrencyContractTest` | 4 | 0 | 0 | 0 | Dos instancias disparan la expiración concurrente; la reserva queda `EXPIRED`, el asiento `AVAILABLE` y los contadores generales en cero. |

`ReservationExpiryService` cambia a `EXPIRED` sólo reservas activas vencidas y, en la misma transacción, libera asientos y cupos generales. `AvailabilityService` y `ReservationService.create` disparan esa expiración al consultar disponibilidad o intentar otra reserva. El SQL permite retener de nuevo un asiento `AVAILABLE` y reservar cupo general cuando el contador recupera capacidad. Esto respalda que el lugar vuelve a estar disponible para otra compra; no se ejecutó una compra E2E de vencimiento, que no es una prueba específica exigida por el PDF para Entrega 1. No se requirió corregir el comportamiento.

## Verificación de US-E1-05

Fecha: 2026-11-11. Se reutilizó el stack Compose existente `ticketya_verification`; no se creó uno nuevo en esta corrida. El usuario interrumpió la repetición del reactor Maven porque los cambios de este turno se limitaron a documentación y Postman.

| Comprobación | Resultado de este turno |
|---|---|
| Compose existente `ticketya_verification` | 11 servicios saludables |
| Colección Postman por Gateway | 42 requests, 72 aserciones, 0 fallos; incluye compra aprobada de asiento numerado con QR |

La última verificación completa de Maven sigue siendo la del cierre F7: `mvn -q verify` con Java 21, Testcontainers y PostgreSQL real ejecutó 221 pruebas actuales, sin fallos, errores ni omisiones. **No se repitió en este turno** y no debe presentarse como validación posterior a los cambios de US-E1-05.

El flujo E1 crea un lugar y un evento, publica ofertas, consulta disponibilidad y compra entradas generales y un asiento numerado con pago aprobado y tickets con QR. La colección también conserva los recorridos existentes de pago rechazado, idempotencia, devoluciones y cancelación con reintegros. Esos recorridos son la base de regresión: deben seguir pasando aunque el trabajo nuevo de US-E1-05 se cubra sólo en el camino feliz.

Para verificar en un entorno local sin modificar los volúmenes de otros proyectos, ejecutar desde la raíz:

```bash
mvn verify
docker compose -p ticketya_e1_verification up --build -d --wait
docker compose -p ticketya_e1_verification ps
npx --yes newman run postman/ticketya.postman_collection.json --env-var baseUrl=http://localhost:8080
docker compose -p ticketya_e1_verification down
```

Comprobar los XML recientes de Surefire para confirmar que las clases Testcontainers ejecutaron pruebas y no quedaron omitidas. El informe histórico de Inventory que sigue registra una corrida anterior; sus 116 pruebas y cinco servicios no representan el stack actual.

## Informe histórico: Establish Inventory and Reservations

Fecha: 2026-11-07  
Cambio: `establish-inventory-and-reservations` (E1-02, PDF-F4, PDF-F5)  
Estado: TODAS LAS VERIFICACIONES PASAN ✓

## Resumen

Se ejecutó la verificación integrada del segundo incremento de TicketYa: inventario de disponibilidad y reservas temporales. Inventory materializa su inventario desde el snapshot publicado de Catalog (sin copiar precios), expone la disponibilidad pública a través del Gateway y mantiene los comandos de reserva en `/internal/**`, fuera del Gateway. La garantía de no doble venta se apoya en PostgreSQL: actualizaciones condicionales, constraints y transacciones, no en caché ni en estado de proceso.

## 1. Build con Maven

Comando: `mvn verify` desde la raíz de `ticketya/`

Resultado: **BUILD SUCCESS**

| Módulo | Tests | Fallos | Errores | Omitidos |
|---|---|---|---|---|
| api-gateway | 25 | 0 | 0 | 0 |
| catalog-service | 32 | 0 | 0 | 0 |
| inventory-service | 59 | 0 | 0 | 0 |
| ticketya (agregador) | — | — | — | — |

Total: **116 tests, 0 fallos, 0 errores, 0 omitidos**.

Entorno:
- JDK 19 (Amazon Corretto 19.0.2)
- Maven 3.9.15
- Docker 29.7.2 (requerido por los tests de Testcontainers)

### 1.1 Desglose de Inventory

| Clase de test | Tests | Qué verifica |
|---|---|---|
| `InventoryAvailabilityContractTest` | 8 | Disponibilidad pública, orden estable, asientos retenidos, vencidos, `404` sin snapshot, ausencia de precios |
| `ReservationContractTest` | 10 | Creación de reservas: asientos, general, último cupo, idempotencia, conflictos, rollback total |
| `ReservationLifecycleContractTest` | 9 | Confirmación, liberación y expiración: transiciones, repetición idempotente, estados inválidos, `404` |
| `ReservationQueryContractTest` | 4 | Consulta por `orderId` y por `reservationId`, vencimiento e ítems |
| `ReservationFingerprintContractTest` | 1 | Fingerprint canónico: el orden de ítems no altera la idempotencia; otro payload con el mismo `orderId` es `409` |
| `HoldTtlContractTest` | 5 | TTL predeterminado 600, actualización a 300, rango 60–3600, `404` sin inventario materializado |
| `InventoryHealthContractTest` | 1 | Contexto completo con PostgreSQL real y actuator `UP` con correlation ID |
| `InventorySchemaConstraintTest` | 3 | Constraints del esquema: duplicados de sector y asiento, tipos inválidos, contadores por encima de capacidad |
| `OpenApiDocumentContractTest` | 9 | `docs/openapi.yaml`: paths, códigos, esquemas, problem+json y ausencia de precios |
| `OpenApiRuntimeParityTest` | 5 | Paridad entre springdoc y `docs/openapi.yaml`, incluido el contrato interno de snapshot |
| `InventoryConcurrencyContractTest` | 4 | Dos instancias sobre la misma base compitiendo por asiento, último cupo, confirmación y expiración |
| **Total** | **59** | |

### 1.2 Desglose de Catalog y Gateway

Catalog suma 32 tests; 5 corresponden a `CatalogSnapshotContractTest` (snapshot publicado, sin precios, inmutable tras cambiar la plantilla, `404` para inexistente o no publicado).

Gateway suma 25 tests; 6 corresponden a `GatewayInventoryContractTest` (routing de `/api/inventory/**`, precedencia sobre `/api/**`, `/internal/**` no publicado, propagación de `X-Correlation-Id` y preservación de `application/problem+json`).

## 2. Concurrencia multi-instancia

`InventoryConcurrencyContractTest` levanta dos contextos de Spring Boot de Inventory sobre el mismo PostgreSQL de Testcontainers y dispara solicitudes simultáneas alternando ambas instancias. Resultados de la corrida:

| Escenario | Solicitudes | Resultado |
|---|---|---|
| Mismo asiento | 8 concurrentes (4 por instancia) | 1 × `201`, 7 × `409`; un único asiento `HELD` y una única reserva `ACTIVE` |
| Último cupo general | 6 concurrentes, con 2 cupos ya retenidos sobre capacidad 3 | 1 × `201`, 5 × `409`; `sold + reserved = 3 = capacity`; disponible `0` |
| Confirmación concurrente | 6 confirmaciones de la misma reserva | 6 × `200` con estado `CONFIRMED`; asiento `SOLD`, `sold=1`, `reserved=0`, 2 ítems |
| Reintento de creación | 6 repeticiones del mismo `orderId` tras confirmar | 6 × `200` con el mismo `reservationId`; una sola fila en `reservations` y `sold` sin cambios |
| Expiración perezosa concurrente | 6 consultas de disponibilidad desde ambas instancias | 6 × `200`; reserva `EXPIRED` una sola vez, asiento `AVAILABLE`, contadores en cero |

Conclusión: la no doble venta no depende del proceso. Las actualizaciones condicionales (`UPDATE ... WHERE status = 'AVAILABLE'` y contadores con guardas de capacidad) más las constraints del esquema serializan la competencia entre instancias.

## 3. Contrato OpenAPI

- Archivo versionado: `docs/openapi.yaml` (8 paths, 14 schemas).
- Validación: `npx --yes @redocly/cli@latest lint docs/openapi.yaml` → **API description is valid**, con 4 warnings esperados de `no-ambiguous-paths` (convivencia de `by-order/{orderId}` con `{reservationId}/...`).
- Runtime de Inventory: `/v3/api-docs` expone los 8 paths. El path `/internal/events/{eventId}/snapshot` es documentación del contrato de Catalog dentro del runtime de Inventory (Inventory no lo sirve).
- Tests de contrato: `OpenApiDocumentContractTest` y `OpenApiRuntimeParityTest` fallan si cambian paths, códigos o esquemas sin actualizar el YAML.

| Path | Método | Exposición |
|---|---|---|
| `/api/inventory/events/{eventId}/availability` | GET | Pública, vía Gateway |
| `/internal/reservations` | POST | Interna |
| `/internal/reservations/by-order/{orderId}` | GET | Interna |
| `/internal/reservations/{reservationId}` | GET | Interna |
| `/internal/reservations/{reservationId}/confirm` | POST | Interna |
| `/internal/reservations/{reservationId}/release` | POST | Interna |
| `/internal/events/{eventId}/hold-ttl` | PUT | Interna |
| `/internal/events/{eventId}/snapshot` | GET | Interna (Catalog) |

## 4. Construcción de imágenes Docker

Comando: `docker compose build` desde la raíz de `ticketya/`

| Imagen | Estado |
|---|---|
| `ticketya-api-gateway:latest` | Built |
| `ticketya-catalog-service:latest` | Built |
| `ticketya-inventory-service:latest` | Built |

Cada imagen ejecuta `mvn verify` en su etapa de build. El Dockerfile de Inventory usa el contexto raíz para copiar `docs/openapi.yaml`, de modo que `OpenApiDocumentContractTest` corre también dentro de la imagen. Los tests con `@Testcontainers(disabledWithoutDocker = true)` se omiten en el build porque no hay socket Docker disponible dentro del contenedor de build; se ejecutan completos en `mvn verify` desde el host.

## 5. Levantamiento con Docker Compose

Comando: `docker compose down -v && docker compose up -d --build --wait`

Resultado: los 5 servicios quedan `Healthy`.

| Servicio | Puerto | Healthcheck | Estado |
|---|---|---|---|
| postgres | 5432 (interno) | `pg_isready` | Healthy |
| inventory-db | 5432 (interno) | `pg_isready` | Healthy |
| catalog-service | 8081 (interno) | `wget /actuator/health` | Healthy |
| inventory-service | 8082 (interno) | `wget /actuator/health` | Healthy |
| api-gateway | 8080 (publicado) | `wget /actuator/health` | Healthy |

El Gateway es el único servicio con puerto publicado al host (`${GATEWAY_PORT:-8080}:8080`). Catalog, Inventory, `postgres` e `inventory-db` sólo son accesibles por la red de Compose. `/internal/**` no tiene ruta en el Gateway.

## 6. Ejecución de Postman vía Gateway

Comando: `npx --yes newman run postman/ticketya.postman_collection.json --env-var baseUrl=http://localhost:8080`

Resultado: **13 requests, 31 aserciones, 0 fallos**

| # | Request | Método | Ruta | Estado |
|---|---|---|---|---|
| 01 | Crear lugar | POST | `/api/venues` | 201 |
| 02 | Crear borrador | POST | `/api/management/events` | 201 |
| 03 | Editar borrador | PUT | `/api/management/events/{id}` | 200 |
| 04 | Configurar ofertas | PUT | `/api/management/events/{id}/offerings` | 200 |
| 05 | Publicar | POST | `/api/management/events/{id}/publish` | 200 |
| 06 | Buscar publicados | GET | `/api/events` | 200 |
| 07 | Detalle público | GET | `/api/events/{id}` | 200 |
| 08 | Disponibilidad de Inventory | GET | `/api/inventory/events/{eventId}/availability` | 200 |
| 09 | Suspender | POST | `/api/management/events/{id}/suspend` | 200 |
| 10 | Detalle tras suspensión | GET | `/api/events/{id}` | 404 |
| 11 | Reanudar | POST | `/api/management/events/{id}/resume` | 200 |
| 12 | Detalle tras reanudación | GET | `/api/events/{id}` | 200 |
| 13 | Eco CorrelationId | POST | `/api/management/events/{id}/suspend` | 200 |

La request 08 valida que Inventory materializa perezosamente el inventario desde el snapshot publicado, devuelve sectores numerados y generales con sus asientos, no expone `price` y propaga `X-Correlation-Id`. Todos los requests van exclusivamente por el Gateway (puerto 8080).

## 7. Smoke test interno de reservas

Ejecutado dentro de la red de Compose (sin publicar puertos), sobre el evento publicado por la colección Postman:

1. `GET /api/inventory/events/{eventId}/availability` → 2 sectores: numerado (3 asientos) y general (capacidad 500).
2. `POST /internal/reservations` con 1 asiento numerado + 2 generales → `201`, estado `ACTIVE`, 2 ítems.
3. `POST /internal/reservations/{reservationId}/confirm` → `200`, estado `CONFIRMED`.
4. `GET /v3/api-docs` → expone `/api/inventory/events/{eventId}/availability`, `/internal/reservations`, `/internal/events/{eventId}/hold-ttl` y `/internal/events/{eventId}/snapshot`.

También se verificó que el Gateway no publica `/internal/**` (sin ruta configurada para ese prefijo).

## 8. Limpieza

Comando: `docker compose down` (sin `-v` para conservar volúmenes) o `docker compose down -v` para reset completo de `catalog-postgres-data` e `inventory-postgres-data`.

## 9. Checklist de Definition of Done (§21 del blueprint)

| Criterio | Evidencia |
|---|---|
| Dockerfile propio e imagen construida individualmente | `services/inventory-service/Dockerfile`; `docker compose build` construye `ticketya-inventory-service` |
| Compose raíz declara el componente y sus dependencias | `inventory-service`, `inventory-db`; depende de `inventory-db` y `catalog-service` sanos |
| `docker compose up --build` levanta el incremento con healthchecks sanos | 5/5 servicios `Healthy` |
| Configuración por variables de entorno, sin rutas ni secretos | `DB_URL`, `DB_USER`, `DB_PASSWORD`, `CATALOG_SERVICE_URL`, `INVENTORY_HOLD_TTL_SECONDS`, `SERVER_PORT` |
| README documenta build, ejecución y pruebas | Secciones de Inventory, variables, Compose, flujo interno, OpenAPI y reset de volúmenes |
| OpenAPI y Postman actualizados y ejecutados por el Gateway | `docs/openapi.yaml` validado; Newman 13 requests / 31 aserciones por el puerto 8080 |
| Tests unitarios, de integración y de concurrencia aplicables | 116 tests verdes, incluidos los 4 escenarios multi-instancia |
| `correlationId` propagado y sin datos sensibles | Tests de correlation ID en los 3 servicios; disponibilidad y errores sin precios ni trazas |

## Conclusión

Todas las verificaciones del cambio `establish-inventory-and-reservations` pasan:

- ✅ `mvn verify` desde la raíz: 116 tests, 0 fallos (25 Gateway + 32 Catalog + 59 Inventory)
- ✅ Concurrencia multi-instancia: 4 escenarios, sin doble venta ni doble liberación
- ✅ `docs/openapi.yaml` válido con Redocly y en paridad con el runtime de springdoc
- ✅ `docker compose build` para las 3 imágenes
- ✅ `docker compose up -d --build --wait`: 5 servicios `Healthy`, sólo el Gateway publicado
- ✅ Newman vía Gateway: 13 requests, 31 aserciones, 0 fallos
- ✅ Smoke test interno de reserva y confirmación, y OpenAPI interno de Inventory
- ✅ DoD de §21 del blueprint cubierto y sin desviaciones

El cambio queda listo para archivado tras la revisión del equipo.
