# Proposal

## Why

El equipo necesita una vista arquitectónica compartida de TicketYa antes de profundizar cada épica, para que los límites de servicios, ownership de datos y flujos críticos no se decidan de forma aislada. El blueprint debe cumplir los mínimos del PDF con una solución didáctica y defendible, evitando incorporar complejidad propia de producción sin una necesidad demostrada.

## What Changes

- Materializar un blueprint general de TicketYa que cubra el contexto, los actores, sistemas externos y límites de microservicios.
- Definir responsabilidades y ownership de datos para catálogo, inventario, compras, pagos, entradas/acceso, fila virtual, preferencias, recomendaciones, notificaciones y reportes.
- Modelar el flujo completo de compra, incluyendo reserva temporal, pago, emisión de entradas, idempotencia, expiración y compensaciones.
- Modelar devoluciones, cancelación masiva, validación QR y fila virtual.
- Definir las estrategias objetivo de comunicación síncrona y asíncrona, consistencia, resiliencia, cache, seguridad, trazabilidad y despliegue.
- Separar visualmente la arquitectura mínima de Entrega 1 de la arquitectura objetivo de Entrega 2.
- Establecer un mapa de épicas y dependencias que permita refinar e implementar capacidades sin perder coherencia global.
- Relacionar `establish-event-catalog` como primera épica funcional dentro del blueprint, sin duplicar sus requisitos.
- Establecer guidelines de simplicidad: Java 19 como estándar, el PDF como piso obligatorio y toda tecnología o patrón adicional sujeto a una justificación concreta.
- Tratar la correctitud, concurrencia e idempotencia exigidas por el TPE como drivers; no tratar performance productiva, escala extrema o sofisticación operativa como objetivos del proyecto.

## Capabilities

### New Capabilities

Ninguna. Este cambio documenta arquitectura y no incorpora comportamiento observable.

### Modified Capabilities

Ninguna. Las capacidades funcionales se especificarán y evolucionarán en los cambios de cada épica.

## Impact

- Se agregan exclusivamente artefactos de planificación arquitectónica dentro de este cambio OpenSpec.
- No se modifica código, contratos funcionales, bases de datos ni configuración ejecutable.
- El blueprint condicionará los diseños de épicas posteriores y funcionará como referencia para diagramas y decisiones exigidos por el TPE.
- Las decisiones detalladas de cada servicio seguirán perteneciendo a sus respectivos cambios, empezando por `establish-event-catalog`.
- Herramientas como brokers, caches, migradores de esquema y stacks de observabilidad quedan como alternativas hasta que una épica justifique su necesidad y costo.
