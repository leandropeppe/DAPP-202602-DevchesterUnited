FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
COPY . .
COPY docker/observability-scenarios/unsafe-broad-chain/SecurityConfig.java src/main/java/com/devchesterunited/devchesterunitedv2/configuration/security/SecurityConfig.java
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew bootJar --no-daemon --console=plain

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar

USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
