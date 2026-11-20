# Spec Delta

## MODIFIED Requirements

### Requirement: Delegar la autorización de dominio a los servicios

El Gateway SHALL NOT decidir autorización de dominio. Los servicios SHALL seguir validando el rol y el ownership de cada recurso, incluso cuando el Gateway ya haya exigido un rol para la ruta. Para `/api/customers/**` el Gateway SHALL exigir un JWT con rol `CLIENT`, evaluar la ruta antes del comodín de catálogo y no enrutar los contratos internos de preferencias. Trazabilidad: PDF-NF-GATEWAY y PDF-NF-SECURITY.

#### Scenario: El servicio revalida el ownership

- **WHEN** un ORGANIZER autenticado solicita por el Gateway un recurso de otro organizador
- **THEN** el servicio de dominio responde `403` aunque el Gateway haya aceptado el rol de la ruta

#### Scenario: Cliente accede a sus preferencias

- **WHEN** un `CLIENT` autenticado solicita `GET /api/customers/me/preferences` o `GET /api/customers/me/recommendations` por el Gateway
- **THEN** Preferences Service recibe la misma ruta con la identidad derivada del `sub` y responde con el `X-Correlation-Id` propagado

#### Scenario: La ruta de preferencias precede al comodín de catálogo

- **WHEN** una solicitud comienza con `/api/customers/`
- **THEN** el Gateway la envía a Preferences Service y no a Catalog Service

#### Scenario: Rol distinto de cliente

- **WHEN** un `ORGANIZER`, `ADMIN` o `ACCESS_STAFF` solicita `/api/customers/me/preferences` o `/api/customers/me/recommendations`
- **THEN** el Gateway responde `403` con `application/problem+json` sin invocar a Preferences Service

#### Scenario: Acceso sin token

- **WHEN** un cliente solicita `/api/customers/**` sin JWT
- **THEN** el Gateway responde `401` con `application/problem+json` y el `X-Correlation-Id` resuelto, sin invocar a Preferences Service

#### Scenario: Contratos internos no publicados

- **WHEN** un cliente externo intenta acceder a `/internal/customers/{customerId}/recommendation-signals` o a `/internal/events/recommendation-candidates` a través del Gateway
- **THEN** el Gateway no los enruta y responde como ruta no encontrada
