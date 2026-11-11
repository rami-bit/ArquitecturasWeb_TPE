# Spec Delta

## ADDED Requirements

### Requirement: Publicar operaciones de devolución y cancelación
Gateway SHALL enrutar solicitudes del titular y decisiones administrativas a Purchase, y cancelación administrativa a Catalog/Purchase, sin exponer comandos internos de Ticket, Payment ni Inventory.

#### Scenario: Solicitud pública de devolución
- **WHEN** el titular solicita una devolución por la ruta pública
- **THEN** Gateway preserva identidad y correlation ID y devuelve la respuesta de Purchase

#### Scenario: Comando interno no público
- **WHEN** un cliente externo intenta invocar liberación de unidad o reintegro interno
- **THEN** Gateway responde ruta no encontrada
