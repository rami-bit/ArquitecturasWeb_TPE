# Workflow OpenSpec para TicketYa

Este documento define cómo avanzar desde el enunciado
`TPE-ArqWeb-TicketYa-2026.pdf` hasta una implementación incremental, trazable y
validada de TicketYa. El objetivo es que las decisiones de arquitectura y las
user stories se acuerden antes de escribir código, sin convertir la
especificación en un diseño rígido imposible de refinar.

## 1. Fuentes de verdad

Se utilizarán tres niveles de documentación:

1. **Enunciado del TPE:** define alcance obligatorio, entregas y restricciones.
2. **OpenSpec:** describe capacidades, requisitos, escenarios, diseño y trabajo
   pendiente.
3. **Código, contratos OpenAPI y pruebas:** demuestran que una especificación
   archivada está implementada.

Si aparecen contradicciones, el PDF prevalece sobre una decisión interna. Una
decisión nueva o un cambio de alcance debe registrarse en un cambio OpenSpec
antes de modificar la implementación.

Cada requisito o user story debe conservar una referencia al punto del PDF que
la origina, por ejemplo `PDF-F4`, `PDF-NF-CONCURRENCY` o `PDF-E1-TESTING`.

## 2. Modelo de OpenSpec utilizado

El proyecto usa el schema `spec-driven`:

```text
proposal -> specs -> design -> tasks -> implementation -> archive
```

- `proposal.md`: explica el problema, alcance, valor y capacidades afectadas.
- `specs/<capability>/spec.md`: contiene requisitos verificables y escenarios.
- `design.md`: documenta arquitectura, flujos, datos y decisiones técnicas.
- `tasks.md`: divide el cambio en unidades pequeñas, ordenadas y comprobables.
- `openspec/specs/`: contiene las especificaciones vigentes luego de archivar.

Un directorio bajo `openspec/changes/` representa trabajo propuesto o en curso;
no es todavía una capacidad estable del producto.

## 3. Modos de trabajo

### 3.1 Explore: investigar sin implementar

Usar `$openspec-explore` cuando el problema todavía es ambiguo o se necesita
comparar alternativas. Este modo sirve para leer el repositorio, revisar el PDF,
dibujar flujos, detectar riesgos y discutir límites de servicios.

Ejemplos:

```text
$openspec-explore definir límites entre catálogo, inventario y compras
$openspec-explore comparar bloqueo pesimista con actualización atómica
$openspec-explore revisar qué ocurre si el pago se aprueba y vence la reserva
```

Resultado esperado: decisiones y preguntas abiertas. No se implementa código.

### 3.2 Propose: convertir una idea acordada en un cambio

Usar `$openspec-propose` cuando el objetivo y el alcance ya son suficientemente
claros. Genera la propuesta, especificaciones, diseño y tareas del cambio.

```text
$openspec-propose crear la reserva temporal de asientos numerados para PDF-F4 y
PDF-F5, incluyendo expiración y prueba de concurrencia
```

Antes de aceptar la propuesta se revisa que los cuatro artefactos sean
coherentes y que no haya decisiones importantes escondidas solamente en las
tareas.

### 3.3 Update: refinar planificación existente

Usar `$openspec-update-change` si cambian requisitos, escenarios, arquitectura
o tareas antes o durante la implementación. Este modo modifica los artefactos
de planificación, pero no el código.

```text
$openspec-update-change reserve-numbered-seats: la reserva dura 10 minutos y
debe poder recuperarse de una respuesta de pago perdida
```

Un cambio significativo nunca se resuelve editando únicamente el código: se
actualizan primero los artefactos afectados y luego se continúa la aplicación.

### 3.4 Apply: implementar un cambio aprobado

Usar `$openspec-apply-change` cuando `proposal`, `specs`, `design` y `tasks`
están revisados. Se implementan tareas en orden, incluyendo migraciones,
contratos OpenAPI, pruebas y documentación.

```text
$openspec-apply-change reserve-numbered-seats
```

Si la implementación revela un requisito nuevo o invalida el diseño, se pausa
`apply`, se refina el cambio con `update-change` y después se reanuda.

