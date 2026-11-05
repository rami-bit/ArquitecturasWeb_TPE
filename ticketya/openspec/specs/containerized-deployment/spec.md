# Containerized Deployment Specification

## Purpose

Define un despliegue local reproducible para que cualquier integrante pueda construir y levantar el incremento completo sin depender de rutas o configuración personal.

## Requirements

### Requirement: Dockerfile por componente de aplicación

El sistema SHALL proveer un `Dockerfile` independiente para cada microservicio, Gateway u otro componente ejecutable desarrollado por el equipo.

#### Scenario: Construir una imagen individual

- **WHEN** un integrante construye la imagen desde el `Dockerfile` de un componente
- **THEN** la imagen se genera sin depender de rutas personales ni artefactos previamente compilados fuera de su contexto declarado

#### Scenario: Agregar un nuevo componente

- **WHEN** una épica incorpora un nuevo componente ejecutable desarrollado por el equipo
- **THEN** esa misma épica incorpora su `Dockerfile` y verifica la construcción de su imagen

### Requirement: Compose central en la raíz

El sistema SHALL mantener un único `docker-compose.yml` en la raíz del repositorio con todos los componentes y dependencias requeridos por el incremento actual.

#### Scenario: Levantar el incremento completo

- **WHEN** un integrante ejecuta `docker compose up --build` desde la raíz del repositorio con la configuración documentada
- **THEN** Compose construye y levanta API Gateway, Catalog Service y PostgreSQL para esta épica

#### Scenario: Evolucionar el sistema

- **WHEN** una épica posterior agrega, elimina o reemplaza un componente de ejecución
- **THEN** actualiza el mismo `docker-compose.yml` raíz para representar el sistema completo vigente

### Requirement: Imágenes oficiales para infraestructura

El sistema SHALL usar imágenes oficiales para dependencias estándar de infraestructura y SHALL evitar Dockerfiles custom que no respondan a una personalización necesaria.

#### Scenario: Declarar PostgreSQL

- **WHEN** se configura PostgreSQL en el Compose central
- **THEN** se referencia una versión explícita de una imagen oficial sin crear un Dockerfile propio para la base

### Requirement: Configuración portable

El despliegue SHALL recibir configuración mediante variables de entorno o archivos de ejemplo seguros, sin versionar rutas personales ni secretos reales.

#### Scenario: Ejecutar desde otro entorno

- **GIVEN** un checkout limpio y las variables documentadas
- **WHEN** otro integrante levanta el sistema con Compose
- **THEN** los componentes se conectan usando nombres de servicio de la red de Compose y no requieren rutas locales del autor
