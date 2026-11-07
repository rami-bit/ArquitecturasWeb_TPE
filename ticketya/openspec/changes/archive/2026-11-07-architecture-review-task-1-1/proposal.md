# Proposal

## Why

La tarea 1.1 del cambio `establish-ticketya-architecture` pide revisar los diagramas de contexto, contenedores y despliegue, verificar que rendericen y que distingan Entrega 1 de Entrega 2. El blueprint actual deja ambiguo si "contenedores" significa C4 o contenedores Docker, mezcla estado actual con objetivo en un solo diagrama de despliegue, y no tiene criterios claros de aceptación para dar la tarea por cerrada. Esta refinación cierra 1.1 antes de avanzar a 1.2.

## What Changes

- Definir que "contenedor C4" es una unidad ejecutable/desplegable y que cada microservicio Spring Boot es uno, con mapeo 1:1 a imagen Docker en Entrega 2.
- Separar los diagramas en cinco vistas con un solo alcance cada una (D1-D5): contexto transversal, C4 contenedores E1, C4 contenedores E2, despliegue E1, despliegue E2.
- Agregar una tabla de estado actual fuera de los diagramas para no mezclar "lo que existe hoy" con "lo que el blueprint planifica".
- Corregir `design.md`: `payment_db` como "a decidir", pasarela externa fuera de alcance, `Delivery2` reemplazado por capacidades E2 a refinar, relación Inventory-Catalog como snapshot, nombres unificados entre servicios.
- Reescribir §20 Despliegue para distinguir el mínimo del PDF (E1 local con bases en Docker; E2 todo en Docker) de la decisión del proyecto (Docker desde E1 como adelanto voluntario).
- Definir criterios de aceptación de 1.1 (D1-D5, render con `mmdc`, tabla de estado, límites con 1.5).
- Mantener sin commit los cambios pendientes de `implement-event-discovery` hasta que el usuario lo indique.

## Capabilities

Este cambio no introduce ni modifica capacidades de comportamiento del sistema: solo refina diagramas de arquitectura y documentación. Por ello no se crean specs y `skip_specs: true` está activado en `.openspec.yaml`.

## Impact

- `ticketya/openspec/changes/establish-ticketya-architecture/design.md`: secciones 2, 3, 4 y 20 (diagramas D1-D5 y texto de despliegue).
- `ticketya/openspec/changes/establish-ticketya-architecture/tasks.md`: criterios de aceptación de la tarea 1.1.
- No se toca código fuente, Dockerfiles, Compose ni POM.
