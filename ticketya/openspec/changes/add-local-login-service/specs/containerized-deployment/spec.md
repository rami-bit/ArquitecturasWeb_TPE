# Spec Delta

## ADDED Requirements

### Requirement: Levantar el proveedor de login local en Compose

El Compose central SHALL incluir Auth Service con healthcheck, cuentas fijas y material de firma persistente. En el arranque normal SHALL configurar al Gateway para validar sus JWT y SHALL publicar el login sólo mediante el Gateway ligado al loopback del host, sin exponer el puerto propio de Auth Service. Trazabilidad: PDF-NF-DEPLOYMENT y PDF-NF-SECURITY.

#### Scenario: Arranque normal
- **WHEN** se levanta el Compose raíz con la configuración local documentada
- **THEN** Auth Service y Gateway quedan `healthy` y el login por Gateway emite un token utilizable en rutas protegidas

#### Scenario: Reinicio sin pérdida de clave
- **GIVEN** un JWT local no vencido
- **WHEN** se reinicia Auth Service conservando el volumen de firma
- **THEN** el Gateway sigue validando ese JWT

#### Scenario: Puerto interno
- **WHEN** se inspecciona el stack normal
- **THEN** Auth Service no publica un puerto HTTP propio al host, el Gateway consulta su clave pública por la red interna y el puerto del Gateway se liga sólo al loopback del host

#### Scenario: Emisor alternativo de pruebas
- **WHEN** una corrida aislada configura `JWT_ISSUER_URI` y `JWT_JWK_SET_URI` para el IdP de prueba
- **THEN** el Gateway valida ese emisor sin exigir tokens del Auth Service local
