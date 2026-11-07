# Despliegue en producción

Cómo publicar Sintéticas en internet con su propio dominio y HTTPS. Sirve cualquier servidor Linux con Docker
(un VPS). Toma unos 30 minutos la primera vez.

## Cómo queda armado

```
Internet ──► Caddy (puertos 80 y 443, HTTPS automático)
               ├── /api/*  ──► backend (Spring Boot, perfil "prod")  ──► PostgreSQL
               └── todo lo demás ──► frontend compilado (PWA)
```

- Todo va por **un solo dominio**: el navegador no necesita CORS y la app se puede instalar (PWA exige HTTPS).
- **Solo Caddy** recibe conexiones de internet. La base de datos y el backend no tienen puertos abiertos.
- Caddy pide y renueva solo el **certificado HTTPS** (Let's Encrypt).
- El backend lee la **IP real** de cada visitante (necesaria para los límites de intentos de login y de
  "olvidé mi contraseña"); nadie puede falsificarla enviando la cabecera `X-Forwarded-For`.
- Swagger y `/actuator` **no** se publican.

Archivos: [`docker-compose.prod.yml`](../docker-compose.prod.yml), [`.env.prod.example`](../.env.prod.example),
[`frontend/Caddyfile`](../frontend/Caddyfile), [`frontend/Dockerfile.prod`](../frontend/Dockerfile.prod),
[`application-prod.yml`](../backend/src/main/resources/application-prod.yml), [`deploy/`](../deploy).

## Lo que se necesita

| Qué | Recomendación |
|---|---|
| Servidor | VPS con Ubuntu 24.04, **2 GB de RAM**, 1 vCPU y 25 GB de disco (Java + PostgreSQL no caben cómodos en 1 GB) |
| Dominio | Uno propio (`sinteticas.com.co`) o un subdominio gratuito (DuckDNS, No-IP) |
| Correo | Una cuenta de Gmail con **contraseña de aplicación** (o cualquier SMTP) |

## 1. Preparar el servidor

```bash
# Docker (script oficial)
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # cerrar sesión y volver a entrar

# Firewall: solo SSH, HTTP y HTTPS
sudo ufw allow OpenSSH && sudo ufw allow 80 && sudo ufw allow 443 && sudo ufw enable
```

## 2. Apuntar el dominio

En el proveedor del dominio, crear un registro **A** con el nombre (`@` o `sinteticas`) y la **IP pública del servidor**.
Comprobar que ya apunta (puede tardar unos minutos):

```bash
dig +short sinteticas.ejemplo.com   # debe mostrar la IP del servidor
```

Si el dominio no apunta al servidor, Caddy no puede obtener el certificado HTTPS.

## 3. Descargar el proyecto y configurarlo

```bash
sudo mkdir -p /opt/sinteticas && sudo chown $USER /opt/sinteticas
git clone https://github.com/Julianx8/sinteticas.git /opt/sinteticas
cd /opt/sinteticas && git checkout main

cp .env.prod.example .env.prod
chmod 600 .env.prod          # solo tu usuario puede leerlo
nano .env.prod
```

Completar cada valor. Las claves se generan así (una distinta para cada una):

```bash
openssl rand -hex 24      # POSTGRES_PASSWORD
openssl rand -base64 48   # JWT_SECRET
```

**Correo con Gmail:** en la cuenta de Google activar la verificación en 2 pasos, luego *Seguridad → Contraseñas de
aplicaciones*, crear una y ponerla en `MAIL_PASSWORD` (sin espacios). `MAIL_USERNAME` y el correo de `MAIL_FROM` son esa
misma cuenta.

> `.env.prod` tiene todas las claves del sistema: **nunca** se sube a Git ni se comparte por chat. Guarden una copia
> en un gestor de contraseñas.

## 4. Levantar

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
docker compose -f docker-compose.prod.yml --env-file .env.prod logs -f web backend   # Ctrl+C para salir
```

La primera vez tarda unos minutos (compila backend y frontend). Cuando el backend diga `Started SinteticasApplication`
y Caddy `certificate obtained successfully`, comprobar:

```bash
./deploy/smoke-test.sh https://sinteticas.ejemplo.com
```

Debe terminar en **Todo bien.** Luego entrar con el `SUPER_ADMIN_EMAIL`, crear las cuentas de los dueños de sede y
darles el rol «Dueño de sede» en *Usuarios*.

## 5. Publicar una versión nueva

Producción sigue la rama `main` (lo probado). Cuando se mezcla `develop` en `main`:

```bash
cd /opt/sinteticas
git pull
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
./deploy/smoke-test.sh https://sinteticas.ejemplo.com
```

Las migraciones de Flyway se aplican solas al arrancar el backend. Los datos y los certificados se conservan
(viven en volúmenes de Docker).

**Volver a la versión anterior** si algo sale mal: `git checkout <commit-anterior>` y el mismo `up -d --build`.
Ojo: si la versión nueva tenía una migración, la base ya quedó migrada; en ese caso restaurar la copia de seguridad
de antes de publicar (paso 6).

## 6. Copias de seguridad

```bash
./deploy/backup.sh                 # crea backups/sinteticas-AAAA-MM-DD_HHMM.sql.gz y borra las de más de 14 días
```

Programarla todos los días a las 3 a. m. (`crontab -e`):

```
0 3 * * * cd /opt/sinteticas && ./deploy/backup.sh >> backups/backup.log 2>&1
```

Las copias quedan **en el mismo servidor**: si el servidor se pierde, se pierden. Cada cierto tiempo descargar una
(`scp usuario@servidor:/opt/sinteticas/backups/<archivo> .`) o activar los respaldos del proveedor del VPS.

**Restaurar** (reemplaza los datos actuales):

```bash
gunzip -c backups/sinteticas-AAAA-MM-DD_HHMM.sql.gz | \
  docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T postgres \
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

## 7. Día a día

| Para | Comando |
|---|---|
| Ver si todo está arriba | `docker compose -f docker-compose.prod.yml --env-file .env.prod ps` |
| Ver errores del backend | `docker compose -f docker-compose.prod.yml --env-file .env.prod logs --tail 200 backend` |
| Reiniciar el backend | `docker compose -f docker-compose.prod.yml --env-file .env.prod restart backend` |
| Espacio en disco | `df -h` y `docker system df` |

Los logs de cada contenedor se limitan a 50 MB (5 archivos de 10 MB) para no llenar el disco.

## Probar producción en tu computador

Sin dominio ni correo real, por HTTP en `http://localhost`:

```bash
cp .env.prod.example .env.prod
# En .env.prod: SITE_ADDRESS=:80, PUBLIC_URL=http://localhost, MAIL_ENABLED=false, MAIL_HOST=localhost
# y completar las claves
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
./deploy/smoke-test.sh http://localhost
```

Usa los puertos 80 y 443, y su propia base de datos (no la de desarrollo). Para apagarlo:
`docker compose -f docker-compose.prod.yml --env-file .env.prod down` (agregar `-v` borra también sus datos).

El CI hace exactamente esto en cada cambio (trabajo «Producción»): levanta `docker-compose.prod.yml`, corre
`deploy/smoke-test.sh` y las pruebas de navegador contra el build de producción.
