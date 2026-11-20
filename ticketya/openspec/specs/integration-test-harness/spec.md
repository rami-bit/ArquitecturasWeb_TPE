# integration-test-harness Specification

## Purpose

Define la verificación automatizada de punta a punta del sistema completo, desde la salud de los contenedores hasta los flujos del Gateway, sin destruir datos ajenos.

## Requirements

### Requirement: Verificar el sistema completo de forma automatizada

El proyecto SHALL incluir un arnés que compruebe o levante el stack, espere a que todos los servicios informen `healthy`, obtenga tokens del IdP local y ejecute la colección Postman por el Gateway. El arnés SHALL terminar con código distinto de cero ante cualquier fallo de salud o de aserción. Trazabilidad: PDF-E2-TESTING.

#### Scenario: Stack saludable

- **GIVEN** los contenedores en estado `healthy`
- **WHEN** se ejecuta el arnés en modo reutilización
- **THEN** ejecuta la colección completa y finaliza con código cero si todas las aserciones pasan

#### Scenario: Servicio no saludable

- **GIVEN** un servicio del stack en estado `unhealthy`
- **WHEN** se ejecuta el arnés
- **THEN** no ejecuta la colección, informa el servicio afectado y finaliza con código distinto de cero

### Requirement: No destruir datos ajenos

El arnés SHALL usar un nombre de proyecto Compose propio cuando levante el sistema y SHALL NO ejecutar `docker compose down -v` sobre datos existentes. En modo reutilización SHALL dejar el stack exactamente como lo encontró. Trazabilidad: PDF-NF-DEPLOYMENT.

#### Scenario: Reutilización de un stack

- **GIVEN** un stack ya levantado en el puerto del Gateway
- **WHEN** se ejecuta el arnés en modo reutilización
- **THEN** no detiene ni elimina contenedores ni volúmenes

#### Scenario: Levantamiento aislado

- **GIVEN** ningún stack en el puerto del Gateway
- **WHEN** el arnés levanta el sistema
- **THEN** usa su propio nombre de proyecto y al terminar sólo detiene los contenedores que creó, conservando volúmenes

### Requirement: Emitir un resumen verificable

El arnés SHALL imprimir un resumen con cantidad de servicios, requests ejecutados, aserciones fallidas y resultado final, de modo que la evidencia pueda copiarse al informe de verificación. Trazabilidad: PDF-NF-TRACE.

#### Scenario: Resumen de una corrida

- **WHEN** termina una corrida del arnés
- **THEN** la salida incluye salud de los servicios, total de requests, fallos y codificación de salida
