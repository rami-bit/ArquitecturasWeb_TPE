# Roadmap del Orquestador — Reparación post-merge + Épica PDF-F10

> Estado analizado: `main` en `6f1ed0b` (2026-11-20). No se modificó ningún archivo del repositorio.
> Referencia funcional: `TPE-ArqWeb-TicketYa-2026.md` (transcripción versionada del PDF; el PDF binario no es legible por el modelo).
> Workflow obligatorio: `OpenSpec.md` — explore → propose → update → apply → sync → archive.

## 0. Veredicto del análisis

### 0.1 Lo que sí está bien aplicado

| Capacidad | Evidencia | Estado |
|---|---|---|
| PDF-F6 Fila virtual | `queue-service` con PostgreSQL, posiciones bajo advisory lock, tandas por tiempo, token HMAC de un solo uso, `X-Admission-Token` en Purchase, ruta `/api/queue/**` en Gateway, 47 pruebas de queue (0 fallos, 0 omitidos) | Implementación sólida |
| PDF-F8 Acceso QR (E2-03) | Endpoints `/api/access/**`, marca atómica de uso, pruebas de concurrencia, Postman 32 y 37-42 | Implementado |
| PDF-F12 Reportes (E2-04) | `GET /api/reports/events/{eventId}`, ownership, degradación sin Inventory, pruebas y Postman 28-31/43 | Implementado |
| PDF-NF-TRACE (E2-02) | `audit-service` + `audit-db`, filtros de request, publicación asíncrona resiliente, `/api/audit/**` sólo ADMIN | Implementado |
| PDF-NF-SECURITY (E2-01) | JWT validado en Gateway, roles `CLIENT/ORGANIZER/ADMIN/ACCESS_STAFF`, identidad derivada, pruebas de seguridad | Implementado |

### 0.2 Lo que impide considerar el cierre como Done

| Problema | Evidencia concreta | Impacto |
|---|---|---|
| El contrato OpenAPI consolidado perdió las rutas y schemas de E2-02/03/04 | `docs/openapi.yaml` tiene 39 paths y 0 paths de `/api/access`, `/api/reports`, `/api/audit`; el commit `2649f08` sí los tenía (42 paths) | `ConsolidatedOpenApiDocumentContractTest`: 18 pruebas, **3 fallos** |
| El test de contrato quedó con una aserción obsoleta que prohíbe capacidades ya implementadas | `theContractDoesNotDeclareEntrega2Capabilities` falla porque el path `/api/queue/...` contiene `queue` | `mvn verify` no puede pasar |
| La colección Postman perdió el flujo de fila virtual | 68 requests, 119 aserciones, **0 requests a `/api/queue`**; el flujo existía en `4efdf23` y se perdió al fusionar `078356e` | F6 no tiene E2E por Gateway; el README afirma que sí |
| Existe un cambio OpenSpec duplicado y no archivable | `openspec/changes/fila-virtual` repite `virtual-queue` ya archivada; `openspec validate --all --strict` falla con “ADDED failed … already exists” | No se puede validar ni archivar; contradice la fuente de verdad |
| `api-gateway` no pasa validación estricta | 2 requisitos superan 500 caracteres (`requirements[3]` y `[5]`) | `openspec validate --all --strict`: 15/17 |
| TODO/NEXT y roadmaps están desactualizados | TODO dice “US-F6 pendiente (con Redis)”; NEXT lista F6 como pendiente; los roadmaps usan `/api/queue/join`, `/position`, `/trigger` (no existen) y dicen 17/17 passing | El orquestador tomaría decisiones sobre datos falsos |

**Conclusión:** F6 y las funcionalidades nuevas están implementadas en código y specs, pero **la integración post-merge no está cerrada**. Antes de empezar una épica nueva hay que reparar el contrato, la colección Postman, el cambio duplicado y la validación estricta.

## 1. Siguiente épica recomendada

**PDF-F10 — Preferencias y recomendaciones**, con **MongoDB** como base NoSQL obligatoria.

Motivos:

