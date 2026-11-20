# Spec Delta

## Purpose

Define el patrón de circuit breaker y sus fallbacks para que una falla repetida de Payment o del adaptador de notificaciones no degrade la saga de compra ni bloquee al cliente.

## ADDED Requirements

### Requirement: Abrir el circuito ante fallas repetidas de Payment

Purchase SHALL contar como falla de Payment sólo los errores de transporte y las respuestas `5xx`, y SHALL abrir el circuito al alcanzar el umbral configurado de fallas consecutivas. Las respuestas `4xx` de negocio SHALL NOT abrir el circuito. Trazabilidad: PDF-NF-RESILIENCE.

#### Scenario: Racha de errores del servidor

- **GIVEN** un umbral de 5 fallas consecutivas
- **WHEN** Payment responde `503` en cinco cobros seguidos
- **THEN** el circuito pasa a abierto y los siguientes cobros se rechazan sin llamar a Payment

#### Scenario: Rechazo de pago no cuenta como falla

- **GIVEN** un circuito cerrado con fallas previas acumuladas
- **WHEN** Payment responde `402 REJECTED`
- **THEN** el circuito sigue cerrado y el contador de fallas se reinicia

### Requirement: Fallo rápido con el circuito abierto

Con el circuito abierto, Purchase SHALL responder `503` con `application/problem+json` antes de invocar a Payment, SHALL liberar la reserva correspondiente y SHALL NOT marcar la orden como pagada. Trazabilidad: PDF-NF-RESILIENCE.

#### Scenario: Compra con el circuito abierto

- **GIVEN** el circuito de Payment abierto
- **WHEN** un cliente intenta comprar
- **THEN** Purchase responde `503`, no envía el cobro y libera la reserva creada

### Requirement: Recuperación automática en semiabierto

Vencida la duración de apertura, el circuito SHALL permitir una sola llamada de prueba. Un éxito SHALL cerrarlo y una falla SHALL reabrirlo. Trazabilidad: PDF-NF-RESILIENCE.

#### Scenario: La dependencia se recupera

- **GIVEN** un circuito abierto cuya duración ya venció
- **WHEN** la llamada de prueba a Payment responde `200`
- **THEN** el circuito pasa a cerrado y se reinician los contadores

#### Scenario: La dependencia sigue caída

- **GIVEN** un circuito en semiabierto
- **WHEN** la llamada de prueba falla
- **THEN** el circuito vuelve a abierto por la duración configurada

### Requirement: Fallback local de notificaciones

El despacho de notificaciones SHALL registrar siempre el evento en el log local con `correlationId`, `orderId`, `customerId` y tipo, SHALL proteger el envío remoto opcional con el circuito y SHALL NOT lanzar excepciones ni alterar la respuesta de la compra cuando el adaptador falle o no esté configurado. Trazabilidad: PDF-NF-RESILIENCE.

#### Scenario: Adaptador de notificaciones caído

- **GIVEN** un adaptador remoto configurado que responde `503`
- **WHEN** Purchase confirma una orden
- **THEN** la compra termina `201`, el evento queda en el log local y el circuito de notificaciones se abre tras el umbral

#### Scenario: Sin adaptador configurado

- **GIVEN** `NOTIFICATION_SERVICE_URL` vacía
- **WHEN** Purchase compensa una orden
- **THEN** no se intenta envío remoto y el evento `ORDER_CANCELLED` queda en el log local
