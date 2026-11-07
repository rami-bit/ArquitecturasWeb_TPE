# Tasks

## 1. Diagramas D1-D5

- [x] 1.1 Reemplazar §2 Contexto por D1 (actores, canales, sistemas externos; sin distinción E1/E2; nota de que canales externos no son contenedores a construir) y verificar que renderiza con `mmdc`.
- [x] 1.2 Reemplazar §3 Arquitectura lógica por D2 (C4 contenedores E1: cada microservicio Spring Boot como unidad independiente, base por ownership, mapeo 1:1 a imagen Docker en E2; sin `Delivery2` como nodo único, sin flecha "versión futura" a pasarela externa, Inventory-Catalog como snapshot) y verificar que renderiza con `mmdc`.
- [x] 1.3 Reemplazar texto de §4 Entrega 2 por D3 (C4 contenedores E2: contenedores confirmados + grupo punteado "capacidades E2 a refinar": JWT, fila virtual, preferencias/recomendaciones, notificaciones, reportes) y verificar que renderiza con `mmdc`.
- [x] 1.4 Reemplazar §20 Despliegue por D4 (despliegue E1) y D5 (despliegue E2) con Compose, red, puertos, volúmenes, healthchecks; solo Gateway publicado al host; verificar que ambos renderizan con `mmdc`.

## 2. Tabla de estado actual y correcciones semánticas

- [x] 2.1 Agregar tabla de estado actual fuera de los diagramas (qué existe hoy vs E1 target vs E2 target: Gateway, Catalog, catalog_db implementados; resto planificado; preferencias/NoSQL y capacidades E2 a decidir).
- [x] 2.2 Corregir `payment_db` como "a decidir" en lugar de base afirmada.
- [x] 2.3 Marcar pasarela externa (PSP) como fuera de alcance.
- [x] 2.4 Unificar nombres entre diagramas y tablas (Payment / Payment Mock / Pasarela simulada; Ticket / Ticket & Access Service).

## 3. Texto de despliegue y criterios de aceptación

- [x] 3.1 Reescribir §20 para distinguir mínimo del PDF (E1 local + bases Docker; E2 todo Docker) de decisión del proyecto (Docker desde E1 como adelanto voluntario).
- [x] 3.2 Agregar tabla operativa por contenedor (build context, puerto, red, volumen, healthcheck).
- [x] 3.3 Agregar criterios de aceptación de 1.1 a `tasks.md` de `establish-ticketya-architecture` (D1-D5, render con `mmdc`, tabla de estado, límites con 1.5).

## 4. Render y verificación

- [x] 4.1 Ejecutar `cmd /c "mmdc -i ..."` para cada uno de D1-D5 y registrar comando y resultado.
- [x] 4.2 Revisar especialmente el `alt` anidado de la saga y las flechas punteadas en los diagramas de otras tareas (saga, devoluciones, fila virtual) sin bloquear 1.1.
- [x] 4.3 Verificar que 1.1 de `establish-ticketya-architecture` tenga criterios de aceptación verificables y que 1.5 siga siendo la tarea que confirma la política de Dockerfile por componente y Compose único.

## Workflow follow-up

- Revisar con el equipo los diagramas D1-D5 antes de pasar a 1.2.
- Mantener sin commit los cambios pendientes de `implement-event-discovery` hasta que el usuario lo indique.
