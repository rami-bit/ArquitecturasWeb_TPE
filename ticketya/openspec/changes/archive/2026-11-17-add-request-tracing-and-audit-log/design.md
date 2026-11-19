# Design

## Context

Todas las solicitudes ya llevan `X-Correlation-Id`: el Gateway lo genera o lo conserva, y cada servicio lo propaga a sus dependencias y lo coloca en MDC. Lo que falta es registrar cada solicitud y contar con un almacenamiento común y consultable de las operaciones críticas.

## Goals / Non-Goals

**Goals:** una línea de acceso por solicitud en cada componente; una bitácora consultable por operación; que la auditoría nunca bloquee ni haga fallar el negocio.

**Non-Goals:** reemplazar los logs por un stack de observabilidad; auditar lecturas; garantizar entrega exactamente una vez.

## Decisions

### 1. Registro de acceso con filtros propios

`RequestLoggingFilter` (servlet, después de `CorrelationIdFilter`) y `AccessLogWebFilter` (WebFlux, en el Gateway antes de la seguridad) escriben en el logger `ticketya.http`. El Gateway obtiene usuario y rol con `AuthenticatedIdentityCaptureFilter`, ubicado en la cadena de seguridad después de la autenticación y antes de la autorización, para que también los `403` queden con usuario. Los healthchecks van en DEBUG.

### 2. Audit Service con PostgreSQL propio

Se elige un microservicio dedicado y no un stack ELK/Loki: es consultable por correlation id, operación y recurso, se restringe a `ADMIN` y no agrega infraestructura pesada al Compose. La tabla `audit_events` es append-only, con `details JSONB`. La clave primaria es el `eventId` del productor, lo que vuelve idempotente el reintento.

### 3. Publicación asíncrona fire-and-forget

`AuditPublisher` siempre escribe el evento en el log local (`ticketya.audit`). Si `AUDIT_SERVICE_URL` está configurada, lo encola en un pool acotado (cola de 1000) y lo envía con timeouts de 1 s y 2 s y hasta 3 intentos con espera lineal. No reintenta los `4xx` salvo `429`. Si la cola se llena o los intentos se agotan, registra una advertencia con `eventId` y `correlationId`. Así la bitácora tiene consistencia eventual y no es transaccional con el negocio. Se descartó un broker (Kafka/RabbitMQ) porque agrega un componente de infraestructura que el resto del sistema no usa todavía.

### 4. Dónde se audita

En la capa de controlador de cada productor, alrededor de la operación ya existente, sin modificar la saga: el resultado se toma de la respuesta (estado de la orden o de la devolución) o de la excepción de dominio (`REJECTED`, `COMPENSATED`, `FAILED`).

## Risks / Trade-offs

- Si un productor se detiene con eventos en cola, esos eventos se pierden en Audit y quedan sólo en su log local. Es aceptable para el alcance didáctico; un outbox transaccional sería la evolución.
- Duplicar `AuditPublisher` en tres servicios preserva la independencia de los proyectos Maven a costa de código repetido. Cada copia tiene su propia prueba.
