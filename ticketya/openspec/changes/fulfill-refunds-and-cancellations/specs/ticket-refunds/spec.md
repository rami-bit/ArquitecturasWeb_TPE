# Spec Delta

## Purpose

Gestiona solicitudes y decisiones sobre devoluciones de tickets vendidos, con reintegros y liberación de lugares coordinados de forma reintentable.

## ADDED Requirements

### Requirement: Solicitar devolución dentro de la ventana
Purchase SHALL permitir al titular solicitar la devolución de un ticket pagado hasta 72 horas antes del comienzo del evento y SHALL persistir la solicitud sin duplicados.

#### Scenario: Solicitud válida
- **GIVEN** un ticket pagado propio y un evento que comienza en más de 72 horas
- **WHEN** el titular solicita la devolución
- **THEN** se crea una solicitud pendiente asociada al ticket

#### Scenario: Solicitud fuera de plazo
- **WHEN** el titular solicita la devolución a 72 horas o menos del comienzo
- **THEN** se rechaza sin alterar el ticket ni crear solicitud

#### Scenario: Reintento y titular ajeno
- **WHEN** el titular repite la solicitud del mismo ticket
- **THEN** se devuelve la misma solicitud sin duplicarla
- **WHEN** otro cliente solicita o consulta la devolución del ticket
- **THEN** se responde 404 sin revelar datos

### Requirement: Decidir solicitud de devolución
Purchase SHALL permitir solo a ADMIN aprobar o rechazar una solicitud pendiente, guardando la decisión de forma idempotente.

#### Scenario: Rechazo
- **WHEN** ADMIN rechaza una solicitud pendiente
- **THEN** la solicitud queda rechazada y el ticket conserva su validez y lugar vendido

#### Scenario: Aprobación
- **WHEN** ADMIN aprueba una solicitud pendiente
- **THEN** Purchase inicia el reintegro y expone el progreso hasta completar la revocación y liberación

#### Scenario: Decisión incompatible
- **WHEN** se intenta cambiar una decisión ya tomada por otra distinta
- **THEN** se responde 409 y se conserva la decisión original

### Requirement: Completar reintegro sin duplicar efectos
Purchase SHALL revocar el ticket, solicitar un reintegro idempotente por su importe original y liberar su unidad vendida solo tras confirmar el reintegro. Payment Mock SHALL aceptar varios reintegros parciales por cobro sin superar el importe cobrado. Los fallos parciales SHALL permanecer reintentables.

#### Scenario: Reintegro exitoso
- **WHEN** el proveedor confirma el reintegro de un ticket aprobado
- **THEN** el ticket queda revocado, su unidad vuelve a disponibilidad y la solicitud queda completada

#### Scenario: Fallo parcial y reintento
- **WHEN** falla temporalmente un comando interno después de aprobar
- **THEN** la solicitud conserva su progreso y un reintento no crea reintegros ni liberaciones duplicados

### Requirement: Cancelar ventas de un evento
Purchase SHALL procesar cada ticket pagado del evento cancelado con reintegro idempotente y SHALL exponer el progreso del lote para reintentos.

#### Scenario: Cancelación con ventas
- **WHEN** ADMIN cancela un evento con tickets pagados
- **THEN** se cierra la venta y cada ticket entra en procesamiento de revocación, reintegro y liberación

#### Scenario: Repetir cancelación
- **WHEN** se repite el procesamiento de un evento cancelado
- **THEN** se conserva una sola operación de reintegro por ticket y se completa lo pendiente
