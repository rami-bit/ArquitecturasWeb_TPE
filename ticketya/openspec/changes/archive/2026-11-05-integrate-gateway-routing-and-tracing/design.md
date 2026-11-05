# Design

## Context

`services/api-gateway` es hoy un esqueleto: sólo define puerto y actuator, sin rutas ni filtros. `services/catalog-service` ya expone todos sus endpoints bajo `/api/**`, ya tiene un `CorrelationIdFilter` que lee, genera y devuelve `X-Correlation-Id`, y ya valida `X-User-Id` / `X-User-Role` en su capa de dominio. El Compose central ya publica sólo el puerto del Gateway y pasa `CATALOG_SERVICE_URL=http://catalog-service:8081`.

Véanse `proposal.md` para la motivación y `specs/api-gateway/spec.md` para el comportamiento exigido.

## Goals / Non-Goals

**Goals:**

- Exponer el catálogo bajo `/api/**` a través del Gateway sin cambiar el contrato de Catalog Service.
- Resolver un único `X-Correlation-Id` en el punto de entrada y hacerlo viajar en request y response.
- Demostrar con pruebas rápidas que la ruta, la identidad y el correlation ID funcionan sin requerir Docker.
- Mantener el cambio concentrado en `api-gateway`.

**Non-Goals:**

- Implementar autenticación JWT, autorización en el Gateway, rate limiting, retry, circuit breaker o service discovery.
- Modificar el comportamiento de Catalog Service, su OpenAPI o su esquema.
- Exponer OpenAPI/Swagger a través del Gateway en este cambio.
- Ejecutar la prueba extremo a extremo real con Compose; queda para 6.3/6.5 de `establish-event-catalog`.
- Endurecer la validación del correlation ID más allá de tratarlo como valor opaco no vacío.

## Decisions

### 1. Ruta declarativa en `application.yml`

Se agrega una única ruta:

- `id`: `catalog-service`
- `uri`: `${CATALOG_SERVICE_URL:http://localhost:8081}`
- predicado: `Path=/api/**`
- sin `StripPrefix`, `RewritePath` ni `SetPath`

El default permite ejecutar el Gateway con Maven contra un Catalog local en `:8081`; Compose sobreescribe la URL con el nombre DNS interno. No se usa `lb://` porque no hay service discovery y el Compose DNS alcanza.

**Alternativas consideradas:**

- `RouteLocator` en Java: más código sin beneficio para una sola ruta estática.
- `lb://catalog-service` + Eureka/Consul: agrega infraestructura no justificada.
- Rutas separadas por endpoint: más configuración y mantenimiento sin necesidad actual.

### 2. `WebFilter` para el correlation ID

Se implementa un `WebFilter` en `api-gateway` con orden alto:

1. Lee `X-Correlation-Id` de la request.
2. Si es no vacío, lo recorta y lo conserva; si falta o queda vacío, genera un UUID v4.
3. Muta la request para enviar ese valor a Catalog Service, preservando los demás headers.
4. Registra el valor resuelto para fijarlo en la response.

Se elige `WebFilter` en lugar de `GlobalFilter` porque también cubre rutas no encontradas, errores previos al routing y el propio actuator. El correlation ID queda disponible incluso cuando el fallo ocurre antes de llegar a Catalog Service.

**Alternativas consideradas:**

- `GlobalFilter`: idiomático de Gateway, pero no se ejecuta para rutas no matcheadas.
- Confiar sólo en Catalog Service: no cumple que el Gateway genere o propague en el punto de entrada.
- `AddRequestHeader` estático: no sirve para generar valores dinámicos.

### 3. Fijar la response en `beforeCommit`

El header de respuesta se setea en `beforeCommit`, no antes de invocar la cadena. Catalog Service también devuelve `X-Correlation-Id`; fijarlo al final garantiza un único valor y evita que el downstream lo pise o lo duplique. El Gateway queda como autoridad del identificador resuelto en el borde.

La request sí se muta antes de la cadena para que Catalog Service use exactamente el mismo ID en su atributo, MDC, header y `problem+json`.

### 4. La identidad provisional se reenvía por comportamiento default

