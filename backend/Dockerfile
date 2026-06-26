# Stage 1 — compilar el JAR
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
# Dar permisos de ejecución al wrapper de Maven
RUN chmod +x mvnw
# Descarga dependencias primero (capa cacheada para rebuilds rápidos)
RUN ./mvnw dependency:go-offline -q
COPY src src
RUN ./mvnw package -DskipTests -q

# Stage 2 — imagen de runtime liviana
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# -Xmx450m: límite de heap para el free tier de Render (512MB total)
ENTRYPOINT ["java", "-Xmx450m", "-jar", "app.jar"]
