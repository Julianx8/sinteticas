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

Abre `.env` y completa los valores (pueden ser los mismos para los tres en desarrollo local, no son los de producción):

- `POSTGRES_PASSWORD`: contraseña de la base de datos.
- `JWT_SECRET`: clave para firmar los tokens de sesión, **mínimo 32 caracteres** (`openssl rand -base64 48`). Sin ella el backend no arranca.
- `SUPER_ADMIN_EMAIL` / `SUPER_ADMIN_PASSWORD`: el primer super administrador, que se crea solo al arrancar. Es quien asigna el rol `ADMIN` a los dueños de cancha.

> ⚠️ **Si ya tenías el proyecto clonado:** antes `.env` estaba subido a Git y ahora no. Al hacer `git pull` de `develop`, Git **borra tu `.env` local**. Haz una copia antes (`cp .env .env.backup`), haz el pull y luego restáurala (`mv .env.backup .env`) agregando las variables nuevas de `.env.example`.

### Levantar todo

```bash
docker compose up --build
```

Verifica que respondan:
- Backend → http://localhost:8080/api/v1/health
- Documentación de la API (Swagger) → http://localhost:8080/swagger-ui.html
- Frontend → http://localhost:5173
- Correos de desarrollo (Mailpit) → http://localhost:8025
- PostgreSQL → puerto 5432 (no se abre en navegador, es normal)

Si algo falla en este paso, avisa en el grupo antes de seguir — mejor resolverlo entre los tres que cada uno con una versión distinta funcionando.

### Correr los tests del backend

Los tests de integración levantan un PostgreSQL real con Testcontainers, así que solo necesitan Docker:

```bash
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "${PWD}/backend:/app" -v sinteticas-m2:/root/.m2 -w /app -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal maven:3.9-eclipse-temurin-21 mvn -B verify
```

(En Git Bash para Windows, antepón `MSYS_NO_PATHCONV=1` al comando.)

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

### Organización del backend

El código está organizado **por funcionalidad** (no por capa): cada paquete tiene su entidad, repositorio,
servicio, controlador y DTOs. Así cada quien trabaja en su rama sin chocar con los demás.

```
com.sinteticas.api
├── common/        # configuración, errores (ProblemDetail), paginación, hora del negocio
├── auth/          # login/registro, JWT, seguridad, creación del primer SUPER_ADMIN
├── user/          # usuarios, roles, "mi cuenta" y administración de usuarios
├── venue/         # sedes
├── court/         # canchas
├── schedule/      # horarios semanales con precios y bloqueos
├── notification/  # envío de correos (reservas y restablecer contraseña)
├── reservation/   # reservas y disponibilidad
└── report/        # reportes de sede (ocupación, ingresos, demanda) y de plataforma
```

Documentación:

- [docs/api/README.md](docs/api/README.md): endpoints, roles, reglas de reserva y códigos de error (o Swagger en http://localhost:8080/swagger-ui.html)
- [docs/base-datos/modelo.md](docs/base-datos/modelo.md): diagrama entidad-relación y restricciones
- [docs/decisiones/](docs/decisiones/): decisiones de arquitectura (JWT, reservas sin solapamiento, zona horaria)

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
- ❌ No se hace merge de un PR sin que al menos compile y levante con `docker compose up --build` (GitHub Actions corre los tests en cada PR; si sale en rojo, no se mezcla)

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