### 3.5 Sync: publicar requisitos sin cerrar el cambio

Usar `$openspec-sync-specs` sólo si hace falta trasladar las especificaciones
delta a las especificaciones principales antes de archivar. No reemplaza la
revisión ni cierra el cambio.

### 3.6 Archive: cerrar y consolidar

Usar `$openspec-archive-change` cuando todas las tareas y pruebas estén
completas. El archivado consolida las especificaciones del cambio en
`openspec/specs/` y mueve el cambio al historial.

```text
$openspec-archive-change reserve-numbered-seats
```

No se archiva una historia parcialmente implementada ni con escenarios de
aceptación fallando.

## 4. Flujo general del proyecto

```text
+--------------------+
| PDF del TPE        |
+---------+----------+
          |
          v
+--------------------+     revisar alcance, actores y restricciones
| Descubrimiento     |     $openspec-explore
+---------+----------+
          |
          v
+--------------------+     servicios, ownership de datos, APIs y flujos
| Arquitectura base  |     $openspec-propose
+---------+----------+
          |
          v
+--------------------+     épicas y user stories priorizadas
| Backlog trazable   |     criterios y dependencias
+---------+----------+
          |
          v
+--------------------+     propose -> update -> apply -> archive
| Historia vertical |<------------------------------------------+
+---------+----------+                                           |
          |                                                      |
          v                                                      |
+--------------------+     tests + OpenAPI + revisión            |
| Incremento válido  +-------------------------------------------+
+--------------------+          siguiente historia
```

### Fase A: transformar el PDF en backlog

1. Identificar actores: cliente, organizador, administrador y personal de
   acceso.
2. Convertir cada punto funcional en una o más capacidades.
3. Registrar requisitos no funcionales como restricciones verificables, no
   como notas generales.
4. Separar alcance de Entrega 1 y Entrega 2.
5. Relacionar cada user story con su referencia del PDF.

El backlog inicial cubre todo el sistema, pero sólo se detalla hasta nivel
implementable lo que entrará en las próximas historias.

### Fase B: acordar la arquitectura general

Crear un cambio inicial `establish-ticketya-architecture` que defina, al menos:

- límites y responsabilidades de microservicios;
- propiedad de datos y referencias entre servicios;
- API Gateway y contratos externos;
- flujo de compra y compensaciones;
- mecanismo contra la doble venta entre múltiples instancias;
- idempotencia de compra y pago;
- estrategia inicial de logs y correlation ID;
- arquitectura objetivo de Entrega 2: JWT, mensajería, MongoDB, Redis, cache,
  resiliencia, observabilidad y contenedores;
- diagramas de módulos, secuencia y datos exigidos por el PDF.

Este cambio puede crear documentación y esqueletos mínimos, pero no debe
implementar toda la funcionalidad del sistema. Su función es fijar las reglas
que usarán las historias posteriores.

### Fase C: refinar por épica y user story

Cada historia debe ser un incremento vertical observable a través del Gateway.
Debe incluir regla de negocio, persistencia, API y pruebas necesarias. Evitar
historias horizontales como “crear todos los controllers” o “crear todas las
bases”. Esas actividades pueden ser tareas internas de una historia vertical.

Orden inicial sugerido para Entrega 1:

1. Crear lugares con sectores numerados y no numerados (`PDF-F2`).
2. Crear y editar eventos en borrador con precios (`PDF-F1` a `PDF-F3`).
3. Publicar eventos y consultarlos desde el catálogo (`PDF-F3`).
4. Buscar y ordenar eventos por múltiples criterios (`PDF-F9`).
5. Reservar temporalmente asientos numerados (`PDF-F4`, `PDF-F5`).
6. Reservar cupos en sectores no numerados (`PDF-F4`, `PDF-F5`).
7. Comprar con pago simulado e idempotencia (`PDF-F4`, `PDF-F5`).
8. Emitir entradas con QR único (`PDF-F4`).
9. Solicitar y resolver devoluciones (`PDF-F7`).
10. Cancelar un evento y reintegrar compras (`PDF-F7`).

