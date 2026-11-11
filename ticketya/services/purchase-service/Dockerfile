FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY services/purchase-service/pom.xml ./pom.xml
COPY services/purchase-service/src ./src
RUN mvn -q -DskipTests package
FROM amazoncorretto:19-alpine
WORKDIR /app
RUN addgroup -S ticketya && adduser -S ticketya -G ticketya
COPY --from=build /workspace/target/purchase-service-0.1.0-SNAPSHOT.jar app.jar
USER ticketya
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
