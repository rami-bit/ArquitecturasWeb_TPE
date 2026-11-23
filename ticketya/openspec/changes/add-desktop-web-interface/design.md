# Design

## Context

Ver `proposal.md` para la motivación y `specs/` para el comportamiento. No existe proyecto frontend. `docs/openapi.yaml` define al Gateway en `localhost:8080` como entrada pública. Catalog expone búsqueda/detalle y gestión; Inventory expone disponibilidad; Purchase expone órdenes, devoluciones, cancelación y reportes. El Gateway valida JWT de un IdP externo; `tools/local-test-idp/LocalTestIdp.java` emite tokens de prueba con `exp` a 3600 segundos y necesita conservar su clave para que el JWKS siga validándolos. F11 ya expone email propio y postergación por el Gateway. La respuesta de Purchase conserva `eventStartsAt` histórico y no informa `currentEventStartsAt`.

## Goals / Non-Goals

**Goals:**

- Permitir una demostración completa desde navegador de escritorio sin credenciales ni altas de usuarios.
- Usar exactamente las rutas públicas y semántica de errores del Gateway, conservando al backend como fuente de verdad para permisos, precios, cupos y estados.
- Mantener aisladas las intenciones de compra y vistas de cada perfil al alternar identidades.

**Non-Goals:**

- Proveedor de identidad, registro de usuarios, recuperación de contraseña o seguridad de un despliegue público.
- Interfaz móvil, lector QR para personal de acceso o cambios en APIs de dominio.
- Bandeja de devoluciones pendientes: la API no ofrece listado ni consulta administrativa previa por ID.

## Decisions

### Proyecto y ejecución local

Crear `frontend/` como aplicación React + TypeScript de renderizado cliente, con Vite para desarrollo y empaquetado estático. Es una interfaz de formularios, tablas y estados interactivos que no necesita renderizado de servidor; esta opción reduce piezas operativas frente a un frontend con servidor propio. El servidor local escuchará sólo en loopback y redirigirá `/api/**` a `http://localhost:8080`, de modo que el navegador use un único origen y no dependa de agregar CORS al Gateway. El frontend no hablará directamente con servicios internos, Payment Mock, Mailpit ni el IdP. Alternativa considerada: páginas servidas por Spring/Thymeleaf; añadirían acoplamiento de despliegue al backend y gestión de sesión en servidor para un demo que ya usa API pública.

### Tokens hardcodeados para el demo

Un archivo local `frontend/src/demo/tokens.local.ts`, excluido de Git y con plantilla sin valores reales, contendrá constantes JWT para los perfiles de cliente, organizador y administrador y sus etiquetas. Un script opcional podrá obtener tokens nuevos del `/token` del IdP de prueba para los UUID ya usados por Postman y escribir ese archivo antes de una demo; luego la aplicación sólo lee las constantes, sin pedir tokens durante el login simulado. Se documentará iniciar el IdP con `--key-file`, configurar `JWT_ISSUER_URI`/`JWT_JWK_SET_URI` del Gateway y renovar las constantes tras una hora. No se versionarán JWT ni se imprimirán en logs de aplicación. Alternativa considerada: solicitar tokens al IdP desde cada selección de perfil; sería un login funcional, distinto de la decisión de usar tokens estáticos en la interfaz.

La selección vive en memoria; Salir o cambiar de perfil limpia estado de pantalla, peticiones en curso y token activo. Se decodifica `exp` sólo para avisar y bloquear una operación obviamente vencida; el Gateway sigue validando firma, emisor y permisos. Una respuesta `401` invalida la sesión simulada; `403` muestra falta de permisos. Nunca se envían `X-User-Id` ni `X-User-Role` desde el navegador.

### Navegación y composición de datos

Usar un marco de escritorio con navegación lateral y tres áreas según perfil. La vista pública tiene búsqueda con filtros, paginación y detalle. El detalle combina `GET /api/events/{id}` para datos/precios con `GET /api/inventory/events/{eventId}/availability` para cupos/asientos; ambos usan los IDs estables, sin traducir por nombre. La vista de asientos será una grilla por fila y número, no un plano espacial que la API no proporciona. Para admisión general se elige cantidad. La selección no retiene lugares: Purchase reserva al confirmar.

