# Event Discovery Specification

## Purpose

Permite que clientes descubran y consulten eventos públicamente disponibles mediante filtros y ordenamientos previsibles sobre el catálogo publicado.

## Requirements

### Requirement: Buscar sólo eventos visibles

El sistema SHALL exponer públicamente en resultados de búsqueda únicamente eventos cuyo estado actual sea `PUBLISHED`. El mismo criterio SHALL regir el contrato interno de candidatos para recomendaciones, que sólo SHALL incluir eventos `PUBLISHED` con `startsAt` futuro, acotados a 200 por proximidad. Trazabilidad: PDF-F10 y PDF-NF-CONSISTENCY.

#### Scenario: Buscar con eventos en estados diferentes

- **GIVEN** existen eventos `DRAFT`, `PUBLISHED` y `SUSPENDED`
- **WHEN** un cliente realiza una búsqueda pública
- **THEN** el sistema devuelve sólo los eventos `PUBLISHED`

#### Scenario: Evento suspendido deja de ser visible

- **GIVEN** un evento aparecía en resultados públicos
- **WHEN** un administrador lo suspende
- **THEN** las búsquedas públicas posteriores dejan de incluirlo

#### Scenario: Candidatos de recomendación sólo publicados

- **GIVEN** eventos en estado `DRAFT`, `PUBLISHED`, `SUSPENDED` y `CANCELLED`
- **WHEN** Preferences solicita `GET /internal/events/recommendation-candidates`
- **THEN** Catalog devuelve únicamente los eventos `PUBLISHED`

#### Scenario: Candidatos con fecha futura

- **GIVEN** eventos publicados con `startsAt` anterior y posterior al instante de la consulta
- **WHEN** Preferences solicita los candidatos
- **THEN** Catalog excluye los eventos cuya fecha ya pasó

#### Scenario: Candidatos acotados por proximidad

- **GIVEN** más de 200 eventos publicados y futuros
- **WHEN** Preferences solicita los candidatos con `limit=200`
- **THEN** Catalog devuelve los 200 más próximos por `startsAt` ascendente y `eventId` ascendente

### Requirement: Filtrar eventos

El sistema SHALL permitir combinar filtros por tipo, organizador, participante, ciudad y rango de fecha y hora.

#### Scenario: Combinar filtros

- **GIVEN** existen eventos publicados con diferentes tipos, participantes y ciudades
- **WHEN** un cliente combina filtros de tipo, participante y ciudad
- **THEN** el sistema devuelve sólo eventos que cumplen todos los filtros informados

#### Scenario: Filtrar por rango de fechas

- **WHEN** un cliente informa límites de fecha válidos
- **THEN** el sistema devuelve eventos publicados cuya fecha y hora se encuentran dentro del rango inclusivo

#### Scenario: Rango de fechas inválido

- **WHEN** el límite inicial del rango es posterior al límite final
- **THEN** el sistema rechaza la consulta e identifica el rango inválido

### Requirement: Paginar resultados

El sistema SHALL paginar toda búsqueda e informar página, tamaño, cantidad total de elementos y cantidad total de páginas.

#### Scenario: Solicitar una página válida

- **WHEN** un cliente solicita una página y tamaño admitidos
- **THEN** el sistema devuelve los elementos de esa página junto con metadata de paginación

#### Scenario: Solicitar un tamaño no admitido

- **WHEN** un cliente solicita un tamaño de página fuera de los límites publicados por la API
- **THEN** el sistema rechaza la consulta e identifica el parámetro inválido

### Requirement: Ordenar por fecha

El sistema SHALL permitir ordenar resultados por fecha y hora del evento, usando el identificador del evento como desempate estable.

#### Scenario: Orden ascendente por fecha

- **WHEN** un cliente solicita orden ascendente por fecha
- **THEN** el sistema devuelve primero los eventos más próximos y desempata por identificador

#### Scenario: Orden descendente por fecha

- **WHEN** un cliente solicita orden descendente por fecha
- **THEN** el sistema devuelve primero los eventos más lejanos y desempata por identificador

### Requirement: Ordenar por precio

El sistema SHALL permitir ordenar eventos por el menor precio positivo de sus sectores publicados, usando el identificador como desempate estable.

#### Scenario: Orden ascendente por precio

- **GIVEN** cada evento publicado tiene uno o más sectores valorizados
- **WHEN** un cliente solicita orden ascendente por precio
- **THEN** el sistema ordena por el menor precio del snapshot de cada evento

