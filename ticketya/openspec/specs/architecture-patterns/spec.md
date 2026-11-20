# architecture-patterns Specification

## Purpose

Define el documento único que justifica los patrones arquitectónicos y las estrategias de consistencia usados en TicketYa, con trazabilidad al enunciado y a la evidencia verificable.

## Requirements

### Requirement: Documentar los patrones en un único lugar

El repositorio SHALL contener un documento único que explique API Gateway, Saga, Circuit Breaker, BFF, Service Discovery, caché y resiliencia. Cada patrón SHALL indicar el problema que resuelve, la alternativa más simple descartada, el costo asumido y la evidencia que demuestra su uso. Trazabilidad: PDF-NF-GATEWAY, PDF-NF-RESILIENCE y PDF-NF-CACHE.

#### Scenario: Patrones trazables

- **WHEN** se consulta el documento de patrones
- **THEN** cada patrón referencia al menos un requisito PDF y un archivo o prueba del repositorio

#### Scenario: Sin duplicación

- **WHEN** se actualiza una decisión de arquitectura
- **THEN** el documento único es la fuente de verdad y los demás documentos enlazan a él en lugar de copiar su contenido

### Requirement: Documentar consistencia y compensaciones

El documento SHALL distinguir consistencia fuerte y eventual, identificar qué operaciones usan cada una y describir las compensaciones de la saga de compra, la idempotencia y el manejo de fallos parciales. Trazabilidad: PDF-NF-CONSISTENCY y PDF-F5.

#### Scenario: Compra con fallo parcial

- **GIVEN** un pago aprobado cuya confirmación de inventario falla
- **WHEN** se consulta el documento de consistencia
- **THEN** describe el reintegro, la liberación de la reserva y el estado compensado resultante

#### Scenario: Fuentes autoritativas

- **WHEN** se consulta el documento sobre disponibilidad y caché
- **THEN** establece que la disponibilidad de inventario nunca se cachea como verdad autoritativa

### Requirement: Mantener el alcance de F11 fuera del documento de implementación

El documento SHALL NO afirmar que las notificaciones están implementadas. Cualquier mención a F11 SHALL remitir al trabajo pendiente delegado. Trazabilidad: PDF-F11.

#### Scenario: Estado de notificaciones

- **WHEN** se lee la sección de comunicaciones asíncronas
- **THEN** distingue el despacho con fallback local ya existente de la entrega de notificaciones pendiente
