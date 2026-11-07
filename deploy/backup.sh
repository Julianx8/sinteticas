#!/usr/bin/env bash
# Copia de seguridad de la base de datos de producción.
# Uso (en el servidor, desde la carpeta del proyecto):   ./deploy/backup.sh
# Programarla todos los días a las 3 a. m. (crontab -e):
#   0 3 * * * cd /opt/sinteticas && ./deploy/backup.sh >> backups/backup.log 2>&1
# Restaurar (¡reemplaza los datos actuales!):
#   gunzip -c backups/sinteticas-AAAA-MM-DD_HHMM.sql.gz | docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
set -euo pipefail

KEEP_DAYS="${KEEP_DAYS:-14}"
DIR="$(dirname "$0")/../backups"
mkdir -p "$DIR"
FILE="$DIR/sinteticas-$(date +%Y-%m-%d_%H%M).sql.gz"

# pg_dump corre dentro del contenedor con sus propias variables: la contraseña no pasa por la línea de comandos
docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T postgres \
  sh -c 'pg_dump --clean --if-exists -U "$POSTGRES_USER" -d "$POSTGRES_DB"' | gzip > "$FILE"

echo "$(date '+%F %T') copia creada: $FILE ($(du -h "$FILE" | cut -f1))"
# Se borran las de más de KEEP_DAYS días
find "$DIR" -name 'sinteticas-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
