# Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
# Only copy the backend POM and source
COPY backend/pom.xml .
COPY backend/src ./src
# Build the application (skipping tests for speed)
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /app/target/backend-1.0.0-SNAPSHOT.jar app.jar

# AWS Elastic Beanstalk passes the port via the PORT env var. Default to 8080.
ENV PORT=8080
EXPOSE ${PORT}

ENTRYPOINT ["java", "-jar", "app.jar"]