Las historias 5, 6 y 7 requieren especial atención porque concentran los
riesgos de concurrencia y consistencia.

### Fase D: implementar y cerrar cada historia

Para cada historia:

1. Explorar dudas y dependencias.
2. Crear un cambio con nombre en `kebab-case`.
3. Revisar propuesta, escenarios, diseño y tareas.
4. Validar los artefactos antes de implementar.
5. Aplicar las tareas y mantenerlas actualizadas.
6. Ejecutar pruebas unitarias, integración y contrato correspondientes.
7. Probar los endpoints a través del API Gateway y actualizar la colección de
   Postman.
8. Archivar el cambio sólo cuando cumple su Definition of Done.
9. Elegir la siguiente historia a partir de las especificaciones vigentes.

## 5. Plantilla conceptual de una user story

La frase de user story aporta contexto, pero los escenarios son el contrato
verificable.

```markdown
## US-E1-05 - Reservar un asiento numerado

Como cliente
quiero reservar temporalmente un asiento disponible
para poder completar el pago sin que otro cliente lo compre.

Trazabilidad: PDF-F4, PDF-F5, PDF-NF-CONCURRENCY

### Criterios de aceptación

- Dado un asiento disponible, cuando se solicita una reserva válida, entonces
  queda retenido durante 10 minutos.
- Dado un asiento retenido, cuando otro cliente intenta reservarlo, entonces
  recibe un conflicto y no se crea una segunda reserva.
- Dada una reserva vencida, cuando se procesa su expiración, entonces el asiento
  vuelve a estar disponible.
- Dadas solicitudes concurrentes desde varias instancias, sólo una puede
  reservar el mismo asiento.
```

En la especificación OpenSpec estos criterios deben expresarse como requisitos
normativos y escenarios `Given/When/Then`. Los ejemplos concretos de JSON y
códigos HTTP deben coincidir con el contrato OpenAPI.

## 6. Definition of Ready

Una historia está lista para `apply` cuando:

- tiene objetivo, alcance y exclusiones explícitas;
- referencia requisitos del PDF;
- identifica actores y permisos, incluso si Entrega 1 usa un header de usuario;
- sus escenarios incluyen camino feliz, errores y casos límite;
- tiene contratos de entrada, salida y códigos HTTP;
- define ownership y cambios de datos;
- resuelve concurrencia, idempotencia y compensaciones cuando corresponda;
- declara dependencias con otras capacidades;
- sus tareas incluyen pruebas y documentación;
- `openspec validate <change> --strict` no informa errores.

## 7. Definition of Done

Una user story o épica se considera terminada sólo cuando cumple todos los
criterios aplicables de esta sección. Cada cambio puede agregar criterios
específicos, pero no eliminar estos criterios generales.

### Funcionalidad

- Todos los escenarios OpenSpec y criterios de aceptación están implementados.
- Los caminos exitosos, errores y casos límite relevantes tienen comportamiento
  definido.
- No quedan tareas obligatorias del cambio sin completar.
- No se implementó funcionalidad fuera del alcance acordado.

### Arquitectura

- La implementación respeta el servicio dueño y el ownership de datos
  definidos.
- Ningún servicio accede directamente a la base de otro ni existen foreign keys
  entre bases de distintos servicios.
- Las comunicaciones entre componentes usan contratos explícitos.
- Las decisiones críticas no dependen exclusivamente de memoria o cache.
- Las operaciones que lo requieren son transaccionales o idempotentes.
- Toda desviación del blueprint fue registrada previamente en OpenSpec.

### Simplicidad

- La solución es la alternativa más simple que cumple el PDF y las specs.
- Toda herramienta o patrón adicional documenta el requisito o problema que
  resuelve, la alternativa más simple considerada, el costo agregado y cómo se
  verificará su utilidad.
- No se incorporaron componentes para anticipar escala o necesidades
  productivas fuera del alcance didáctico.
- No existen abstracciones, microservicios o infraestructura sin una
  responsabilidad actual verificable.

### API

