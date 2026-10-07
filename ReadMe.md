
# TP Arquitecturas Web

## Requisitos

- JDK 19 o superior.
- Maven.
- Docker con Docker Compose.

## Base de datos

La aplicacion usa MySQL en `localhost:3306`, usuario `root` y password `root`:

- **TP1** usa la base `integrador1`.
- **TP2** usa la base `integrador2`.
- **TP3** usa la base `integrador2` (compartida con TP2, mismas tablas y datos).

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

## Ejecutar TP3 (Spring Boot)

El TP3 es una API REST con Spring Boot que se conecta a la base `integrador2`
(misma que el TP2, con los datos ya cargados). Requiere MySQL levantado.

```bash
cd TP3
mvn spring-boot:run
```

La API queda disponible en `http://localhost:8080`.

## Endpoints TP3

| Metodo | Ruta | Descripcion | Respuesta |
|--------|------|-------------|-----------|
| GET | `/estudiantes` | Listar todos los estudiantes | 200 |
| POST | `/estudiantes` | Dar de alta un estudiante | 201 |
| GET | `/inscripciones` | Listar todas las inscripciones | 200 |
| POST | `/inscripciones` | Matricular un estudiante en una carrera | 201 / 400 / 404 / 409 |

Todos los responses son JSON con DTOs (nunca entities). En los bodies de ejemplo
son obligatorios todos los campos: si falta un campo numerico (`int`), el request
falla con 400 antes de llegar a la logica.

### POST /estudiantes

```json
{
  "dni": 88888888,
  "nombre": "Juan",
  "apellido": "Perez",
  "edad": 25,
  "genero": "Male",
  "ciudad": "Tandil",
  "nroLibreta": 88888
}
```

### POST /inscripciones

Caso exito (201):

```json
{
  "dni": 12345678,
  "idCarrera": 1,
  "inscripcion": 2024,
  "antiguedad": 1,
  "graduacion": 0
}
```

Falta `idCarrera` (400):

```json
{
  "dni": 12345678,
  "inscripcion": 2024,
  "antiguedad": 1,
  "graduacion": 0
}
```

Estudiante inexistente (404):

```json
{
  "dni": 99999999,
  "idCarrera": 1,
  "inscripcion": 2024,
  "antiguedad": 1,
  "graduacion": 0
}
```

Ya inscripto en la carrera (409):

```json
{
  "dni": 71779527,
  "idCarrera": 15,
  "inscripcion": 2024,
  "antiguedad": 1,
  "graduacion": 0
}
```

## Testear

Para verificar que el proyecto compile:

```bash
mvn clean test
```

Actualmente no hay tests unitarios automatizados; este comando valida la compilacion del proyecto.

Para el **TP3** el mismo comando (`cd TP3 && mvn test`) necesita Docker levantado,
porque el contexto de Spring arranca contra MySQL.

## Detener MySQL

```bash
docker compose down
```
