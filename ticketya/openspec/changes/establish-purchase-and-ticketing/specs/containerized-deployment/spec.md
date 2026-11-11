# Spec Delta

## ADDED Requirements

### Requirement: Run purchase and payment locally with owned databases
The root Compose deployment SHALL build Purchase Service and Payment Mock, provide each with its own PostgreSQL database and volume, and wait for healthy dependencies before starting each service.

#### Scenario: Start the complete purchase increment
- **WHEN** a developer runs `docker compose up --build` with documented environment values
- **THEN** Gateway, Catalog, Inventory, Ticket, Purchase, Payment Mock, and each service database become healthy

#### Scenario: Keep internal components private
- **WHEN** Compose starts Purchase, Payment Mock, and their databases
- **THEN** these components expose no host ports and communicate over the internal Compose network

#### Scenario: Build new services independently
- **WHEN** a developer builds either new service from its Dockerfile
- **THEN** its image builds using the repository build context and without machine-specific paths
