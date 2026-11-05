# Venue Management Specification

## Purpose

Define el catálogo canónico de lugares y sus configuraciones físicas reutilizables para que los eventos puedan seleccionar una plantilla consistente.

## Requirements

### Requirement: Registrar lugares canónicos

El sistema SHALL permitir que un administrador registre un lugar con identificador, nombre, dirección, ciudad, latitud y longitud.

#### Scenario: Administrador registra un lugar válido

- **WHEN** un usuario con rol `ADMIN` envía todos los datos válidos de un lugar
- **THEN** el sistema registra el lugar y devuelve su representación con un identificador único

#### Scenario: Organizador intenta registrar un lugar

- **WHEN** un usuario con rol `ORGANIZER` intenta registrar un lugar
- **THEN** el sistema rechaza la operación por falta de permisos

#### Scenario: Datos geográficos inválidos

- **WHEN** un administrador informa una latitud fuera de `-90..90` o una longitud fuera de `-180..180`
- **THEN** el sistema rechaza la operación e identifica los campos inválidos

### Requirement: Configurar sectores numerados

El sistema SHALL permitir que un administrador configure sectores numerados mediante filas y asientos identificados de forma única dentro del sector.

#### Scenario: Crear sector numerado

- **WHEN** un administrador agrega a un lugar un sector numerado con filas y asientos válidos
- **THEN** el sistema registra el sector y deriva su capacidad de la cantidad de asientos

#### Scenario: Asiento duplicado

- **WHEN** la configuración repite la misma combinación de fila y número dentro de un sector
- **THEN** el sistema rechaza la configuración sin guardar parcialmente el sector

### Requirement: Configurar sectores de admisión general

El sistema SHALL permitir que un administrador configure sectores no numerados con una capacidad máxima positiva.

#### Scenario: Crear sector de admisión general

- **WHEN** un administrador agrega un sector no numerado con capacidad mayor que cero
- **THEN** el sistema registra el sector con tipo `GENERAL_ADMISSION` y la capacidad indicada

#### Scenario: Capacidad inválida

- **WHEN** un administrador intenta configurar capacidad igual o menor que cero
- **THEN** el sistema rechaza la configuración

### Requirement: Modificar plantillas de lugares

El sistema SHALL permitir que un administrador modifique un lugar y su plantilla sin alterar snapshots de eventos que ya abandonaron el estado `DRAFT`.

#### Scenario: Modificar un lugar utilizado por un evento publicado

- **GIVEN** un evento publicado posee un snapshot del lugar
- **WHEN** un administrador modifica la plantilla canónica de ese lugar
- **THEN** el sistema conserva sin cambios el snapshot del evento publicado

### Requirement: Consultar lugares para configurar eventos

El sistema SHALL permitir que organizadores y administradores consulten lugares y sus sectores para seleccionar una plantilla al crear un evento.

#### Scenario: Organizador consulta lugares

- **WHEN** un usuario con rol `ORGANIZER` solicita los lugares disponibles
- **THEN** el sistema devuelve una lista paginada con sus sectores y tipos

#### Scenario: Cliente consulta la vista de gestión de lugares

- **WHEN** un usuario con rol `CLIENT` solicita la vista de gestión de lugares
- **THEN** el sistema rechaza la operación por falta de permisos
