# Build Stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -pl :planning-service -am -DskipTests

# Run Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/planning-service/target/planning-service-*.jar app.jar
EXPOSE 8082
# Default to dev profile so application-dev.yml (with API key defaults) is loaded.
# Railway env vars override this when set in the dashboard.
ENV SPRING_PROFILES_ACTIVE=dev
ENTRYPOINT ["java", "-jar", "app.jar"]