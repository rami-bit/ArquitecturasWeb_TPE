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
  - [ ] [x] Cancelar eventos desde Catalog y bloquear nuevas reservas en Inventory.
- [x] [ ] Permitir al cliente seleccionar evento, sector y, cuando corresponda, asientos específicos.
  - [x] [x] Consultar eventos publicados y su detalle, incluidos sectores, precios y asientos.
  - [x] [x] Inventory acepta el sector/asientos seleccionados en la solicitud interna de reserva.
  - [ ] [ ] Completar el flujo de seleccion y compra del cliente mediante un servicio Purchase.
- [ ] [ ] Reservar temporalmente los lugares durante el proceso de pago y liberarlos si el pago se rechaza o vence la reserva.
  - [x] [x] Retener asientos o cupos con TTL y liberarlos automaticamente al vencer la reserva.
  - [x] [x] Exponer la liberacion idempotente de una reserva activa.
  - [ ] [ ] Conectar el rechazo del pago con la liberacion de la reserva desde el flujo Purchase.
- [ ] [ ] Registrar el detalle de la compra y el medio de pago; limitar la cantidad de entradas por cliente y evento.
- [ ] [ ] Emitir entradas con un código QR único cuando el pago sea aprobado.
  - [x] [x] Emitir tickets con QR firmado, guardar el hash unico y hacer idempotente la emision por orden/item.
  - [ ] [ ] Integrar la emision con la aprobacion efectiva del pago desde Purchase.
- [x] [ ] Garantizar que un asiento o lugar no pueda venderse dos veces, incluso ante compras simultáneas o reintentos.
- [ ] [ ] Permitir solicitar devoluciones hasta 72 horas antes del evento; permitir que un administrador apruebe o rechace cada solicitud.
- [ ] [ ] Al aprobar una devolución, liberar el lugar y solicitar el reintegro a la pasarela de pagos.
  - [x] [x] Inventory permite liberar una reserva activa de forma idempotente (capacidad base, no un ticket ya vendido).
  - [ ] [ ] Implementar aprobacion de devolucion de un ticket vendido, liberar su lugar y solicitar el reintegro.
- [ ] [ ] Al cancelar un evento, pasar las entradas vendidas a reintegro y notificar a sus compradores.
  - [ ] [x] Catalog permite cancelar un evento y notifica a Inventory para bloquear nuevas reservas.
  - [ ] [ ] Invalidar los tickets vendidos, iniciar reintegros y notificar a los compradores.
- [x] [x] Permitir buscar eventos por tipo, organizador, participante, ciudad y rango de fechas, ordenando por fecha, precio o relevancia.
- [ ] [ ] Proveer una pasarela de pagos simulada (mock) para el flujo de compra.

## Historias y especificaciones

- [ ] [ ] Transformar los requerimientos del sistema completo en features y user stories.
- [ ] [ ] Incluir criterios de aceptación para las historias.
- [x] [ ] Versionar las especificaciones en el repositorio si se usa OpenSpec u otra práctica similar.

## Arquitectura y diseño

- [ ] [ ] Definir los microservicios, sus responsabilidades y su API pública.
- [ ] [ ] Implementar cada microservicio como proyecto Spring Boot independiente, construible y ejecutable por separado.
- [ ] [ ] Usar APIs REST síncronas para la comunicación de esta entrega.
- [x] [x] Centralizar el acceso externo mediante un API Gateway (punto de entrada único).
- [ ] [ ] Definir una base de datos relacional por microservicio (se permiten esquemas separados en una misma tecnología).
- [x] [x] Diseñar y documentar el mecanismo contra la doble venta, válido aunque haya varias instancias del microservicio.
- [ ] [ ] Crear un diagrama de módulos de la arquitectura de solución.
- [ ] [ ] Crear un diagrama de secuencia de compra de una entrada numerada, incluidos pago rechazado y vencimiento de la reserva.
- [ ] [ ] Crear un diagrama entidad-relación por microservicio, con claves y relaciones entre servicios.
- [ ] [ ] Indicar entidades solapadas entre las bases de datos de los servicios, si las hubiera.

## API REST

- [ ] [ ] Documentar los endpoints con método HTTP, ruta, parámetros y códigos de respuesta.
- [ ] [ ] Incluir ejemplos JSON de request y response.
- [ ] [ ] Documentar la API con OpenAPI/Swagger, incluyendo descripciones, ejemplos y códigos de error.

## Implementación y ejecución

- [ ] [ ] Implementar completamente en Spring Boot los microservicios y el API Gateway necesarios para el alcance de Entrega 1.
- [ ] [ ] No exigir autenticación en esta entrega; identificar al usuario mediante parámetro o encabezado.
- [ ] [ ] Preparar instrucciones de ejecución local de los microservicios; las bases de datos pueden iniciarse con Docker.
- [ ] [ ] Incluir README con instrucciones para ejecutar el sistema.
- [ ] [ ] Incluir documentación técnica adicional de diseño.
- [ ] [ ] Incluir datos de prueba.
- [ ] [ ] Incluir una colección de requests de Postman o herramienta similar para probar los endpoints.

## Pruebas

- [ ] [ ] Agregar pruebas unitarias de lógica de negocio para compra.
- [ ] [ ] Agregar pruebas unitarias de lógica de negocio para devoluciones.
- [ ] [ ] Agregar pruebas unitarias de lógica de negocio para búsqueda.
- [x] [ ] Agregar una prueba con muchas compras simultáneas del mismo asiento (o del último lugar de una zona) y verificar que solo una resulte exitosa.
  - [x] [x] Probar competencia multi-instancia por el mismo asiento o el ultimo cupo durante la reserva.
  - [ ] [ ] Probar la compra completa concurrente, incluyendo pago y emision de tickets.

## Entrega del repositorio

- [ ] [ ] Incluir especificaciones (si se utilizó OpenSpec u otra práctica similar), documentación de diseño, README, datos de prueba y colección de requests.
