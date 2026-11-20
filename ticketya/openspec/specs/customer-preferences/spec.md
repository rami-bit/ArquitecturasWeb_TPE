# customer-preferences Specification

## Purpose

Permite que cada cliente registre y consulte sus preferencias de ciudad, tipos de evento y artistas favoritos, persistidas en un documento propio en MongoDB, sin acceder a datos de otros clientes ni a las bases de otros servicios.

## Requirements

### Requirement: Registrar las preferencias propias

El servicio SHALL permitir que un cliente autenticado registre o reemplace sus preferencias mediante `PUT /api/customers/me/preferences`, identificándolo por el `sub` del JWT validado. La operación SHALL ser idempotente, SHALL devolver la representación resultante con su `version` y SHALL NOT aceptar un identificador de cliente en el cuerpo ni en la ruta. Trazabilidad: PDF-F10 y PDF-NF-SECURITY.

#### Scenario: Alta inicial de preferencias

- **GIVEN** un cliente autenticado sin preferencias registradas
- **WHEN** envía `PUT /api/customers/me/preferences` con ciudad, tipos de evento y artistas favoritos válidos
- **THEN** el servicio responde `200` con las preferencias normalizadas, `version` igual a `1` y `updatedAt` informado

#### Scenario: Reemplazo idempotente de preferencias

- **GIVEN** un cliente con preferencias registradas en `version` `2`
- **WHEN** el mismo cliente repite el `PUT` con el mismo contenido
- **THEN** el servicio responde `200` con el mismo documento, sin crear un segundo documento y con `version` incrementada

#### Scenario: Cuerpo con identificador de cliente

- **GIVEN** un cliente autenticado
- **WHEN** envía un `PUT` cuyo cuerpo incluye un `customerId` distinto del `sub`
- **THEN** el servicio responde `400` con `application/problem+json` y no escribe preferencias

#### Scenario: Solicitud sin identidad

- **WHEN** un cliente envía `PUT /api/customers/me/preferences` sin JWT válido
- **THEN** el Gateway responde `401` con `application/problem+json` y no invoca al servicio de preferencias

### Requirement: Consultar las preferencias propias

El servicio SHALL devolver las preferencias del cliente autenticado mediante `GET /api/customers/me/preferences`, identificándolo por el `sub` del JWT validado. Si el cliente todavía no registró preferencias, SHALL responder `200` con ciudad nula, listas vacías y `version` cero, sin crear el documento. Trazabilidad: PDF-F10.

#### Scenario: Consultar preferencias registradas

- **GIVEN** un cliente con preferencias registradas
- **WHEN** consulta `GET /api/customers/me/preferences`
- **THEN** el servicio devuelve su ciudad, sus tipos de evento, sus artistas favoritos, `updatedAt` y `version`

#### Scenario: Cliente sin preferencias

- **GIVEN** un cliente que nunca registró preferencias
- **WHEN** consulta `GET /api/customers/me/preferences`
- **THEN** el servicio responde `200` con `city` nula, `eventTypes` y `favoriteArtists` vacías, `version` `0`, y no crea el documento

#### Scenario: Preferencias de otro cliente

- **GIVEN** dos clientes autenticados, cada uno con preferencias distintas
- **WHEN** uno consulta sus preferencias
- **THEN** el servicio devuelve únicamente las del `sub` del token presentado

### Requirement: Validar el contenido de las preferencias

El servicio SHALL rechazar con `400` y `application/problem+json` una ciudad vacía o de más de 120 caracteres, más de 10 tipos de evento, más de 20 artistas favoritos, un elemento vacío, un elemento que exceda la longitud admitida o un duplicado ignorando mayúsculas. Los tipos SHALL tratarse como texto libre porque Catalog no define un enum. Trazabilidad: PDF-F10.

#### Scenario: Ciudad en blanco

- **WHEN** un cliente envía una ciudad compuesta sólo por espacios
- **THEN** el servicio responde `400` con `application/problem+json` e identifica el campo `city`

#### Scenario: Demasiados artistas favoritos

