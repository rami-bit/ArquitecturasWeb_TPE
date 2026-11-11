# Checklist — Entrega 1: Core del sistema

> **Doble checklist:** el primer casillero indica cobertura por una especificacion OpenSpec completada; el segundo indica implementacion completada en el codigo.
> - [x] [ ]: especificado, implementacion pendiente.
> - [ ] [x]: implementado, sin especificacion OpenSpec completada.
> - [x] [x]: especificado e implementado.
> - [ ] [ ]: ambos pendientes.

> Si un item esta parcialmente cubierto, manten abierto el punto principal y desglosa debajo subitems con su propio estado. El punto principal se completa cuando todos sus subitems requeridos esten cubiertos e implementados.

Basado en `TPE-ArqWeb-TicketYa-2026.pdf`, secciones “Modalidad de entrega”, “Tareas — Entrega 1” y “Entregable 1”.

## Requisitos funcionales

- [x] [x] Modelar eventos con ID, nombre, descripción, tipo, organizador, participantes, fecha y hora, lugar y estado (borrador, publicado, agotado, cancelado, finalizado).
- [x] [x] Modelar lugares con ID, nombre, dirección, ubicación y sectores.
- [x] [x] Soportar sectores numerados (filas y asientos identificados) y sectores no numerados con capacidad máxima.
- [x] [x] Permitir al organizador crear eventos en borrador, configurar sectores y precios por sector, y publicar eventos.
- [ ] [ ] Permitir a administradores suspender o cancelar eventos.
  - [x] [x] Suspender eventos publicados con rol ADMIN.
  - [ ] [x] Cancelar eventos desde Catalog con rol ADMIN y bloquear nuevas reservas en Inventory.
- [x] [ ] Permitir al cliente seleccionar evento, sector y, cuando corresponda, asientos específicos.
  - [x] [x] Consultar eventos publicados y su detalle, incluidos sectores, precios y asientos.
  - [x] [x] Inventory acepta el sector/asientos seleccionados en la solicitud interna de reserva.
  - [ ] [x] Purchase crea orden y orquesta reserva, pago, confirmacion de Inventory y emision de tickets. E2E aprobado en admision general y asiento numerado.
  - [ ] [x] Flujo E2E verificado por Gateway: compra aprobada, compra rechazada, reintento idempotente, consulta de tickets por titular y limite secuencial.
- [ ] [ ] Reservar temporalmente los lugares durante el proceso de pago y liberarlos si el pago se rechaza o vence la reserva.
  - [x] [x] Retener asientos o cupos con TTL y liberarlos automaticamente al vencer la reserva.
  - [x] [x] Exponer la liberacion idempotente de una reserva activa.
  - [ ] [x] Purchase libera la reserva ante pago rechazado; E2E confirma orden REJECTED, sin tickets y sin cupos retenidos. Expiracion implementada; falta prueba automatizada del flujo de compra vencido.
- [ ] [ ] Registrar el detalle de la compra y el medio de pago; limitar la cantidad de entradas por cliente y evento.
  - [ ] [x] Purchase persiste orden, items, snapshot de precio y medio de pago, con clave idempotente y limite configurable.
  - [ ] [x] E2E confirma reintento con la misma clave, conflicto al reutilizarla con otro payload y limite secuencial de cuatro entradas; falta cobertura automatizada de persistencia y concurrencia integral.
- [ ] [ ] Emitir entradas con un código QR único cuando el pago sea aprobado.
  - [x] [x] Emitir tickets con QR firmado, guardar el hash unico y hacer idempotente la emision por orden/item.
  - [ ] [x] Purchase invoca Ticket despues de aprobar el pago y confirmar Inventory.
  - [ ] [x] E2E aprobado emite QR y la consulta del titular devuelve el ticket; otro titular recibe 404. Falta automatizar reintento de emision y fallos downstream.
- [x] [ ] Garantizar que un asiento o lugar no pueda venderse dos veces, incluso ante compras simultáneas o reintentos.
- [ ] [x] Permitir solicitar devoluciones hasta 72 horas antes del evento; permitir que un administrador apruebe o rechace cada solicitud.
- [ ] [x] Al aprobar una devolución, liberar el lugar y solicitar el reintegro a la pasarela de pagos.
  - [x] [x] Inventory permite liberar una reserva activa de forma idempotente (capacidad base, no un ticket ya vendido).
  - [ ] [x] Implementar aprobacion de devolucion de un ticket vendido, liberar su lugar y solicitar el reintegro.
