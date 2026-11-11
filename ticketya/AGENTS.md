# Instrucciones para agentes de TicketYa

Estas reglas aplican a todo el repositorio, incluidos subagentes. El proyecto es didáctico: preferir soluciones simples que funcionen localmente o con `docker compose`. Seguir `OpenSpec.md` y el orden de `TODO.md`; consultar `NEXT.md` como registro de avance, no como fuente de verdad si hubo un merge posterior.

## Antes de editar

1. Revisar `git status`, el cambio OpenSpec activo y los contratos actuales (código, esquema SQL, OpenAPI, Gateway, Postman y Compose) del flujo afectado. Conservar cambios ajenos del workspace.
2. Si hay conflictos de merge, controladores/rutas duplicados, esquemas incompatibles o dos contratos distintos para la misma operación, informarlo antes de editar. Elegir el contrato vigente a partir de OpenSpec y el código integrado; documentar la decisión.
3. Acordar con los otros agentes qué archivos edita cada uno. No ejecutar builds o pruebas que escriban los mismos `target/` en paralelo. Secuenciar corridas con Docker si compiten por recursos.

## Responsabilidad de pruebas de cada agente

- Quien modifica código debe ejecutar las pruebas del módulo afectado y una prueba de integración del comportamiento que cambió. Si el cambio afecta un contrato entre servicios, probar también el consumidor o el flujo completo. No entregar código como “listo” solo porque compila o porque otro agente probará la integración.
- Quien modifica SQL debe verificarlo contra PostgreSQL real mediante Testcontainers o Compose. H2 y mocks no prueban la compatibilidad del esquema de producción local.
- Quien modifica rutas, JSON, estados o IDs debe comparar productor y consumidor: controlador, cliente HTTP, esquema, OpenAPI, Gateway y colección Postman. Probar por el Gateway cuando la ruta sea pública. Incluir un caso de éxito y los errores o reintentos relevantes.
- Quien modifica un Dockerfile, POM o Compose debe construir la imagen afectada y comprobar que el servicio inicia y queda `healthy`. Los Dockerfiles empaquetan con `-DskipTests`; **no** sustituyen `mvn verify`.
- Quien elimina o migra una prueba debe conservar cobertura equivalente o registrar explícitamente la cobertura perdida en `NEXT.md`/`TODO.md`. No contar pruebas antiguas que solo permanecen en `target/`.

## Comandos de verificación

Desde la raíz de `ticketya`:

```powershell
# Java 19+ y Docker disponibles
mvn -pl services/<modulo> -am verify
mvn verify

# En Windows, si el host solo tiene JDK 17: Maven Java 21 con Docker Desktop.
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "${PWD}:/workspace" -w /workspace -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal maven:3.9.9-eclipse-temurin-21-alpine mvn -q -pl services/<modulo> -am verify
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "${PWD}:/workspace" -w /workspace -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal maven:3.9.9-eclipse-temurin-21-alpine mvn -q verify

openspec validate <cambio> --strict
git diff --check
```

Si se usa Testcontainers, comprobar en los XML **recientes** de `services/<modulo>/target/surefire-reports/TEST-*.xml` que las clases esperadas tengan `tests > 0`, `failures = 0`, `errors = 0` y `skipped = 0`. Un exit code 0 de Maven con pruebas omitidas **no** es verificación de integración. Si Docker no está disponible, registrar el bloqueo y mantener abierta la tarea correspondiente. Mantener alineadas todas las dependencias de Testcontainers; una mezcla de versiones puede hacer que Docker sea detectado como incompatible y las pruebas se omitan.

Para cambios que cruzan servicios, levantar un stack limpio con un nombre de proyecto Compose nuevo, sin borrar volúmenes ajenos:

```powershell
docker compose -p ticketya_verification up --build -d --wait
docker compose -p ticketya_verification ps
postman collection run postman/ticketya.postman_collection.json --env-var baseUrl=http://localhost:8080 --no-report-events
```

Comprobar que todos los servicios esperados estén `healthy` y que la colección termine sin fallos. Si la primera petición de un flujo falla, diagnosticar su request y response antes de interpretar fallos en cascada por IDs nulos. Repetir el E2E con datos nuevos después de corregirlo. También se puede usar el comando Newman del `README.md`. No ejecutar `docker compose down -v` sobre datos existentes sin autorización.

## Antes de dar por terminada una tarea o integrar ramas

1. Ejecutar las pruebas propias tras la **última** edición; repetir las afectadas si otro agente cambió un contrato compartido.
2. En la integración final, ejecutar `mvn verify` del reactor, `docker compose up --build -d --wait`, el flujo Postman por Gateway, `openspec validate <cambio> --strict` y `git diff --check`. Revisar omisiones de Testcontainers y estado de salud, no solo los códigos de salida.
3. Reportar comandos ejecutados, cantidad de pruebas, fallos/omisiones, versión o stack usado y riesgos pendientes. Marcar un checkbox OpenSpec/TODO solo con evidencia completa. Si algo falla, dejar el cambio abierto y registrar causa y próximo paso en `NEXT.md`.

Estado conocido al redactar esto: el merge de Purchase/Payment/Inventory ya requirió reconciliar contratos y esquemas; `mvn verify` y Compose pasaron, pero la colección Postman todavía falla desde la primera compra `/api/orders` con HTTP 400. No presentar US-F7.3 como cerrada hasta resolver ese E2E.
