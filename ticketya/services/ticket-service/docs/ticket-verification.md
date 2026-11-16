# Verificación de Ticket Service

## Verificaciones completadas

- La verificación de Maven desde el agregador:
  `.\TP3\mvnw.cmd -f ticketya\pom.xml -pl services/ticket-service -am verify`
  finalizó con código de salida 0 y `BUILD SUCCESS` usando JDK 19. Pasaron las
  14 tests: siete unitarios y siete de contrato; ninguno falló ni se omitió.
- Los siete tests de contrato usaron Testcontainers con PostgreSQL 16.4 y
  verificaron las respuestas HTTP de emisión y error, la decodificación del QR
  y la persistencia exclusiva del hash, los reintentos idempotentes, el conflicto
  ante contenido distinto, la validación de solicitudes y la concurrencia.
- `docker compose -f ticketya\docker-compose.yml config --quiet` finalizó
  correctamente con un valor temporal de `TICKET_QR_SIGNING_SECRET` usado solo
  para la verificación. No se guardó ningún secreto en el repositorio.
- `docker compose -f ticketya\docker-compose.yml up --build -d --wait ticket-service`
  construyó la imagen de Ticket Service e inició `ticket-service` junto con
  `ticket-db`; ambos informaron un estado saludable. El servicio devolvió
  `{"status":"UP"}` en `/actuator/health`, y `/v3/api-docs` mostró la ruta interna
  de emisión de entradas.

## Integración posterior

La verificación registrada arriba corresponde al incremento original de Ticket
Service: inició intencionalmente solo `ticket-service` y `ticket-db`, no todo el
entorno. Purchase se integró posteriormente y solicita la emisión interna sólo
después de aprobar el pago y confirmar Inventory. La evidencia actual del flujo
integrado por Gateway, junto con la verificación del stack completo, está en
[`docs/verification.md`](../../../docs/verification.md).

La revocación de tickets se incorporó posteriormente con las devoluciones y
cancelaciones de Entrega 1. La validación de acceso y el uso único del QR
corresponden a US-F8 de Entrega 2 y no forman parte de esta verificación.
