# Tasks

## 1. Auth Service y cuentas fijas

- [ ] 1.1 Crear `services/auth-service` como módulo Spring Boot del reactor con endpoint health y configuración validada de cuatro cuentas con UUID y roles únicos; verificar `mvn -pl services/auth-service -am verify` y arranque del módulo sin base de datos.
- [ ] 1.2 Cargar únicamente hashes de contraseña para las cuentas `CLIENT`, `ORGANIZER`, `ADMIN` y `ACCESS_STAFF`; verificar con pruebas que usuario/contraseña correctos identifican la cuenta y que un rol/UUID enviado en el request no la modifica.
- [ ] 1.3 Implementar `POST /api/auth/login` con JSON estricto, errores `400`/`401` genéricos, `Cache-Control: no-store`, correlation ID y logs sin credenciales; verificar éxito, usuario inexistente, contraseña incorrecta, campos extra y ausencia de secretos en los registros.
- [ ] 1.4 Documentar el contrato y las cuentas de demostración en OpenAPI propio y README del módulo; verificar que ejemplos de request/response y códigos corresponden a pruebas del controlador.

## 2. Firma JWT y claves

- [ ] 2.1 Implementar generación inicial, persistencia y carga de clave RSA con `kid` estable, fallando ante material guardado inválido; verificar arranque nuevo, reinicio con misma clave y rechazo de clave corrupta sin sobrescritura.
- [ ] 2.2 Emitir JWT RS256 de una hora con `iss`, `sub`, `iat`, `exp` y arreglo `roles` de un elemento, y publicar sólo JWKS público; verificar firma/claims, cuatro roles, expiración y ausencia de campos privados en JWKS mediante pruebas de integración del módulo.
- [ ] 2.3 Documentar vida útil, persistencia de la clave y ausencia de refresh/revocación en README/OpenAPI propio; verificar que el ejemplo emitido puede validarse con el JWKS publicado.

## 3. Gateway y contrato público

- [ ] 3.1 Agregar `POST /api/auth/login` anónimo y su ruta antes de Catalog, sin enrutar JWKS ni rutas internas; verificar por Gateway login `200`, credenciales inválidas `401`, cuerpo inválido `400`, `X-Correlation-Id` y precedencia de rutas.
- [ ] 3.2 Verificar que un JWT local habilita una ruta de cada rol permitido y que token ausente, vencido, de rol incorrecto o del IdP de prueba recibe `401`/`403` según corresponda; ejecutar pruebas del módulo Gateway tras la última edición.
- [ ] 3.3 Actualizar `docs/openapi.yaml` y crear colección Postman separada de login local con un endpoint protegido; verificar que productor, Gateway, OpenAPI y colección coinciden sin cambiar la colección E2E del IdP de prueba.

## 4. Despliegue local

- [ ] 4.1 Agregar Dockerfile de Auth Service, volumen propio para la clave y healthcheck a Compose, sin publicar su puerto; construir la imagen y verificar que el servicio inicia y queda `healthy`.
- [ ] 4.2 Configurar issuer/JWKS de Auth Service por defecto, conservar overrides del Gateway y ligar el puerto publicado del Gateway a `127.0.0.1`; verificar stack Compose limpio `healthy`, login por Gateway y acceso protegido con token local.
- [ ] 4.3 Reiniciar Auth Service conservando el volumen y verificar que un JWT no vencido sigue aceptado; documentar arranque normal, credenciales de demo, pérdida de volumen y modo de IdP de prueba en README y diagramas.

## 5. Compatibilidad del IdP de prueba

- [ ] 5.1 Conservar `tools/local-test-idp/LocalTestIdp.java`, su endpoint de tokens arbitrarios y la configuración de emisor de `postman/integration-harness.py`; verificar que el arnés obtiene JWT para UUID nuevos sin usar cuentas fijas.
- [ ] 5.2 Ejecutar la colección E2E vigente, el script de compras concurrentes y el flujo de carga que usa el IdP de prueba en un stack aislado; verificar que los tokens funcionan allí y que un token local de otro emisor es rechazado.
- [ ] 5.3 Documentar explícitamente los dos modos de emisor y sus comandos de verificación en README; verificar que ningún ejemplo mezcla tokens locales y de prueba en la misma instancia de Gateway.

## 6. Integración final

- [ ] 6.1 Ejecutar `mvn verify` del reactor tras la última edición y revisar reportes XML recientes para confirmar pruebas de integración ejecutadas, sin fallos ni omisiones; registrar cantidad y versión de Java/Maven en `docs/verification.md`.
- [ ] 6.2 Levantar un proyecto Compose nuevo con `up --build -d --wait`, verificar todos los servicios esperados `healthy`, ejecutar la colección de login local por Gateway y comprobar un flujo protegido con cada rol; registrar resultados en `docs/verification.md`.
- [ ] 6.3 Ejecutar el arnés y la colección E2E existentes con el IdP de prueba en otro stack aislado, `openspec validate add-local-login-service --strict` y `git diff --check`; registrar requests, aserciones, fallos/omisiones y riesgos en `docs/verification.md`, `NEXT.md` y `TODO.md` antes de marcar el cambio completo.
