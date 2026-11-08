# Event Management Specification

## Purpose

Define el ciclo editorial de los eventos desde su borrador hasta su publicación y suspensión, preservando una configuración inmutable para ventas futuras.

## Requirements

### Requirement: Crear eventos en borrador

El sistema SHALL permitir que un organizador cree un evento propio en estado `DRAFT` seleccionando un lugar canónico, y SHALL permitir que un administrador cree uno para cualquier organizador.

#### Scenario: Organizador crea un borrador propio

- **WHEN** un organizador informa un nombre y un lugar existente para un nuevo evento
- **THEN** el sistema crea el evento en estado `DRAFT` y asigna como propietario al organizador autenticado

#### Scenario: Organizador intenta crear para un tercero

- **WHEN** un organizador intenta crear un evento cuyo propietario es otro organizador
- **THEN** el sistema rechaza la operación por falta de permisos

#### Scenario: Administrador crea un borrador para un organizador

- **WHEN** un administrador informa un organizador y un lugar existentes
- **THEN** el sistema crea el evento en estado `DRAFT` para el organizador indicado

### Requirement: Configurar un borrador

El sistema SHALL permitir editar los datos del evento, participantes, lugar seleccionado, sectores habilitados y precios mientras el evento permanezca en estado `DRAFT`.

#### Scenario: Propietario modifica su borrador

- **GIVEN** un evento en estado `DRAFT`
- **WHEN** su organizador propietario modifica su configuración con datos válidos
- **THEN** el sistema guarda la nueva configuración

#### Scenario: Organizador modifica un borrador ajeno

- **GIVEN** un evento en estado `DRAFT` perteneciente a otro organizador
- **WHEN** un organizador intenta modificarlo
- **THEN** el sistema rechaza la operación por falta de permisos

#### Scenario: Administrador modifica un borrador ajeno

- **GIVEN** un evento en estado `DRAFT`
- **WHEN** un administrador modifica su configuración con datos válidos
- **THEN** el sistema guarda la nueva configuración

#### Scenario: Configurar un sector ajeno al lugar

- **WHEN** se intenta habilitar un sector que no pertenece al lugar seleccionado
- **THEN** el sistema rechaza la configuración

#### Scenario: Configurar un precio no positivo

- **WHEN** se asigna a un sector un precio igual o menor que cero
- **THEN** el sistema rechaza la configuración

### Requirement: Publicar un evento válido

El sistema SHALL publicar un borrador sólo si posee datos completos, fecha futura, al menos un participante y al menos un sector habilitado con precio positivo.

#### Scenario: Propietario publica un borrador válido

- **GIVEN** un borrador válido y completamente configurado
- **WHEN** su organizador propietario solicita publicarlo
- **THEN** el sistema cambia el estado a `PUBLISHED` y devuelve el evento publicado

#### Scenario: Administrador publica un borrador válido

- **GIVEN** un borrador válido perteneciente a cualquier organizador
- **WHEN** un administrador solicita publicarlo
- **THEN** el sistema cambia el estado a `PUBLISHED`

#### Scenario: Publicar una configuración incompleta

- **GIVEN** un borrador sin participantes o sin sectores habilitados y valorizados
- **WHEN** un usuario autorizado solicita publicarlo
- **THEN** el sistema rechaza la publicación y conserva el estado `DRAFT`

#### Scenario: Publicar un evento con fecha no futura

- **GIVEN** un borrador cuya fecha y hora no son futuras
- **WHEN** un usuario autorizado solicita publicarlo
- **THEN** el sistema rechaza la publicación y conserva el estado `DRAFT`

### Requirement: Crear un snapshot al publicar

El sistema SHALL crear atómicamente un snapshot inmutable de los datos del evento, lugar, sectores, asientos, capacidades y precios al abandonar `DRAFT` mediante publicación.

#### Scenario: Publicación crea el snapshot completo

- **GIVEN** un borrador válido con sectores numerados y no numerados
- **WHEN** el evento se publica
- **THEN** el snapshot contiene la configuración vigente y el cambio de estado se confirma en la misma operación

#### Scenario: Falla al crear el snapshot

- **GIVEN** un borrador válido
- **WHEN** no puede guardarse completamente su snapshot
- **THEN** el sistema revierte la operación y conserva el evento en `DRAFT` sin snapshot parcial

#### Scenario: Cambiar la plantilla canónica después de publicar

- **GIVEN** un evento publicado con snapshot
- **WHEN** cambia el lugar canónico o alguno de sus sectores
- **THEN** el snapshot publicado permanece sin modificaciones

### Requirement: Publicación idempotente

El sistema SHALL tratar una nueva solicitud de publicación de un evento ya publicado como una operación idempotente sin crear snapshots adicionales.

#### Scenario: Repetir publicación

- **GIVEN** un evento en estado `PUBLISHED` con snapshot
- **WHEN** un usuario autorizado repite la solicitud de publicación
- **THEN** el sistema devuelve la representación publicada existente y conserva un único snapshot

### Requirement: Inmutabilidad fuera de borrador

El sistema MUST rechazar modificaciones de datos o configuración cuando el evento ya no se encuentre en estado `DRAFT`.

