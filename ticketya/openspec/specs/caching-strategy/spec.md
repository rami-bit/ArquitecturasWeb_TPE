# caching-strategy Specification

## Purpose

Define qué datos del catálogo pueden cachearse, por cuánto tiempo y bajo qué eventos se invalidan, sin comprometer la verdad autoritativa de inventario ni la personalización de recomendaciones.

## Requirements

### Requirement: Cachear sólo lecturas del catálogo publicado

Catalog SHALL poder cachear en memoria las respuestas de búsqueda y detalle de eventos publicados con un TTL y un tamaño máximo configurables. La clave SHALL incluir filtros, paginación y orden. Inventory availability, precios de compra y recomendaciones SHALL NOT cachearse. Trazabilidad: PDF-NF-CACHE.

#### Scenario: Acierto de caché

- **GIVEN** una búsqueda ya consultada dentro del TTL
- **WHEN** un cliente repite la misma búsqueda
- **THEN** Catalog devuelve la representación cacheada sin consultar la base

#### Scenario: Claves distintas

- **WHEN** dos búsquedas difieren en filtros, página u orden
- **THEN** se tratan como entradas distintas y no comparten resultado

#### Scenario: Disponibilidad siempre fresca

- **WHEN** un cliente consulta disponibilidad de inventario
- **THEN** Inventory la calcula en el momento de la consulta, sin leer la caché de Catalog

### Requirement: Invalidar al cambiar el catálogo

Catalog SHALL invalidar las entradas afectadas después de publicar, suspender, cancelar, reanudar o reemplazar las ofertas de un evento. Si la invalidación no puede ejecutarse, la entrada SHALL expirar como máximo al vencer el TTL. Trazabilidad: PDF-NF-CACHE y PDF-NF-CONSISTENCY.

#### Scenario: Suspensión de un evento

- **GIVEN** un evento en la caché de búsqueda
- **WHEN** un administrador lo suspende
- **THEN** las búsquedas posteriores no devuelven ese evento aunque no haya vencido el TTL

#### Scenario: Fallo de invalidación

- **GIVEN** una invalidación que no pudo completarse
- **WHEN** transcurre el TTL configurado
- **THEN** la entrada se descarta y la siguiente consulta refleja el estado vigente

### Requirement: Configurar y observar la caché

El despliegue SHALL exponer el TTL y el tamaño máximo mediante variables de entorno, y Catalog SHALL registrar aciertos, fallos e invalidaciones sin incluir datos personales. Trazabilidad: PDF-NF-CACHE y PDF-NF-TRACE.

#### Scenario: Configuración por entorno

- **WHEN** cambia `CATALOG_CACHE_TTL_SECONDS` antes de levantar Compose
- **THEN** Catalog aplica el nuevo valor sin cambios de código

#### Scenario: Registro sin datos sensibles

- **WHEN** se consulta y se invalida la caché
- **THEN** los logs informan clave, resultado e invalidación, sin tokens ni identificadores de cliente

### Requirement: Invalidar el catálogo al postergar un evento

Catalog SHALL invalidar búsqueda y detalle cacheados después del commit de una postergación confirmada. El TTL existente SHALL seguir limitando la obsolescencia ante fallo de invalidación. Trazabilidad: PDF-F11 y PDF-NF-CACHE.

#### Scenario: Postergación confirmada

- **WHEN** se confirma una postergación con búsqueda y detalle cacheados
- **THEN** las lecturas siguientes muestran la fecha vigente y el orden actualizado

#### Scenario: Postergación rechazada

- **WHEN** se rechaza una postergación
- **THEN** no se invalida la caché por ese intento ni se publica aviso
