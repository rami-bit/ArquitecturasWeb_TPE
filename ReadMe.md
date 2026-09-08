
# TP Arquitecturas Web

## Requisitos

- JDK 19 o superior.
- Maven.
- Docker con Docker Compose.

## Base de datos

La aplicacion usa MySQL en `localhost:3306`, base `integrador1`, usuario `root` y password `root`.

Levantar MySQL:

```bash
docker compose up -d
```

Ver logs:

```bash
docker compose logs -f mysql
```

Abrir una consola MySQL:

```bash
docker exec -it java-dev-mysql mysql -uroot -proot integrador1
```

Reiniciar la base desde cero:

```bash
docker compose down -v
docker compose up -d
```

## Ejecutar

Con MySQL levantado, ejecutar:

```bash
mvn clean compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=Main
```

El programa carga los CSV de `src/main/resources/csv` y luego imprime, entre otros datos:

- El producto que mas recaudo.
- Los clientes ordenados por total facturado.

## Testear

Para verificar que el proyecto compile:

```bash
mvn clean test
```

Actualmente no hay tests unitarios automatizados; este comando valida la compilacion del proyecto.

## Detener MySQL

```bash
docker compose down
```
