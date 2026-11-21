# Spec Delta

## ADDED Requirements

### Requirement: Postergar la fecha y hora de un evento publicado

Catalog SHALL exponer `PATCH /api/management/events/{id}/schedule` para que el `ORGANIZER` dueño o `ADMIN` cambie sólo `startsAt` de un evento `PUBLISHED` que aún no comenzó. La nueva fecha, comparada como instante, SHALL ser posterior a la fecha vigente y al instante de gestión más 72 horas. El request SHALL incluir revisión esperada y clave de idempotencia. El resto de la configuración SHALL conservarse. Trazabilidad: PDF-F11.

#### Scenario: Postergación válida del organizador

- **GIVEN** un evento `PUBLISHED` cuya fecha vigente es posterior al momento actual
- **WHEN** el organizador dueño propone una fecha posterior a la vigente y a `now + 72h`
- **THEN** Catalog confirma la nueva fecha, incrementa la revisión de programación y devuelve fecha anterior, fecha vigente y revisión

#### Scenario: Administrador posterga un evento ajeno

- **WHEN** `ADMIN` posterga válidamente un evento publicado de cualquier organizador
- **THEN** Catalog confirma el cambio con el mismo contrato de respuesta

#### Scenario: Fecha adelantada, igual o dentro de las 72 horas

- **WHEN** la fecha propuesta es anterior o igual a la vigente, o no supera estrictamente `now + 72h`
- **THEN** Catalog responde `409` y conserva la fecha y revisión vigentes

#### Scenario: Estado o actor no permitido

- **WHEN** se intenta postergar un evento `DRAFT`, `SUSPENDED`, `CANCELLED` o `FINISHED`, o lo solicita un organizador ajeno
- **THEN** Catalog rechaza con `409` o `403` según corresponda y no cambia ningún dato

#### Scenario: El evento ya comenzó

- **WHEN** un actor autorizado intenta postergar un evento cuya fecha vigente ya llegó
- **THEN** Catalog responde `409` sin cambiar la fecha ni crear un aviso

#### Scenario: Dos postergaciones concurrentes

- **GIVEN** dos solicitudes basadas en la misma revisión vigente
- **WHEN** ambas intentan confirmar fechas distintas
- **THEN** sólo una incrementa la revisión y la otra recibe `409` por revisión obsoleta

#### Scenario: Reintento de la misma operación

- **WHEN** el mismo actor reintenta una postergación ya confirmada con la misma clave de idempotencia y el mismo cuerpo
- **THEN** recibe el resultado original sin crear otra revisión ni otro aviso

### Requirement: Mantener fecha vigente y configuración publicada consistentes

Catalog SHALL conservar la configuración original de lugar, sectores, asientos, capacidad, participantes y precios, y SHALL ofrecer la fecha vigente y la revisión de programación a los consumidores del evento tras una postergación. La fecha de publicación original SHALL permanecer disponible como dato histórico. Trazabilidad: PDF-F11 y PDF-NF-CONSISTENCY.

#### Scenario: Lecturas después de postergar

- **WHEN** Catalog confirma una postergación
- **THEN** gestión y contratos internos de compra y recomendación informan la fecha vigente y conservan los mismos identificadores, lugar y precios

#### Scenario: Programación de un evento cancelado para consumidores internos

- **WHEN** Purchase consulta internamente la programación de un evento cancelado con entradas vendidas
- **THEN** Catalog informa su última fecha vigente, revisión y estado sin publicar el detalle del evento a clientes anónimos

#### Scenario: La regla de devolución sigue vigente

- **WHEN** un cliente solicita devolver una entrada después de una postergación
- **THEN** Purchase aplica la regla existente de 72 horas antes de la fecha vigente obtenida de Catalog, sin excepción nueva por reprogramación

## MODIFIED Requirements

### Requirement: Inmutabilidad fuera de borrador

El sistema MUST rechazar modificaciones de datos o configuración cuando el evento ya no se encuentre en estado `DRAFT`, excepto la postergación de `startsAt` de un evento `PUBLISHED` mediante el comando específico de programación. La configuración de venta publicada MUST permanecer inmutable.

#### Scenario: Intentar editar un evento publicado

- **GIVEN** un evento en estado `PUBLISHED`
- **WHEN** un organizador o administrador intenta modificar sus datos o configuración por la operación general de edición
- **THEN** el sistema rechaza el cambio y conserva el snapshot publicado

#### Scenario: Intentar editar un evento suspendido

- **GIVEN** un evento en estado `SUSPENDED`
- **WHEN** un organizador o administrador intenta modificar sus datos o configuración
- **THEN** el sistema rechaza la operación y conserva el snapshot

#### Scenario: Postergar por el comando específico

- **GIVEN** un evento `PUBLISHED` con configuración de venta publicada
- **WHEN** su dueño o `ADMIN` usa el comando de programación con una fecha válida
- **THEN** sólo cambia la fecha vigente, mientras el snapshot original de venta se conserva
