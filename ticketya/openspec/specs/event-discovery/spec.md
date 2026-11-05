# Event Discovery Specification

## Purpose

Permite que clientes descubran y consulten eventos públicamente disponibles mediante filtros y ordenamientos previsibles sobre el catálogo publicado.

## Requirements

### Requirement: Buscar sólo eventos visibles

El sistema SHALL exponer públicamente en resultados de búsqueda únicamente eventos cuyo estado actual sea `PUBLISHED`.

#### Scenario: Buscar con eventos en estados diferentes

- **GIVEN** existen eventos `DRAFT`, `PUBLISHED` y `SUSPENDED`
- **WHEN** un cliente realiza una búsqueda pública
- **THEN** el sistema devuelve sólo los eventos `PUBLISHED`

#### Scenario: Evento suspendido deja de ser visible

- **GIVEN** un evento aparecía en resultados públicos
- **WHEN** un administrador lo suspende
- **THEN** las búsquedas públicas posteriores dejan de incluirlo

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

El sistema SHALL permitir consultar sin autenticación el snapshot de un evento `PUBLISHED`, incluyendo participantes, fecha, lugar, sectores y precios.

#### Scenario: Consultar evento publicado

- **GIVEN** existe un evento en estado `PUBLISHED`
- **WHEN** un cliente solicita su detalle público
- **THEN** el sistema devuelve la información inmutable de su snapshot sin afirmar disponibilidad de entradas

#### Scenario: Consultar evento no visible

- **GIVEN** el evento está en `DRAFT` o `SUSPENDED`
- **WHEN** un cliente solicita su detalle público
- **THEN** el sistema responde como recurso no encontrado sin revelar sus datos

#### Scenario: Consultar identificador inexistente

- **WHEN** un cliente solicita un identificador de evento inexistente
- **THEN** el sistema responde como recurso no encontrado
