# Esquema de la base de datos de Catalog

Catalog Service crea su esquema de PostgreSQL con el script SQL de inicialización
ubicado en `services/catalog-service/src/main/resources/schema.sql`. Spring Boot
ejecuta ese archivo cada vez que inicia la aplicación porque
`spring.sql.init.mode` está configurado como `always`; luego Hibernate valida las
asociaciones de entidades con `ddl-auto: validate`, pero no crea ni modifica las
tablas.

Este es el mecanismo mínimo necesario para la primera entrega: el proyecto parte
de una base de datos vacía, no necesita un historial de migraciones de producción
y mantiene el esquema visible como SQL común, sin agregar una dependencia de
migraciones. Por eso, las instrucciones de `schema.sql` deben poder ejecutarse
varias veces de forma segura. Si en futuras entregas es necesario migrar bases
que ya contienen datos persistentes, el equipo deberá trasladar estas
instrucciones a migraciones versionadas en lugar de ampliar indefinidamente este
script de inicialización.

Para verificar la inicialización desde una base de datos vacía, inicia el entorno
Compose desde la raíz con un nombre de proyecto nuevo (o elimina su volumen de
Catalog) y espera a que `catalog-service` informe un estado saludable. La
comprobación de PostgreSQL confirma que se creó la base vacía, y la comprobación
del servicio confirma que `schema.sql` se ejecutó correctamente antes de la
validación de Hibernate.
