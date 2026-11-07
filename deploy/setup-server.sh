#!/usr/bin/env bash
# Prepara un servidor Ubuntu nuevo para Sintéticas: Docker, firewall y swap.
# Pensado para Oracle Cloud "Always Free" (ARM o AMD) y sirve en cualquier VPS Ubuntu.
#
# Uso, en el servidor recién creado:
#   curl -fsSL https://raw.githubusercontent.com/Julianx8/sinteticas/main/deploy/setup-server.sh | bash
# o, si ya clonaste el proyecto:  ./deploy/setup-server.sh
# Se puede correr varias veces sin problema.
set -euo pipefail

echo "==> Actualizando paquetes"
sudo apt-get update -y
sudo DEBIAN_FRONTEND=noninteractive apt-get upgrade -y
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y git curl ca-certificates

echo "==> Docker"
if ! command -v docker > /dev/null; then
  curl -fsSL https://get.docker.com | sudo sh
fi
sudo usermod -aG docker "$USER"
sudo systemctl enable --now docker

echo "==> Abriendo los puertos 80 y 443 en el firewall del sistema"
# Las imágenes Ubuntu de Oracle traen reglas de iptables que rechazan todo menos SSH (puerto 22),
# aunque el puerto esté abierto en la "Security List" del panel. Se agregan antes de la regla REJECT.
if [ -f /etc/iptables/rules.v4 ]; then
  for port in 80 443; do
    if ! sudo iptables -C INPUT -p tcp --dport "$port" -m state --state NEW -j ACCEPT 2> /dev/null; then
      sudo iptables -I INPUT 6 -p tcp --dport "$port" -m state --state NEW -j ACCEPT
    fi
  done
  sudo netfilter-persistent save
elif command -v ufw > /dev/null; then
  sudo ufw allow OpenSSH
  sudo ufw allow 80/tcp
  sudo ufw allow 443/tcp
  sudo ufw --force enable
fi

echo "==> Memoria de intercambio (swap) de 2 GB"
# Evita que el servidor se quede sin memoria al compilar el backend y el frontend (sobre todo con 1 GB de RAM)
if ! sudo swapon --show | grep -q /swapfile; then
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi

echo "==> Carpeta del proyecto"
sudo mkdir -p /opt/sinteticas
sudo chown "$USER":"$USER" /opt/sinteticas
if [ ! -d /opt/sinteticas/.git ]; then
  git clone https://github.com/Julianx8/sinteticas.git /opt/sinteticas
fi

cat << 'EOF'

Listo. Siguientes pasos (docs/despliegue.md):
  1. Cierra la sesión SSH y vuelve a entrar (para usar docker sin sudo).
  2. cd /opt/sinteticas
  3. cp .env.prod.example .env.prod && chmod 600 .env.prod && nano .env.prod
  4. docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
  5. ./deploy/smoke-test.sh https://TU-DOMINIO
EOF
