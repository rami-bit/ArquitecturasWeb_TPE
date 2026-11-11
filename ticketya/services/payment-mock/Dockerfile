FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY services/payment-mock/pom.xml ./services/payment-mock/pom.xml
COPY services/payment-mock/src ./services/payment-mock/src
RUN mvn -q -f services/payment-mock/pom.xml -DskipTests package

FROM amazoncorretto:19-alpine
WORKDIR /app
RUN addgroup -S ticketya && adduser -S ticketya -G ticketya
COPY --from=build /workspace/services/payment-mock/target/payment-mock-0.1.0-SNAPSHOT.jar app.jar
USER ticketya
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
