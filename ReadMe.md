
# TP Arquitecturas Web

## Requisitos

- JDK 19 o superior.
- Maven.
- Docker con Docker Compose.

## Base de datos

La aplicacion usa MySQL en `localhost:3306`, usuario `root` y password `root`:

- **TP1** usa la base `integrador1`.
- **TP2** usa la base `integrador2`.

El `docker-compose.yml` de la raiz crea ambas bases automaticamente (`docker/mysql/init.sql`).

Levantar MySQL desde la raiz del proyecto:

```bash
docker compose up -d
```

Ver logs:

```bash
docker compose logs -f mysql
```

Abrir una consola MySQL:

```bash
docker exec -it java-dev-mysql mysql -uroot -proot integrador2
```

Reiniciar la base desde cero:

```bash
docker compose down -v
docker compose up -d
```

## Ejecutar

Con MySQL levantado, ejecutar desde la subcarpeta del TP correspondiente (`TP1/` o `TP2/`):

```bash
cd TP2
mvn clean compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=Main
```

**TP2** carga los CSV de `src/main/resources/csv` (solo si las tablas estan vacias) y luego muestra un menu con las opciones:

1. Dar de alta un estudiante
2. Matricular un estudiante en una carrera
3. Recuperar todos los estudiantes con criterio de ordenamiento
4. Recuperar un estudiante por numero de libreta
5. Recuperar todos los estudiantes por genero
6. Recuperar las carreras con estudiantes inscriptos, ordenadas por cantidad de inscriptos
7. Recuperar los estudiantes de una determinada carrera, filtrado por ciudad
8. Generar reporte de carreras
9. Salir

**TP1** carga los CSV de clientes/productos/facturas e imprime, entre otros datos:

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
