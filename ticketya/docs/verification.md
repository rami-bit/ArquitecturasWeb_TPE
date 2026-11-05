# Verificación integrada - Establish Event Catalog

Fecha: 2026-11-05  
Cambio: `establish-event-catalog`  
Estado: TODAS LAS VERIFICACIONES PASAN ✓

## Resumen

Se ejecutó la verificación integrada completa del primer incremento de TicketYa (catálogo de eventos) desde un checkout limpio, siguiendo las instrucciones del `README.md`. Todas las verificaciones pasan.

## 1. Build con Maven

Comando: `mvn verify` desde la raíz de `ticketya/`

Resultado: **BUILD SUCCESS**

| Módulo | Tests | Fallos | Errores | Tiempo |
|---|---|---|---|---|
| api-gateway | 19 | 0 | 0 | 31s |
| catalog-service | 27 | 0 | 0 | 43s |
| ticketya (agregador) | — | — | — | 0s |

Total: 46 tests, 0 fallos, 0 errores.

Entorno:
- JDK 19 (Amazon Corretto 19.0.2)
- Maven 3.9.15
- Java version reportada: 19.0.2

## 2. Construcción de imágenes Docker

Comando: `docker compose build` desde la raíz de `ticketya/`

Resultado: Ambas imágenes construidas exitosamente.

| Imagen | Estado |
|---|---|
| `ticketya-api-gateway:latest` | Built |
| `ticketya-catalog-service:latest` | Built |

Cada imagen ejecuta `mvn verify` internamente en su etapa de build (los tests de Testcontainers se saltean sin socket Docker dentro del build, pero los tests de persistencia contra H2 y los de integuración de endpoint pasan).

## 3. Levantamiento con Docker Compose

Comando: `docker compose down -v && docker compose up -d --wait` desde la raíz de `ticketya/`

Resultado: Todos los servicios saludables.

| Servicio | Puerto | Healthcheck | Estado |
|---|---|---|---|
| postgres | 5432 (interno) | `pg_isready` | Healthy |
| catalog-service | 8081 (interno) | `wget /actuator/health` | Healthy |
| api-gateway | 8080 (publicado) | `wget /actuator/health` | Healthy |

El Gateway expone el único puerto publicado (`8080`). Catalog Service y PostgreSQL no publican puertos al host.

## 4. Validación de OpenAPI

Comando: `docker compose exec -T catalog-service wget -qO- http://localhost:8081/v3/api-docs`

Resultado: OpenAPI 3.0.1 disponible internamente en Catalog Service. Contiene los paths del flujo:

| Path | Método | Tag |
|---|---|---|
| `/api/venues` | GET, POST | Venues |
| `/api/venues/{id}` | PUT | Venues |
| `/api/management/events` | GET, POST | Event management |
| `/api/management/events/{id}` | GET, PUT | Event management |
| `/api/management/events/{id}/offerings` | PUT | Event management |
| `/api/management/events/{id}/publish` | POST | Event management |
| `/api/management/events/{id}/suspend` | POST | Event management |
| `/api/management/events/{id}/resume` | POST | Event management |
| `/api/events` | GET | Public event discovery |
| `/api/events/{eventId}` | GET | Public event discovery |

OpenAPI y Swagger UI no se publican por el Gateway (opción A validada: chequeo interno del contenedor).

## 5. Ejecución de Postman vía Gateway

Comando: `npx --yes newman run postman/ticketya.postman_collection.json --env-var baseUrl=http://localhost:8080`

Resultado: **12 requests ejecutadas, 27 aserciones, 0 fallos**

| # | Request | Método | Ruta | Estado |
|---|---|---|---|---|
| 01 | Crear lugar | POST | `/api/venues` | 201 |
| 02 | Crear borrador | POST | `/api/management/events` | 201 |
| 03 | Editar borrador | PUT | `/api/management/events/{id}` | 200 |
| 04 | Configurar ofertas | PUT | `/api/management/events/{id}/offerings` | 200 |
| 05 | Publicar | POST | `/api/management/events/{id}/publish` | 200 |
| 06 | Buscar publicados | GET | `/api/events` | 200 |
| 07 | Detalle público | GET | `/api/events/{id}` | 200 |
| 08 | Suspender | POST | `/api/management/events/{id}/suspend` | 200 |
| 09 | Detalle tras suspensión | GET | `/api/events/{id}` | 404 |
| 10 | Reanudar | POST | `/api/management/events/{id}/resume` | 200 |
| 11 | Detalle tras reanudación | GET | `/api/events/{id}` | 200 |
| 12 | Eco CorrelationId | POST | `/api/management/events/{id}/suspend` | 200 |

Todos los requests van exclusivamente a través del API Gateway (puerto 8080). Ninguno llama directamente a Catalog Service (8081).

Datos repetibles: cada corrida genera un `runId` único para nombre de lugar y participante, evitando colisiones entre ejecuciones.

## 6. Limpieza

Comando: `docker compose down` (sin `-v` para conservar el volumen si se desea inspeccionar).

Para reset completo: `docker compose down -v`.

## Conclusión

Todas las verificaciones de la tarea 6.5 pasan:
- ✅ `mvn verify` desde la raíz y cada servicio
- ✅ Construcción de imágenes Docker
- ✅ `docker compose up --build` con servicios saludables
- ✅ Validación de OpenAPI interna en contenedor
- ✅ Ejecución de Postman/Newman vía Gateway (27/27 aserciones)
- ✅ Evidencia registrada en este archivo

El cambio `establish-event-catalog` está listo para archivado tras revisión del equipo.
