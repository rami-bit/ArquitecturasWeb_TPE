# Design

## Context

El cambio `establish-ticketya-architecture` tiene veinte tareas de revisión. La 1.1 fue cerrada por el cambio archivado `2026-11-07-architecture-review-task-1-1`. Las diecinueve restantes (1.2 a 5.3) revisan el blueprint contra el PDF, validan límites y datos, recorren flujos críticos, auditan la consistencia transversal y registran la aprobación.

Este cambio es documental: no modifica código, contratos, bases, Dockerfiles ni Compose. Su salida es una auditoría trazable, las correcciones aceptadas en el blueprint y el registro de aprobación.

Fuentes usadas:

- `TPE-ArqWeb-TicketYa-2026.pdf` (5 páginas): funcionalidad 1–12, consideraciones de arquitectura y no funcionales, tabla de Entregas 1/2 y tareas de cada entrega.
- `OpenSpec.md`: workflow, Definition of Ready y Definition of Done.
- `openspec/changes/establish-ticketya-architecture/proposal.md` y `design.md`.
- `openspec/specs/event-management`, `event-discovery`, `venue-management`, `api-gateway` y `containerized-deployment`.
- Cambios archivados `establish-event-catalog`, `integrate-gateway-routing-and-tracing` y `architecture-review-task-1-1`.
- Código, `docker-compose.yml`, `README.md` y `docs/verification.md` del incremento implementado.

## Goals / Non-Goals

**Goals:**

- Verificar que cada punto del PDF tiene servicio dueño o decisión arquitectónica.
- Verificar que cada funcionalidad pertenece a una única épica primaria.
- Corregir las ambigüedades que afectan venta, pago, devolución o acceso.
- Dejar el blueprint aprobado, validado y con evidencia de cierre.

**Non-Goals:**

- Implementar funcionalidad nueva de TicketYa.
- Elegir la base NoSQL, el broker, el cache o el stack de observabilidad.
- Reemplazar las specs, el OpenAPI o los ER de cada épica.
- Declarar aprobación del equipo sin registro explícito; la aprobación se registra en §23 del blueprint.

## Hallazgos y correcciones aplicadas

| ID | Hallazgo | Tarea | Severidad | Corrección aplicada |
|---|---|---|---|---|
| F-01 | No existía una matriz explícita PDF→servicio→épica | 1.2, 1.3 | Alta | §21 del blueprint: cobertura funcional, no funcional y entregables |
| F-02 | `SOLD_OUT` no tenía dueño claro frente a Inventory | 1.2, 2.1 | Alta | §5: estado derivado de disponibilidad; Inventory sigue siendo autoridad |
| F-03 | El punto de entrada de la cancelación masiva era ambiguo y podía crear un ciclo Catalog→Purchase→Catalog | 2.1, 3.3 | Alta | §5 y §12: Purchase orquesta; Catalog sólo cambia el estado editorial |
| F-04 | La devolución reintegraba antes de revocar el ticket, dejando una entrada utilizable | 3.3 | Alta | §12: bloquear/revocar → reintegrar → liberar, con estados de fallo parcial |
| F-05 | El límite por cliente no explicaba cómo se aplica bajo concurrencia | 3.1, 3.2 | Alta | §9 y §11: contador transaccional con actualización condicional o `FOR UPDATE` |
| F-06 | D3 omitía `payment_db`, ya presente en D2 | 1.1, 5.1 | Media | D3: `payment_db` como "a decidir" |
| F-07 | §4 conservaba un diagrama E1 duplicado de D2 | 5.1 | Media | §4: referencia a D2; se eliminó el diagrama repetido |
| F-08 | Inventory copiaba precios, creando una segunda fuente de precio | 2.1, 2.3 | Media | §6: Inventory copia layout sin precios; Purchase copia el precio histórico |
| F-09 | El propósito de cada copia/snapshot no estaba explícito | 2.3 | Media | §6: tabla de propósito histórico u operativo |
| F-10 | La notificación de cancelación (PDF-F7) parecía E1, pero el PDF ubica notificaciones en E2 | 1.2, 3.3 | Media | §12 y §15: E1 cancela y reintegra; E2-03 notifica |
| F-11 | Los cambios post-publicación de eventos no estaban reconciliados con snapshots inmutables | 1.2, 3.3 | Media | §12 y §15: E1 sólo cancela; E2 define versionado y notificación |
| F-12 | La regla de decisión asíncrona no exigía forma de validación | 4.1 | Media | §8: cuatro campos obligatorios para toda propuesta asíncrona |
| F-13 | Las tecnologías opcionales no estaban consolidadas | 4.4 | Media | §22: tabla baseline vs. fuera de baseline |
| F-14 | La DoD de épica no enumeraba el chequeo de imagen y Compose | 4.5 | Media | §21: checklist de DoD con `docker compose up --build` |
| F-15 | D4 mostraba sólo el incremento actual sin aclarar que no era el E1 completo | 1.1, 5.1 | Baja | D4 renombrado "incremento actual" y nota de crecimiento por épica |
| F-16 | El bootstrap SQL y la decisión sobre migradores no estaban en el blueprint | 4.4 | Baja | §20 y §22: `schema.sql` baseline; Flyway/Liquibase fuera |
| F-17 | No se exigía unicidad de `qr_hash` | 3.4 | Baja | §11 y §14: índice único y emisión única por ítem |
| F-18 | Faltaban SQL de confirmación, liberación y expiración | 3.2 | Media | §10: operaciones conceptuales completas y constraints |
| F-19 | La reconciliación con `establish-event-catalog` no estaba registrada | 4.3 | Baja | §21 y §4.3 de este diseño |
| F-20 | Riesgo de tratar la prueba de carga como driver de performance productiva | 1.4 | Baja | §1: guidelines de simplicidad; §21: prueba de carga en E2-08 |
| F-21 | El Compose implementado llama `postgres` al contenedor y `ticketya_catalog` a la base; el blueprint usa `catalog_db` | 4.3 | Baja | §20: nota de nombres y criterio a unificar en la próxima épica |

