# Build Stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -pl :identity-service -am -DskipTests

# Run Stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/identity-service/target/identity-service-*.jar app.jar
COPY --from=build /app/credentials /app/credentials
EXPOSE 8081
# Default to prod profile for Railway deployment.
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "app.jar"]