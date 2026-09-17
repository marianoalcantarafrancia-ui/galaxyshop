#!/usr/bin/env bash
# =============================================================================
#  lib.sh - Funciones de apoyo para hablar con la Admin REST API de Keycloak
# =============================================================================
set -Eeuo pipefail

KC_URL="${KC_URL:-http://localhost:8081}"
KC_ADMIN="${KC_ADMIN:-admin}"
KC_ADMIN_PASSWORD="${KC_ADMIN_PASSWORD:-Admin#2026}"

_TOKEN=""
_TOKEN_TS=0

# --- colores ---------------------------------------------------------------
C_OK=$'\033[0;32m'; C_INFO=$'\033[0;36m'; C_WARN=$'\033[0;33m'; C_ERR=$'\033[0;31m'; C_OFF=$'\033[0m'

# Los logs van SIEMPRE a stderr: stdout queda libre para que las funciones
# devuelvan datos (ids de clientes, grupos, policies...) mediante $( ).
ok()   { echo "${C_OK}  OK${C_OFF}   $*" >&2; }
info() { echo "${C_INFO}  ->${C_OFF}   $*" >&2; }
warn() { echo "${C_WARN} WARN${C_OFF}  $*" >&2; }
die()  { echo "${C_ERR} ERROR${C_OFF} $*" >&2; exit 1; }
step() { echo >&2; echo "${C_INFO}=== $* ===${C_OFF}" >&2; }

# --- autenticacion ---------------------------------------------------------
# El access token del realm master dura 60s: se renueva solo cada 40s.
kc_token() {
  local now; now=$(date +%s)
  if [ -n "$_TOKEN" ] && [ $((now - _TOKEN_TS)) -lt 40 ]; then
    echo "$_TOKEN"; return
  fi
  local resp
  resp=$(curl -s -X POST "$KC_URL/realms/master/protocol/openid-connect/token" \
      -H 'Content-Type: application/x-www-form-urlencoded' \
      -d "username=$KC_ADMIN" -d "password=$KC_ADMIN_PASSWORD" \
      -d 'grant_type=password' -d 'client_id=admin-cli')
  _TOKEN=$(echo "$resp" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("access_token",""))' 2>/dev/null || true)
  [ -n "$_TOKEN" ] || die "No se pudo autenticar en Keycloak ($KC_URL). Respuesta: $resp"
  _TOKEN_TS=$now
  echo "$_TOKEN"
}

# kc_api <METODO> <PATH> [BODY_JSON]  -> imprime el body; aborta si HTTP >= 400
kc_api() {
  local method="$1" path="$2" body="${3:-}"
  local tmp code
  tmp=$(mktemp)
  if [ -n "$body" ]; then
    code=$(curl -s -o "$tmp" -w '%{http_code}' -X "$method" "$KC_URL/admin/realms$path" \
        -H "Authorization: Bearer $(kc_token)" -H 'Content-Type: application/json' -d "$body")
  else
    code=$(curl -s -o "$tmp" -w '%{http_code}' -X "$method" "$KC_URL/admin/realms$path" \
        -H "Authorization: Bearer $(kc_token)")
  fi
  if [ "$code" -ge 400 ]; then
    cat "$tmp" >&2; rm -f "$tmp"
    die "$method $path -> HTTP $code"
  fi
  cat "$tmp"; rm -f "$tmp"
}

# Igual que kc_api pero devuelve el codigo HTTP y nunca aborta.
# Se usa para las operaciones que deben ser idempotentes (409 = ya existe).
kc_api_soft() {
  local method="$1" path="$2" body="${3:-}"
  local tmp code
  tmp=$(mktemp)
  if [ -n "$body" ]; then
    code=$(curl -s -o "$tmp" -w '%{http_code}' -X "$method" "$KC_URL/admin/realms$path" \
        -H "Authorization: Bearer $(kc_token)" -H 'Content-Type: application/json' -d "$body")
  else
    code=$(curl -s -o "$tmp" -w '%{http_code}' -X "$method" "$KC_URL/admin/realms$path" \
        -H "Authorization: Bearer $(kc_token)")
  fi
  rm -f "$tmp"
  echo "$code"
}

# jqp <expresion-python>  : el JSON de stdin llega en la variable d
jqp() { python3 -c "import sys,json;d=json.load(sys.stdin);$1" 2>/dev/null || true; }

# pick_id <campo> <valor> : id del primer elemento de la lista JSON de stdin
# cuyo <campo> vale <valor>. La API de Authorization Services devuelve el
# identificador en "_id" en lugar de "id", por eso se aceptan ambos.
pick_id() {
  KC_PICK_F="$1" KC_PICK_V="$2" python3 -c '
import sys, json, os
campo = os.environ["KC_PICK_F"]
valor = os.environ["KC_PICK_V"]
try:
    data = json.load(sys.stdin)
except Exception:
    raise SystemExit(0)
if isinstance(data, dict):
    data = [data]
for x in data:
    if x.get(campo) == valor:
        ident = x.get("id") or x.get("_id")
        if ident:
            print(ident)
        break
' 2>/dev/null || true
}

# urlenc <texto> : codifica un valor para usarlo en query string
urlenc() { KC_ENC="$1" python3 -c 'import os,urllib.parse;print(urllib.parse.quote(os.environ["KC_ENC"]))'; }
