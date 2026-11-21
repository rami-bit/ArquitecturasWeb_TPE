# Spec Delta

## MODIFIED Requirements

### Requirement: Compose central en la raíz

El sistema SHALL mantener un único `docker-compose.yml` en la raíz con todos los componentes del incremento actual, incluidos Preferences con MongoDB, Notification Service con base propia y Mailpit como receptor SMTP local. Cada servicio de aplicación y base SHALL tener healthcheck y no publicar su API al host; sólo el Gateway publica la API de TicketYa. Trazabilidad: PDF-NF-DEPLOYMENT, PDF-NF-PERSISTENCE y PDF-F11.

#### Scenario: Levantar el incremento completo

- **WHEN** un integrante ejecuta `docker compose up --build` desde la raíz del repositorio con la configuración documentada
- **THEN** Compose construye y levanta API Gateway, Catalog Service, Inventory Service, sus bases PostgreSQL y las demás dependencias del incremento vigente, y sus healthchecks quedan sanos

#### Scenario: Evolucionar el sistema

- **WHEN** una épica posterior agrega, elimina o reemplaza un componente de ejecución
- **THEN** actualiza el mismo `docker-compose.yml` raíz para representar el sistema completo vigente

#### Scenario: Mantener bases de datos separadas

- **GIVEN** el incremento incluye Catalog Service e Inventory Service
- **WHEN** Compose levanta ambos servicios
- **THEN** cada uno usa su propia base PostgreSQL y su propio volumen, sin compartir esquema ni acceso directo a los datos del otro

#### Scenario: Inventory sólo es accesible por la red interna

- **WHEN** Compose levanta Inventory Service y su base
- **THEN** ninguno publica un puerto al host y ambos sólo son accesibles desde la red interna de Compose

#### Scenario: Levantar preferencias con su base NoSQL

- **WHEN** Compose levanta `preferences-service` y `preferences-db`
- **THEN** ambos quedan `healthy`, la base usa un volumen propio y ninguno publica un puerto al host

#### Scenario: Preferencias usa una base exclusiva

- **GIVEN** el incremento incluye Preferences Service
- **WHEN** Compose levanta el sistema
- **THEN** `preferences-service` escribe sólo en `preferences-db` y no accede a las bases de Catalog ni de Purchase

#### Scenario: Levantar email verificable

- **WHEN** Compose levanta Notification Service, su base y Mailpit
- **THEN** el servicio y la base quedan saludables, SMTP es accesible en la red interna y la interfaz de Mailpit sólo se publica en `127.0.0.1` para inspección local; ninguna API de microservicio se publica directamente
