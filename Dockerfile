# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-21 AS build
COPY . .
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jdk-alpine
VOLUME /tmp
COPY --from=build /target/*.jar app.jar
# CRITICAL: Limit memory to 300MB so Render doesn't kill it
ENTRYPOINT ["java","-Xmx300m","-jar","/app.jar"]
EXPOSE 8080