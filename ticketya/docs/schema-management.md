# Catalog database schema

Catalog Service creates its PostgreSQL schema with the SQL bootstrap at
`services/catalog-service/src/main/resources/schema.sql`. Spring Boot runs that
file on every application start because `spring.sql.init.mode` is `always`;
Hibernate then validates the entity mappings with `ddl-auto: validate` and does
not create or alter tables itself.

This is the minimum mechanism needed for the first delivery: the project starts
from an empty database, does not need production migration history, and keeps the
schema visible as ordinary SQL without adding a migration dependency. Statements
in `schema.sql` must therefore be safe to execute repeatedly. If later deliveries
need to migrate databases that already contain durable data, the team should move
these statements into versioned migrations rather than extending this bootstrap
indefinitely.

To verify initialization from an empty database, start the root Compose stack
with a new Compose project name (or with its catalog volume removed), then wait
for `catalog-service` to become healthy. The PostgreSQL health check establishes
that the empty database was created, and the service health check establishes
that `schema.sql` ran successfully before Hibernate validation.
