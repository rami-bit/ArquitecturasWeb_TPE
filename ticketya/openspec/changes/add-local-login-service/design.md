# Design

## Context

Ver `proposal.md` y `specs/`. El Gateway es un Resource Server reactivo: `application.yml` toma `JWT_ISSUER_URI` y `JWT_JWK_SET_URI`, `TicketYaJwtAuthenticationConverter` exige `sub` UUID, `exp` y un único rol de cuatro, y `IdentityPropagationGatewayFilter` quita el Bearer y propaga identidad validada. Compose hoy usa un issuer de ejemplo `.invalid`. El IdP de prueba emite cualquier `sub`/rol solicitado por query sin credenciales y el arnés de integración lo configura por variables de entorno. La interfaz propuesta en `add-desktop-web-interface` aún supone JWT hardcodeados.

## Goals / Non-Goals

**Goals:**

- Dar al stack local un login demostrable sin iniciar un proceso de identidad manualmente.
- Mantener los claims y la autorización existentes sin tocar bases ni APIs de dominio.
- Conservar el IdP de prueba para casos con identidades nuevas y roles arbitrarios.

**Non-Goals:**

- Implementar OAuth/OIDC completo, registro, recuperación de contraseña, administración de cuentas, refresh tokens o revocación.
- Convertir el IdP de prueba en login de producto o permitir seleccionar `sub`/rol desde la solicitud pública.
- Aceptar dos emisores simultáneos en una instancia del Gateway.

## Decisions

### Auth Service separado y cuentas fijas

Agregar `services/auth-service` al reactor como Spring Boot HTTP y a Compose como servicio independiente. Las cuatro cuentas se cargan de configuración con `username`, UUID estable, un rol y hash de contraseña; no se agrega base de datos porque no habrá mutaciones de cuentas. Los UUID de `CLIENT`, `ORGANIZER`, `ADMIN` y `ACCESS_STAFF` serán distintos, incluso si la colección Postman actual usa un mismo `userId` para tokens de cliente y organizador. El login compara el hash mediante `PasswordEncoder` y nunca acepta rol, UUID ni claims en el cuerpo. Se elige esto frente a Keycloak o Spring Authorization Server completo porque sólo se necesita un emisor local de tokens Bearer para cuatro identidades didácticas. Las cuentas y contraseñas de demostración se documentan; sus hashes son el único material guardado en configuración del servicio.

### Contrato HTTP y emisión

`POST /api/auth/login` recibe JSON `{ "username": "...", "password": "..." }` y responde `200` con `{ "access_token": "...", "token_type": "Bearer", "expires_in": 3600 }`, `Cache-Control: no-store` y `X-Correlation-Id`. Cuerpo malformado/campos no admitidos: `400 application/problem+json`. Cuenta desconocida o contraseña incorrecta: el mismo `401 application/problem+json` genérico. No se registran contraseñas, hashes ni JWT. Auth Service usa el soporte JOSE de Spring Security para firmar RS256 con `kid`; claims `iss`, `sub`, `iat`, `exp` y `roles: [un único valor]`. El token dura una hora. El endpoint interno `GET /.well-known/jwks.json` publica sólo la clave pública; la clave privada no sale del contenedor. No se ofrecen endpoints de refresh ni logout de servidor. Frente a firmar manualmente JWT, el encoder de la biblioteca reduce código criptográfico propio.

### Clave y emisor estables

Auth Service guarda el par RSA en un volumen propio de Compose. En el primer arranque genera la clave y la escribe de forma atómica; al reiniciar carga exactamente la misma clave y `kid`, y falla si el material guardado es inválido en vez de reemplazarlo silenciosamente. Así los JWT vigentes sobreviven un reinicio y el JWKS cacheado por el Gateway sigue correspondiendo al emisor. El emisor del stack normal es `http://auth-service:8089`, usado literalmente como `iss` y como `JWT_ISSUER_URI`; el Gateway toma `JWT_JWK_SET_URI=http://auth-service:8089/.well-known/jwks.json`. El nombre de servicio es resoluble en la red de Compose y no necesita ser accesible desde el navegador. El puerto del Gateway se publica en `127.0.0.1`; Auth Service no publica puertos propios.

### Enrutamiento y modos de verificación

Agregar en Gateway una regla `permitAll` para `POST /api/auth/login` antes de `/api/**` y una ruta `/api/auth/login` hacia Auth Service antes del comodín de Catalog. El filtro global seguirá descartando `Authorization`, `X-User-Id` y `X-User-Role` externos antes de enrutar; Auth Service no los necesita. JWKS es sólo interno y no tiene ruta pública por Gateway. Todas las demás rutas conservan sus controles. Documentar el login en OpenAPI del servicio y consolidado.

El stack normal usa Auth Service. El arnés `postman/integration-harness.py` ya sobreescribe `JWT_ISSUER_URI` y `JWT_JWK_SET_URI` para `tools/local-test-idp/LocalTestIdp.java`; se conserva ese mecanismo y el endpoint de prueba `/token?sub=...&role=...`. Los scripts de concurrencia/carga que crean UUID nuevos siguen usando el IdP de prueba. No se mezclan tokens: un Gateway configurado con Auth Service rechaza tokens de prueba y viceversa. Auth Service puede permanecer levantado y saludable durante una corrida de prueba aunque el Gateway confíe en el IdP de prueba. Una colección Postman separada para login y acceso protegido verifica el stack normal; la colección E2E vigente permanece en el modo de IdP de prueba.

### Relación con la interfaz de escritorio

`add-desktop-web-interface` contiene un requisito y tareas para JWT hardcodeados. Antes de aplicar esa interfaz se debe usar `openspec-update-change` para reemplazarlos por un formulario que invoque `POST /api/auth/login`, conserve el token sólo durante la sesión de la pestaña y lo descarte al salir. La navegación obtiene el rol del JWT recibido, pero el Gateway y los servicios siguen siendo la autoridad real. Si el token expira, el usuario vuelve a iniciar sesión. Este cambio de Auth Service no edita los artefactos del otro cambio y no presupone que la interfaz ya esté implementada.

## Risks / Trade-offs

- **Contraseñas de demo conocidas y API HTTP local** → ligar el Gateway a loopback, no publicar Auth Service y documentar que el stack no se expone a una red compartida.
- **Clave perdida o volumen borrado** → los JWT anteriores dejan de validar; conservar el volumen entre reinicios y documentar que borrar datos locales reinicia el emisor.
- **JWT válido tras pulsar Salir** → usar TTL de una hora; Salir sólo elimina la copia del navegador, como se acordó.
- **Un solo emisor por Gateway** → correr verificaciones del IdP de prueba con variables propias y no reutilizar sus tokens en el stack normal.
- **Plan de interfaz desactualizado** → actualizar sus artefactos antes de implementación; no implementar dos mecanismos de login simultáneos.

## Migration Plan

Agregar Auth Service, volumen y healthcheck sin migrar datos de dominio. Cambiar los valores por defecto del Compose local al issuer/JWKS internos; conservar las variables de override y el IdP de prueba. Probar ambos modos por separado. Para revertir, restaurar la configuración anterior del Gateway y dejar de levantar Auth Service; las identidades y datos de dominio no requieren migración.
