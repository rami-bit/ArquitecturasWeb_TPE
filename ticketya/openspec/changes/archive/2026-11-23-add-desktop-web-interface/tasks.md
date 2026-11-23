# Tasks

## 1. Base de escritorio y sesión de demostración

- [x] 1.1 Crear `frontend/` con React, TypeScript, Vite y estructura de pantallas de escritorio; verificar instalación, arranque local y build de producción sin rutas API directas a servicios internos.
- [x] 1.2 Configurar proxy local `/api` hacia el Gateway y un cliente HTTP que maneje JWT activo, `application/problem+json`, errores de red y estados `401`/`403`; verificar con pruebas del cliente HTTP y una petición pública/protegida por Gateway.
- [x] 1.3 Definir identidades fijas para los UUID de Postman y emisión de JWT de prueba; verificar que los tres perfiles reciben tokens vigentes, el Gateway los acepta y `git status` no muestra JWT ni claves.
- [x] 1.4 Implementar selector de cliente/organizador/administrador, Salir y limpieza de estado al cambiar perfil; verificar en prueba de interfaz que el JWT enviado corresponde al perfil activo y que no se envía tras Salir.
- [x] 1.5 Manejar `exp` local y rechazo `401` sin reintento de comandos de escritura; verificar token vencido y token de rol incorrecto por Gateway, y documentar arranque, proxy y renovación de tokens en `frontend/README.md`.

## 2. Descubrimiento y selección del cliente

- [x] 2.1 Implementar búsqueda de eventos con filtros, orden y paginación de `GET /api/events`; verificar resultados, página vacía y parámetro inválido con pruebas de interfaz y contrato por Gateway.
- [x] 2.2 Implementar detalle que combine Catalog e Inventory, con sectores generales y grilla de asientos numerados usando IDs de API; verificar selección de ambos tipos, precios de Catalog y cambios de disponibilidad sin afirmar reserva.
- [x] 2.3 Documentar el recorrido de búsqueda/selección y la diferencia entre precio y disponibilidad en `frontend/README.md`; verificar que los pasos coinciden con la UI y con `docs/openapi.yaml`.

## 3. Fila y compra

- [x] 3.1 Implementar entrada, consulta periódica y estado de fila virtual para `highDemand`; verificar `WAITING`, `ADMITTED`, expiración y evento común, con prueba integrada por Gateway.
- [x] 3.2 Implementar checkout de demostración con ítems válidos, tokens de Payment Mock, `Idempotency-Key` persistida por intención y `X-Admission-Token` cuando corresponda; verificar compra general y numerada confirmadas por Gateway con una única orden por intención.
- [x] 3.3 Implementar estados `REJECTED`, `PAYMENT_PENDING`, `PAID_PENDING_TICKETS`, timeout y `409` de cupo, con consulta/reconciliación y reintento de misma clave/cuerpo; verificar que un reintento incierto no duplica cobro ni tickets y que un conflicto refresca disponibilidad.
- [x] 3.4 Documentar la simulación de pago, fila, reintentos y límites en `frontend/README.md`; verificar el flujo documentado con datos nuevos.

## 4. Entradas y preferencias del cliente

- [x] 4.1 Implementar listado/detalle de órdenes como fuente de «Mis entradas», mostrando QR sólo de tickets emitidos y estados sin tickets; verificar titular, orden pendiente/rechazada y aislamiento al cambiar de perfil.
- [x] 4.2 Implementar solicitud y seguimiento de devolución propia; verificar éxito y rechazo por ventana de 72 horas mediante Gateway, sin cambiar el ticket local ante rechazo.
- [x] 4.3 Implementar preferencias y recomendaciones del cliente; verificar guardar/leer preferencias, resultado vacío y acceso a detalle con disponibilidad consultada nuevamente.
- [x] 4.4 Documentar «Mis entradas», devoluciones y preferencias en `frontend/README.md`; verificar que no se afirma un listado independiente de tickets ni cupo garantizado por recomendaciones.

## 5. Gestión del organizador

