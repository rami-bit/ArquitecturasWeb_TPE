# Spec Delta

## MODIFIED Requirements

### Requirement: Compose central en la raíz

El sistema SHALL mantener un único `docker-compose.yml` en la raíz del repositorio con todos los componentes y dependencias requeridos por el incremento actual. SHALL incluir `preferences-service` y su base MongoDB `preferences-db` (`mongo:7`) con volumen propio, healthchecks y sin publicar puertos al host. Trazabilidad: PDF-NF-DEPLOYMENT y PDF-NF-PERSISTENCE.

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

### Requirement: Imágenes oficiales para infraestructura

El sistema SHALL usar imágenes oficiales para dependencias estándar de infraestructura y SHALL evitar Dockerfiles custom que no respondan a una personalización necesaria. La base NoSQL SHALL usar la imagen oficial `mongo:7` con versión explícita y sin Dockerfile propio.

#### Scenario: Declarar PostgreSQL

- **WHEN** se configura PostgreSQL en el Compose central
- **THEN** se referencia una versión explícita de una imagen oficial sin crear un Dockerfile propio para la base

#### Scenario: Declarar MongoDB

- **WHEN** se configura MongoDB en el Compose central
- **THEN** se referencia la imagen oficial `mongo:7` con versión explícita y sin crear un Dockerfile propio para la base
