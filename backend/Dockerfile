# ---------- Etapa de build ----------
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Copiamos primero solo lo necesario para descargar dependencias (mejor cache)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Ahora copiamos el resto del código y compilamos
COPY src src
RUN ./mvnw -B clean package -DskipTests

# ---------- Etapa final (imagen liviana) ----------
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/sinteticas-api.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