## 1.2 Trazabilidad PDF → servicio → épica

La matriz completa quedó en §21 del blueprint. Resumen de decisiones:

| PDF | Servicio dueño | Épica primaria | Decisión |
|---|---|---|---|
| F1 Eventos | Catalog | E1-01 | Datos, participantes, fecha, lugar y estados editoriales |
| F2 Lugares y sectores | Catalog | E1-01 | Sectores numerados/generales y precios por evento |
| F3 Borrador/publicación/suspensión/cancelación | Catalog | E1-01 | Catalog dueño del estado; E1-04 ejecuta el proceso de cancelación |
| F4 Reserva, pago, compra y QR | Purchase/Payment/Ticket, con Inventory | E1-03 | Inventory colabora en la reserva |
| F5 No doble venta | Inventory, con Purchase idempotente | E1-02 | Inventory y PostgreSQL son la autoridad |
| F6 Fila virtual | Capacidad E2 | E2-02 | No garantiza stock |
| F7 Devoluciones y cancelación | Purchase, con Payment/Ticket/Inventory | E1-04 | Proceso persistido y reanudable |
| F8 Acceso QR | Ticket & Access | E2-05 | Uso único atómico |
| F9 Búsqueda | Catalog | E1-01 | Filtros y orden ya especificados |
| F10 Preferencias/recomendaciones | Capacidad E2 con NoSQL | E2-04 | NoSQL se elige en la épica |
| F11 Notificaciones | Capacidad E2 | E2-03 | Compra, cancelación/cambio y artista favorito |
| F12 Reportes | Capacidad E2 | E2-06 | Por sector, ocupación y recaudación |

Todos los no funcionales del PDF tienen fila propia en §21. Los entregables de diseño del PDF (módulos, secuencia, ER, OpenAPI, README, pruebas) también quedaron mapeados; el ER por microservicio se difiere explícitamente a cada épica, que es la unidad que conoce su esquema.

## 1.3 Cobertura por épica

La tabla de §21 asigna una épica primaria por funcionalidad y marca las colaboradoras. No hay funcionalidad del PDF sin épica. Se agregó la nota de que `integrate-gateway-routing-and-tracing` es un sub-cambio de E1-01, no una épica nueva, para que el mapa refleje el historial real.

## 1.4 Simplicidad

§1 del blueprint incorpora una tabla con las decisiones del equipo:

- Java 19 y Maven 3.9+ son estándar del proyecto; el PDF no fija una versión de Java.
- El PDF es el piso obligatorio.
- La performance productiva no es driver; sólo se exigen la prueba de concurrencia y la prueba de carga del TPE.
- Toda tecnología o patrón adicional requiere requisito, alternativa simple, costo y validación.

