# Proposal

## Why

Purchase ya tiene timeouts y reintentos idempotentes, pero un servicio externo caído sigue ocupando hilos y generando llamadas inútiles: cada compra vuelve a intentar cobrar contra Payment Mock aunque lleve varias fallas consecutivas. Además, el despacho de notificaciones no tiene una degradación explícita: si el adaptador de notificaciones falla, no está definido qué hace la operación de negocio. La Entrega 2 exige resiliencia ante servicios externos y un fallback verificable.

## What Changes

- Circuito de three states (cerrado, abierto, semiabierto) reutilizable en Purchase Service, sin dependencias nuevas, con umbral de fallas y duración de apertura configurables.
- Aplicación del circuito a las llamadas de `PaymentClient`: fallas de transporte y `5xx` cuentan; respuestas de negocio `4xx` no abren el circuito. Con el circuito abierto, `charge` responde `503` sin llamar a Payment y Purchase libera la reserva.
- Despacho de notificaciones con el mismo circuito y fallback local: el evento siempre se registra en el log local con su `correlationId`; si el adaptador remoto está caído o no está configurado, la compra no se ve afectada.
- Configuración por variables de entorno, OpenAPI sin cambios (ninguna ruta nueva) y documentación de arquitectura/verificación.

## Capabilities

### New Capabilities

- `resilience-circuit-breaker`: apertura y cierre automático del circuito ante fallas de dependencias externas, con degradación controlada y sin afectar la consistencia de la saga de compra.

### Modified Capabilities

Ninguna: no cambia el comportamiento observable de las operaciones existentes salvo el fallo rápido documentado, y no se agregan rutas públicas ni internas.

## Impact

- `services/purchase-service`: nuevo componente de resiliencia, integración en `PaymentClient` y en el despacho de notificaciones, y pruebas unitarias y con stubs HTTP.
- Variables nuevas: `PURCHASE_CIRCUIT_FAILURE_THRESHOLD`, `PURCHASE_CIRCUIT_OPEN_SECONDS` y `NOTIFICATION_SERVICE_URL` (vacía = sólo fallback local).
- Sin cambios en OpenAPI ni en Postman: el flujo de compra existente sigue siendo la evidencia de no regresión.

## Simplificaciones declaradas

- Sin Resilience4j: el patrón se implementa con un componente pequeño y determinista, suficiente para el alcance didáctico y sin agregar dependencias al reactor.
- Sin persistencia del estado del circuito: cada instancia reinicia en cerrado; no hay coordinación entre réplicas.
- F11 sigue sin implementarse: hoy sólo se publican eventos de dominio al despacho con fallback local; no hay entrega por email ni push.
