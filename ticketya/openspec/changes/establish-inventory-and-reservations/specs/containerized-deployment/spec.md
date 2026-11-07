# Spec Delta

## MODIFIED Requirements

### Requirement: Compose central en la raíz

El sistema SHALL mantener un único `docker-compose.yml` en la raíz del repositorio con todos los componentes y dependencias requeridos por el incremento actual.

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