La prueba de carga queda en E2-08 y no habilita optimizaciones preventivas.

## 1.5 Despliegue

§20 mantiene la regla: un `Dockerfile` por componente de aplicación desarrollado, un único `docker-compose.yml` incremental en la raíz, imágenes oficiales para dependencias estándar y `docker compose up --build` como verificación. Se aclara que sólo el Gateway publica un puerto al host en Compose y que la ejecución local de servicios es sólo para desarrollo. Se agrega el bootstrap `schema.sql` como mecanismo mínimo y se deja Flyway/Liquibase fuera de baseline.

## 2.1 Dueños transaccionales

| Capacidad | Dueño | Verificación |
|---|---|---|
| Lugares, eventos, participantes y precios publicados | Catalog | No decide disponibilidad ni ventas |
| Disponibilidad, holds, confirmación y stock | Inventory | Única autoridad de venta; PostgreSQL |
| Orden, límite por cliente, detalle, medio de pago y devolución | Purchase | Orquesta la saga; no modifica inventario directamente |
| Resultado de pago y reintegro | Payment Mock | Persiste su resultado e idempotencia |
| Ticket, QR y uso | Ticket & Access | Actualización atómica; no decide cobro |
| Fila virtual | Capacidad E2 | Habilita intentos; no garantiza stock |
| Preferencias/recomendaciones | Capacidad E2 | No decide ventas |
| Notificaciones | Capacidad E2 | No revierte compras |
| Reportes | Capacidad E2 | Sólo lectura/proyección |

No quedan capacidades con dos dueños transaccionales. `SOLD_OUT` es derivado y `CANCELLED` es editorial de Catalog, mientras que el proceso de cancelación y los reintegros pertenecen a Purchase. Esa separación evita el ciclo Catalog→Purchase→Catalog.

## 2.2 Ownership y persistencia

- Catalog tiene `catalog_db` PostgreSQL y es fuente de verdad de lugares, eventos y snapshots publicados.
- Inventory tendrá su base relacional propia para inventario, holds y stock.
- Purchase tendrá `purchase_db` PostgreSQL para órdenes, ítems, límite y devoluciones.
- Payment Mock tendrá almacenamiento propio para resultado e idempotencia; la tecnología queda a decidir.
- Ticket & Access tendrá `ticket_db` PostgreSQL para tickets, hash de QR y uso.
- Preferencias/recomendaciones usará una base NoSQL que se seleccionará y justificará en E2-04; MongoDB es candidata, no decisión.
- No hay foreign keys entre bases; las referencias son IDs y valores copiados.
- La única base implementada hoy es `catalog_db`; el resto se crea en su épica.

## 2.3 Copias y snapshots

§6 del blueprint ahora tiene una tabla con propósito explícito:

| Copia | Propósito | Límite |
|---|---|---|
| `Catalog:EventSnapshot` | Histórico inmutable y fuente de inicialización | No decide disponibilidad ni compras |
| `Inventory:EventInventory/layout` | Operación de asignación y stock | No decide metadatos, estado ni precio |
| `Purchase:OrderItem` | Histórico de precio/descripción para recibo y devolución | No decide disponibilidad ni estado actual |
| `Ticket:event snapshot` | Validación y visualización de la entrada | No decide estado, precio ni disponibilidad |
| `Payment:orderId` | Correlación e idempotencia | No copia la orden completa |

Inventory ya no copia precios. Cada copia tiene un propósito histórico u operativo y ninguna crea una FK entre bases.

## 3.1 Saga de compra

§9 del blueprint incorpora la tabla de ramas:

| Rama | Estado | Recuperación |
|---|---|---|
| Pago aprobado y tickets emitidos | `CONFIRMED` | Ninguna |
| Pago rechazado | `REJECTED` | Liberar hold idempotente |
| Pago incierto | `PAYMENT_PENDING` | Consultar; no asumir fracaso |
| Reserva vencida o confirmación fallida | `COMPENSATING` → `REFUNDED` | Reintegrar y liberar |
| Emisión fallida | `PAID_PENDING_TICKETS` | Reintentar; si es permanente, compensar |
| Compra duplicada | Estado original | Devolver la orden existente |

