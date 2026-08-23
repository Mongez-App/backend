# Build Stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B clean package -pl :identity-service -am -DskipTests

# Run Stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/identity-service/target/identity-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]