Spring Cloud Gateway reenvía los headers custom de la request salvo los hop-by-hop. Por eso `X-User-Id` y `X-User-Role` ya viajan sin un filtro adicional. Se agrega una prueba que lo verifica, pero no se duplica la lógica de autorización ni se inventan headers cuando faltan.

**Alternativas consideradas:**

- Filtro explícito que copie identidad: redundante y con riesgo de descartar otros headers.
- Validar rol en el Gateway: duplica una regla de dominio y contradice el diseño de la épica.

### 5. Pruebas sin Docker y sin dependencias nuevas

La verificación se divide en dos niveles:

- **Unit test del filtro:** usa `MockServerWebExchange` y una cadena de prueba para comprobar generación, propagación, mutación de request y header de response.
- **Integración del Gateway:** levanta el contexto real con `@SpringBootTest(webEnvironment = RANDOM_PORT)`, sobreescribe `CATALOG_SERVICE_URL` hacia un `HttpServer` del JDK y usa `WebTestClient` para comprobar routing, método, query params, identidad, correlation ID único y passthrough de errores.

El stub del JDK evita sumar MockWebServer o WireMock. El test existente de Catalog Service sigue cubriendo la generación y propagación del lado downstream.

**Alternativas consideradas:**

- Test con Catalog Service real en JUnit: requiere dos aplicaciones o Testcontainers, más lento y con más infraestructura.
- Sólo test unitario: no demuestra que la ruta real del `application.yml` funcione.
- Sólo Postman/Newman: deja el feedback del build más lento y depende de Docker.

La prueba extremo a extremo real Gateway -> Catalog -> PostgreSQL se ejecuta en 6.3/6.5 con Compose y Postman/Newman, donde además se validan los flujos de negocio.

### 6. Sin cambios en Catalog Service ni en Compose

Catalog Service ya cumple su parte: recibe el header, lo propaga y lo devuelve. El Compose ya mantiene el catálogo sin puerto publicado y ya define `CATALOG_SERVICE_URL`. Este cambio no modifica esos archivos.

## Risks / Trade-offs

- **[El Gateway confía en un correlation ID entrante arbitrario]** -> Se acepta durante la Entrega 1 como valor opaco no vacío; un endurecimiento de formato o longitud puede agregarse luego sin cambiar el contrato.
- **[Los headers de identidad son falsificables]** -> Es el mecanismo provisional acordado para la Entrega 1; Catalog Service sigue validando rol y ownership y JWT queda para la Entrega 2.
- **[Una sola ruta `/api/**` puede volverse ambigua con más servicios]** -> Cuando se incorporen Purchase o Ticket, la ruta se dividirá por prefijo en un cambio específico.
- **[El stub de pruebas puede divergir de Catalog Service]** -> Los tests de contrato cubren headers y passthrough; la prueba real con Compose/Newman en 6.5 valida el comportamiento integrado.
- **[El header de respuesta puede duplicarse si cambia el orden de filtros]** -> El test de integración verifica un único valor y `beforeCommit` fija el valor final.
- **[Una URL de catálogo mal configurada rompe el routing]** -> El default local y la variable de Compose están documentados; la verificación de Compose queda en 6.5.

## Migration Plan

1. Agregar la ruta declarativa y el filtro de correlation ID en `api-gateway`.
2. Incorporar el test unitario del filtro y el test de integración con stub.
3. Ejecutar `mvn verify` en `api-gateway` y en la raíz.
4. Verificar que Catalog Service sigue sin cambios y que Compose mantiene el catálogo interno.
5. Al cerrar la implementación, reconciliar las tareas 6.1 y 6.2 de `establish-event-catalog` con este cambio y dejar la prueba real para 6.3/6.5.

El rollback consiste en quitar la ruta y el filtro; no hay migraciones de datos ni cambios de contrato persistentes.

## Open Questions

- Si 6.5 necesita validar OpenAPI desde el host, habrá que decidir si se expone `/v3/api-docs` por el Gateway o se consulta dentro del contenedor. No afecta el diseño de routing ni de correlation ID.
- El `design.md` de `establish-event-catalog` lista `GET /api/management/venues` y un detalle por ID que la implementación no expone; conviene alinear el contrato al preparar la colección Postman de 6.3.