- [ ] [ ] Al cancelar un evento, pasar las entradas vendidas a reintegro y notificar a sus compradores.
  - [ ] [x] Catalog permite cancelar un evento y notifica a Inventory para bloquear nuevas reservas.
  - [ ] [x] Revocar los tickets vendidos e iniciar reintegros de forma reintentable; la notificación a compradores queda en US-F11 (E2).
- [x] [x] Permitir buscar eventos por tipo, organizador, participante, ciudad y rango de fechas, ordenando por fecha, precio o relevancia.
- [ ] [x] Proveer una pasarela de pagos simulada (mock) para el flujo de compra.
  - [ ] [x] Payment Mock implementa cobros aprobados/rechazados, persistencia e idempotencia; Purchase integra el contrato.
  - [ ] [x] Confirmar el flujo completo de compra con pago aprobado y rechazado mediante una prueba E2E por Gateway.

## Roadmap funcional: user stories y dependencias

Este roadmap convierte F1-F12 del PDF en historias priorizadas. Los IDs ayudan a abrir cambios OpenSpec, relacionar tareas y ver el orden. Catalog e Inventory estan cubiertos; Purchase, Payment Mock y Ticket estan integrados y el flujo E2E por Gateway fue verificado. Quedan pendientes pruebas automatizadas de resiliencia y concurrencia.

### Base disponible

- **US-F1/F2/F3/F9 - Catalog:** lugares, eventos, borradores, publicacion, busqueda y detalle ya estan implementados. La cancelacion esta iniciada, pero la orquestacion de reintegros aun no.
- **US-F5 - Inventory:** disponibilidad, reservas temporales, expiracion y prevencion de doble reserva bajo concurrencia estan implementadas.
- **US-F4 QR - Ticket:** `issue-ticket-qr` implementa emision interna e idempotente, integrada con Purchase; compra aprobada E2E emite y permite consultar el QR por el titular.

### Prioridad y secuencia

