# TP3 - API REST

API REST desarrollada con Spring Boot y Spring Data JPA. Utiliza la misma base
de datos MySQL que TP2; TP2 crea el esquema y carga los datos iniciales desde
los archivos CSV.

## Requisitos

- JDK 19 (permite ejecutar tanto TP2 como TP3).
- Maven.
- Docker con Docker Compose.
- Postman, opcionalmente, para probar la API.

Los comandos siguientes parten desde la raiz del repositorio.

## 1. Levantar MySQL

```bash
cd TP3
docker compose up -d
docker compose ps
```

El servicio levanta MySQL en `localhost:3306` y crea la base `integrador2` con
las siguientes credenciales:

```text
usuario: root
password: root
```

Esperar hasta que `docker compose ps` muestre el contenedor `tp2-mysql` como
`healthy` antes de continuar.

## 2. Crear el esquema y cargar los datos

TP2 utiliza Hibernate para crear o actualizar las tablas y carga los CSV. La
carga de matriculas utiliza UPSERT, por lo que puede ejecutarse nuevamente sin
generar inscripciones duplicadas.

```bash
cd ../TP2
mvn clean compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=Main
```

Una vez finalizada la carga aparece el menu de TP2. Elegir la opcion `9` para
cerrarlo antes de iniciar TP3.

## 3. Ejecutar TP3

```bash
cd ../TP3
mvn spring-boot:run
```

La API queda disponible en:

```text
http://localhost:8080
```

Para una verificacion rapida:

```bash
curl http://localhost:8080/estudiantes
curl http://localhost:8080/inscripciones
```

## Endpoints implementados

| Metodo | Ruta | Descripcion |
| --- | --- | --- |
| `GET` | `/estudiantes` | Recupera todos los estudiantes. |
| `GET` | `/estudiantes/orden?orden=apellido` | Recupera estudiantes ordenados por `nombre`, `apellido`, `edad` o `dni`. |
| `GET` | `/estudiantes/{numeroLibreta}` | Recupera un estudiante por numero de libreta universitaria. |
| `GET` | `/estudiantes/genero/{genero}` | Recupera estudiantes filtrados por genero. |
| `POST` | `/estudiantes` | Da de alta un estudiante. |
| `GET` | `/inscripciones` | Recupera todas las inscripciones. |
| `POST` | `/inscripciones` | Matricula un estudiante en una carrera. |
| `GET` | `/carreras/inscriptos` | Recupera las carreras con inscriptos, ordenadas por cantidad. |
| `GET` | `/inscripciones/estudiantes/{carrera}?ciudad={ciudad}` | Recupera estudiantes de una carrera filtrados por ciudad de residencia. |
| `GET` | `/inscripciones/carrera` | Genera el reporte de inscriptos y egresados por carrera y anio. |

## Probar con Postman

La coleccion local en formato Postman v3 se encuentra en:

```text
postman/collections/tp3
```

La coleccion incluye la variable `base_url`, configurada inicialmente como
`http://localhost:8080`, y variables para los estudiantes, el ordenamiento y
las matriculas de prueba. Tambien contiene consultas para los filtros por
genero, carrera y ciudad, el listado de carreras por cantidad de inscriptos y
el reporte anual.


## Detener el entorno

Para detener MySQL conservando sus datos:

```bash
cd TP3
docker compose down
```

Para eliminar también el volumen y recrear la base desde cero:

```bash
docker compose down -v
docker compose up -d
```

El uso de `-v` elimina permanentemente los datos guardados en MySQL. Después
de recrear el volumen se debe ejecutar nuevamente el paso de carga con TP2.

## Problema con un volumen anterior

Si la tabla `EstudianteCarrera` fue creada antes de incorporar IDs
autogenerados, MySQL puede informar `Field 'id' doesn't have a default value`.
La migracion necesaria se ejecuta una sola vez:

```bash
docker exec -it tp2-mysql mysql -uroot -proot integrador2
```

```sql
ALTER TABLE EstudianteCarrera
MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
```
