# Spec Delta

## ADDED Requirements

### Requirement: Enrutar las operaciones de la fila virtual por el Gateway

El Gateway SHALL exponer las operaciones de la fila virtual bajo `/api/queue/**`, exigiendo autenticación Bearer y propagando la identidad derivada de los claims validados. La ruta de la fila SHALL tener precedencia sobre el comodín de catálogo. El Gateway SHALL NOT enrutar los comandos internos de la fila.

#### Scenario: Enrutar la entrada a la fila

- **WHEN** un cliente autenticado envía `POST /api/queue/events/{eventId}/entries` a través del Gateway
- **THEN** el servicio de la fila recibe la misma ruta con la identidad derivada del `sub` y los roles validados

#### Scenario: Enrutar la consulta del turno

- **WHEN** un cliente autenticado consulta `GET /api/queue/events/{eventId}/entries/me` a través del Gateway
- **THEN** el servicio de la fila recibe la misma ruta y devuelve la respuesta con el `X-Correlation-Id` propagado

#### Scenario: La fila precede al comodín de catálogo

- **WHEN** un cliente autenticado envía una solicitud a `/api/queue/**`
- **THEN** la solicitud la atiende el servicio de la fila y no el servicio de catálogo

#### Scenario: Acceso sin token

- **WHEN** un cliente envía una solicitud a `/api/queue/**` sin JWT
- **THEN** el Gateway responde `401` con `application/problem+json` y el `X-Correlation-Id`, sin llamar al servicio de la fila

#### Scenario: Comandos internos no publicados

- **WHEN** un cliente externo intenta acceder a un comando interno de la fila a través del Gateway
- **THEN** el Gateway no lo enruta y responde como ruta no encontrada
