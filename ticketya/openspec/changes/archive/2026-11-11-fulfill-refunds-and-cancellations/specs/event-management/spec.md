# Spec Delta

## ADDED Requirements

### Requirement: Cancelar un evento y cerrar sus ventas
El sistema SHALL permitir solo a ADMIN cancelar un evento publicado o suspendido, conservar su snapshot y bloquear nuevas ventas. Purchase SHALL poder iniciar el procesamiento de tickets vendidos tras la cancelación.

#### Scenario: Cancelación administrativa
- **GIVEN** un evento publicado con tickets vendidos
- **WHEN** ADMIN solicita cancelarlo
- **THEN** Catalog lo marca `CANCELLED`, Inventory rechaza nuevas reservas y Purchase inicia los reintegros

#### Scenario: Repetir cancelación
- **GIVEN** un evento cancelado
- **WHEN** ADMIN repite la solicitud
- **THEN** se conserva el mismo estado sin duplicar procesamiento

#### Scenario: Rol no autorizado
- **WHEN** un organizador o cliente solicita cancelar un evento
- **THEN** se rechaza la operación sin cambiar su estado