- Es la siguiente funcionalidad pendiente del PDF después de F6/F8/F12.
- Es el vehículo natural para cumplir “al menos un microservicio con base NoSQL” (`PDF-NF-PERSISTENCE`).
- Desbloquea F11 (notificación de artista favorito en la ciudad) y la estrategia de cache/recomendaciones.
- Los dos roadmaps existentes (`ROADMAP-Orquestador-F6.md`, `ROADMAP-Orquestador-Gratis.md`) **no cubren F10** y contienen estados/endpoints incorrectos; este roadmap los reemplaza para la próxima épica.

**Nota de numeración:** el blueprint `establish-ticketya-architecture` llama `E2-04` a preferencias, pero este repositorio ya usó `E2-04` para reportes. Para evitar ambigüedad, usar siempre `PDF-F10` y el nombre de cambio `add-preferences-and-recommendations`; no reutilizar `E2-04` en documentación nueva.

---

## 2. Fase 0 — Reparación de integración (bloqueante)

Objetivo: dejar `main` verde y coherente antes de agregar capacidades. No es una épica nueva; es cierre de deuda del merge.

### Tareas

> **Cierre Fase 0 (2026-11-20):** completada y verificada. `docs/openapi.yaml` 45 paths / 51 operaciones / 75 schemas; `ConsolidatedOpenApiDocumentContractTest` 18/18; colección Postman 85 requests con `F6-01`…`F6-17`; `openspec validate --all --strict` 17/17; `mvn verify` 388 pruebas, 0 fallos, 0 errores, 0 omitidos; Compose 15/15 healthy; Newman 94 requests, 147 aserciones, 0 fallos; `git diff --check` limpio. Evidencia en `NEXT.md` y `docs/verification.md`.

| # | Tarea | Dueño sugerido | Archivos | Criterio de aceptación |
|---|---|---|---|---|
| 0.1 | Reconciliar `docs/openapi.yaml`: recuperar de `2649f08` los 6 paths y 13 schemas de E2-02/03/04, conservando los paths de F6 y `X-Admission-Token` | spec-designer | `docs/openapi.yaml` | 42+ paths; `/api/access/**`, `/api/reports/**`, `/api/audit/**`, `/internal/audit-events` presentes; schemas `AccessValidation*`, `OrganizerEventReport`, `Audit*` presentes |
| 0.2 | Corregir `ConsolidatedOpenApiDocumentContractTest`: eliminar la aserción “no E2 capabilities” o limitarla a capacidades futuras (preferencias, recomendaciones, notificaciones); mantener las aserciones de E2 implementadas | contract-tester | `services/api-gateway/src/test/java/.../ConsolidatedOpenApiDocumentContractTest.java` | 18 pruebas, 0 fallos |
| 0.3 | Restaurar el flujo Postman de F6 desde `4efdf23`, adaptándolo a la colección actual (JWT, E2-02/03/04) | contract-tester | `postman/ticketya.postman_collection.json` | Requests de join/poll/leave y compra con admisión; variables `highDemandEventId`, `admissionToken`, `queueEntryId`; 0 fallos en Newman |
| 0.4 | Eliminar el cambio duplicado `openspec/changes/fila-virtual` (F6 ya está archivada) | general | `openspec/changes/fila-virtual/**` | `openspec list` sólo muestra cambios reales; no hay delta que intente re-agregar `virtual-queue` |
| 0.5 | Acortar/dividir los requisitos largos de `openspec/specs/api-gateway/spec.md` | spec-designer | `openspec/specs/api-gateway/spec.md` | `openspec validate --all --strict` sin warnings |
| 0.6 | Actualizar TODO/NEXT/README y marcar los roadmaps viejos como superados | general | `TODO.md`, `NEXT.md`, `README.md`, `ROADMAP-Orquestador-*.md` | F6 figura cerrada; E2-04 reportes cerrado; pendientes reales: F10, F11, cache, circuit breaker, carga, integración |
| 0.7 | Verificación completa post-reparación | general | — | `mvn verify` reactor verde; Compose 13/13 healthy; Newman 0 fallos; `openspec validate --all --strict`; `git diff --check` |

### Quality gate Fase 0

No se inicia F10 hasta que:

- `mvn verify` del reactor pase sin fallos ni omisiones.
- `ConsolidatedOpenApiDocumentContractTest` pase (18/18).
- La colección Postman incluya y ejecute el flujo F6.
- `openspec validate --all --strict` pase.
- `git diff --check` limpio.