| Orden | Historia / criterio de aceptacion | Dependencias y paralelismo |
|---|---|---|
| **P0 / verificacion** | **US-F4.1 - Crear orden (implementado; E2E verificado).** Purchase persiste cliente, evento, items, sector/asiento, snapshot de precio, medio de pago y estado; incluye limite por cliente/evento y clave idempotente. La prueba unitaria y los flujos E2E basicos pasan; falta automatizar persistencia y concurrencia integral. | Catalog e Inventory existentes. El contrato esta definido en el change OpenSpec `establish-purchase-and-ticketing`. |
| **P0 / verificacion** | **US-F4.2 - Simular pago (implementado; E2E aprobado/rechazado verificado).** Payment Mock conserva referencias y resuelve reintentos sin duplicar cargos; ambas respuestas se ejercitaron por el flujo de compra. | El contrato con Purchase esta implementado y se verificaron cobros aprobados y rechazados a traves del Gateway. |
| **P0 / pruebas** | **US-F4.3 - Completar compra (implementado; E2E verificado).** La saga reserva, cobra, confirma o libera inventario y expone estados consultables. Aprobacion, rechazo, reintento idempotente y limite secuencial pasan; faltan pruebas de expiracion, concurrencia y fallos downstream. | Depende de US-F4.1, US-F4.2 y los comandos internos de Inventory. |
| **P0 / pruebas** | **US-F4.4 - Emitir y consultar entradas compradas (implementado; E2E verificado).** Purchase invoca Ticket tras aprobacion y confirmacion; QR emitido y consulta autorizada del titular comprobados por Gateway. Falta automatizar reintento de emision y recuperación ante fallos. | Depende de US-F4.3 y del contrato interno existente de Ticket Service. |
| **P1** | **US-F7.1 - Solicitar devolucion (implementado; E2E basico verificado).** El titular puede solicitarla hasta 72 horas antes; se guarda estado y se impiden solicitudes duplicadas. Postman verifico solicitud, consulta, reintento y 404 para otro usuario por Gateway (25 requests, 51 aserciones, 0 fallos); falta ejecutar la prueba Testcontainers del borde de 72 horas. | Depende de ordenes/tickets de US-F4.4 y fecha del evento de Catalog. |
| **P1 / pruebas** | **US-F7.2 - Resolver devolucion (implementado; E2E basico verificado).** ADMIN aprueba o rechaza; la aprobacion revoca el ticket, reintegra su importe y libera la unidad vendida con progreso reintentable. Postman paso 35 requests y 61 aserciones, incluidos dos reintegros parciales de un mismo pago. Falta ejecutar Testcontainers para fallos parciales y persistencia. | Depende de US-F7.1, Ticket revocable, Payment Mock con reintegro parcial e Inventory con liberacion de unidad vendida. |
| **P1 / pruebas** | **US-F7.3 - Cancelar evento con reintegros (implementado; E2E verificado).** ADMIN inicia el proceso; Catalog marca `CANCELLED`; Purchase revoca tickets, procesa reintegros y cierra inventario de forma idempotente. Postman pasó 41 requests y 69 aserciones, incluidos seis tickets vendidos, rechazo previo, reintento y bloqueo de compra posterior. | Depende de US-F4.3/4.4 y de las primitivas de US-F7.2. Purchase orquesta; Catalog no orquesta reintegros. La notificacion a compradores se completa en US-F11 (E2). |
| **P1 / cierre E1** | **US-E1-05 - Integrar y entregar el core.** Gateway publica las rutas necesarias; OpenAPI/Postman cubren los flujos; README/datos de prueba permiten ejecutar; pruebas integradas verifican compra, rechazo, vencimiento, idempotencia y competencia. | Depende de US-F4.1-F4.4 y US-F7.1-F7.3. Se pueden preparar contratos y ejemplos en paralelo, pero cerrar la verificacion E2E al final. |
| **P2 / E2** | **US-F6 - Fila virtual.** Para evento de alta demanda, informar posicion y habilitar tandas ordenadas; el token de admision no garantiza disponibilidad de inventario. | Se puede especificar en paralelo con P0; la integracion depende del endpoint de compra de US-F4.3 e Inventory. |
| **P2 / E2** | **US-F8 - Validar acceso QR.** Personal valida existencia, evento correcto y no usado; el primer escaneo marca uso atomico y los siguientes se rechazan. | Depende del contrato de tickets emitidos de US-F4.4. Puede implementarse en paralelo con devoluciones cuando se acuerden los estados de revocacion/uso. |
| **P2 / E2** | **US-F10 - Preferencias y recomendaciones.** Guardar tipos, artistas y ciudad; sugerir eventos por preferencias e historial de compras. | Catalog ya existe; historial depende de US-F4.1-F4.4. Definir NoSQL y su justificacion en esta historia. |
| **P2 / E2** | **US-F11 - Notificar al cliente.** Enviar confirmacion de compra, cancelacion/cambio y eventos de artistas favoritos en su ciudad. | Confirmacion depende de US-F4.3; cancelacion de US-F7.3; alertas personalizadas de US-F10. Se puede desarrollar el adaptador de notificaciones en paralelo una vez acordado el contrato de eventos. |
| **P2 / E2** | **US-F12 - Reportes del organizador.** Mostrar vendidos/devueltos por sector, ocupacion y recaudacion por evento propio. | Depende de compras de US-F4 y devoluciones/cancelacion de US-F7; requiere filtrar por ownership del organizador. |

### Grafo de dependencias y trabajo paralelo

```mermaid
flowchart TD
    CAT["Catalog: F1-F3/F9"] --> INV["Inventory: F5"]
    INV --> CONTRACTS["Contratos de E1-03 aprobados"]
    CONTRACTS --> ORDER["US-F4.1 Orden"]
    CONTRACTS --> PAY["US-F4.2 Payment Mock"]
    ORDER --> SAGA["US-F4.3 Compra / saga"]
    PAY --> SAGA
    SAGA --> QR["US-F4.4 Integrar emision QR"]
    QR --> REF["US-F7.1-F7.2 Devoluciones"]
    QR --> CANCEL["US-F7.3 Cancelacion y reintegros"]
    REF --> CORE["E1-05 Integracion core"]
    CANCEL --> CORE
    SAGA --> QUEUE["E2 F6 Fila virtual"]
    QR --> ACCESS["E2 F8 Acceso QR"]
    SAGA --> PREF["E2 F10 Preferencias"]
    CANCEL --> NOTIFY["E2 F11 Notificaciones"]
    PREF --> NOTIFY
    REF --> REPORT["E2 F12 Reportes"]
    SAGA --> REPORT
```

**Como elegir la siguiente tarea:** completar en Purchase las pruebas de persistencia, expiracion, concurrencia, fallos downstream y reintento QR. La coleccion Postman renombrada paso localmente: 21 requests, 47 aserciones y 0 fallos, con 11 contenedores saludables. Luego completar devoluciones/cancelacion con reintegros y cerrar E1. E2 puede refinarse en paralelo cuando sus dependencias contractuales esten definidas.

## Historias y especificaciones

