# Tasks

## 1. Solicitud de devolución

- [ ] 1.1 Persistir solicitud única por ticket en Purchase, con titular, importe, evento, decisión y progreso; verificar constraints y lectura en PostgreSQL real.
- [ ] 1.2 Exponer solicitud y consulta del titular, con ventana de 72 horas y respuesta 404 para terceros; verificar límites de tiempo, duplicados y permisos con pruebas HTTP.
- [x] 1.3 Documentar rutas y ejemplos en OpenAPI/Postman; ejecutar los ejemplos contra Compose.

## 2. Decisión y reintegro individual

- [x] 2.1 Exponer decisión ADMIN idempotente y rechazo sin efectos colaterales; verificar roles y decisión incompatible en pruebas.
- [ ] 2.2 Implementar revocación idempotente en Ticket y documentar su contrato; verificar estado y reintento en PostgreSQL real.
- [ ] 2.3 Implementar liberación de unidad vendida en Inventory; verificar asiento, cupo general, reintento y competencia con pruebas PostgreSQL.
- [ ] 2.3a Extender Payment Mock para varios reintegros parciales idempotentes por cobro, con límite por importe y pruebas de persistencia.
- [ ] 2.4 Orquestar revocación, reintegro y liberación en Purchase con progreso durable; verificar fallo en cada dependencia y recuperación sin dobles efectos.
- [x] 2.5 Documentar flujo aprobado/rechazado en OpenAPI/Postman; verificarlo por Gateway en Compose.

## 3. Cancelación masiva

- [ ] 3.1 Asegurar cancelación solo ADMIN en Catalog y bloqueo de nuevas reservas en Inventory; verificar idempotencia y carreras con pruebas.
- [ ] 3.2 Crear procesamiento reintentable de todos los tickets pagados del evento en Purchase; verificar que cada ticket reciba un único reintegro y que se exponga progreso.
- [ ] 3.3 Publicar ruta administrativa en Gateway y documentar contrato y ejemplo en OpenAPI/Postman; verificar evento con ventas por Compose.

## 4. Integración

- [ ] 4.1 Ejecutar `mvn verify`, `docker compose up --build --wait` y `openspec validate fulfill-refunds-and-cancellations --strict`; verificar compra, devolución y cancelación mediante Gateway.

## Workflow follow-up

- Archivar el change después de la revisión y de completar todos los criterios de OpenSpec.md.