### Requirement: Ordenar por relevancia

El sistema SHALL permitir ordenar por relevancia textual considerando nombre, descripción y participantes del evento, con un desempate estable.

#### Scenario: Búsqueda textual relevante

- **GIVEN** varios eventos publicados contienen el texto buscado en campos diferentes
- **WHEN** un cliente solicita orden por relevancia
- **THEN** el sistema devuelve primero las coincidencias con mayor relevancia y desempata de forma estable

#### Scenario: Relevancia sin texto de búsqueda

- **WHEN** un cliente solicita relevancia sin informar texto de búsqueda
- **THEN** el sistema rechaza la consulta e identifica el parámetro faltante

### Requirement: Consultar detalle público

El sistema SHALL permitir consultar sin autenticación el detalle de un evento `PUBLISHED`, incluyendo participantes, fecha vigente, lugar, sectores y precios. La configuración publicada SHALL provenir del snapshot original y la postergación SHALL cambiar sólo la fecha presentada.

#### Scenario: Consultar evento publicado

- **GIVEN** existe un evento en estado `PUBLISHED`
- **WHEN** un cliente solicita su detalle público
- **THEN** el sistema devuelve la configuración inmutable de su snapshot y la fecha vigente, sin afirmar disponibilidad de entradas

#### Scenario: Consultar evento no visible

- **GIVEN** el evento está en `DRAFT` o `SUSPENDED`
- **WHEN** un cliente solicita su detalle público
- **THEN** el sistema responde como recurso no encontrado sin revelar sus datos

#### Scenario: Consultar identificador inexistente

- **WHEN** un cliente solicita un identificador de evento inexistente
- **THEN** el sistema responde como recurso no encontrado

### Requirement: Preserve sector identifiers across purchase selection

The public published event detail SHALL identify each sector with its stable source sector ID, matching the sector identifier used by Inventory availability and reservation contracts.

#### Scenario: Select a sector for purchase

- **GIVEN** a published event whose sector is visible in both Catalog detail and Inventory availability
- **WHEN** the client submits the sector ID from the published event detail to Purchase
- **THEN** Purchase and Inventory refer to the same sector without name-based translation

#### Scenario: Preserve sector identity across catalog changes

- **GIVEN** a published event snapshot and a later edit to its venue template
- **WHEN** a client reads the published event detail and Inventory availability
- **THEN** both continue to expose the original source sector ID for that event

### Requirement: Informar la marca de alta demanda en el catálogo público

Las consultas públicas del catálogo SHALL informar si el evento está marcado como de alta demanda, tanto en el listado de eventos como en el detalle de un evento publicado, para que el cliente pueda ingresar a la fila virtual antes de intentar comprar.

#### Scenario: Listado de eventos con la marca

- **GIVEN** eventos publicados, algunos marcados como de alta demanda
- **WHEN** un cliente consulta el listado público de eventos
- **THEN** cada resultado informa la marca de alta demanda del evento

#### Scenario: Detalle de un evento de alta demanda

- **GIVEN** un evento publicado marcado como de alta demanda
- **WHEN** un cliente consulta su detalle público
- **THEN** la respuesta informa la marca de alta demanda del evento junto con su snapshot, participantes, fecha, lugar, sectores y precios

#### Scenario: Detalle de un evento que no está en la fila

- **GIVEN** un evento publicado sin marca de alta demanda
- **WHEN** un cliente consulta su detalle público
- **THEN** la respuesta informa que el evento no está marcado como de alta demanda y sigue sin afirmar disponibilidad de entradas

### Requirement: Usar la fecha vigente en descubrimiento y recomendaciones

Las búsquedas públicas y los candidatos internos de recomendación SHALL filtrar, ordenar y mostrar la fecha vigente después de una postergación. SHALL conservar filtros, paginación y desempates existentes. Trazabilidad: PDF-F9, PDF-F10 y PDF-F11.

#### Scenario: Búsqueda por rango

- **WHEN** un evento se posterga fuera del rango de fechas consultado
- **THEN** deja de aparecer en ese rango y aparece en el rango de la fecha nueva

#### Scenario: Orden y recomendaciones

- **WHEN** se posterga un evento publicado
- **THEN** búsqueda y candidatos de recomendaciones lo ordenan y consideran futuro según la fecha nueva
