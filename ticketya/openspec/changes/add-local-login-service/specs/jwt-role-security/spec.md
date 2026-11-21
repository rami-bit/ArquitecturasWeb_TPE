# Spec Delta

## MODIFIED Requirements

### Requirement: Validar identidad JWT emitida por un proveedor externo

Las operaciones protegidas SHALL aceptar únicamente un JWT firmado y vigente, con `sub` y exactamente un rol válido para TicketYa, emitido por el proveedor de identidad confiable configurado para esa instancia del Gateway. Ese emisor SHALL poder ser Auth Service local en el stack normal o el IdP de prueba en una corrida aislada; una instancia SHALL confiar en un único emisor a la vez. Trazabilidad: PDF-NF-SECURITY.

#### Scenario: Identidad válida

- **WHEN** una solicitud protegida presenta un JWT firmado por el proveedor de identidad configurado, no vencido, con `sub` UUID y un único rol válido
- **THEN** el sistema identifica al actor por `sub` y sus roles por el claim de roles

#### Scenario: Token ausente, inválido o vencido

- **WHEN** una solicitud protegida presenta un token ausente, malformado, con firma inválida, vencido o sin claims de identidad válidos
- **THEN** el Gateway responde `401` y no invoca el servicio de dominio

#### Scenario: Emisor local en el stack normal

- **GIVEN** el Gateway está configurado para confiar en Auth Service local
- **WHEN** recibe un JWT vigente emitido por ese servicio
- **THEN** valida firma, emisor, expiración, `sub` UUID y rol antes de enrutar la operación protegida

#### Scenario: Emisor de prueba en un stack aislado

- **GIVEN** el Gateway de una corrida de pruebas está configurado para confiar en el IdP de prueba
- **WHEN** recibe un JWT vigente emitido por ese IdP para una identidad arbitraria
- **THEN** aplica las mismas validaciones y reglas de autorización que en el stack normal

#### Scenario: Token de otro emisor

- **WHEN** el Gateway recibe un JWT cuyo emisor no coincide con su configuración actual
- **THEN** responde `401` y no invoca el servicio de dominio
