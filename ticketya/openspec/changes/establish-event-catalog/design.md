# Design

## Context

El repositorio todavía no contiene implementación. La Entrega 1 exige microservicios Spring Boot independientes, API Gateway, persistencia relacional por servicio, APIs REST y ejecución local. Esta épica cubre el primer corte vertical del catálogo; véanse `proposal.md` y las specs de `venue-management`, `event-management` y `event-discovery` para el comportamiento esperado.

Java 19 y Maven son estándares del proyecto. Las rutas personales de JDK no se guardarán en archivos versionados. La Entrega 1 identifica temporalmente al usuario mediante encabezados y difiere JWT para la Entrega 2.

## Goals / Non-Goals

**Goals:**

- Establecer una estructura de repositorio con proyectos Spring Boot construibles y ejecutables por separado.
- Entregar un flujo completo a través del Gateway: administrar lugares, preparar un evento, publicarlo y descubrirlo públicamente.
- Mantener una única autoridad para los datos de catálogo y producir snapshots adecuados para inicializar inventario en una épica posterior.
- Definir contratos, errores, trazabilidad básica y pruebas suficientes para evolucionar sin romper clientes.

**Non-Goals:**

- Implementar Inventory, Purchase, Ticket, Payment Mock o servicios de Entrega 2.
- Exponer disponibilidad de entradas o derivar `SOLD_OUT`.
- Implementar cancelación, finalización, cambios posteriores a publicación o notificaciones.
- Introducir JWT, mensajería, Redis, MongoDB, cache, service discovery o Elasticsearch.
- Contener rutas locales de herramientas o configuración personal del equipo.

## Decisions

### 1. Dos proyectos Spring Boot independientes en un monorepo Maven

La primera épica tendrá `services/api-gateway` y `services/catalog-service`, cada uno con su propio `pom.xml`, empaquetado y configuración. Un `pom.xml` agregador en la raíz permitirá construirlos juntos sin impedir builds independientes.

```text
ticketya/
+-- pom.xml
+-- services/
|   +-- api-gateway/
|   |   +-- pom.xml
|   +-- catalog-service/
|       +-- pom.xml
+-- infra/
|   +-- compose.yaml
+-- docs/
+-- postman/
+-- openspec/
```

Se usará Java 19, Maven, Spring Boot 3.x compatible con Java 19 y una versión de Spring Cloud compatible con la versión elegida de Spring Boot. Las versiones se fijarán en los POM; no se usarán rangos dinámicos.

**Alternativa considerada:** repositorios separados. Se descarta para simplificar la entrega académica, ejecución y revisión, manteniendo igualmente independencia de construcción.

### 2. API Gateway como único punto de entrada externo

Spring Cloud Gateway publicará `/api/**` y en esta épica enviará las rutas de catálogo a Catalog Service. Los clientes y la colección Postman nunca llamarán directamente al puerto interno del servicio.

El Gateway generará o propagará `X-Correlation-Id`. Durante Entrega 1 propagará `X-User-Id` y `X-User-Role`; Catalog Service seguirá validando presencia, rol y ownership porque la autorización es una regla del dominio. Estos encabezados no se consideran seguridad real y serán reemplazados por claims verificados en Entrega 2.

**Alternativa considerada:** autorización sólo en el Gateway. Se descarta porque una llamada interna o una futura ruta mal configurada podría omitir reglas de ownership.

### 3. Catalog Service concentra el bounded context de catálogo

Un único servicio será dueño de lugares, plantillas físicas, eventos, participantes, ofertas por sector, snapshots y búsqueda. Separar lugares o búsqueda ahora agregaría coordinación distribuida sin una necesidad de escala independiente en la primera entrega.

Internamente se organizará por responsabilidades:

```text
catalog
+-- api              controllers, DTOs y manejo de errores
+-- application      casos de uso y límites transaccionales
+-- domain           entidades, estados, permisos y validaciones
+-- infrastructure   JPA, búsqueda PostgreSQL y adaptadores
```

Las APIs responderán errores con `application/problem+json`, incluyendo al menos `status`, `title`, `detail`, `instance` y `correlationId`.

