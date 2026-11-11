FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM amazoncorretto:19-alpine
WORKDIR /app
RUN addgroup -S ticketya && adduser -S ticketya -G ticketya
COPY --from=build /workspace/target/api-gateway-0.1.0-SNAPSHOT.jar app.jar
USER ticketya
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
