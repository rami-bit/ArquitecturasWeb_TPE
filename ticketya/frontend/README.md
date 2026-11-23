# Interfaz web de escritorio de TicketYa

La interfaz es una demostración local para navegador de escritorio. Todas sus llamadas de negocio usan `/api` a través del Gateway en `localhost:8080`; Vite redirige ese prefijo durante el desarrollo. No necesita un proveedor de login propio: el selector de perfiles usa JWT de prueba generados antes de abrir la página.

## Inicio local

Requisitos: Node.js y npm, JDK para el IdP de prueba, Docker para el stack. Desde la raíz del repositorio, en una terminal PowerShell, iniciá el IdP con una clave persistente:

```powershell
New-Item -ItemType Directory -Force .local | Out-Null
java tools/local-test-idp/LocalTestIdp.java --port 19998 --issuer http://host.docker.internal:19998 --key-file .local/idp-key.pem
```

En otra terminal, iniciá el backend con el mismo emisor y JWKS:

```powershell
$env:JWT_ISSUER_URI = 'http://host.docker.internal:19998'
$env:JWT_JWK_SET_URI = 'http://host.docker.internal:19998/.well-known/jwks.json'
docker compose up --build -d --wait
```

En una tercera terminal, desde `frontend/`:

```powershell
npm install
npm run tokens:refresh
npm run dev
```

Abrí la URL local que muestra Vite. Para comprobar la compilación, ejecutá `npm run build`. El proxy de desarrollo apunta a `http://127.0.0.1:8080`; el backend no se consulta desde el frontend mediante puertos internos de servicios. La versión empaquetada requiere servir `/api` por el mismo origen mediante un proxy equivalente.

Para las comprobaciones dirigidas del frontend, usá `npm test`. Con el IdP, el Gateway y Vite iniciados y los JWT renovados, `npm run smoke:browser` recorre Cliente, Organizador, Administrador y Salir en Chrome local; también exige HTTP 200 en las consultas de gestión y sedes de ambos perfiles. `CHROME_PATH` permite indicar otro ejecutable instalado y `FRONTEND_URL` otra URL de Vite. El script no descarga un navegador.

`tokens:refresh` escribe `src/demo/tokens.local.ts` con JWT para los UUID de cliente, organizador y administrador usados en Postman. El archivo y la clave `.local/idp-key.pem` son locales y están excluidos de Git. Los tokens duran aproximadamente una hora: volvé a ejecutar el comando y recargá la página antes de otra demostración. La selección de perfil y Salir son simulados; el Gateway valida firma, vencimiento y roles. Un `401` cierra la sesión simulada y un `403` informa falta de permiso. No uses estos JWT ni esta interfaz en un entorno público.

Si todas las operaciones protegidas devuelven **500** mientras la búsqueda pública funciona, comprobá que el IdP de prueba siga activo en `:19998` y que `JWT_ISSUER_URI` y `JWT_JWK_SET_URI` del Gateway apunten a `http://host.docker.internal:19998` y su `/.well-known/jwks.json`. El valor de ejemplo `identity.example.invalid` no sirve para el demo. Tras corregir esas variables, recreá el Gateway, ejecutá `npm run tokens:refresh` y recargá Vite. Con una clave distinta a la usada antes, también hay que recrear el Gateway para renovar el JWKS en caché.

## Cliente

1. En **Buscar eventos**, combiná filtros de texto, tipo, organizador, participante, ciudad y fechas. La lista muestra solo eventos publicados, ordenados y paginados por Catalog. Una búsqueda sin resultados se muestra vacía; los parámetros inválidos se informan como error.
2. Abrí un evento. Los datos y precios vienen de Catalog; la disponibilidad y los IDs de asientos vienen de Inventory. En un sector general elegí cantidad; en uno numerado elegí asientos de la grilla. Esta selección no reserva cupo.
3. Seleccioná el perfil **Cliente**. En eventos de alta demanda, entrá a la fila y esperá la admisión antes de comprar. La admisión tiene vencimiento y no garantiza entradas.
4. En el checkout de demostración, elegí un resultado de Payment Mock: aprobado, rechazado o pendiente. No ingreses datos de tarjeta. Cada intención usa una clave de idempotencia; ante un timeout, consultá la orden conocida o reintentá la misma intención con el mismo cuerpo y clave. Una nueva selección inicia otra intención. Si Purchase informa conflicto de cupo, actualizá disponibilidad y revisá la selección.
5. En **Mis entradas**, las órdenes de Purchase son la fuente del listado. Una orden pendiente o rechazada puede no tener tickets. El QR se muestra solo para tickets emitidos. Desde un ticket propio podés solicitar devolución y seguir el estado que devuelve Purchase; el servidor decide el plazo de 72 horas. Un rechazo no modifica el ticket mostrado.
6. En **Preferencias**, guardá ciudad, tipos y artistas favoritos y consultá recomendaciones. Una recomendación no garantiza cupo: al abrirla se vuelve a consultar disponibilidad.

## Organizador

Seleccioná **Organizador** y abrí **Mis eventos**. La lista la filtra Catalog por propiedad. Para crear un borrador, elegí una sede existente y completá nombre, fecha, participantes, sectores ofrecidos, precios y alta demanda. Los campos del borrador se pueden editar mientras el evento esté en `DRAFT`; la publicación pide confirmación y Catalog verifica su validez. El snapshot de la sede al publicar no cambia por ediciones posteriores de la sede canónica.

El reporte por evento muestra ventas, devoluciones, ocupación y recaudación por sector y total. Si `capacityAvailable` es `false`, las cifras de capacidad y ocupación se muestran como no disponibles junto con la advertencia que devuelve Purchase. Para un evento publicado propio, **Postergar** exige una fecha posterior a la vigente y a la hora actual más 72 horas. La interfaz envía la revisión actual y una clave idempotente; un `409` obliga a recargar el evento antes de intentar de nuevo.

## Administrador

Seleccioná **Administrador** para gestionar sedes generales o numeradas, consultar eventos globalmente, suspender, reanudar, cancelar y revisar auditoría. La cancelación usa `POST /api/events/{eventId}/cancellation` de Purchase para iniciar reintegros, previa confirmación. Las decisiones de devolución se hacen con un ID conocido: la API no publica una bandeja administrativa de pendientes ni una vista previa por ID. La bitácora permite filtrar y seguir una correlación, sin mostrar credenciales ni JWT.

## F11: contacto y cambios de fecha

En el perfil Cliente, **Email de contacto** consulta `GET /api/notifications/me/email`. Si no hay contacto muestra “No registrado”; al guardar llama a `PUT` con solo `{ "email": "..." }`. Los avisos anteriores sin contacto no se envían retroactivamente. Podés inspeccionar los correos que captura Mailpit en `http://127.0.0.1:8025` después de una compra o postergación.

En **Mis entradas**, `eventStartsAt` de la orden se etiqueta como fecha histórica de compra. Para mostrar la fecha vigente, la interfaz consulta `GET /api/events/{id}` de Catalog por `eventId`, y reutiliza ese resultado cuando hay varias órdenes del mismo evento. Si el detalle público devuelve `404` o falla, la pantalla avisa que no pudo obtener la fecha vigente; nunca presenta la fecha histórica como vigente.
