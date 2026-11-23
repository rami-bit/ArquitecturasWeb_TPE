# Proposal

## Why

TicketYa ya expone por el Gateway los flujos principales de compra y gestión, pero no tiene interfaz gráfica. Una aplicación web de escritorio permitirá demostrar esos flujos y el valor adicional previsto por el enunciado sin convertir al proyecto didáctico en un producto de autenticación.

## What Changes

- Crear una aplicación web exclusivamente de escritorio que consuma la API pública por el Gateway y permita buscar eventos, elegir sector/asientos, pasar por la fila virtual, comprar y consultar órdenes, entradas y devoluciones.
- Incorporar un área de gestión para `ORGANIZER` y `ADMIN`: borradores, publicación, reportes y, para `ADMIN`, sedes, suspensión, reanudación, cancelación, decisiones de devolución y auditoría.
- Incorporar un selector local de perfiles de demostración con login/logout simulado. Los JWT de prueba se hardcodean en una configuración **local no versionada** de la interfaz; no se implementa proveedor de identidad ni se modifican las reglas de autorización del Gateway.
- Mostrar vencimiento de sesión y permitir sustituir los JWT de una hora emitidos por el IdP de prueba antes de cada demostración.
- Incorporar desde el inicio el registro de email propio y la postergación de eventos publicados mediante los contratos F11 ya implementados. En «Mis entradas», consultar la fecha vigente en el detalle público de Catalog por `eventId`, manteniendo la fecha histórica de la orden y advirtiendo cuando la vigente no pueda consultarse.
- Documentar arranque local, datos de prueba, renovación de tokens y recorridos de demostración. No se exige diseño adaptable a móvil.

## Capabilities

### New Capabilities

- `desktop-demo-session`: selección y cierre de perfiles locales con JWT de prueba, expiración y límites de autorización.
- `desktop-customer-interface`: descubrimiento, selección, fila, compra y consulta de entradas/devoluciones desde escritorio.
- `desktop-event-operations`: gestión de eventos, sedes, reportes y operaciones administrativas desde escritorio.

### Modified Capabilities

Ninguna. La interfaz consume los contratos públicos existentes, incluidos los de F11, sin alterar sus requisitos.

## Impact

Nuevo proyecto frontend, configuración local de tokens, proxy local hacia `api-gateway`, documentación de ejecución y pruebas de interfaz/integración por Gateway. Se corrige la carga de sectores y asientos en `GET/PUT /api/venues` de Catalog: la interfaz necesita el listado para crear borradores y ambos endpoints devolvían 500 al convertir entidades fuera de la transacción. No se agregan tablas, endpoints ni servicios de dominio. Los contratos F11 ya están disponibles; la fecha vigente se obtiene de Catalog y no de un campo nuevo en la respuesta de Purchase. Trazabilidad: PDF-INTERFAZ-GRAFICA, PDF-F1-F4, PDF-F6-F7, PDF-F9-F12 y PDF-NF-SECURITY.
