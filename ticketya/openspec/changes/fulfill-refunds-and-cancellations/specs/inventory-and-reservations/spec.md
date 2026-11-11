# Spec Delta

## ADDED Requirements

### Requirement: Liberar una unidad vendida tras reintegro
Inventory SHALL liberar una unidad vendida identificada por reserva e ítem de manera idempotente y atómica, sin afectar otras unidades de la misma venta.

#### Scenario: Liberar asiento vendido
- **GIVEN** un asiento vendido de una reserva confirmada
- **WHEN** Purchase informa el reintegro confirmado de ese ítem
- **THEN** el asiento pasa a disponible y una repetición no cambia otros contadores

#### Scenario: Liberar cupo general vendido
- **GIVEN** una unidad vendida de admisión general
- **WHEN** Purchase informa su reintegro confirmado
- **THEN** vendidos baja en una unidad y disponible sube en una unidad

#### Scenario: Unidad ajena o no vendida
- **WHEN** Purchase intenta liberar una unidad que no pertenece a la reserva confirmada o ya no está vendida por otra causa
- **THEN** Inventory responde conflicto sin alterar disponibilidad

### Requirement: Bloquear reservas tras cancelación
Inventory SHALL rechazar nuevas reservas para un evento cancelado sin alterar reservas confirmadas hasta recibir comandos de liberación por unidad.

#### Scenario: Evento cancelado
- **WHEN** Purchase intenta reservar una unidad de un evento cancelado
- **THEN** Inventory rechaza la reserva y conserva los lugares vendidos hasta su reintegro
