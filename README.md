# Sintéticas 

Plataforma web para reservar canchas sintéticas en Girardot.

Este documento explica **qué es el proyecto, cómo está armado, y cómo debemos trabajar los tres en él**. Léanlo completo antes de tocar código.

---

## 1. Stack tecnológico

| Componente     | Tecnología                      |
| -------------- | -------------------------------- |
| Backend        | Java 21 + Spring Boot + Maven    |
| Base de datos  | PostgreSQL 16                    |
| Migraciones    | Flyway                           |
| Frontend       | React + TypeScript + Vite        |
| Contenedores   | Docker + Docker Compose          |
| Control de versiones | Git + GitHub               |

Todo el proyecto corre con **un solo comando** (`docker compose up --build`), así que nadie necesita instalar Java, Maven, Node ni PostgreSQL a mano. Solo Docker y Git.

---

## 2. Requisitos antes de empezar

Cada integrante necesita instalado:

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (con Docker Compose incluido)
- [Git](https://git-scm.com/downloads)
- Un editor (recomendado: VS Code)

No necesitan instalar Java ni Node localmente — todo corre dentro de los contenedores.

---

## 3. Clonar el proyecto (primera vez)

```bash
git clone https://github.com/USUARIO/sinteticas.git
cd sinteticas
git checkout develop
```

**Nunca trabajen directo sobre `main`.** `develop` es la rama base del día a día.

### Crear tu archivo de variables de entorno

```bash
cp .env.example .env
```

Abre `.env` y pon una contraseña (puede ser la misma que usen los tres para desarrollo local, no es la de producción).

### Generar el Maven Wrapper del backend (solo una vez, cada uno en su máquina)

```bash
docker run --rm -v "${PWD}/backend:/app" -w /app maven:3.9-eclipse-temurin-21 mvn -N wrapper:wrapper
```

En Windows PowerShell la sintaxis es la misma.

### Levantar todo

```bash
docker compose up --build
```

Verifica que respondan:
- Backend → http://localhost:8080/api/v1/health
- Frontend → http://localhost:5173
- PostgreSQL → puerto 5432 (no se abre en navegador, es normal)

Si algo falla en este paso, avisa en el grupo antes de seguir — mejor resolverlo entre los tres que cada uno con una versión distinta funcionando.

---

## 4. Estructura del proyecto

```
sinteticas/
│
├── docker-compose.yml     # orquesta los 3 contenedores
├── .env.example           # plantilla de variables (SÍ se sube a Git)
├── .env                   # valores reales (NUNCA se sube)
│
├── backend/                # Spring Boot
│   ├── pom.xml              # dependencias Java
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/sinteticas/api/     # código Java
│       └── resources/
│           ├── application.yml           # configuración
│           └── db/migration/             # scripts SQL de Flyway (V1__, V2__...)
│
├── frontend/                # React + TypeScript
│   ├── package.json
│   ├── Dockerfile
│   └── src/                  # componentes, páginas, lógica de UI
│
└── docs/                     # evidencia para el proyecto de grado
    ├── arquitectura/
    ├── base-datos/
    ├── api/
    ├── requisitos/
    ├── diagramas/
    └── decisiones/
```

**Cómo se conectan las piezas:** `docker-compose.yml` levanta primero PostgreSQL, luego el backend (que aplica automáticamente las migraciones de Flyway sobre la base de datos), y luego el frontend, que le habla al backend por HTTP en `localhost:8080`.

---

## 5. Quién trabaja en qué

División inicial de responsabilidades (no exclusiva — los tres deben entender todo el sistema):

| Persona | Área principal |
| ------- | --------------- |
| Integrante 1 | Backend / Spring Boot / arquitectura |
| Integrante 2 | Frontend / React / UX |
| Integrante 3 | Base de datos / Docker / QA / documentación |

---

## 6. Flujo de trabajo con Git (léelo con calma, es lo más importante)

```
main
 │
 └── develop
      │
      ├── feature/auth
      ├── feature/reservations
      └── feature/courts
```

- **`main`**: siempre estable. Solo recibe merges desde `develop` cuando algo está probado.
- **`develop`**: rama de trabajo diario. Aquí se juntan todas las features.
- **`feature/lo-que-sea`**: cada tarea puntual vive en su propia rama.

### Antes de empezar cualquier tarea nueva

```bash
git checkout develop
git pull origin develop
git checkout -b feature/nombre-corto-de-la-tarea
```

Ejemplos de nombres: `feature/login`, `feature/reservation-form`, `feature/court-schema`.

### Mientras trabajas

Haz commits pequeños y frecuentes, con mensajes claros:

```
feat: add login endpoint
fix: prevent duplicate reservation on same slot
refactor: extract reservation validation to service
test: add integration test for court availability
docs: update database schema notes
chore: update docker compose healthcheck
```

### Cuando termines la tarea

```bash
git push -u origin feature/nombre-corto-de-la-tarea
```

Luego en GitHub: abre un **Pull Request hacia `develop`** (nunca hacia `main`). Si es posible, que otro del equipo lo revise antes de aprobar el merge.

### Reglas que NO nos podemos saltar

- ❌ Nadie trabaja directo sobre `main`
- ❌ Nunca se sube el archivo `.env`
- ❌ Nunca se guardan contraseñas o claves en el código
- ❌ No se cambian tablas de la base de datos a mano — todo cambio va en una nueva migración de Flyway (`V2__...`, `V3__...`, nunca se edita `V1` una vez fue mergeada a `develop`)
- ❌ No se hace merge de un PR sin que al menos compile y levante con `docker compose up --build`

---

## 7. Si algo se rompe

1. Revisa los logs del contenedor que falla:
   ```bash
   docker compose logs backend
   docker compose logs frontend
   docker compose logs postgres
   ```
2. Si el problema persiste, avísalo en el grupo con el error exacto antes de intentar "arreglarlo" borrando cosas.
3. Nunca hagas `git push --force` a `develop` ni a `main`.

---

## 8. Contacto / dudas

Cualquier duda sobre la arquitectura o el flujo de trabajo, pregunten antes de improvisar — es más rápido resolverlo entre los tres que deshacer un conflicto de Git después.