- [x] 5.1 Implementar listado/detalle de eventos propios y formulario de borrador con lugar, participantes, offerings, precios y alta demanda; verificar creación/edición de un evento propio y denegación de uno ajeno por Gateway.
- [x] 5.2 Implementar publicación y bloqueo visual de edición fuera de `DRAFT`; verificar publicación válida, validación incompleta y permanencia del snapshot tras editar una sede canónica.
- [x] 5.3 Implementar reporte por evento y manejo de `capacityAvailable: false`; verificar métricas por sector/totales, advertencia de capacidad y rechazo de reporte ajeno.
- [x] 5.4 Documentar el recorrido del organizador y los estados editables en `frontend/README.md`; verificar pasos contra Catalog y reportes reales.

## 6. Operaciones de administrador

- [x] 6.1 Implementar listado/edición de sedes y sectores sólo para `ADMIN`; corregir `GET/PUT /api/venues` de Catalog para cargar sectores/asientos antes de cerrar la transacción; verificar con PostgreSQL real, sede general y numerada por Gateway, y que `ORGANIZER` no obtiene acción ni permiso de escritura.
- [x] 6.2 Implementar suspensión, reanudación y cancelación con confirmación; verificar que la cancelación usa Purchase, inicia reintegros y que una transición inválida refresca el estado.
- [x] 6.3 Implementar decisión de devolución por ID conocido y mostrar el resultado del comando; verificar aprobación/rechazo y error por ID inválido, sin inventar bandeja ni vista previa administrativa.
- [x] 6.4 Implementar búsqueda de auditoría y detalle por correlación; verificar filtros y que no se muestren JWT, secretos ni credenciales.
- [x] 6.5 Documentar operaciones administrativas y límite de consulta de devoluciones en `frontend/README.md`; verificar que los pasos corresponden a rutas públicas existentes.

## 7. Funciones F11

- [x] 7.1 Implementar el formulario de email propio para `CLIENT` con `GET/PUT /api/notifications/me/email`; verificar lectura, guardado, ausencia de contacto y rechazo de acceso ajeno por Gateway.
- [x] 7.2 En «Mis entradas», conservar `eventStartsAt` histórico de la orden y consultar `GET /api/events/{id}` por `eventId` para mostrar la fecha vigente; verificar postergación, varias órdenes del mismo evento y advertencia ante `404` o fallo de Catalog sin presentar la fecha histórica como vigente.
- [x] 7.3 Implementar postergación para dueño/ADMIN con revisión, clave idempotente y validación previa de fecha; verificar éxito, adelanto, igualdad a 72 horas, `409` por revisión y rol ajeno por Gateway.
- [x] 7.4 Documentar las pantallas F11, la diferencia entre fecha histórica y vigente y la verificación de correos en Mailpit en `frontend/README.md`; verificar el recorrido completo por Gateway con el stack saludable.

## 8. Integración final

- [x] 8.1 Ejecutar pruebas y build del frontend tras la última edición, smoke automatizado de navegador por cliente, organizador y administrador y recorridos dirigidos por Gateway con un stack Compose aislado; registrar cantidad, fallos/omisiones y versiones en `docs/verification.md`.
- [x] 8.2 Por indicación del usuario, cerrar con validación dirigida del código alterado: `npm test`, `npm run build`, smoke de navegador, integración PostgreSQL de `GET/PUT /api/venues`, Compose aislado y selección de flujos Postman por Gateway; ejecutar `openspec validate add-desktop-web-interface --strict` y `git diff --check`, comprobar XML Testcontainers y salud del stack, y registrar resultados reales en `docs/verification.md`, `NEXT.md` y `TODO.md`. No ejecutar regresión Maven del reactor ni colección Postman completa.

## 9. IdP local y renovación de JWT

- [x] 9.1 Iniciar el IdP de prueba con Compose, persistir su clave y hacer que el Gateway consulte su JWKS por la red interna; verificar imagen y salud.
- [x] 9.2 Obtener JWT por perfil en tiempo de uso y renovarlo antes de vencer; verificar cambio de perfil, Salir, expiración y ausencia de reintento de escrituras.
- [x] 9.3 Actualizar instrucciones de arranque y verificar el flujo protegido por Vite y Gateway con el IdP de Compose.
