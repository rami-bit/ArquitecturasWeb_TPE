# Design

## Context

Ver `proposal.md` para la motivación. La colección crea datos nuevos mediante `runId`, compra por admisión general y asiento numerado, y recorre rechazo, reintentos, devoluciones y cancelación. La evidencia Maven previa es de 221 pruebas sin fallos ni omisiones; Compose construyó 11 servicios saludables; la colección ampliada pasó con 42 requests y 72 aserciones sin fallos. Ese avance cubrió el camino feliz, pero no todas las pruebas que el PDF exige expresamente para Entrega 1. La comparación se basa en las páginas 2 a 5 de `TPE-ArqWeb-TicketYa-2026.pdf`.

## Goals / Non-Goals

**Goals:**

- Demostrar ambos modelos de sector con la misma colección y datos independientes por corrida.
- Documentar cómo repetir la verificación local con Java 21, Docker Desktop y PostgreSQL real mediante Testcontainers cuando cambie código o contrato.
- Preservar la cobertura existente como piso de regresión.
- Verificar el comportamiento de liberación al vencer una reserva y agregar pruebas unitarias de compra, devoluciones y búsqueda.
- Probar muchas compras simultáneas del mismo asiento o último cupo y comprobar que sólo una termina exitosa.

**Non-Goals:**

- Cambiar contratos, estados, esquema o infraestructura del sistema salvo que una prueba requerida revele un defecto.
- Exigir para E1 una prueba E2E específica del vencimiento durante el pago, una matriz de fallos parciales entre servicios o una prueba específica de reintento de emisión QR. Estos casos siguen abiertos en el change de Purchase como cobertura adicional; el PDF no los enumera como pruebas obligatorias de Entrega 1.

## Decisions

### Extender la colección existente

La compra numerada se agrega después de publicar el evento y consultar disponibilidad, usando el ID de asiento de Inventory y una `Idempotency-Key` nueva. Se comprueba la orden confirmada y el ticket emitido en la respuesta de esa misma compra. Se conserva el resto de la colección para detectar regresiones. La alternativa de crear otra colección duplicaría la preparación del evento y dificultaría comparar resultados.

### Completar la evidencia exigida por el PDF

La liberación al vencer ya tiene pruebas de contrato contra PostgreSQL en Inventory (`ReservationLifecycleContractTest` y `HoldTtlContractTest`); se revisan sus aserciones y el comportamiento visible antes de crear otra prueba. El PDF pide el comportamiento, no una prueba E2E de pago vencido. Las pruebas unitarias nuevas se enfocan en reglas de negocio aislables de compra, devoluciones y búsqueda, sin replicar las pruebas HTTP existentes.

La prueba de simultaneidad dispara muchas solicitudes de compra para el mismo asiento o último cupo con compradores y claves idempotentes distintos. Debe observar una sola compra exitosa y verificar que el lugar queda vendido una sola vez. Se reutiliza PostgreSQL/Inventory como autoridad de disponibilidad; la prueba multi-instancia de Inventory existente complementa la evidencia del mecanismo entre instancias. No se convierte este caso en una prueba de carga: el PDF reserva esa exigencia para Entrega 2.

Cuando se agreguen estas pruebas o se corrija código, se ejecuta `mvn verify` del módulo afectado y el reactor completo con Java 21/Testcontainers. Se comprueban los XML Surefire de clases fuente actuales y se repite el recorrido por Gateway si cambia un contrato o el comportamiento de compra. Los resultados anteriores de 221 pruebas y 42 requests/72 aserciones son una referencia, no sustituyen la verificación posterior a cambios pertinentes.

### Separar evidencia de cobertura pendiente

El informe de verificación distingue las corridas anteriores de las futuras. `complete-e1-core-delivery` sólo se considera completo para el PDF cuando se aportan las pruebas unitarias y la prueba de simultaneidad expresas. La tarea 3.3 de Purchase puede permanecer abierta por sus escenarios adicionales sin impedir el cierre del alcance estricto de Entrega 1; TODO/NEXT deben dejar clara esa diferencia.

## Risks / Trade-offs

- Un estado previo del stack podría afectar resultados → usar datos nuevos con `runId` en cada corrida y comprobar la salud antes de Postman.
- Los XML viejos en `target/` podrían inflar el conteo → comparar cada XML con una clase presente en `src/test` y verificar fecha posterior al último cambio de código pertinente.
- Una prueba sólo de reserva podría no demostrar «muchas compras simultáneas» → ejecutar el flujo público de compra y exigir exactamente un resultado exitoso.
- Los casos adicionales de OpenSpec podrían confundirse con exigencias del PDF → conservarlos como deuda separada y no marcarlos completados por la evidencia E1.

## Migration Plan

No hay migración de datos prevista. Completar las pruebas requeridas, repetir las verificaciones afectadas y actualizar la evidencia. Si aparece una regresión, corregirla en el servicio dueño y repetir el módulo y el flujo afectados antes de registrar el cierre.
