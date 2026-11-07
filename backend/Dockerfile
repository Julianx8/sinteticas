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

# La aplicación no corre como root dentro del contenedor
RUN groupadd --system app && useradd --system --gid app --no-create-home app

COPY --from=build --chown=app:app /app/target/sinteticas-api.jar app.jar

USER app

EXPOSE 8080

# La memoria de Java se ajusta al límite del contenedor (sirve igual en un portátil que en un servidor pequeño)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
