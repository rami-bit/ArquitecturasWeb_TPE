
## Guía rápida: ejecutar Testcontainers en Windows con Docker Desktop

El host tiene JDK 17 y el proyecto compila para Java 19. Los builds normales de Docker Compose ejecutan Maven dentro del Dockerfile sin acceso a /var/run/docker.sock; por eso Testcontainers detectaba que no había daemon y omitía las pruebas. La solución fue ejecutar Maven en un contenedor Java 21 separado, montando tanto el repositorio como el socket de Docker Desktop.

Desde PowerShell, en la raíz del repositorio, con Docker Desktop activo:

    docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "${PWD}:/workspace" -w /workspace -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal maven:3.9.9-eclipse-temurin-21-alpine mvn -q verify

Para ejecutar solo Inventory, por ejemplo:

    docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "${PWD}:/workspace" -w /workspace -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal maven:3.9.9-eclipse-temurin-21-alpine mvn -q -pl services/inventory-service -am test

El montaje del socket permite que Testcontainers cree PostgreSQL y Ryuk. TESTCONTAINERS_HOST_OVERRIDE hace que el proceso Maven dentro del contenedor alcance los puertos publicados por Docker Desktop mediante host.docker.internal. Se comprobó primero que el socket era visible con:

    docker run --rm -v /var/run/docker.sock:/var/run/docker.sock maven:3.9.9-eclipse-temurin-21-alpine sh -c 'ls -l /var/run/docker.sock'

Confirmar que las pruebas corrieron, en lugar de quedar omitidas, revisando el código de salida y los XML en services/<modulo>/target/surefire-reports/TEST-*.xml: tests > 0, failures=0, errors=0 y skipped=0 para cada clase Testcontainers. En esta sesión Inventory, Ticket y Payment Mock cumplieron esa condición. Purchase aún mostró skipped en sus clases Testcontainers durante una corrida, por lo que requiere repetir y diagnosticar ese módulo. Docker Desktop dio una respuesta transitoria HTTP 400 en una detección, pero un reintento posterior encontró el socket y arrancó PostgreSQL.

## Actualización tras merge — 2026-11-11

### Estado comprobado

- El merge `86557d9` había combinado dos implementaciones incompatibles de Purchase: controladores duplicados `/api/purchases` y `/api/orders`, consultas viejas sobre `ticket_payload`/estado `PAID` frente a `order_tickets`/`CONFIRMED`, y Gateway/Postman apuntando al contrato anterior. Se conservaron la saga y el contrato nuevos `/api/orders`; se eliminaron los cuatro controladores raíz legados y su test de compra basado en el esquema viejo. Se migraron las pruebas F7.
- Purchase: se creó la tabla `order_tickets` que faltaba; Testcontainers quedó alineado por BOM 1.21.4 (antes se mezclaban artefactos 1.19.7 y 1.21.4 y las pruebas se omitían). Devolución envía el ID de sector de Inventory y el importe parcial a Payment.
- Payment Mock: se retiró el store legado incompatible, se alineó el esquema con la saga, se implementaron reintegros parciales idempotentes por cobro con límite acumulado y se conservó `/refund` para compensación total. Se agregó Actuator porque el healthcheck de Compose daba 404.
- Inventory: se completó el SELECT de `findReservationItems` (el mapper leía columnas ausentes y todas las reservas devolvían 500); la liberación de unidad vendida ahora filtra por ID de sector de Inventory.
- Gateway, README, OpenAPI consolidado/local y colección Postman se migraron a `/api/orders`. La colección mantiene 41 requests. Los Dockerfiles de Purchase y Payment ahora copian POM padre e hijo correctamente; los seis Dockerfiles empaquetan con `-DskipTests` porque la verificación real se hace por separado.
- `mvn -q verify` del reactor completo pasó en contenedor Maven Java 21 con socket Docker. XML: Inventory 73/73, Ticket 17/17, Payment 24/24, Purchase 33/33, todos sin fallos ni omisiones. `openspec validate fulfill-refunds-and-cancellations --strict` y `git diff --check` pasaron antes de los últimos ajustes de Dockerfile/Actuator.
- `docker compose -p ticketya_reconciled up --build -d --wait` terminó con 11 contenedores saludables sobre volúmenes nuevos, sin tocar los volúmenes previos. OpenSpec `fulfill-refunds-and-cancellations` quedó en 12/13 tareas: falta 4.1, la verificación E2E por Gateway. No archivar aún.

### Blocker actual

- La colección Postman local (`postman collection run postman/ticketya.postman_collection.json --env-var baseUrl=http://localhost:8080 --no-report-events`) falló: la primera compra a `/api/orders` devuelve HTTP 400. Las solicitudes siguientes usan IDs nulos y generan fallos derivados; no se diagnosticó todavía el JSON exacto de esa primera petición. Resolver esa causa antes de interpretar los demás fallos. El stack Compose `ticketya_reconciled` quedó levantado y saludable.
- El merge dejó una limitación de datos: los volúmenes PostgreSQL creados con el esquema viejo `/api/purchases` no son migrados por `CREATE TABLE IF NOT EXISTS`. Usar un proyecto Compose nuevo/volúmenes limpios o diseñar una migración explícita. No borrar volúmenes anteriores sin necesidad.
- Se eliminó `PurchaseFlowIntegrationTest` legado (6 tests del esquema viejo). Los 21 contratos nuevos de órdenes cubren los flujos básicos, pero quedan por automatizar compra concurrente integral, expiración de hold, fallos downstream y reintento de QR; TODO.md ya los señala como pendientes de E1.

### Próximos pasos

1. Inspeccionar la primera petición de compra de la colección Postman y su respuesta 400. Comparar con `PurchaseOrderController`/`PurchaseService` y `docs/openapi.yaml`; corregir payload o implementación, luego repetir la colección sobre un nuevo evento o un proyecto Compose limpio para evitar datos de la corrida fallida.
2. Comprobar que compra aprobada/rechazada, reintento idempotente, devoluciones individuales, cancelación masiva y bloqueo de compra posterior pasen por Gateway; verificar aserciones y filas de Purchase/Payment si hace falta.
3. Repetir `git diff --check`, `openspec validate fulfill-refunds-and-cancellations --strict` y, por los cambios de POM/Dockerfile posteriores al reactor, al menos el build/verificación afectada. Marcar tarea 4.1 solo cuando Postman pase; después revisar Definition of Done de OpenSpec.md antes de archivar.
4. Actualizar TODO.md con evidencia final; seguir con US-E1-05 y la cobertura de resiliencia/concurrencia pendiente. Conservar el objetivo didáctico y la ejecución local/Compose.

### Comando Testcontainers que funcionó

Desde PowerShell, con Docker Desktop activo: `docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "${PWD}:/workspace" -w /workspace -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal maven:3.9.9-eclipse-temurin-21-alpine mvn -q verify`. Revisar los XML Surefire: código 0 no garantiza que Testcontainers no haya omitido clases. Purchase necesitó el BOM 1.21.4 completo para Docker Engine 29.7.2.
