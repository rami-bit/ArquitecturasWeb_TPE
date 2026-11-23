# desktop-event-operations Specification

## Purpose

Permite gestionar eventos y consultar su resultado comercial desde una aplicación web de escritorio, mostrando al organizador sus propios recursos y al administrador las operaciones globales habilitadas por TicketYa.

## Requirements

### Requirement: Listar eventos gestionables según el perfil

La interfaz SHALL consultar la vista de gestión de Catalog con el JWT activo y mostrar los eventos y acciones permitidos por el rol. SHALL respetar la limitación de ownership aplicada por la API. Trazabilidad: PDF-F3 y PDF-NF-SECURITY.

#### Scenario: Organizador consulta sus eventos
- **WHEN** el perfil `ORGANIZER` abre Mis eventos
- **THEN** la interfaz muestra la lista devuelta por Catalog y no ofrece acciones globales de administrador

#### Scenario: Administrador consulta todos los eventos
- **WHEN** el perfil `ADMIN` abre Gestión de eventos
- **THEN** la interfaz muestra los eventos devueltos por Catalog con sus estados y operaciones aplicables

### Requirement: Crear y publicar borradores

La interfaz SHALL permitir crear y editar borradores con lugar, datos, participantes, sectores habilitados, precios y marca de alta demanda; SHALL solicitar confirmación explícita antes de publicar. Fuera de `DRAFT` SHALL dejar de ofrecer edición de estos campos. Trazabilidad: PDF-F1-F3.

#### Scenario: Organizador prepara un borrador propio
- **WHEN** un organizador selecciona un lugar y guarda un borrador válido
- **THEN** la interfaz muestra el evento `DRAFT` confirmado por Catalog y sus ofertas por sector

#### Scenario: Publicar borrador completo
- **WHEN** el usuario autorizado confirma Publicar sobre un borrador completo
- **THEN** la interfaz invoca el comando de publicación y muestra el estado devuelto

#### Scenario: Borrador incompleto
- **WHEN** Catalog rechaza la publicación por datos faltantes o fecha inválida
- **THEN** la interfaz conserva el formulario y muestra los errores para corregirlos

### Requirement: Mostrar reportes por evento

La interfaz SHALL mostrar vendidos, devueltos, ocupación y recaudación por sector y total del reporte de Purchase. SHALL mostrar las advertencias si la capacidad no está disponible. Trazabilidad: PDF-F12.

#### Scenario: Reporte completo
- **WHEN** un organizador abre el reporte de su evento
- **THEN** la interfaz muestra métricas por sector y totales de la respuesta

#### Scenario: Reporte parcial
- **WHEN** el reporte indica `capacityAvailable: false`
- **THEN** la interfaz muestra ventas y montos disponibles, marca capacidad/ocupación como no disponibles y presenta la advertencia

### Requirement: Gestionar sedes como administrador

La interfaz SHALL ofrecer creación y edición de sedes y sectores sólo en el área `ADMIN`, usando las rutas públicas de Catalog. Trazabilidad: PDF-F2 y PDF-NF-SECURITY.

#### Scenario: Administrador crea una sede
- **WHEN** un administrador guarda una sede con sectores numerados o generales válidos
- **THEN** la interfaz muestra la sede confirmada y la deja disponible para seleccionar en borradores

#### Scenario: Organizador necesita una sede
- **WHEN** un organizador crea un borrador
- **THEN** la interfaz le permite seleccionar una sede existente sin ofrecer creación o edición de sedes

### Requirement: Gestionar estados administrativos

La interfaz SHALL ofrecer a `ADMIN` suspender, reanudar y cancelar según el estado del evento, con confirmación previa para comandos de impacto. Para cancelar SHALL invocar el flujo público de Purchase que inicia los reintegros, no sólo el cambio de estado de Catalog. Trazabilidad: PDF-F3 y PDF-F7.

#### Scenario: Suspender o reanudar
- **WHEN** un administrador confirma una transición admitida
- **THEN** la interfaz invoca el comando correspondiente y muestra el estado devuelto

#### Scenario: Cancelar con reintegros
- **WHEN** un administrador confirma la cancelación de un evento publicado o suspendido
- **THEN** la interfaz inicia la cancelación por Purchase y muestra el estado/progreso que devuelve la API

#### Scenario: Transición rechazada
- **WHEN** la API rechaza un cambio por estado o permisos
- **THEN** la interfaz informa el motivo y vuelve a consultar el estado del evento

### Requirement: Decidir una devolución conocida

La interfaz SHALL permitir a `ADMIN` aprobar/rechazar una solicitud de devolución mediante su ID conocido y mostrar el resultado del comando. No SHALL presentar una bandeja de pendientes ni una vista previa administrativa mientras la API no publique sus consultas. Trazabilidad: PDF-F7.

#### Scenario: Solicitud encontrada
- **WHEN** el administrador informa un ID de devolución conocido y confirma una decisión
- **THEN** la interfaz envía la decisión por la ruta pública de Purchase y muestra el estado devuelto

#### Scenario: ID inexistente o decisión inválida
- **WHEN** Purchase rechaza la decisión por ID inexistente o estado inválido
- **THEN** la interfaz informa el error sin afirmar que la devolución fue resuelta

### Requirement: Consultar auditoría administrativa

La interfaz SHALL permitir a `ADMIN` consultar la bitácora pública por sus filtros disponibles y abrir el detalle de una traza sin mostrar credenciales o tokens. Trazabilidad: PDF-NF-TRACEABILITY.

#### Scenario: Buscar operaciones críticas
- **WHEN** el administrador aplica filtros de auditoría admitidos
- **THEN** la interfaz muestra las entradas devueltas y permite seguir una operación por su identificador de correlación

### Requirement: Postergar eventos publicados

La interfaz SHALL ofrecer a dueño `ORGANIZER` o `ADMIN` postergar un evento `PUBLISHED` sólo hacia una fecha posterior a la vigente y al momento de gestión más 72 horas. SHALL enviar la revisión y clave idempotente que exige Catalog, y mostrar el resultado confirmado. Trazabilidad: PDF-F11.

#### Scenario: Postergación confirmada
- **WHEN** el usuario autorizado confirma una nueva fecha válida para un evento publicado
- **THEN** la interfaz envía el cambio, muestra la fecha vigente y actualiza la revisión recibida

#### Scenario: Revisión obsoleta o fecha inválida
- **WHEN** Catalog rechaza la postergación por conflicto, plazo o adelanto
- **THEN** la interfaz conserva la fecha original, informa el motivo y vuelve a consultar el evento
