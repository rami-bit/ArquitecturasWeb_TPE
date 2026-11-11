FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY services/payment-mock/pom.xml ./pom.xml
RUN mvn -B -ntp dependency:go-offline
COPY services/payment-mock/src ./src
RUN mvn -B -ntp verify

FROM amazoncorretto:19-alpine
WORKDIR /app
RUN addgroup -S ticketya && adduser -S ticketya -G ticketya
COPY --from=build /workspace/target/payment-mock-0.1.0-SNAPSHOT.jar app.jar
USER ticketya
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
