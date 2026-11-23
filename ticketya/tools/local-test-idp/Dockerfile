FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY LocalTestIdp.java .
EXPOSE 19998
CMD ["java", "LocalTestIdp.java", "--port", "19998", "--issuer", "http://host.docker.internal:19998", "--key-file", "/data/idp-key.pem"]
