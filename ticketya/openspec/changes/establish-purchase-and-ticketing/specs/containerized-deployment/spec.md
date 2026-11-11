# Spec Delta

## ADDED Requirements

### Requirement: Incluir Purchase y Payment en el Compose central

El `docker-compose.yml` raíz SHALL declarar Purchase Service, Payment Mock y sus bases PostgreSQL con volúmenes propios, healthchecks y variables de entorno. Ninguno de los dos servicios SHALL publicar un puerto al host.

#### Scenario: Levantar el incremento con compra

- **WHEN** un integrante ejecuta `docker compose up --build` desde la raíz con la configuración documentada
- **THEN** Compose construye y levanta Purchase Service, Payment Mock, sus bases y los servicios previos, y todos los healthchecks quedan sanos

#### Scenario: Mantener bases separadas

- **GIVEN** el incremento incluye Purchase Service y Payment Mock
- **WHEN** Compose levanta ambos servicios
- **THEN** cada uno usa su propia base PostgreSQL y su propio volumen, sin compartir esquema ni acceso directo a los datos del otro

#### Scenario: Servicios internos sin puerto público

- **WHEN** Compose levanta Purchase Service, Payment Mock y sus bases
- **THEN** ninguno publica un puerto al host y todos son accesibles sólo desde la red interna de Compose

#### Scenario: Construir una imagen individual

- **WHEN** un integrante construye la imagen de Purchase Service o de Payment Mock desde su Dockerfile
- **THEN** la imagen se genera sin depender de artefactos compilados fuera de su contexto declarado