---

## 3. Fase 1 — Épica PDF-F10: Preferencias y recomendaciones

### 3.1 Objetivo y alcance

Permitir que un cliente registre sus preferencias (tipos de evento, artistas favoritos, ciudad) y reciba recomendaciones explicables basadas en esas preferencias y en su historial de compras, incluyendo la señal “quienes compraron este evento también compraron…”.

**Incluido:**

- Alta/consulta de preferencias propias.
- Recomendaciones personalizadas con motivo y puntaje.
- Señal de co-compra a partir de órdenes confirmadas.
- Persistencia en MongoDB.
- Contrato OpenAPI, Postman, Gateway, Docker/Compose, pruebas y documentación.

**Excluido:**

- Machine learning, embeddings o motor de recomendaciones externo.
- Notificaciones (F11).
- Cache de recomendaciones (se justifica “sin cache” inicialmente).
- Interfaz gráfica.
- Historial de navegación o tracking de clics.

### 3.2 Decisiones de arquitectura a resolver en `explore`/`design`

| ID | Decisión | Recomendación inicial | Alternativa descartada |
|---|---|---|---|
| D1 | ¿Servicio propio o módulo? | `preferences-service` propio con `preferences-db` MongoDB | Módulo en Catalog (mezcla relacional con NoSQL y lectura pública); módulo en Purchase (mezcla órdenes con personalización) |
| D2 | Base NoSQL | MongoDB 7 oficial | PostgreSQL JSONB (no cumple el requisito explícito de al menos una NoSQL) |
| D3 | Señal de historial | Purchase expone un contrato interno de señales; Preferences no accede a su base | Copiar órdenes a MongoDB (duplica ownership y crea datos stale) |
| D4 | Candidatos de eventos | Catalog expone un contrato interno de candidatos publicados/futuros | N+1 contra el detalle público; cache de catálogo (fuera de alcance) |
| D5 | Fórmula | Afinidad simple + co-compra, explicable y determinista | ML o ranking opaco |
| D6 | Sin preferencias/historial | Fallback a eventos publicados próximos, con motivo `UPCOMING_FALLBACK` | Devolver lista vacía (mala experiencia) |
| D7 | Cache | Sin cache inicial; recomendaciones se calculan on-demand | Cache con TTL e invalidación (complejidad no justificada) |
| D8 | Consistencia | Lectura on-demand; recomendaciones son advisory, nunca autoridad de venta/disponibilidad | Proyección asíncrona con broker (fuera de línea base) |
| D9 | Seguridad | `CLIENT` autenticado; ruta `me` sin customerId; aislamiento por `sub` | CustomerId en path (riesgo de IDOR) |

### 3.3 Modelo de datos MongoDB

Base: `ticketya_preferences`. Colección: `customer_preferences`.

```json
{
  "_id": "UUID del cliente",
  "city": "Buenos Aires",
  "eventTypes": ["CONCERT", "THEATER"],
  "favoriteArtists": ["Artista A", "Artista B"],
  "updatedAt": "2026-11-20T12:00:00Z",
  "version": 3
}
```

- `_id` es el `customerId`; no se crea índice extra.
- Upsert atómico por documento; no requiere transacción multi-documento.
- Validación: ciudad no vacía si se envía; tipos contra el enum de Catalog; artistas sin duplicados, máximo configurable (p. ej. 20); longitud máxima de cada artista.
- No se persisten órdenes ni eventos: Purchase y Catalog siguen siendo dueños.

### 3.4 Contratos

**Públicos (por Gateway, JWT `CLIENT`):**

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| `PUT` | `/api/customers/me/preferences` | Alta/actualización idempotente de preferencias | `200`, `400`, `401`, `403` |
| `GET` | `/api/customers/me/preferences` | Consulta de preferencias propias | `200`, `401`, `403` |
| `GET` | `/api/customers/me/recommendations?limit=10` | Recomendaciones personalizadas | `200`, `400`, `401`, `403`, `503` |

**Internos (red de servicios, sin Gateway):**

