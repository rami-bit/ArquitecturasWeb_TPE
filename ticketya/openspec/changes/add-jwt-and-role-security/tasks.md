# Tasks

## 1. Validar JWT en el Gateway

- [x] 1.1 Agregar validación de tokens del IdP externo configurable por entorno (firma, expiración, `sub` UUID y exactamente un rol soportado); verificar con tokens de prueba firmados y casos de token válido, expirado, malformado y firma inválida.
- [x] 1.2 Exigir Bearer en operaciones protegidas y conservar anónimas las consultas públicas y healthchecks; probar respuestas `401` y preservación de `X-Correlation-Id` antes de llegar al upstream.
- [x] 1.3 Reemplazar `X-User-Id`/`X-User-Role` externos por identidad derivada de claims validados y no reenviar el Bearer al servicio; probar con upstream que headers falsificados no cambian identidad ni permisos, incluso en una consulta pública anónima.
- [x] 1.4 Documentar configuración del issuer/JWKS y uso de tokens externos en README; integrar variables en Compose y verificar build de la imagen del Gateway y healthcheck del servicio.

## 2. Conservar autorización y ownership de dominio

- [x] 2.1 Revisar las reglas existentes de Catalog y Purchase para roles y ownership; adaptar sólo las incompatibles con la identidad derivada y pasar las pruebas de integración de roles y ownership de cada módulo afectado.
- [x] 2.2 Probar por Gateway los accesos autorizados y denegados de `CLIENT`, `ORGANIZER`, `ADMIN` y `ACCESS_STAFF`, incluyendo ownership de eventos, órdenes y devoluciones; verificar `403` por rol insuficiente y `404` para recursos ajenos cuando ya lo exige el contrato.

## 3. Actualizar contratos y clientes

- [x] 3.1 Actualizar `docs/openapi.yaml` con HTTP Bearer por operación, manteniendo explícitamente públicas las búsquedas/disponibilidad y no publicando rutas internas; actualizar y ejecutar el test contractual del documento.
- [x] 3.2 Actualizar Postman y README para recibir un token emitido por el IdP mediante variable de entorno; ejecutar la colección por Gateway y comprobar endpoints públicos sin token y protegidos con token.

## 4. Verificación integrada de la épica

- [x] 4.1 Ejecutar `mvn -pl services/api-gateway,services/catalog-service,services/purchase-service -am verify` y comprobar en los reportes recientes que las pruebas de seguridad esperadas tienen `tests > 0`, cero fallos/errores y ninguna omisión.
- [x] 4.2 Levantar Compose con nombre de proyecto aislado y configuración documentada, comprobar salud de todos los servicios y repetir el flujo protegido por Gateway; ejecutar `openspec validate add-jwt-and-role-security --strict` y `git diff --check` sin borrar volúmenes ajenos.