### 4. PostgreSQL relacional y ownership exclusivo

Catalog Service tendrá una base PostgreSQL propia y la administrará con Flyway. Ningún otro servicio accederá directamente a sus tablas. Las relaciones dentro del bounded context sí usarán foreign keys y restricciones de unicidad.

Modelo lógico abreviado:

```text
VENUE 1 ----- N VENUE_SECTOR 1 ----- N VENUE_SEAT
  |
  +----- N EVENT (mientras es DRAFT referencia la plantilla)
              |
              +----- N EVENT_PARTICIPANT ----- 1 PARTICIPANT
              |
              +----- N EVENT_SECTOR_OFFERING
              |
              +----- 0..1 EVENT_SNAPSHOT
                              |
                              +----- N SNAPSHOT_PARTICIPANT
                              +----- N SNAPSHOT_SECTOR
                                             |
                                             +----- N SNAPSHOT_SEAT
```

Restricciones relevantes:

- UUID como identificador público.
- `(venue_sector_id, row_label, seat_number)` único para asientos numerados.
- Capacidad de sector general mayor que cero.
- Precio persistido como decimal exacto, nunca punto flotante.
- Un único `EVENT_SNAPSHOT` por evento mediante restricción única.
- Fechas persistidas como instantes y expuestas con offset explícito.
- Índices sobre estado, fecha, tipo, organizador, ciudad y relaciones de participantes.

### 5. Snapshot relacional e inmutable al publicar

El snapshot se guardará en tablas normalizadas, no como una referencia viva al lugar ni como un único JSON opaco. Esto permite constraints, consultas por precio y reutilización posterior por Inventory Service.

La publicación será un único caso de uso transaccional dentro de Catalog Service:

```text
Organizer/Admin       Gateway          Catalog Service        PostgreSQL
      |                   |                   |                    |
      | POST publish      |                   |                    |
      |------------------>|------------------>|                    |
      |                   |                   | validate draft     |
      |                   |                   |------------------->|
      |                   |                   | copy snapshot      |
      |                   |                   |------------------->|
      |                   |                   | DRAFT->PUBLISHED   |
      |                   |                   |------------------->|
      |                   |                   | commit             |
      |                   |                   |<-------------------|
      | 200 published     |<------------------|                    |
      |<------------------|                   |                    |
```

Si cualquier inserción falla, la transacción revierte snapshot y estado. Una unique constraint sobre `event_id` impide snapshots duplicados. Repetir `publish` sobre un evento ya publicado devuelve la representación existente sin volver a copiar datos.

Después de publicar, los datos del evento y snapshot no se editan. Sólo se actualiza el estado mediante transiciones permitidas:

```text
DRAFT --publish--> PUBLISHED --suspend--> SUSPENDED
                         ^                    |
                         +------resume--------+
```

`SOLD_OUT`, `CANCELLED` y `FINISHED` forman parte del modelo, pero esta épica no expone comandos para alcanzarlos.

**Alternativas consideradas:**

- Bloquear la edición de la plantilla del lugar cuando tiene eventos publicados: demasiado restrictivo.
- Snapshot JSONB: simple de copiar, pero peor para constraints, orden por precio y consumo futuro.
- Crear inventario durante `publish`: acopla esta épica a un servicio que todavía no existe.

### 6. Búsqueda en PostgreSQL

Catalog Service resolverá filtros y ordenamientos en PostgreSQL. La consulta siempre incluirá `status = PUBLISHED` y combinará filtros con semántica AND.

- Fecha: campo temporal del snapshot, con `event_id` como desempate.
- Precio: mínimo precio de `SNAPSHOT_SECTOR`, con `event_id` como desempate.
- Relevancia: búsqueda textual de PostgreSQL sobre nombre, descripción y participantes; requiere texto de consulta.
- Paginación: page/size con límite máximo documentado y metadata total.

La implementación podrá usar `tsvector` y un índice GIN. No se cacheará disponibilidad ni se incorporará Elasticsearch.

**Alternativa considerada:** Elasticsearch. Se descarta por costo operativo y duplicación de datos desproporcionados para Entrega 1.