- **WHEN** un cliente envía 21 artistas favoritos
- **THEN** el servicio responde `400` con `application/problem+json` e identifica el campo `favoriteArtists`

#### Scenario: Elemento duplicado ignorando mayúsculas

- **WHEN** un cliente envía `["Concierto", "CONCIERTO"]` como tipos de evento
- **THEN** el servicio responde `400` con `application/problem+json` y no escribe preferencias

#### Scenario: Artista que excede la longitud admitida

- **WHEN** un cliente envía un artista favorito de más de 200 caracteres
- **THEN** el servicio responde `400` con `application/problem+json` e identifica el elemento inválido

### Requirement: Persistir un documento por cliente en MongoDB

El servicio SHALL persistir las preferencias en la colección `customer_preferences` de la base `ticketya_preferences`, con `_id` igual al UUID del cliente y un único documento por cliente. Cada escritura SHALL ser un upsert atómico de un solo documento, sin transacciones multi-documento, y SHALL actualizar `updatedAt` y `version`. Trazabilidad: PDF-NF-PERSISTENCE.

#### Scenario: Documento identificado por el cliente

- **GIVEN** un cliente que registró preferencias
- **WHEN** se inspecciona la colección `customer_preferences`
- **THEN** existe exactamente un documento cuyo `_id` es el UUID del cliente, con ciudad, listas, `updatedAt` y `version`

#### Scenario: Escrituras concurrentes del mismo cliente

- **GIVEN** un cliente que envía dos `PUT` concurrentes con contenido distinto
- **WHEN** ambas escrituras terminan
- **THEN** la colección conserva un único documento válido y su `version` refleja las escrituras aplicadas

#### Scenario: Persistencia entre reinicios

- **GIVEN** un cliente que registró preferencias
- **WHEN** el servicio se reinicia y el cliente vuelve a consultar
- **THEN** el servicio devuelve las mismas preferencias desde MongoDB

### Requirement: Aislar las preferencias por cliente

Sólo un `CLIENT` autenticado SHALL acceder a sus propias preferencias. La identidad SHALL derivarse exclusivamente del `sub` del JWT y ninguna ruta SHALL recibir un `customerId`. Cualquier otro rol SHALL recibir `403` sin leer ni escribir preferencias, y un cliente SHALL NOT acceder a las preferencias de otro. Trazabilidad: PDF-NF-SECURITY.

#### Scenario: Rol distinto de cliente

- **WHEN** un usuario autenticado con rol `ORGANIZER`, `ADMIN` o `ACCESS_STAFF` consulta o modifica preferencias
- **THEN** el servicio responde `403` con `application/problem+json` y no lee ni escribe ningún documento

#### Scenario: Identidad ajena en encabezados

- **GIVEN** un cliente autenticado que envía `X-User-Id` con el identificador de otro cliente
- **WHEN** consulta sus preferencias por el Gateway
- **THEN** el servicio usa el `sub` del JWT validado y devuelve las preferencias del titular, no las del encabezado

#### Scenario: Ruta sin identificador de cliente

- **WHEN** se inspecciona el contrato de las operaciones de preferencias
- **THEN** ninguna ruta pública recibe un `customerId` y todas resuelven la identidad desde el JWT

### Requirement: Errores consistentes y trazabilidad de preferencias

Las respuestas de error SHALL usar `application/problem+json` con `type`, `title`, `status`, `detail`, `instance` y `correlationId`. Cada operación SHALL registrar el cliente, la operación, el estado resultante y el tiempo de procesamiento, sin registrar el contenido de las preferencias ni el token. Trazabilidad: PDF-NF-TRACE.

#### Scenario: Error con correlation ID

- **WHEN** un cliente recibe un error de preferencias
- **THEN** la respuesta es `application/problem+json`, incluye `correlationId` y devuelve el mismo `X-Correlation-Id` propagado en la solicitud

#### Scenario: Registro sin datos sensibles

- **WHEN** un cliente registra o consulta sus preferencias
- **THEN** el servicio registra cliente, operación, estado y duración, y no registra el contenido de las preferencias ni el token
