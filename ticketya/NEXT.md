
## Actualización US-F7.3 — 2026-11-10

Se implementó cancelación masiva según TODO.md y el cambio OpenSpec fulfill-refunds-and-cancellations, con subagentes en Catalog/Inventory, Purchase y contratos/E2E. El cambio sigue activo y no archivado.

### Avance verificado

- Catalog permite cancelar solo a ADMIN eventos PUBLISHED/SUSPENDED y repetir la operación; Inventory bloquea nuevas reservas incluso si aún no había inventario y serializa cancelación, reserva y confirmación por evento.
- Purchase expone POST/GET /api/events/{eventId}/cancellation por Gateway, procesa páginas de 20 tickets con cursor persistido y progreso. Reutiliza devoluciones pendientes/aprobadas; una solicitud individual rechazada se conserva y genera una operación CANCELLATION distinta. Se corrigió la compra posterior a cancelación para responder 404.
- Se actualizaron OpenSpec, OpenAPI, Gateway, README, Postman y TODO.md. Validación OpenSpec estricta y git diff --check pasaron.
- Docker Compose levantó 11 servicios saludables. Postman completo pasó: 41 requests, 68 aserciones, 0 fallos. Incluyó 4 tickets vendidos, devolución previa rechazada, reintento, progreso y bloqueo de nueva compra.
- PostgreSQL de Purchase mostró 4 tickets distintos completados por evento E2E y 5 filas de devolución: la quinta conserva el rechazo previo. Testcontainers Inventory pasó en PostgreSQL real tras corregir el test nuevo y el ID público de sector: EventCancellationContractTest 3/3, ReservationContractTest 10/10. Ticket 9/9 y Payment Mock 2/2 también pasaron con Testcontainers.
- Se usó contenedor Maven Java 21 con socket Docker montado y TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal. Las pruebas Testcontainers de Purchase quedaron omitidas en la corrida por módulos: EventCancellationIntegrationTest 1, PurchaseFlowIntegrationTest 6 y RefundDecisionIntegrationTest 2.

### Pendiente y próximos pasos

1. Ejecutar específicamente las pruebas Testcontainers de Purchase con socket Docker y diagnosticar por qué Surefire las omitió; corregir fallos si aparecen. No marcar tareas de persistencia/recuperación Purchase ni 3.2 hasta verificarlas.
2. Repetir mvn verify del reactor completo en contenedor Java 21 con socket, Compose, Postman y validación OpenSpec si cambió código. La primera corrida completa encontró dos fallos de Inventory ya corregidos; la corrida posterior Inventory pasó.
3. En tasks.md del cambio se marcaron 2.3, 3.1 y 3.3 en este turno; 3.2 y 4.1 siguen abiertos. Marcar otros solo con evidencia. TODO.md ya registra US-F7.3 implementado y E2E verificado.
4. No archivar el cambio hasta cumplir tareas y Definition of Done de OpenSpec.md. El siguiente ítem del roadmap es US-E1-05.


### Guía rápida: ejecutar Testcontainers en Windows con Docker Desktop

El host tiene JDK 17 y el proyecto compila para Java 19. Los builds normales de Docker Compose ejecutan Maven dentro del Dockerfile sin acceso a /var/run/docker.sock; por eso Testcontainers detectaba que no había daemon y omitía las pruebas. La solución fue ejecutar Maven en un contenedor Java 21 separado, montando tanto el repositorio como el socket de Docker Desktop.

Desde PowerShell, en la raíz del repositorio, con Docker Desktop activo:

    docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "${PWD}:/workspace" -w /workspace -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal maven:3.9.9-eclipse-temurin-21-alpine mvn -q verify

Para ejecutar solo Inventory, por ejemplo:

    docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "${PWD}:/workspace" -w /workspace -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal maven:3.9.9-eclipse-temurin-21-alpine mvn -q -pl services/inventory-service -am test

El montaje del socket permite que Testcontainers cree PostgreSQL y Ryuk. TESTCONTAINERS_HOST_OVERRIDE hace que el proceso Maven dentro del contenedor alcance los puertos publicados por Docker Desktop mediante host.docker.internal. Se comprobó primero que el socket era visible con:

    docker run --rm -v /var/run/docker.sock:/var/run/docker.sock maven:3.9.9-eclipse-temurin-21-alpine sh -c 'ls -l /var/run/docker.sock'

Confirmar que las pruebas corrieron, en lugar de quedar omitidas, revisando el código de salida y los XML en services/<modulo>/target/surefire-reports/TEST-*.xml: tests > 0, failures=0, errors=0 y skipped=0 para cada clase Testcontainers. En esta sesión Inventory, Ticket y Payment Mock cumplieron esa condición. Purchase aún mostró skipped en sus clases Testcontainers durante una corrida, por lo que requiere repetir y diagnosticar ese módulo. Docker Desktop dio una respuesta transitoria HTTP 400 en una detección, pero un reintento posterior encontró el socket y arrancó PostgreSQL.
