FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
# Build context is the repository root so the versioned OpenAPI contract travels with
# the image and the contract tests can read it from the workspace.
COPY services/inventory-service/pom.xml ./pom.xml
RUN mvn -B -ntp dependency:go-offline
COPY services/inventory-service/src ./src
COPY docs/openapi.yaml ./docs/openapi.yaml
RUN mvn -B -ntp verify

FROM amazoncorretto:19-alpine
WORKDIR /app
RUN addgroup -S ticketya && adduser -S ticketya -G ticketya
COPY --from=build /workspace/target/inventory-service-0.1.0-SNAPSHOT.jar app.jar
USER ticketya
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
