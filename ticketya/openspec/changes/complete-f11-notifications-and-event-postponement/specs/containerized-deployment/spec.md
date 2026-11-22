# Spec Delta

## ADDED Requirements

### Requirement: Capturar email localmente por SMTP

El Compose central SHALL incluir Mailpit como receptor SMTP de Notification Service. SMTP SHALL quedar en la red interna y la interfaz de inspección SHALL publicarse sólo en `127.0.0.1:8025`. Trazabilidad: PDF-F11 y PDF-NF-DEPLOYMENT.

#### Scenario: Levantar correo verificable

- **WHEN** se levanta el Compose central
- **THEN** Notification Service puede entregar `EMAIL` a Mailpit y los mensajes se inspeccionan localmente sin exponer SMTP ni la API del microservicio al host