| Servicio | Método | Ruta | Uso |
|---|---|---|---|
| Purchase | `GET` | `/internal/customers/{customerId}/recommendation-signals` | Devuelve `purchasedEventIds` y `coPurchases[{eventId,count}]` de órdenes confirmadas |
| Catalog | `GET` | `/internal/events/recommendation-candidates?limit=200` | Devuelve eventos publicados futuros con `id`, `name`, `eventType`, `startsAt`, `city`, `participants`, `minPrice` |

Reglas:

- `GET /api/customers/me/recommendations` no expone disponibilidad ni stock.
- Errores con `application/problem+json` y `X-Correlation-Id`.
- Límite `1..20`, default 10; fuera de rango `400`.
- Si Catalog no responde: `503`.
- Si Purchase no responde: se devuelven recomendaciones sólo por preferencias, con warning `HISTORY_UNAVAILABLE`.
- Si no hay preferencias ni historial: fallback `UPCOMING_FALLBACK`.

### 3.5 Algoritmo de recomendación

Candidatos: eventos `PUBLISHED`, con `startsAt > now`, que el cliente no haya comprado.

Puntaje explicable:

```text
score = 3 * (eventType ∈ favoriteEventTypes)
      + 2 * min(3, artistas del evento ∈ favoriteArtists)
      + 2 * (city == customer.city)
      + min(5, coPurchaseCount)
      + 1 * (startsAt dentro de 30 días)
```

- Orden: `score` descendente, `startsAt` ascendente, `eventId` ascendente.
- `reasons`: `FAVORITE_EVENT_TYPE`, `FAVORITE_ARTIST`, `SAME_CITY`, `CO_PURCHASE`, `UPCOMING_FALLBACK`.
- Sin ML; la fórmula y los pesos se documentan en `design.md` y se prueban con casos deterministas.
- La recomendación no reserva ni garantiza inventario.

### 3.6 Resiliencia, cache y trazabilidad

- Timeouts explícitos: conexión 2 s, lectura 5 s a Catalog y Purchase.
- Sin reintentos automáticos en lecturas de recomendación; fallback controlado.
- Sin cache inicial: se documenta en `design.md` que un miss/stale no debe afectar ventas y que la escala didáctica no justifica TTL/invalidación.
- `RequestLoggingFilter` y `X-Correlation-Id` en el servicio nuevo.
- No se publica auditoría de preferencias salvo que el equipo lo justifique; no es operación crítica del PDF.

### 3.7 Seguridad

- Gateway: ruta `/api/customers/**` antes del comodín `/api/**`, `hasRole("CLIENT")` (o `CLIENT`/`ADMIN` si se decide soporte).
- Servicio: resuelve `X-User-Id` UUID y `X-User-Role`; sólo `CLIENT` (y `ADMIN` si se acuerda).
- Ninguna ruta pública recibe `customerId`; el `sub` del JWT es la única identidad.
- Pruebas: sin token `401`, rol incorrecto `403`, cliente A no ve preferencias de B.

### 3.8 Artefactos OpenSpec

Cambio: `add-preferences-and-recommendations` (kebab-case).

- `proposal.md`: problema, valor, alcance, exclusiones, capacidades.
- `specs/customer-preferences/spec.md`: requisitos y escenarios de alta/consulta/aislamiento.
- `specs/event-recommendations/spec.md`: requisitos y escenarios de recomendación, co-compra, fallback y errores.
- `design.md`: D1-D9, modelo MongoDB, contratos, fórmula, resiliencia, cache, seguridad, despliegue.
- `tasks.md`: fases 1-8, cada tarea con prueba y documentación.
- Capacidades modificadas: `api-gateway`, `purchase-orders` (contrato interno de señales), `event-discovery` (contrato interno de candidatos), `containerized-deployment` (MongoDB + servicio).

Validación previa a `apply`: `openspec validate add-preferences-and-recommendations --strict`.

### 3.9 Tareas de implementación