Los estados terminales son `CONFIRMED`, `REJECTED` y `REFUNDED`; los intermedios tienen reintento o reconciliación. Un timeout no se interpreta como fracaso.

## 3.2 Inventario y doble venta

§10 agrega las operaciones conceptuales de confirmación, liberación y expiración, además del hold existente. Las condiciones se evalúan en el `UPDATE` y PostgreSQL serializa las filas; dos instancias no pueden confirmar el mismo asiento. Se explicitan unicidad de `(event_id, seat_id)`, `(event_id, sector_id)` y `reservation_id`. La expiración sólo toca `HELD` y nunca `SOLD`.

## 3.3 Devoluciones y cancelación

§12 corrige el orden: bloquear/revocar ticket → reintegrar → liberar inventario. Un reintegro incierto queda `REFUND_PENDING` y no libera inventario; una revocación fallida no reintegra todavía. La cancelación masiva la orquesta Purchase, que pide a Catalog el cambio idempotente a `CANCELLED`, bloquea/revoca tickets, reintegra, libera inventario y deja la notificación a E2-03. La cancelación de E1 es consultable aunque la notificación sea E2.

## 3.4 Fila virtual y QR

§13 explicita que el token de admisión permite intentar comprar, no reserva ni garantiza stock; Inventory revalida. §14 mantiene el `UPDATE` atómico por `qr_hash`, agrega unicidad de hash y de emisión por ítem, y recuerda que la base guarda el hash, no el token completo. Un cache o una lectura previa no pueden validar el uso.

## 4.1 Integraciones y asincronía

§8 agrega la tabla de integraciones E1 con REST síncrono por defecto y la regla de que toda propuesta asíncrona debe documentar requisito, alternativa simple, costo y validación. Outbox/Inbox no son baseline; se evalúan sólo si se adopta un broker. Las integraciones candidatas a asíncrono en E2 son notificaciones y reportes.

## 4.2 Cache, resiliencia, seguridad y trazabilidad

§16 agrega la tabla de autoridad de decisión: Inventory decide venta, Payment decide pago, Ticket decide uso, Purchase decide orden, Catalog decide estado editorial. Un cache sólo puede servir lectura; un fallback de pago no aprueba; un log no es fuente de verdad. §17 y §18 refuerzan que timeouts/retries/fallback no reemplazan autoridad y que los roles son `CLIENT`, `ORGANIZER`, `ADMIN` y `ACCESS_STAFF`.

## 4.3 Reconciliación con establish-event-catalog

El cambio `establish-event-catalog` ya referencia el blueprint en su propuesta y su alcance coincide con PDF-F1, F2, F3 y F9, la épica E1-01 y los servicios Gateway/Catalog/catalog_db. Su implementación de Docker en E1 es el adelanto voluntario documentado en §20. No se detectó contradicción de comportamiento que exija un delta de specs. Como el cambio está archivado y no es editable, la reconciliación se registra en este diseño y en la nota de §21; no se modifica el cambio archivado.

El sub-cambio `integrate-gateway-routing-and-tracing` agrega routing y correlation ID al Gateway y queda registrado como refinamiento de E1-01, no como épica nueva. También se detectó una diferencia de nombres: el Compose implementado usa el servicio `postgres` y la base `ticketya_catalog`, mientras que el blueprint usa `catalog_db`; se documenta en §20 y el renombrado se difiere a la épica que agregue la próxima base.

## 4.4 Tecnologías opcionales

§22 consolida la decisión:

| Tecnología/patrón | Baseline |
|---|---|
| API Gateway, PostgreSQL, Docker/Compose, Java 19/Spring Boot | Incluidos |
| Base NoSQL | Obligatoria en E2, elección diferida a E2-04 |
| Cache, Redis, broker, Flyway/Liquibase, OpenTelemetry/Loki/Prometheus/Grafana, Eureka, Resilience4j, framework de saga, BFF | Fuera de baseline |

Cada fila indica requisito, alternativa simple y validación. Ninguna se incorpora sin actualizar la tabla y responder las cuatro preguntas de §1.

## 4.5 Definition of Done de épica

§21 agrega el checklist de componente ejecutable: Dockerfile propio, Compose raíz actualizado, `docker compose up --build`, healthchecks, variables de entorno, README, OpenAPI/Postman, tests y correlation ID. Esto convierte la regla de despliegue en una condición verificable de cada épica que agrega un componente.

