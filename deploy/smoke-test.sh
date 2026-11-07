#!/usr/bin/env bash
# Prueba rápida de un despliegue de producción: ¿abre la app, responde la API y no queda nada expuesto de más?
# Uso:  ./deploy/smoke-test.sh https://sinteticas.ejemplo.com      (o http://localhost en pruebas)
# Termina con error (código 1) si algo falla. Lo usa el CI y sirve después de cada publicación.
set -uo pipefail

BASE="${1:?Uso: smoke-test.sh <url> (p. ej. https://sinteticas.ejemplo.com)}"
BASE="${BASE%/}"
FAILED=0

ok()   { printf '  OK     %s\n' "$1"; }
fail() { printf '  FALLA  %s (%s)\n' "$1" "$2"; FAILED=1; }

expect_status() { # descripción, ruta, código esperado
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE$2")
  [ "$code" = "$3" ] && ok "$1" || fail "$1" "respondió $code, se esperaba $3"
}

expect_body() { # descripción, ruta, texto que debe aparecer
  curl -s "$BASE$2" | grep -q -- "$3" && ok "$1" || fail "$1" "no contiene «$3»"
}

expect_no_body() { # descripción, ruta, texto que NO debe aparecer
  curl -s "$BASE$2" | grep -q -- "$3" && fail "$1" "contiene «$3»" || ok "$1"
}

expect_header() { # descripción, ruta, cabecera: valor (expresión regular)
  curl -sI "$BASE$2" | grep -qiE -- "$3" && ok "$1" || fail "$1" "falta la cabecera «$3»"
}

echo "Probando $BASE"
expect_status  "La app abre"                                   "/"                    200
expect_body    "Las rutas de la app sirven la PWA"              "/mis-reservas"        '<div id="root">'
expect_body    "La API responde en el mismo dominio"            "/api/v1/health"       '"status":"UP"'
expect_status  "El catálogo público responde"                   "/api/v1/venues"       200
expect_status  "Una ruta privada pide sesión"                   "/api/v1/reservations/mine" 401
expect_body    "Manifest de la PWA"                             "/manifest.webmanifest" '"display":"standalone"'
expect_header  "Service worker sin caché (la app se actualiza)" "/sw.js"               '^cache-control: no-cache'
expect_header  "Protección contra iframes"                      "/"                    '^x-frame-options: DENY'
expect_header  "Sin adivinar tipos de archivo"                  "/"                    '^x-content-type-options: nosniff'
expect_no_body "Swagger no está expuesto"                       "/swagger-ui.html"     'swagger-ui'
expect_no_body "Actuator no está expuesto"                      "/actuator/health"     '"status"'
if [[ "$BASE" == https://* ]]; then
  expect_header "HTTPS obligatorio en el navegador (HSTS)"      "/"                    '^strict-transport-security: max-age='
  http_code=$(curl -s -o /dev/null -w '%{http_code}' "http://${BASE#https://}/")
  [[ "$http_code" =~ ^30[178]$ ]] && ok "HTTP redirige a HTTPS" || fail "HTTP redirige a HTTPS" "respondió $http_code"
fi

if [ "$FAILED" = 0 ]; then echo "Todo bien."; else echo "Hay fallas."; fi
exit "$FAILED"
