# Spec Delta

## Purpose

Define la prueba de carga del flujo de compra exigida por la Entrega 2 y el informe que la documenta, con resultados medidos y reproducibles desde un stack local.

## ADDED Requirements

### Requirement: Ejecutar un perfil de carga reproducible

El sistema SHALL contar con un script de carga que Ejecute compras concurrentes contra el API Gateway con un perfil por etapas y datos propios por solicitud. El perfil SHALL ser configurable y SHALL usar claves de idempotencia e identidades distintas para no colisionar con la colección funcional. Trazabilidad: PDF-E2-TESTING.

#### Scenario: Perfil por etapas

- **WHEN** se ejecuta el script con el perfil por omisión
- **THEN** se recorren al menos tres niveles de concurrencia y se registran latencias, aciertos y errores por etapa

#### Scenario: Datos aislados

- **GIVEN** una corrida previa de la colección Postman
- **WHEN** se ejecuta la prueba de carga
- **THEN** cada solicitud usa un identificador de cliente y una clave de idempotencia nuevos, sin reutilizar datos de la corrida anterior

### Requirement: Informar resultados medidos

El equipo SHALL publicar un informe con fecha, entorno, perfil, latencias p50/p95/p99, throughput, tasa de errores, interpretación y comando de reproducción. El informe SHALL basarse en una ejecución real contra el stack de verificación. Trazabilidad: PDF-E2-TESTING.

#### Scenario: Informe completo

- **WHEN** se consulta `docs/load-test-report.md`
- **THEN** contiene el perfil ejecutado, las métricas por etapa y la interpretación de los errores observados

#### Scenario: Reproducción

- **WHEN** otro integrante sigue el comando documentado sobre un stack saludable
- **THEN** obtiene el mismo tipo de métricas sin instalar dependencias adicionales

### Requirement: No sustituir la verificación funcional

La prueba de carga SHALL ser complementaria a la colección Postman: un resultado aceptable de carga SHALL NOT dar por cumplidos los escenarios funcionales. Trazabilidad: PDF-E2-TESTING.

#### Scenario: Carga correcta con E2E pendiente

- **GIVEN** una prueba de carga sin errores
- **WHEN** no se ejecutó la colección Postman
- **THEN** el arnés de integración sigue reportando la verificación como incompleta
