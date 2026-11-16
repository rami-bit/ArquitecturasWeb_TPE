# Spec Delta

## MODIFIED Requirements

### Requirement: Crear eventos en borrador

El sistema SHALL permitir que un organizador cree un evento propio en estado `DRAFT` seleccionando un lugar canónico, y SHALL permitir que un administrador cree uno para cualquier organizador. El evento SHALL admitir una marca de alta demanda, que SHALL ser opcional y SHALL valer `false` cuando no se informe.

#### Scenario: Organizador crea un borrador propio

- **WHEN** un organizador informa un nombre y un lugar existente para un nuevo evento
- **THEN** el sistema crea el evento en estado `DRAFT` y asigna como propietario al organizador autenticado

#### Scenario: Organizador intenta crear para un tercero

- **WHEN** un organizador intenta crear un evento cuyo propietario es otro organizador
- **THEN** el sistema rechaza la operación por falta de permisos

#### Scenario: Administrador crea un borrador para un organizador

- **WHEN** un administrador informa un organizador y un lugar existentes
- **THEN** el sistema crea el evento en estado `DRAFT` para el organizador indicado

#### Scenario: Crear un evento marcado como de alta demanda

- **WHEN** un organizador crea un borrador informando la marca de alta demanda
- **THEN** el sistema guarda el evento con esa marca y la marca puede consultarse en la gestión del evento

#### Scenario: Crear un evento sin informar la marca

- **WHEN** un organizador crea un borrador sin informar la marca de alta demanda
- **THEN** el sistema guarda el evento con la marca de alta demanda desactivada

### Requirement: Configurar un borrador

El sistema SHALL permitir editar los datos del evento, participantes, lugar seleccionado, sectores habilitados, precios y la marca de alta demanda mientras el evento permanezca en estado `DRAFT`.

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

#### Scenario: Cambiar la marca de alta demanda de un borrador

- **GIVEN** un evento en estado `DRAFT`
- **WHEN** su organizador propietario activa o desactiva la marca de alta demanda
- **THEN** el sistema guarda la nueva marca

#### Scenario: Cambiar la marca de alta demanda de un evento publicado

- **GIVEN** un evento en estado `PUBLISHED`
- **WHEN** su organizador propietario intenta cambiar la marca de alta demanda
- **THEN** el sistema rechaza la operación y conserva la marca vigente

### Requirement: Exponer el snapshot publicado para inicializar inventario

Catalog SHALL exponer un contrato interno de lectura que devuelva el snapshot publicado de un evento con los identificadores de origen de sus sectores y asientos, sus tipos, capacidades, datos de ubicación y la marca de alta demanda del evento. El contrato SHALL NOT incluir precios y SHALL quedar disponible sólo para otros servicios de la red interna.

#### Scenario: Consultar el snapshot de un evento publicado

- **GIVEN** un evento publicado con snapshot y sectores numerados y de admisión general
- **WHEN** Inventory solicita el snapshot por el contrato interno
- **THEN** Catalog devuelve el identificador del evento, el del snapshot, los datos del lugar, la marca de alta demanda y cada sector con su identificador de origen, tipo y capacidad, incluyendo los asientos con su identificador de origen, fila y número

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

### Requirement: Exponer un snapshot de compra con precios

Catalog SHALL exponer un contrato interno de lectura, separado del snapshot de inventario, que devuelva el snapshot publicado con los datos del evento, lugar, sectores, asientos, el precio de cada sector y la marca de alta demanda del evento. El contrato SHALL NOT publicarse por el Gateway.

#### Scenario: Consultar el snapshot de compra de un evento publicado

- **GIVEN** un evento publicado con snapshot y sectores numerados y de admisión general
- **WHEN** Purchase solicita el snapshot de compra por el contrato interno
- **THEN** Catalog devuelve el evento, el lugar, la marca de alta demanda, cada sector con su identificador de origen, tipo, capacidad y precio, y los asientos con su identificador de origen, fila y número

#### Scenario: Evento no comprable

- **GIVEN** un evento inexistente, en borrador, suspendido o cancelado
- **WHEN** Purchase solicita el snapshot de compra
- **THEN** Catalog responde `404` sin revelar datos del evento

#### Scenario: El snapshot de compra es inmutable

- **GIVEN** un evento publicado con snapshot
- **WHEN** cambia la plantilla canónica del lugar o sus sectores
- **THEN** el contrato interno sigue devolviendo el snapshot original con los precios publicados

#### Scenario: El snapshot de inventario no cambia

- **GIVEN** el contrato interno de snapshot consumido por Inventory
- **WHEN** se agrega el snapshot de compra
- **THEN** el contrato de Inventory sigue sin incluir precios y conserva su respuesta

#### Scenario: Ambos contratos informan la misma marca

- **GIVEN** un evento publicado
- **WHEN** se consultan los dos contratos internos de snapshot
- **THEN** ambos informan la misma marca de alta demanda y difieren únicamente en los precios

#### Scenario: El contrato de compra no se publica por el Gateway

- **WHEN** un cliente externo intenta acceder al snapshot de compra a través del Gateway
- **THEN** el Gateway no lo enruta y responde como ruta no encontrada