- Los endpoints están documentados en OpenAPI.
- Requests, responses, parámetros, códigos HTTP y errores tienen ejemplos.
- Los errores usan un formato consistente.
- Las APIs externas se consumen únicamente mediante el API Gateway.
- La colección Postman demuestra los flujos principales.

### Testing

- `mvn verify` pasa desde la raíz y desde cada microservicio afectado.
- La lógica de negocio tiene pruebas unitarias.
- Persistencia y endpoints críticos tienen pruebas de integración contra la
  tecnología real utilizada.
- Se prueban permisos, estados inválidos y rollback cuando corresponda.
- Los flujos que afectan inventario, QR u otra exclusión mutua incluyen pruebas
  de concurrencia.
- No se exige performance productiva fuera de las pruebas de carga requeridas
  por el TPE.

### Contenedores

- Cada componente ejecutable desarrollado por el equipo tiene un `Dockerfile`
  propio y su imagen puede construirse individualmente.
- El `docker-compose.yml` central de la raíz incluye todos los componentes y
  dependencias del incremento actual.
- `docker compose up --build` levanta el sistema completo disponible.
- Las dependencias estándar usan imágenes oficiales salvo que una
  personalización esté justificada.
- La configuración usa variables de entorno y no contiene rutas personales ni
  secretos reales versionados.

### Operación y trazabilidad

- La configuración necesaria está documentada.
- Cada request registra endpoint, usuario cuando corresponde, estado y tiempo
  de procesamiento.
- El `correlationId` se genera o propaga entre los componentes afectados.
- Los logs no exponen credenciales, tokens, QR completos ni datos sensibles.

### Documentación y cierre

- El README documenta requisitos, configuración, build, ejecución y pruebas.
- Los diagramas afectados representan la implementación real.
- OpenAPI y la colección Postman están actualizados.
- `openspec validate <change> --strict` finaliza sin errores.
- El equipo revisó la implementación y confirmó los criterios de aceptación.
- El cambio está listo para archivarse en OpenSpec.

Como comprobación final, si el incremento no puede demostrarse desde un
checkout limpio siguiendo el README, construyendo sus imágenes, levantando
Compose, ejecutando las pruebas y recorriendo sus flujos principales desde el
Gateway, todavía no está Done.

## 8. Comandos de control

Los modos anteriores guían la colaboración con el agente. Estos comandos sirven
para inspeccionar y validar el estado local:

```powershell
# Cambios activos
openspec list --json

# Capacidades vigentes
openspec list --specs --json

# Estado de artefactos de un cambio
openspec status --change <change> --json

# Ver un cambio o una especificación
openspec show <name>

# Validación estricta antes de implementar y archivar
openspec validate <change> --strict
```

No se crean manualmente directorios dentro de `openspec/changes/`; los cambios
se inicializan mediante OpenSpec para conservar su metadata.

## 9. Convenciones

- Cambios y capacidades: `kebab-case`, por ejemplo
  `reserve-numbered-seats`.
- User stories: `US-E1-NN` y `US-E2-NN`.
- Referencias al PDF: `PDF-F<N>` para funcionales y `PDF-NF-<TOPIC>` para no
  funcionales.
- Commits y pull requests: incluir el nombre del cambio y las historias
  afectadas.
- APIs externas: accesibles únicamente mediante el Gateway.
- Datos: una base o esquema de propiedad exclusiva por microservicio y ninguna
  foreign key entre servicios.
- Disponibilidad de asientos: nunca se cachea como verdad autoritativa.
- Toda operación crítica usa una clave de idempotencia y un `correlationId`
  cuando corresponda.

## 10. Cadencia recomendada

Para evitar especificar todo el sistema con demasiado detalle demasiado pronto:

```text
Arquitectura general -> 1 o 2 historias refinadas -> implementación -> feedback
       ^                                                        |
       +-------------------- refinamiento ----------------------+
```

Mantener como máximo una o dos historias listas para implementación. El resto
permanece como backlog de alto nivel. Al cerrar cada historia, revisar si lo
aprendido modifica la arquitectura, las especificaciones vigentes o la
prioridad de las siguientes historias.

