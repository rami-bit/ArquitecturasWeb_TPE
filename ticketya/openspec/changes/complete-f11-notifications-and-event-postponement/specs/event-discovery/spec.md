# Spec Delta

## ADDED Requirements

### Requirement: Usar la fecha vigente en descubrimiento y recomendaciones

Las búsquedas públicas y los candidatos internos de recomendación SHALL filtrar, ordenar y mostrar la fecha vigente después de una postergación. SHALL conservar filtros, paginación y desempates existentes. Trazabilidad: PDF-F9, PDF-F10 y PDF-F11.

#### Scenario: Búsqueda por rango

- **WHEN** un evento se posterga fuera del rango de fechas consultado
- **THEN** deja de aparecer en ese rango y aparece en el rango de la fecha nueva

#### Scenario: Orden y recomendaciones

- **WHEN** se posterga un evento publicado
- **THEN** búsqueda y candidatos de recomendaciones lo ordenan y consideran futuro según la fecha nueva

## MODIFIED Requirements

### Requirement: Consultar detalle público

El sistema SHALL permitir consultar sin autenticación el detalle de un evento `PUBLISHED`, incluyendo participantes, fecha vigente, lugar, sectores y precios. La configuración publicada SHALL provenir del snapshot original y la postergación SHALL cambiar sólo la fecha presentada.

#### Scenario: Consultar evento publicado

- **GIVEN** existe un evento en estado `PUBLISHED`
- **WHEN** un cliente solicita su detalle público
- **THEN** el sistema devuelve la configuración inmutable de su snapshot y la fecha vigente, sin afirmar disponibilidad de entradas

#### Scenario: Consultar evento no visible

- **GIVEN** el evento está en `DRAFT` o `SUSPENDED`
- **WHEN** un cliente solicita su detalle público
- **THEN** el sistema responde como recurso no encontrado sin revelar sus datos

#### Scenario: Consultar identificador inexistente

- **WHEN** un cliente solicita un identificador de evento inexistente
- **THEN** el sistema responde como recurso no encontrado
