# Spec Delta

## ADDED Requirements

### Requirement: Resolver destinatarios de una publicación relevante

Preferences SHALL ofrecer sólo a servicios internos una búsqueda paginada de identificadores de clientes cuyas preferencias de ciudad y artistas favoritos coincidan con la ciudad y participantes de un evento. SHALL normalizar mayúsculas y espacios extremos y SHALL NOT incluir el contenido de las preferencias ni emails en la respuesta. Trazabilidad: PDF-F11 y PDF-NF-SECURITY.

#### Scenario: Ciudad y artista coincidentes

- **GIVEN** clientes con ciudades y artistas favoritos distintos
- **WHEN** Notification consulta la audiencia con ciudad y participantes del evento publicado
- **THEN** Preferences devuelve sólo los identificadores de clientes que cumplen ambas coincidencias, sin duplicados

#### Scenario: Más destinatarios que una página

- **GIVEN** la audiencia supera el tamaño máximo de página
- **WHEN** Notification recorre las páginas con el cursor devuelto
- **THEN** puede procesar todos los identificadores en orden estable sin omitir clientes

#### Scenario: Contrato interno no público

- **WHEN** un cliente externo intenta invocar la búsqueda de audiencia por el Gateway
- **THEN** el Gateway no enruta la solicitud y no expone preferencias de terceros