## 5.1 Coherencia del blueprint

Las correcciones se aplicaron en el blueprint y se revisó que `proposal.md`, `design.md` y el mapa de épicas no se contradigan:

- La propuesta ahora declara la trazabilidad, la auditoría de tecnologías, las correcciones y la aprobación.
- El diseño mantiene D1 transversal, D2 E1, D3 E2, D4 incremento actual y D5 E2.
- El mapa de épicas conserva E1-01 a E1-05 y E2-01 a E2-08, y agrega la cobertura funcional y la DoD.
- §4 ya no duplica el diagrama E1; D3 incluye `payment_db`; D4 está rotulado como incremento actual.

## 5.2 Validación estricta

Comando ejecutado:

```text
openspec validate establish-ticketya-architecture --strict
```

Resultado: ambos cambios son válidos, sin errores ni advertencias.

```text
Change 'establish-ticketya-architecture' is valid
ℹ [INFO] file: skip_specs is set in .openspec.yaml: change declares no spec-level behavior changes, zero deltas accepted
EXIT=0

Change 'architecture-review-remaining-tasks' is valid
ℹ [INFO] file: skip_specs is set in .openspec.yaml: change declares no spec-level behavior changes, zero deltas accepted
EXIT=0
```

Además, `openspec validate --archived --strict` valida los cinco cambios archivados (5/5) y `openspec validate --specs --strict` valida las cinco especificaciones vigentes (5/5), ambos sin fallos.

## 5.3 Aprobación y referencia para la próxima épica

§23 registra la aprobación del blueprint el 2026-11-07 mediante este cambio. La próxima épica `establish-inventory-and-reservations` debe citar el blueprint y usar §6, §8, §9, §10, §11 y §21. A la fecha no existe un proposal de esa épica; la condición de uso se verificará al abrirlo. Cualquier objeción posterior requiere un cambio OpenSpec.

## Verificación de renderizado

Comando base: `cmd /c "mmdc -i <archivo>.mmd -o <archivo>.svg"` con `mmdc` 12.2.0.

| Diagrama | Sección | Resultado |
|---|---|---|
| D1 Contexto | §2 | OK (205.465 bytes SVG) |
| D2 Contenedores E1 | §3 | OK (213.883 bytes SVG) |
| D3 Contenedores E2 | §4 | OK (211.164 bytes SVG) |
| Relaciones de datos | §7 | OK (206.408 bytes SVG) |
| Saga de compra | §9 | OK (208.785 bytes SVG) |
| Devoluciones | §12 | OK (198.720 bytes SVG) |
| Fila virtual | §13 | OK (205.706 bytes SVG) |
| D4 Despliegue E1 | §20 | OK (204.905 bytes SVG) |
| D5 Despliegue E2 | §20 | OK (229.054 bytes SVG) |

Los nueve bloques Mermaid del blueprint renderizaron sin errores. Los archivos temporales `.mmd` y `.svg` se generaron fuera del repositorio y no se versionan; `mmdc` valida sintaxis y render, no semántica arquitectónica.

## Risks / Trade-offs

| Riesgo | Mitigación |
|---|---|
| La tabla de cobertura se desactualiza al agregar épicas | Cada épica que cambie alcance debe actualizar §21 antes de implementar |
| El estado `SOLD_OUT` derivado se confunde con una decisión de venta | §5 y §16 lo declaran estado comercial; Inventory sigue siendo autoridad |
| Revocar antes de reintegrar deja al cliente sin ticket si el reintegro falla | El estado `REFUND_PENDING` se reconcilia; el administrador resuelve el caso definitivo |
| La cancelación masiva puede quedar a medias | Proceso persistido, por lotes, idempotente y reanudable; no hay transacción distribuida |
| El blueprint archivado deja de ser visible para nuevas épicas | §23 y la propuesta de cada épica exigen citarlo; el historial queda en `openspec/changes/archive/` |
| La aprobación registrada no reemplaza una reunión futura | Cualquier objeción se tramita con un cambio OpenSpec que actualice el blueprint |

## Open Questions

Ninguna bloqueante. La elección de la base NoSQL, la fórmula de recomendación, el almacenamiento de la fila virtual y el stack de logs se resuelven en sus épicas, como ya estaba previsto.