- [x] [x] Transformar los requerimientos del sistema completo en features y user stories.
- [x] [x] Incluir criterios de aceptación para las historias.
- [x] [x] Versionar las especificaciones en el repositorio si se usa OpenSpec u otra práctica similar.

Evidencia: [`docs/Documentacion-de-diseño.md`](docs/Documentacion-de-diseño.md) reúne features y user stories con criterios de aceptación; [`openspec/`](openspec/) versiona los cambios con `proposal.md`, `design.md`, `specs/` y `tasks.md`.

## Arquitectura y diseño

- [x] [x] Definir los microservicios, sus responsabilidades y su API pública.
- [x] [x] Implementar cada microservicio como proyecto Spring Boot independiente, construible y ejecutable por separado.
- [x] [x] Usar APIs REST síncronas para la comunicación de esta entrega.
- [x] [x] Centralizar el acceso externo mediante un API Gateway (punto de entrada único).
- [x] [x] Definir una base de datos relacional por microservicio (se permiten esquemas separados en una misma tecnología).
- [x] [x] Diseñar y documentar el mecanismo contra la doble venta, válido aunque haya varias instancias del microservicio.
- [x] [x] Crear un diagrama de módulos de la arquitectura de solución.
- [x] [x] Crear un diagrama de secuencia de compra de una entrada numerada, incluidos pago rechazado y vencimiento de la reserva.
- [x] [x] Crear un diagrama entidad-relación por microservicio, con claves y relaciones entre servicios.
- [x] [x] Indicar entidades solapadas entre las bases de datos de los servicios, si las hubiera.

Evidencia: [`docs/architecture.md`](docs/architecture.md) reúne servicios y responsabilidades, diagrama de módulos, secuencia de compra, mecanismo contra la doble venta, ER por servicio (Catalog, Inventory, Ticket, Purchase y Payment) y la tabla de entidades solapadas. Los diagramas están en Mermaid y se renderizan desde el README y desde el propio documento.

## API REST

- [x] [x] Documentar los endpoints con método HTTP, ruta, parámetros y códigos de respuesta.
- [x] [x] Incluir ejemplos JSON de request y response.
- [x] [x] Documentar la API con OpenAPI/Swagger, incluyendo descripciones, ejemplos y códigos de error.

Evidencia: [`docs/openapi.yaml`](docs/openapi.yaml) es el contrato consolidado de la entrega (16 paths, 17 operaciones) con ejemplos, errores `application/problem+json` y los esquemas de Purchase, Payment, Catalog e Inventory. Cada servicio expone además su `/v3/api-docs` y Swagger UI en su puerto interno.

## Implementación y ejecución

- [x] [x] Implementar completamente en Spring Boot los microservicios y el API Gateway necesarios para el alcance de Entrega 1.
- [x] [x] No exigir autenticación en esta entrega; identificar al usuario mediante parámetro o encabezado.
- [x] [x] Preparar instrucciones de ejecución local de los microservicios; las bases de datos pueden iniciarse con Docker.
- [x] [x] Incluir README con instrucciones para ejecutar el sistema.
- [x] [x] Incluir documentación técnica adicional de diseño.
- [x] [x] Incluir datos de prueba.
- [x] [x] Incluir una colección de requests de Postman o herramienta similar para probar los endpoints.

Evidencia: el `README.md` documenta requisitos, variables de entorno, build, Compose, healthchecks, OpenAPI, Postman y reset de volúmenes. La identidad viaja en `X-User-Id` (y `X-User-Role` para ADMIN), sin autenticación. Los datos de prueba los genera la colección Postman con un `runId` por corrida sobre un stack limpio; el README incluye ejemplos de compra y la colección cubre desde la creación del lugar hasta la compra aprobada y rechazada.

## Pruebas

- [ ] [ ] Agregar pruebas unitarias de lógica de negocio para compra.
- [ ] [ ] Agregar pruebas unitarias de lógica de negocio para devoluciones.
- [ ] [ ] Agregar pruebas unitarias de lógica de negocio para búsqueda.
- [x] [ ] Agregar una prueba con muchas compras simultáneas del mismo asiento (o del último lugar de una zona) y verificar que solo una resulte exitosa.
  - [x] [x] Probar competencia multi-instancia por el mismo asiento o el ultimo cupo durante la reserva.
  - [ ] [ ] Probar la compra completa concurrente, incluyendo pago y emision de tickets.

## Entrega del repositorio

- [x] [x] Incluir especificaciones (si se utilizó OpenSpec u otra práctica similar), documentación de diseño, README, datos de prueba y colección de requests.