| Fase | Tareas |
|---|---|
| 1. Explorar y decidir | Resolver D1-D9; confirmar MongoDB; confirmar contratos internos; registrar decisiones |
| 2. Proponer | Crear cambio OpenSpec; proposal/specs/design/tasks; validar strict |
| 3. Esqueleto | `services/preferences-service` (Spring Boot, Java 19/21, Spring Data MongoDB), `Dockerfile`, `pom.xml` raíz, `application.yml`, healthcheck |
| 4. Dominio | Documento `CustomerPreferences`, validación, upsert idempotente, errores problem+json |
| 5. Contratos internos | Purchase `recommendation-signals`; Catalog `recommendation-candidates`; clientes con timeouts y fallback |
| 6. Recomendación | Scorer, exclusión de comprados, co-compra, fallback, límite, orden y motivos |
| 7. API/Gateway | Controladores públicos, identidad/roles, ruta Gateway, OpenAPI, Postman |
| 8. Despliegue/docs | Compose `preferences-db` + `preferences-service`, `.env`, README, architecture, ER, secuencia |
| 9. Pruebas | Unitarias, integración MongoDB Testcontainers, contrato MockMvc, clientes con stubs, Gateway, Postman E2E |
| 10. Cierre | `mvn verify`, Compose aislado, Newman, `openspec validate --strict`, `git diff --check`, archivar |

### 3.10 Pruebas mínimas

- `RecommendationScorerTest`: tipo, artista, ciudad, co-compra, exclusión de comprados, límite, orden, fallback.
- `CustomerPreferencesMongoIntegrationTest`: Testcontainers MongoDB; upsert, lectura, aislamiento por cliente, validación.
- `PreferencesApiContractTest`: `PUT/GET`, `400`, `401`, `403`, formato problem+json, correlation ID.
- `RecommendationClientTest`: Catalog/Purchase OK, timeout, Purchase caído → warning, Catalog caído → 503.
- `GatewayPreferencesRoutingTest`: precedencia de ruta, `401`, `403`, propagación de identidad.
- Postman: alta de preferencias, consulta, recomendaciones, actualización cambia orden, cliente B no ve A, co-compra, fallback, rol incorrecto.
- `mvn verify` del reactor y `postman/concurrent-purchases.py` sin regresiones.

### 3.11 Definition of Done específica

- Todos los escenarios OpenSpec implementados.
- MongoDB real probado con Testcontainers; no H2 ni mocks para persistencia.
- OpenAPI consolidado con paths, schemas, ejemplos y errores.
- Postman actualizado y ejecutado por Gateway.
- Gateway y servicio autorizan por rol y aíslan por cliente.
- Sin cache de recomendaciones o con justificación explícita.
- Compose con `preferences-db` y `preferences-service` healthy.
- `openspec validate add-preferences-and-recommendations --strict` sin errores.
- Evidencia en `docs/verification.md` y `NEXT.md`.

---

## 4. Fase 2 — Después de F10 (backlog, no detallar aún)

1. **PDF-F11 Notificaciones**: confirmación de compra, cancelación/cambio y artista favorito en ciudad; asíncrono con reintentos y fallback; nunca revierte una compra.
2. **PDF-NF-CACHE**: estrategia global de catálogo/búsqueda; decidir cache o “no cache” justificado; invalidación; nunca autoridad de disponibilidad.
3. **PDF-NF-RESILIENCE**: circuit breaker/fallback hacia Payment y Notifications; evaluar Resilience4j sólo si timeouts/retries no alcanzan.
4. **PDF-NF-PERFORMANCE**: prueba de carga del flujo de compra con informe (usuarios concurrentes, latencia, errores).
5. **Pruebas de integración entre servicios reales** (Testcontainers/Compose automatizado) y documento único de patrones (Gateway, Saga, BFF, Service Discovery, consistencia).

---

## 5. Orquestación y ownership

### Secuencia

1. **Fase 0** (reparación) — un solo agente por archivo compartido; no paralelizar `mvn verify` sobre los mismos `target/`.
2. **Explore** de F10 — decisiones D1-D9, sin código.
3. **Propose** — spec-designer crea el cambio y valida strict.
4. **Apply** — api-builder implementa; contract-tester prepara contratos/Postman en paralelo sólo cuando el contrato HTTP esté acordado.
5. **Verify** — general/orquestador ejecuta reactor, Compose y Newman; registra evidencia.
6. **Archive** — sólo con DoD completo.

