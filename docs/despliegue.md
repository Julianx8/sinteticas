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

### Opción gratuita: Oracle Cloud «Always Free» + DuckDNS

Oracle da gratis y sin vencimiento un servidor ARM de hasta **4 núcleos y 24 GB de RAM**, de sobra para el sistema.
DuckDNS da gratis un subdominio (`sinteticas-gdt.duckdns.org`). Costo total: **$0**.

**A. Cuenta de Oracle Cloud** — https://signup.cloud.oracle.com
1. Registrarse. Pide una tarjeta **solo para verificar identidad** (hace una retención temporal de ~1 USD que se
   devuelve); mientras se use solo lo marcado «Always Free elegible», no cobra nada.
2. **Región de origen:** elegir **Colombia Central (Bogotá)** si aparece, o la más cercana (São Paulo, Querétaro).
   No se puede cambiar después.

**B. Crear el servidor** — menú ☰ → *Compute* → *Instances* → *Create instance*
1. *Name:* `sinteticas`.
2. *Image and shape* → *Edit*:
   - *Image:* **Canonical Ubuntu 24.04**.
   - *Shape:* *Ampere* → **VM.Standard.A1.Flex** con **2 OCPU y 12 GB** (dentro del límite gratis de 4 y 24).
   - Si sale *«Out of capacity»*: probar otro *Availability domain*, intentar más tarde, o usar
     **VM.Standard.E2.1.Micro** (AMD, 1 GB; también gratis, más justo pero funciona gracias a la swap del script).
3. *Networking:* dejar *Create new virtual cloud network* y **Assign a public IPv4 address** activado.
4. *Add SSH keys:* **Generate a key pair for me** → *Save private key* (guardar el archivo `.key`; sin él no se
   puede entrar al servidor).
5. *Create*. Al quedar en *Running*, copiar la **Public IP address**.

**C. Abrir los puertos 80 y 443 en Oracle**
En la instancia → *Subnet* (enlace) → *Security Lists* → *Default Security List* → *Add Ingress Rules*, dos veces:

| Source CIDR | IP Protocol | Destination Port Range |
|---|---|---|
| `0.0.0.0/0` | TCP | `80` |
| `0.0.0.0/0` | TCP | `443` |

(El firewall *dentro* de Ubuntu también los bloquea: lo arregla el script del paso 1.)

**D. Subdominio en DuckDNS** — https://www.duckdns.org
1. Entrar con Google o GitHub.
2. Escribir el subdominio (p. ej. `sinteticas-gdt`) → *add domain*.
3. En *current ip* pegar la **Public IP** del servidor → *update ip*.

Ese es el dominio: `SITE_ADDRESS=sinteticas-gdt.duckdns.org` y `PUBLIC_URL=https://sinteticas-gdt.duckdns.org`.

**E. Entrar al servidor** (desde Git Bash en Windows, o la terminal en Mac/Linux):

```bash
chmod 600 ~/Downloads/ssh-key-*.key
ssh -i ~/Downloads/ssh-key-*.key ubuntu@IP_PUBLICA
```

Y seguir con el paso 1.

> **A tener en cuenta con Oracle gratis**
> - Oracle puede **recuperar instancias «Always Free» que pasan 7 días casi sin uso** (CPU, red y memoria muy bajas).
>   Con visitas reales no pasa; si les preocupa (p. ej. antes de la sustentación), se puede pasar la cuenta a
>   *Pay As You Go*: sigue sin cobrar mientras solo se use lo gratuito y esas instancias no se recuperan. Revisen
>   que todo lo creado diga «Always Free».
> - Oracle bloquea el puerto 25 de salida; el correo por Gmail usa el **587**, que sí funciona.

## 1. Preparar el servidor

Con el proyecto clonado o directamente desde GitHub, un solo script instala Docker, abre los puertos 80 y 443 en el
firewall de Ubuntu (incluido el de las imágenes de Oracle), crea 2 GB de swap y descarga el proyecto en
`/opt/sinteticas`:

```bash
curl -fsSL https://raw.githubusercontent.com/Julianx8/sinteticas/main/deploy/setup-server.sh | bash
# cerrar la sesión SSH y volver a entrar (para usar docker sin sudo)
```

Ver qué hace: [`deploy/setup-server.sh`](../deploy/setup-server.sh). En otro proveedor que use `ufw`, el script
abre ahí los puertos.

## 2. Apuntar el dominio

En el proveedor del dominio, crear un registro **A** con el nombre (`@` o `sinteticas`) y la **IP pública del servidor**.
Comprobar que ya apunta (puede tardar unos minutos):

```bash
dig +short sinteticas.ejemplo.com   # debe mostrar la IP del servidor
```

Si el dominio no apunta al servidor, Caddy no puede obtener el certificado HTTPS.

## 3. Configurar

El script del paso 1 ya dejó el proyecto en `/opt/sinteticas` (rama `main`).

```bash
cd /opt/sinteticas
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
