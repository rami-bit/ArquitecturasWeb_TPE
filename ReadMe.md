# Trabajos prácticos — Arquitecturas Web

Este repositorio agrupa cuatro entregables independientes de la materia: tres trabajos prácticos de Java/JPA/Spring Boot (TP1, TP2 y TP3) y **TicketYa**, una plataforma de venta de entradas implementada como sistema de microservicios. Cada proyecto tiene su propia documentación de detalle; este archivo funciona como índice y como guía de arranque común.

## Proyectos incluidos

| Proyecto | Naturaleza | Stack principal | Datos | Documentación |
|---|---|---|---|---|
| **TP1** | Aplicación de consola para procesar facturación | Java, JDBC, Apache Commons CSV, Derby/MySQL | Base `integrador1`; CSV de clientes, productos y facturas | [Código de entrada](TP1/src/main/java/Main.java) · [pom.xml](TP1/pom.xml) |
| **TP2** | Aplicación de consola con persistencia ORM | Java, JPA/Hibernate 5, MySQL | Base `integrador2`; estudiantes, carreras e inscripciones cargadas desde CSV | [DER](TP2/DER.md) · [Diagrama de objetos](TP2/diagrama-objetos.md) · [pom.xml](TP2/pom.xml) |
| **TP3** | API REST sobre el modelo de TP2 | Spring Boot 4, Spring Data JPA, MySQL | Comparte `integrador2` con TP2; expone `/estudiantes` e `/inscripciones` | [README de TP3](TP3/README.md) |
| **TicketYa** | Plataforma de venta de entradas (Entregas 1 y 2) | Microservicios Spring Boot 3.2, API Gateway, PostgreSQL 16, MongoDB 7, Docker Compose | Una base por servicio; sin frontend desarrollado (Swagger UI interno por servicio) | [README de TicketYa](ticketya/README.md) · [Carátula Entrega 2](ticketya/docs/entrega2.md) |

## Relación entre los proyectos

- **TP1** es autónomo: usa `integrador1` y no comparte esquema con los demás.
- **TP2** crea el esquema y carga los datos iniciales de `integrador2`. **TP3** se conecta a esa misma base, por lo que TP2 debe ejecutarse antes la primera vez.
- **TicketYa** es un sistema independiente: tiene su propio `docker-compose.yml`, sus volúmenes y su configuración dentro de `ticketya/`. No utiliza las bases `integrador1`/`integrador2` ni el MySQL de la raíz.

| Proyecto | Base | Motor | Acceso |
|---|---|---|---|
| TP1 | `integrador1` | MySQL 8.4 (`localhost:3306`) | Consola (`Main`) |
| TP2 | `integrador2` | MySQL 8.4 (`localhost:3306`) | Consola con menú (`Main`) |
| TP3 | `integrador2` | MySQL 8.4 (`localhost:3306`) | API REST en `http://localhost:8080` |
| TicketYa | Una por servicio | PostgreSQL 16 + MongoDB 7 | API Gateway en `http://localhost:8080` |

> El puerto 8080 se usa tanto por TP3 como por el Gateway de TicketYa: no levantar ambos al mismo tiempo.

## Requisitos comunes

- JDK 19 o superior (TP3 compila con Java 17, pero se recomienda 19 para ejecutar TP2 y TP3 con el mismo entorno).
- Maven (TP3 también incluye `mvnw`).
- Docker con Docker Compose.

## Arranque rápido

### MySQL para TP1, TP2 y TP3

El `docker-compose.yml` de la raíz levanta MySQL 8.4 y crea `integrador1` e `integrador2` desde `docker/mysql/init.sql`:

```bash
docker compose up -d
docker compose ps
```

Credenciales locales: usuario `root`, contraseña `root`. Para una consola:

```bash
docker exec -it java-dev-mysql mysql -uroot -proot integrador2
```

Luego seguir el procedimiento de carga y ejecución de [TP3/README.md](TP3/README.md). Para TP1 y TP2, ejecutar `Main` desde su carpeta con Maven; los pasos específicos están resumidos en el README de TP3 y en los archivos de configuración de cada proyecto.

### TicketYa

Todo el sistema (17 contenedores, incluidas las bases y el Gateway) se levanta desde su propia carpeta:

```bash
cd ticketya
docker compose up --build -d --wait
```

La colección Postman y las pruebas de integración se ejecutan por el Gateway en `http://localhost:8080`. Ver [README de TicketYa](ticketya/README.md) para tokens, variables y comandos de verificación.

## Estructura del repositorio

```text
.
├── TP1/                 aplicaciones de consola (facturación)
├── TP2/                 JPA/Hibernate (estudiantes y carreras) + DER
├── TP3/                 API REST Spring Boot sobre integrador2
├── ticketya/            plataforma de microservicios TicketYa
│   ├── docs/            arquitectura, verificación, carga y patrones
│   ├── postman/         colección E2E, arnés y prueba de carga
│   └── services/        microservicios Spring Boot
├── docker/mysql/        script de inicialización de MySQL para TP1–TP3
├── postman/             colecciones auxiliares de los trabajos prácticos
└── catalog-service/     fuentes auxiliares del entorno de desarrollo
```

El `docker-compose.yml` de la raíz pertenece a TP1–TP3. El Compose de TicketYa está en `ticketya/docker-compose.yml`.

## Estado y referencias

TP1, TP2 y TP3 están completos. TicketYa tiene cerradas las Entregas 1 y 2: `mvn verify` con 436 pruebas sin fallos, 17 contenedores `healthy`, arnés de integración y prueba de carga ejecutados. La única historia funcional pendiente es F11 (notificaciones), delegada a otro compañero.

- [Progreso y estado actual](ticketya/NEXT.md)
- [Checklist por historia](ticketya/TODO.md)
- [Verificación medida](ticketya/docs/verification.md)
- [Informe de prueba de carga](ticketya/docs/load-test-report.md)
- [Patrones y consistencia](ticketya/docs/patterns-and-consistency.md)
