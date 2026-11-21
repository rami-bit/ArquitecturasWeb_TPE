# Spec Delta

## ADDED Requirements

### Requirement: Conservar el IdP de prueba para identidades arbitrarias

El proyecto SHALL mantener `tools/local-test-idp/LocalTestIdp.java` y el arnés que obtiene tokens para UUID y roles generados durante una prueba. La corrida SHALL configurar su Gateway para confiar en ese emisor y no dependerá de las cuentas fijas de Auth Service. Trazabilidad: PDF-E2-TESTING y PDF-NF-SECURITY.

#### Scenario: Test con compradores nuevos
- **WHEN** una prueba de compra concurrente genera UUID nuevos y solicita tokens al IdP de prueba
- **THEN** el Gateway de esa corrida acepta esos JWT y ejecuta el flujo sin requerir altas en Auth Service

#### Scenario: Stack de pruebas aislado
- **WHEN** el arnés levanta su proyecto Compose con el IdP de prueba
- **THEN** configura `JWT_ISSUER_URI` y `JWT_JWK_SET_URI` de ese proyecto para el IdP de prueba y conserva su modo de ejecución y limpieza vigente

#### Scenario: Regresión del login local
- **WHEN** se verifica el stack normal configurado con Auth Service
- **THEN** el login y un endpoint protegido funcionan con tokens locales, mientras un token del IdP de prueba recibe `401` en ese stack
