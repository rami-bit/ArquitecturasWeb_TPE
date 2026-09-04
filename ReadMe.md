
## Manejo de la base de datos
### Start MySQL
docker compose up -d

### Stop MySQL
docker compose down

### Restart it
docker compose restart

### See logs
docker compose logs -f mysql

### Open a MySQL shell inside the container
docker exec -it java-dev-mysql mysql -uuser -ppwd appdb

### Completely destroy the DB and start fresh
docker compose down -v
docker compose up -d