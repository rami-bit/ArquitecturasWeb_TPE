# Spec Delta

## Purpose

Permite alternar identidades de prueba en la aplicación web de escritorio y ejecutar las operaciones protegidas con el JWT correspondiente, sin introducir un proveedor de identidad de producto.

## ADDED Requirements

### Requirement: Seleccionar un perfil de demostración

La interfaz SHALL ofrecer perfiles locales `CLIENT`, `ORGANIZER` y `ADMIN` con identidades fijas de demostración. Al seleccionar un perfil SHALL obtener un JWT vigente del IdP de prueba local, mostrar el perfil activo y habilitar sólo su navegación correspondiente. Trazabilidad: PDF-NF-SECURITY y PDF-INTERFAZ-GRAFICA.

#### Scenario: Elegir cliente
- **WHEN** el usuario selecciona el perfil de cliente y el IdP de prueba responde con un token vigente
- **THEN** la interfaz muestra el área de compra y envía ese JWT en las operaciones protegidas del cliente

#### Scenario: Cambiar de identidad
- **GIVEN** hay un perfil activo con datos de una operación en pantalla
- **WHEN** el usuario elige otro perfil
- **THEN** la interfaz descarta los datos y selecciones del perfil anterior y carga la navegación del nuevo perfil

### Requirement: Cerrar la sesión simulada

La interfaz SHALL permitir cerrar el perfil activo y volver al estado anónimo sin conservar su JWT como identidad seleccionada.

#### Scenario: Salir del perfil
- **WHEN** el usuario pulsa Salir
- **THEN** la interfaz deja de enviar Authorization, oculta las áreas protegidas y conserva disponible la búsqueda pública

### Requirement: Manejar tokens vencidos o inválidos

La interfaz SHALL renovar el JWT del perfil activo antes de su vencimiento, identificar tokens vencidos y tratar una respuesta `401` como sesión no válida, sin repetir automáticamente comandos de escritura con otro token.

#### Scenario: Renovación mientras la página sigue abierta
- **GIVEN** hay un perfil activo y el JWT se acerca a su vencimiento
- **WHEN** el IdP de prueba entrega un nuevo JWT para la misma identidad y rol
- **THEN** la interfaz usa el nuevo JWT en solicitudes posteriores sin cambiar de perfil ni reintentar escrituras en curso

#### Scenario: Token vencido antes de una operación
- **WHEN** el usuario intenta una operación protegida con un JWT cuya expiración ya pasó
- **THEN** la interfaz bloquea la operación e indica que debe seleccionar nuevamente el perfil

#### Scenario: Gateway rechaza el token
- **WHEN** el Gateway responde `401` a una operación protegida
- **THEN** la interfaz informa que la sesión de prueba dejó de ser válida y permite seleccionar otro perfil

### Requirement: Respetar la autorización del Gateway

La interfaz SHALL enviar solicitudes de API pública sólo por el Gateway. Sus menús por rol SHALL reflejar los permisos conocidos, pero una respuesta `403` SHALL mostrarse como denegación y nunca se intentará suplirla con encabezados de identidad arbitrarios.

#### Scenario: Operación no permitida para el perfil
- **WHEN** el Gateway responde `403` a una solicitud con JWT válido
- **THEN** la interfaz informa que el perfil no tiene permiso y no reintenta la operación con otra identidad

#### Scenario: Búsqueda anónima
- **WHEN** no hay perfil seleccionado y el usuario busca eventos o consulta disponibilidad
- **THEN** la interfaz usa las rutas públicas sin encabezado Authorization