### Archivos compartidos (un dueño a la vez)

- `docs/openapi.yaml`
- `postman/ticketya.postman_collection.json`
- `docker-compose.yml`, `.env`, `pom.xml`
- `README.md`, `docs/architecture.md`, `TODO.md`, `NEXT.md`
- `openspec/specs/**`, `openspec/changes/**`

### Reglas

- No crear directorios manualmente en `openspec/changes/`; usar el CLI de OpenSpec.
- No ejecutar builds de módulos distintos en paralelo si comparten `target/`.
- Cada cambio de contrato se compara productor/consumidor: controlador, cliente HTTP, OpenAPI, Gateway y Postman.
- No archivar con escenarios fallando ni con tests omitidos.
- Mantener alineadas las versiones de Testcontainers (hoy 1.21.4).

---

## 6. Comandos de verificación

```powershell
# Reactor
mvn verify

# Módulo afectado
mvn -pl services/preferences-service -am verify

# OpenSpec
openspec validate add-preferences-and-recommendations --strict
openspec validate --all --strict

# Stack aislado
docker compose -p ticketya_f10_verification up --build -d --wait
docker compose -p ticketya_f10_verification ps

# E2E
postman collection run postman/ticketya.postman_collection.json --env-var baseUrl=http://localhost:8080 --no-report-events
python postman/concurrent-purchases.py

# Higiene
git diff --check
```

Revisar en los XML recientes de `target/surefire-reports` que las clases esperadas tengan `tests > 0`, `failures = 0`, `errors = 0`, `skipped = 0`.

---

## 7. Riesgos y preguntas abiertas

1. **Numeración E2**: el blueprint llama E2-04 a preferencias, pero el repo ya usó E2-04 para reportes. Usar `PDF-F10` y nombre de cambio, no E2-04.
2. **Servicio vs módulo**: la recomendación es servicio propio; si el equipo prioriza simplicidad, un módulo en Purchase con MongoDB es la alternativa, pero mezcla ownership. Decidir en `explore`.
3. **MongoDB en CI**: Testcontainers MongoDB requiere Docker; si no está disponible, registrar el bloqueo y no marcar la tarea como completa.
4. **Co-compra**: la consulta puede ser costosa; acotar candidatos y límites. No agregar proyecciones asíncronas sin justificación.
5. **Sin cache**: es una decisión válida; documentar que un dato stale nunca decide ventas ni disponibilidad.
6. **Auditoría de preferencias**: no es operación crítica del PDF; si se agrega, justificarlo y no bloquear la respuesta.
7. **Roadmaps viejos**: `ROADMAP-Orquestador-F6.md` y `ROADMAP-Orquestador-Gratis.md` no deben usarse como fuente de estado; contienen endpoints inexistentes y estados falsos.

---

## 8. Trazabilidad con el PDF

| Referencia | Requisito | Dónde se cubre |
|---|---|---|
| PDF-F10 | Preferencias (tipos, artistas, ciudad) y recomendaciones por historial/co-compra | `customer-preferences`, `event-recommendations` |
| PDF-NF-PERSISTENCE | Al menos una base NoSQL justificada | MongoDB `preferences-db` |
| PDF-NF-SECURITY | JWT y roles | Gateway + servicio, `CLIENT` |
| PDF-NF-TRACE | Log por request y correlation ID | `RequestLoggingFilter`, `X-Correlation-Id` |
| PDF-NF-CACHE | Estrategia de cache y datos no cacheados | Decisión “sin cache” justificada en `design.md` |
| PDF-NF-DEPLOYMENT | Docker por componente y Compose único | `preferences-service` + `preferences-db` |
| PDF-NF-COMM | REST síncrono; asíncrono justificado | Contratos internos síncronos; sin broker |
| PDF-NF-CONSISTENCY | Consistencia y compensaciones | Recomendaciones advisory; no participan de la saga de compra |

---

## 9. Próximo paso propuesto

1. Confirmar este roadmap y el alcance de Fase 0 + F10.
2. Cambiar a agente de implementación (`api-builder`/`general`) y ejecutar Fase 0.
3. Recién con `main` verde, crear el cambio OpenSpec `add-preferences-and-recommendations` y comenzar F10.
