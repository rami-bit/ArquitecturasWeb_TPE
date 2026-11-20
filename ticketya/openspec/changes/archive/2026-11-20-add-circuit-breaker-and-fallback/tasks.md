# Tasks

## 1. Especificación

- [x] 1.1 Crear el cambio `add-circuit-breaker-and-fallback` con propuesta, diseño y spec delta de `resilience-circuit-breaker`
- [x] 1.2 Validar el cambio con `openspec validate add-circuit-breaker-and-fallback --strict`

## 2. Componente de resiliencia

- [x] 2.1 Implementar `CircuitBreaker` con estados CLOSED/OPEN/HALF_OPEN, umbral, duración, una llamada de prueba y contadores
- [x] 2.2 Escribir `CircuitBreakerTest`: acumulación de fallas, rechazo en abierto, semiabierto y cierre tras éxito

## 3. Payment con circuito y fallback

- [x] 3.1 Integrar el circuito en `PaymentClient` sin cambiar las firmas públicas de `charge`, `refund` y `getByOrder`
- [x] 3.2 No contar respuestas `4xx` como fallas y registrar cada transición de estado
- [x] 3.3 Configurar el bean con `PURCHASE_CIRCUIT_FAILURE_THRESHOLD` y `PURCHASE_CIRCUIT_OPEN_SECONDS`
- [x] 3.4 Probar con un stub HTTP que, tras varias fallas `5xx`, no se hacen más llamadas y se responde `503` sin cobrar

## 4. Notificaciones con fallback local

- [x] 4.1 Implementar `NotificationDispatcher` con log local obligatorio y envío remoto opcional protegido por el circuito
- [x] 4.2 Publicar `ORDER_CONFIRMED` y `ORDER_CANCELLED` desde PurchaseService sin lanzar excepciones al flujo
- [x] 4.3 Probar que una falla del adaptador no altera la respuesta de la compra y deja el evento en el log

## 5. Configuración y cierre

- [x] 5.1 Declarar las variables nuevas en `.env`, `docker-compose.yml` y `README.md`
- [x] 5.2 Actualizar `docs/architecture.md` y `docs/verification.md` con el patrón y la evidencia
- [x] 5.3 Ejecutar `mvn verify`, Compose aislado y Newman por el Gateway sin regresiones
- [x] 5.4 Sincronizar y archivar el cambio OpenSpec y ejecutar `openspec validate --all --strict` y `git diff --check`
