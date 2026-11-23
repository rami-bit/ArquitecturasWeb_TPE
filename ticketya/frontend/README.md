# Interfaz web de escritorio de TicketYa

La interfaz es una demostración local para navegador de escritorio. Todas sus llamadas de negocio usan `/api` a través del Gateway en `localhost:8080`; Vite redirige ese prefijo durante el desarrollo. El selector de perfiles usa identidades fijas y solicita JWT al IdP de prueba incluido en Compose.

## Inicio local

Requisitos: Node.js y npm, Docker para el stack. Desde la raíz del repositorio, iniciá Compose:

```powershell
docker compose up --build -d --wait
```

En otra terminal, desde `frontend/`:

```powershell
npm install
npm run dev
```

Abrí la URL local que muestra Vite. Para comprobar la compilación, ejecutá `npm run build`. El proxy de desarrollo apunta `/api` a `http://127.0.0.1:8080` y `/demo-idp` a `http://127.0.0.1:19998`. La versión empaquetada requiere servir ambos prefijos por el mismo origen mediante un proxy equivalente.

Para las comprobaciones dirigidas del frontend, usá `npm test`. Con Compose y Vite iniciados, `npm run smoke:browser` recorre Cliente, Organizador, Administrador y Salir en Chrome local; también exige HTTP 200 en las consultas de gestión y sedes de ambos perfiles. `CHROME_PATH` permite indicar otro ejecutable instalado y `FRONTEND_URL` otra URL de Vite. El script no descarga un navegador.

El IdP publica su JWKS en la red interna de Compose y `/token` en `127.0.0.1:19998`; la clave de firma persiste en el volumen `test-idp-key`. Vite redirige `/demo-idp` a ese puerto local. La interfaz solicita un JWT al seleccionar perfil y otro cinco minutos antes de su vencimiento, sin guardar JWT en disco ni reintentar operaciones de escritura. La selección y Salir son simulados; el Gateway valida firma, vencimiento y roles. Un `401` cierra la sesión simulada y un `403` informa falta de permiso. Este IdP de prueba permite emitir roles libremente: no expongas su puerto ni esta interfaz a una red pública.

Si las operaciones protegidas devuelven **500** mientras la búsqueda pública funciona, comprobá que `test-idp` esté healthy y que `JWT_JWK_SET_URI` del Gateway apunte a `http://test-idp:19998/.well-known/jwks.json`. El emisor de `.env` debe coincidir con `http://host.docker.internal:19998`. Después de corregir la configuración, recreá el Gateway. Si hubo una rotación accidental de clave, recreá el Gateway para renovar su JWKS en caché.

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