En eventos `highDemand`, una pantalla de fila usa `POST /api/queue/events/{eventId}/entries` y sondeo acotado de `GET .../entries/me`; el token de admisión se mantiene asociado al evento y perfil, y sólo pasa a `POST /api/orders` mientras esté vigente. La UI evita prometer cupo. Para pago se ofrecen los resultados de la pasarela simulada (`tok_approved`, `tok_rejected`, `tok_pending`) con etiquetas de demostración, sin formulario de tarjeta real.

El cliente de API centraliza `Authorization`, `Idempotency-Key`, `X-Admission-Token`, parseo de `application/problem+json` y errores de red. Cada confirmación de compra crea una intención con cuerpo y clave UUID; se conserva en `sessionStorage` separada por `sub` hasta respuesta terminal. Ante timeout se reintenta exactamente cuerpo y clave, o se consulta/reconcilia el `orderId` si ya se conoce. Cambiar de perfil elimina la intención visible del anterior y nunca la envía con otro JWT. Las órdenes son la fuente de «Mis entradas»; se muestran QR sólo desde tickets emitidos. `eventStartsAt` de Purchase es la fecha histórica de compra. Para cada `eventId` mostrado en «Mis entradas», la interfaz consulta `GET /api/events/{id}` y usa su `startsAt` como fecha vigente; puede reutilizar el resultado para varias órdenes del mismo evento. Si Catalog devuelve `404` para un evento no visible públicamente o la consulta falla, muestra que la fecha vigente no está disponible y conserva la fecha histórica etiquetada como tal. Nunca deduce la vigente de `eventStartsAt` ni espera un campo `currentEventStartsAt` en la orden.

El área de gestión consulta `GET /api/management/events` y `GET /api/venues`. Un formulario único crea/edita borradores y sus offerings; tras publicar sólo quedan acciones admitidas para el estado y rol. El reporte usa `GET /api/reports/events/{eventId}` y permite capacidad/ocupación nulas cuando Inventory no responde. `ADMIN` edita sedes, gestiona estados y consulta auditoría. La cancelación con reintegros usa `POST /api/events/{eventId}/cancellation` de Purchase; el comando de Catalog por sí solo no cubre el flujo completo. La decisión de devolución se hace por ID informado, sin vista previa ni bandeja. Se muestra el resultado de `POST /api/refunds/{refundId}/decision` y, si queda en progreso, su estado devuelto; una consulta administrativa adicional requiere una futura API y queda fuera de este cambio.

### Integración de F11

La interfaz ofrece email y postergación con los contratos F11 ya implementados. El cliente usa `GET/PUT /api/notifications/me/email`; el organizador dueño o administrador usa `PATCH /api/management/events/{id}/schedule` con `expectedScheduleRevision` e `Idempotency-Key`. El formulario compara instantes para mostrar una validación previa: nueva fecha posterior tanto a la vigente como a ahora + 72 horas; Catalog decide en última instancia. Un `409` obliga a recargar revisión y fecha antes de reintentar. El frontend no consulta Mailpit: Mailpit sirve para verificar correos en la demo, no como buzón de producto.

## Risks / Trade-offs

- **JWT estático expira en una hora** → mostrar expiración, bloquear envío con token vencido y documentar regeneración local antes de la demo.
- **JWT embebido en el bundle es visible al navegador** → limitar la ejecución a loopback/datos de prueba, excluir el archivo de Git y no reutilizar claves ni tokens fuera del demo.
- **Cupo cambia entre consulta y compra** → refrescar disponibilidad tras `409`; no simular una reserva en la UI.
- **Respuestas de compra inciertas** → conservar intención y clave de idempotencia para evitar compras duplicadas.
- **No hay listado administrativo de devoluciones** → decisión por ID conocido y documentación de esa limitación; no inventar datos en una bandeja.
- **La fecha vigente no forma parte de la orden** → consultar el detalle público de Catalog y advertir cuando no esté disponible, por ejemplo para un evento que ya no sea visible públicamente.

## Migration Plan

Agregar el frontend sin migrar datos ni cambiar servicios. Implementar también las pantallas F11 contra las rutas vigentes y verificar sus contratos por Gateway. La reversión consiste en dejar de servir el frontend; el backend y sus datos no cambian.
