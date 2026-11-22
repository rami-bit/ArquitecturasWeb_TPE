# Spec Delta

## ADDED Requirements

### Requirement: Postergar la fecha y hora de un evento publicado

Catalog SHALL exponer `PATCH /api/management/events/{id}/schedule` para el `ORGANIZER` dueño o `ADMIN`, con `startsAt`, `expectedScheduleRevision` e `Idempotency-Key`. Sólo SHALL cambiar la fecha de un evento `PUBLISHED` no iniciado. La nueva fecha SHALL ser posterior a la vigente y al instante de gestión más 72 horas. Trazabilidad: PDF-F11.

#### Scenario: Postergación válida

- **WHEN** el dueño o `ADMIN` propone una fecha que cumple ambos límites
- **THEN** Catalog confirma la nueva fecha, incrementa la revisión y devuelve fechas anterior y nueva

#### Scenario: Fecha inválida

- **WHEN** la fecha nueva adelanta o iguala la vigente, o no supera estrictamente el instante de gestión más 72 horas
- **THEN** Catalog responde `409` sin modificar el evento

#### Scenario: Estado o actor inválido

- **WHEN** el evento no está `PUBLISHED`, ya comenzó o lo solicita un organizador ajeno
- **THEN** Catalog responde `409` o `403`, según corresponda, sin emitir aviso

#### Scenario: Revisión obsoleta o reintento

- **WHEN** dos solicitudes compiten sobre la misma revisión, o el actor repite la misma clave de idempotencia
- **THEN** sólo una modificación crea revisión y aviso; el replay idéntico devuelve el resultado original y una clave reutilizada con otro cuerpo recibe `409`

### Requirement: Preservar la configuración y comunicar el cambio confirmado

Catalog SHALL conservar el snapshot de publicación y SHALL enviar `EVENT_POSTPONED` con un identificador estable y ambas fechas después del commit. La regla de devolución SHALL seguir usando la fecha vigente de Catalog sin excepción nueva. Trazabilidad: PDF-F7 y PDF-F11.

#### Scenario: Cambio confirmado

- **WHEN** se confirma una postergación
- **THEN** lugar, participantes, sectores, asientos, capacidad, precios y QR conservan sus valores, y Notification Service recibe el cambio con fechas anterior y nueva

#### Scenario: Cambio rechazado

- **WHEN** Catalog rechaza la postergación
- **THEN** no envía el hecho ni modifica la fecha vigente

#### Scenario: Devolución posterior

- **WHEN** un comprador solicita una devolución después de la postergación
- **THEN** Purchase aplica la regla existente de 72 horas respecto de la fecha vigente de Catalog

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

- **WHEN** el dueño o `ADMIN` usa el comando de programación sobre un evento `PUBLISHED` con fecha válida
- **THEN** sólo cambia la fecha vigente y se conserva el snapshot original
