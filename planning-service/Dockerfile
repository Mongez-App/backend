# Build Stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B clean package -pl :planning-service -am -DskipTests

# Run Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/planning-service/target/planning-service-*.jar app.jar
EXPOSE 8082
# Safe default for container deployments. Railway's SPRING_PROFILES_ACTIVE
# service variable overrides this at runtime.
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "app.jar"]