### 7. Contrato REST inicial

Rutas externas propuestas:

| Método | Ruta | Acceso | Resultado principal |
|---|---|---|---|
| `POST` | `/api/venues` | ADMIN | Crear lugar |
| `PUT` | `/api/venues/{venueId}` | ADMIN | Modificar plantilla |
| `GET` | `/api/management/venues` | ORGANIZER, ADMIN | Listar lugares configurables |
| `GET` | `/api/management/venues/{venueId}` | ORGANIZER, ADMIN | Ver plantilla |
| `POST` | `/api/management/events` | ORGANIZER, ADMIN | Crear borrador |
| `PUT` | `/api/management/events/{eventId}` | Owner, ADMIN | Editar borrador |
| `PUT` | `/api/management/events/{eventId}/offerings` | Owner, ADMIN | Configurar sectores y precios |
| `POST` | `/api/management/events/{eventId}/publish` | Owner, ADMIN | Publicar |
| `POST` | `/api/management/events/{eventId}/suspend` | ADMIN | Suspender |
| `POST` | `/api/management/events/{eventId}/resume` | ADMIN | Reanudar |
| `GET` | `/api/management/events` | ORGANIZER, ADMIN | Listado de gestión |
| `GET` | `/api/management/events/{eventId}` | Owner, ADMIN | Detalle de gestión |
| `GET` | `/api/events` | Público | Buscar publicados |
| `GET` | `/api/events/{eventId}` | Público | Detalle publicado |

Códigos generales: `200/201` para éxito, `400` para entrada inválida, `403` para permisos, `404` para recurso inexistente o no visible públicamente, `409` para transición o estado incompatible y `500` para fallos inesperados.

### 8. OpenAPI y pruebas forman parte del incremento

Cada endpoint y esquema de error se documentará con springdoc-openapi, incluyendo ejemplos. La colección Postman consumirá sólo el Gateway.

Estrategia de pruebas:

- Unitarias: validaciones de sectores, permisos, estado e invariantes de publicación.
- Persistencia: repositorios, constraints, snapshot y queries de búsqueda contra PostgreSQL real con Testcontainers.
- Integración de Catalog Service: endpoints, errores, ownership y rollback de publicación.
- Integración a través del Gateway: routing, propagación de identidad y `correlationId`.
- Contrato: OpenAPI generado y ejemplos principales ejecutables desde Postman.

## Risks / Trade-offs

- **[Java 19 no es LTS]** -> Fijar versión en build y CI, documentar sólo el requisito `JDK 19` y evitar APIs experimentales.
- **[Encabezados de rol pueden falsificarse]** -> Aceptarlo sólo como mecanismo de Entrega 1, mantener autorización en dominio y reemplazarlo por JWT en Entrega 2.
- **[El snapshot duplica datos]** -> Aceptar la duplicación deliberada para garantizar estabilidad histórica; constraints y transacción evitan copias incompletas.
- **[Búsqueda textual puede degradarse]** -> Indexar filtros y texto, medir planes de consulta y mantener paginación y tamaño máximo.
- **[Cambios en lugares pueden confundir borradores]** -> Revalidar toda referencia y copiar la versión vigente exclusivamente al publicar.
- **[No hay inventario al publicar]** -> El snapshot queda preparado como contrato de entrada; la inicialización de inventario se implementará en la próxima épica sin exponer disponibilidad falsa.

## Migration Plan

1. Crear la estructura Maven y los dos proyectos independientes.
2. Levantar PostgreSQL de catálogo y aplicar migraciones Flyway desde un esquema vacío.
3. Implementar y verificar Catalog Service antes de publicar rutas externas.
4. Configurar Gateway y validar todos los flujos exclusivamente por `/api`.
5. Cargar datos de prueba y publicar la colección Postman.

Como el proyecto parte sin datos productivos, no se requiere migración histórica. El rollback consiste en detener Gateway y Catalog Service y eliminar únicamente la base local de desarrollo creada para esta épica; las migraciones aplicadas no se revertirán destructivamente en entornos compartidos.
