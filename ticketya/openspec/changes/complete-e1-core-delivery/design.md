# Design

## Context

Ver `proposal.md` para la motivación. La colección crea un lugar y un evento nuevos mediante `runId`, compra por admisión general y recorre compra rechazada, reintentos, devoluciones y cancelación. La ampliación añade una compra aprobada de asiento numerado y comprueba el ticket en su respuesta. La evidencia Maven previa es de 221 pruebas sin fallos ni omisiones; Compose construyó 11 servicios saludables. La colección ampliada pasó con 42 requests y 72 aserciones sin fallos. No hubo cambios de código ni contrato que justifiquen repetir Maven o reconstruir imágenes.

## Goals / Non-Goals

**Goals:**

- Demostrar ambos modelos de sector con la misma colección y datos independientes por corrida.
- Documentar cómo repetir la verificación local con Java 21, Docker Desktop y PostgreSQL real mediante Testcontainers cuando cambie código o contrato.
- Preservar la cobertura existente como piso de regresión y relacionar la evidencia previa con el estado actual del repositorio.

**Non-Goals:**

- Cambiar contratos, estados, esquema o infraestructura del sistema.
- Añadir casos nuevos de vencimiento, concurrencia de compra completa, fallos downstream o reintento de QR. Esas obligaciones siguen abiertas en el change de Purchase; el alcance acotado de E1-05 no las acredita.

## Decisions

### Extender la colección existente

La compra numerada se agrega después de publicar el evento y consultar disponibilidad, usando el ID de asiento de Inventory y una `Idempotency-Key` nueva. Se comprueba la orden confirmada y el ticket emitido en la respuesta de esa misma compra. Se conserva el resto de la colección para detectar regresiones. La alternativa de crear otra colección duplicaría la preparación del evento y dificultaría comparar resultados.

### Reutilizar evidencia y comprobar el stack existente

La evidencia Maven y de build Compose ya obtenida es aplicable mientras no cambien código, contratos ni configuración relacionada. Se comprueban los XML Surefire de clases fuente actuales: 221 pruebas con `failures = errors = skipped = 0`; se comprueba la salud actual de los 11 servicios del stack existente y se corre la colección completa por Gateway. El README conserva el comando Maven Java 21 con socket Docker y `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` para futuras repeticiones. La alternativa de reconstruir y repetir todo sin cambios relevantes consume tiempo sin añadir señal de regresión.

### Separar evidencia de cobertura pendiente

El informe de verificación distingue las corridas anteriores de la comprobación actual: comandos, versiones, cantidad de tests, salud de servicios, 42 requests y 72 aserciones. El change de Purchase mantiene sin marcar la tarea 3.3 y detalla los escenarios diferidos; TODO/NEXT conservan esa deuda visible. La alternativa de cerrar esa tarea por la colección E2E actual afirmaría cobertura que no existe.

## Risks / Trade-offs

- Un estado previo del stack podría afectar resultados → usar datos nuevos con `runId` en cada corrida y comprobar la salud antes de Postman.
- Los XML viejos en `target/` podrían inflar el conteo → comparar cada XML con una clase presente en `src/test` y verificar fecha posterior al último cambio de código pertinente.
- El alcance de pruebas nuevas no satisface todos los criterios originales de US-E1-05 → documentar explícitamente las pruebas diferidas y mantener abiertos sus ítems de seguimiento.

## Migration Plan

No hay migración de datos. Ejecutar la colección ampliada sobre el stack existente con un `runId` nuevo y actualizar la evidencia. Si aparece una regresión, corregirla en el servicio dueño y repetir el módulo y el flujo afectados antes de registrar el cierre.