#### Scenario: Intentar editar un evento publicado

- **GIVEN** un evento en estado `PUBLISHED`
- **WHEN** un organizador o administrador intenta modificar sus datos o configuración
- **THEN** el sistema rechaza la operación y conserva el snapshot

#### Scenario: Intentar editar un evento suspendido

- **GIVEN** un evento en estado `SUSPENDED`
- **WHEN** un organizador o administrador intenta modificar sus datos o configuración
- **THEN** el sistema rechaza la operación y conserva el snapshot

### Requirement: Suspender eventos publicados

El sistema SHALL permitir únicamente a administradores cambiar un evento de `PUBLISHED` a `SUSPENDED` sin modificar su snapshot.

#### Scenario: Administrador suspende un evento publicado

- **GIVEN** un evento en estado `PUBLISHED`
- **WHEN** un administrador solicita suspenderlo
- **THEN** el sistema cambia su estado a `SUSPENDED` y conserva el snapshot

#### Scenario: Organizador intenta suspender su evento

- **GIVEN** un evento en estado `PUBLISHED`
- **WHEN** su organizador solicita suspenderlo
- **THEN** el sistema rechaza la operación por falta de permisos

#### Scenario: Suspender desde un estado inválido

- **GIVEN** un evento que no está en estado `PUBLISHED`
- **WHEN** un administrador solicita suspenderlo
- **THEN** el sistema rechaza la transición y conserva el estado actual

### Requirement: Reanudar eventos suspendidos

El sistema SHALL permitir únicamente a administradores cambiar un evento de `SUSPENDED` a `PUBLISHED` sin modificar ni recrear su snapshot.

#### Scenario: Administrador reanuda un evento suspendido

- **GIVEN** un evento en estado `SUSPENDED`
- **WHEN** un administrador solicita reanudarlo
- **THEN** el sistema cambia su estado a `PUBLISHED` y conserva el snapshot existente

#### Scenario: Reanudar desde un estado inválido

- **GIVEN** un evento que no está en estado `SUSPENDED`
- **WHEN** un administrador solicita reanudarlo
- **THEN** el sistema rechaza la transición y conserva el estado actual

### Requirement: Consultar eventos de gestión

El sistema SHALL limitar las vistas de gestión de manera que un organizador acceda sólo a sus eventos y un administrador pueda acceder a cualquiera.

#### Scenario: Organizador lista sus eventos

- **WHEN** un organizador solicita su listado de gestión
- **THEN** el sistema devuelve sus eventos en cualquier estado y no incluye eventos ajenos

#### Scenario: Administrador lista eventos

- **WHEN** un administrador solicita el listado de gestión
- **THEN** el sistema puede devolver eventos de cualquier organizador y estado

### Requirement: Identificación provisional de Entrega 1

El sistema SHALL identificar usuario y rol mediante encabezados de solicitud durante la Entrega 1 y SHALL rechazar operaciones de gestión sin identidad válida.

#### Scenario: Solicitud de gestión sin identidad

- **WHEN** una solicitud de gestión no incluye identificador y rol válidos
- **THEN** el sistema rechaza la operación sin modificar datos

#### Scenario: Solicitud pública sin identidad

- **WHEN** una solicitud pública de búsqueda o detalle no incluye encabezados de identidad
- **THEN** el sistema procesa la consulta sin exigir autenticación

### Requirement: Exponer el snapshot publicado para inicializar inventario

Catalog SHALL exponer un contrato interno de lectura que devuelva el snapshot publicado de un evento con los identificadores de origen de sus sectores y asientos, sus tipos, capacidades y datos de ubicación. El contrato SHALL NOT incluir precios y SHALL quedar disponible sólo para otros servicios de la red interna.

#### Scenario: Consultar el snapshot de un evento publicado

- **GIVEN** un evento publicado con snapshot y sectores numerados y de admisión general
- **WHEN** Inventory solicita el snapshot por el contrato interno
- **THEN** Catalog devuelve el identificador del evento, el del snapshot, los datos del lugar y cada sector con su identificador de origen, tipo y capacidad, incluyendo los asientos con su identificador de origen, fila y número

#### Scenario: Consultar un evento sin snapshot

- **GIVEN** un evento inexistente, en estado `DRAFT` o sin snapshot
- **WHEN** Inventory solicita su snapshot por el contrato interno
- **THEN** Catalog responde `404` sin revelar datos del evento

#### Scenario: El snapshot consultado es inmutable

- **GIVEN** un evento publicado con snapshot
- **WHEN** cambia la plantilla canónica del lugar o sus sectores
- **THEN** el contrato interno sigue devolviendo el snapshot original sin modificaciones

#### Scenario: No exponer precios en el contrato interno

- **GIVEN** un snapshot publicado que contiene precios por sector
- **WHEN** Inventory consulta el snapshot
- **THEN** la respuesta no incluye precios ni ofertas económicas

#### Scenario: El contrato interno no se publica por el Gateway

- **WHEN** un cliente externo intenta acceder al contrato interno de snapshot a través del Gateway
- **THEN** el Gateway no lo enruta y responde como ruta no encontrada
