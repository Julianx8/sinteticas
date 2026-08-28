# Sintéticas

Plataforma de reserva de canchas sintéticas.

## Stack

- Backend: Java 21 + Spring Boot + Maven + Flyway
- Frontend: React + TypeScript + Vite
- Base de datos: PostgreSQL 16
- Orquestación: Docker Compose

## Primer arranque (paso a paso)

### 1. Generar el Maven Wrapper del backend

Este proyecto NO incluye `mvnw` todavía porque requiere generarse una vez.
Desde la raíz del proyecto, con Docker corriendo, ejecuta:

```bash
docker run --rm -v "${PWD}/backend:/app" -w /app maven:3.9-eclipse-temurin-21 mvn -N wrapper:wrapper
```

En Windows PowerShell la variable es la misma sintaxis `${PWD}`.

Verifica que se hayan creado:
```
backend/mvnw
backend/mvnw.cmd
backend/.mvn/wrapper/maven-wrapper.properties
```

### 2. Crear el archivo .env

```bash
cp .env.example .env
```

Y cambia la contraseña por una real.

### 3. Levantar todo

```bash
docker compose up --build
```

Verifica:
- PostgreSQL: `localhost:5432`
- Backend: http://localhost:8080/api/v1/health
- Frontend: http://localhost:5173

## Estructura

```
sinteticas/
├── backend/       # Spring Boot
├── frontend/      # React + TypeScript
├── docs/          # Documentación del proyecto de grado
├── docker-compose.yml
└── .env.example
```
