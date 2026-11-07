# Design

## Context

El cambio `establish-ticketya-architecture` tiene la tarea 1.1 abierta: revisar diagramas de contexto, contenedores y despliegue, verificar que rendericen y que distingan Entrega 1 de Entrega 2. El `design.md` de ese cambio tiene un diagrama de contexto (§2), uno de arquitectura lógica (§3), uno de Entrega 1 (§4), uno de despliegue genérico (§20) y diagramas de flujo de saga, devoluciones y fila virtual que pertenecen a otras tareas.

`mmdc` 12.2.0 está instalado pero PowerShell bloquea su script `.ps1`; se ejecuta con `cmd /c "mmdc ..."`. No hay diagrama de contenedores C4 explícito; el de §98 se llama "Arquitectura lógica general" y no muestra bases ni límites de despliegue. El de §494 es genérico (`ServiceC`, `PgC`, `NoSqlC`) y no distingue estado actual, E1 objetivo y E2 objetivo.

## Goals / Non-Goals

**Goals:**
- Cerrar la tarea 1.1 con criterios de aceptación verificables.
- Tener cinco diagramas con un solo alcance cada uno (D1-D5), sin mezclar E1/E2/actual.
- Dejar constancia de que Docker desde E1 es decisión del proyecto, no mínimo del PDF.
- Registrar qué renderiza con `mmdc` y qué no.

**Non-Goals:**
- No modificar código fuente, Dockerfiles, `docker-compose.yml` ni POM.
- No validar semántica arquitectónica con `mmdc` (solo sintaxis/render).
- No cerrar las tareas 2.x, 3.x ni 1.2 de `establish-ticketya-architecture`.

## Decisions

### D1 - D5 separados, cada uno con un solo alcance

| ID | Sección | Diagrama | Alcance |
|---|---|---|---|
| D1 | §2 | Contexto | Transversal (actores, canales, sistemas externos) |
| D2 | §3 | C4 contenedores E1 | E1 target |
| D3 | §4 | C4 contenedores E2 | E2 target |
| D4 | §20 | Despliegue E1 | E1 target |
| D5 | §20 | Despliegue E2 | E2 target |

Razón: evita la leyenda de tres estados (actual/E1/E2) que el diagrama genérico de §494 intenta cargar. La comparación E1/E2 se hace entre D2 y D3; el estado actual va en una tabla aparte.

### Cada microservicio Spring Boot es un contenedor C4

Un contenedor C4 es una unidad ejecutable/desplegable. Cada servicio (Gateway, Catalog, Inventory, Purchase, Payment Mock, Ticket & Access) es uno y se mapea 1:1 a una imagen Docker con su propio `Dockerfile` en Entrega 2. Las bases de datos también son contenedores C4 de infraestructura (PostgreSQL por ownership, NoSQL candidata en E2).

### Docker desde E1 es decisión del proyecto

El mínimo del PDF para E1 es ejecución local con bases en Docker; Docker por servicio es obligatorio en E2. El repositorio ya adelanta Docker también en E1 (implementado en `establish-event-catalog`). El blueprint debe decirlo como adelanto voluntario y no borrar la distinción del PDF.

### `skip_specs: true`

Este cambio no modifica comportamiento del sistema, solo diagramas y documentación. No se crean specs.

### Correcciones semánticas en `design.md` de `establish-ticketya-architecture`

- `payment_db`: marcado como "a decidir" (la tabla de ownership ya lo dice; el diagrama no puede afirmar una base).
- Pasarela externa (PSP): fuera de alcance en E1/E2; se usa Payment Mock.
- `Delivery2`: reemplazado por un grupo punteado "capacidades E2 a refinar" (JWT, fila virtual, preferencias/recomendaciones, notificaciones, reportes).
- Relación Inventory-Catalog: etiquetada como snapshot/copia, no como llamada runtime.
- Nombres unificados entre diagramas y tablas (Payment / Payment Mock / Pasarela simulada; Ticket / Ticket & Access Service).

### §20 Despliegue reescrito

- Regla común desde E1: cada servicio tiene Dockerfile propio, Compose raíz único, imágenes oficiales para dependencias estándar, `docker compose up --build`, variables de entorno para config/credenciales, Gateway es la única API publicada.
- Se añade: E1 mínimo del PDF (local + DBs Docker) vs E1 actual (Docker desde E1, adelanto) vs E2 (todos en Docker).
- Tabla operativa por contenedor: build context, puerto, red, volumen, healthcheck.

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| D2/D3 duplican información de D1 | Cada uno tiene alcance distinto; D1 es transversal y no se repite en D2/D3 |
| `mmdc` puede fallar en Windows por Chromium/Puppeteer | Usar `cmd /c` y, si falla, `PUPPETEER_EXECUTABLE_PATH` apuntando a Chrome/Edge |
| `design.md` de `establish-ticketya-architecture` se edita desde otro cambio | Editar solo secciones 2, 3, 4 y 20; no tocar 5-19 ni 21+ |
| Render de `alt` anidado de la saga y flechas punteadas | Validar con `mmdc` al editar; los diagramas de saga/devoluciones/fila virtual se validan en sus tareas |

## Open Questions

Ninguna que bloquee 1.1. Las capacidades E2 (JWT, fila virtual, preferencias/recomendaciones, notificaciones, reportes) se definen como "a refinar" y se mapean en 1.2.

## Registro de renderizado con `mmdc`

Comando usado: `cmd /c "mmdc -i <archivo>.mmd -o <archivo>.svg"`

Versión: `mmdc` 12.2.0 (Chromium headless vía Puppeteer)

Entorno: Windows PowerShell bloquea el script `.ps1` de `mmdc`; se ejecutó con `cmd /c "mmdc ..."`.

| Diagrama | Sección | Archivo `.mmd` | Resultado |
|---|---|---|---|
| D1 Contexto | §2 de `design.md` de `establish-ticketya-architecture` | D1.mmd | OK - render generado sin errores |
| D2 C4 contenedores E1 | §3 | D2.mmd | OK - render generado sin errores |
| D3 C4 contenedores E2 | §4 | D3.mmd | OK - render generado (tras entrecomillar el nombre del subgraph con paréntesis) |
| D4 Despliegue E1 | §20 | D4.mmd | OK - render generado sin errores |
| D5 Despliegue E2 | §20 | D5.mmd | OK - render generado sin errores |

Nota: Los archivos `.mmd` y `.svg` temporales se borraron tras la verificación; los diagramas Mermaid quedan embebidos en `design.md` de `establish-ticketya-architecture`. `mmdc` valida sintaxis y render, no semántica arquitectónica.
