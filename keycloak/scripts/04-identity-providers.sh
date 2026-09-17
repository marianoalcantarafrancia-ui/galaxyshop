#!/usr/bin/env bash
# =============================================================================
#  Rubrica #7 - Integracion externa: Social Login con Identity Providers
#  Se registran GitHub y Google como IdP del realm galaxyshop. Las credenciales
#  llegan por variable de entorno: nunca se versionan en el repositorio.
# =============================================================================
set -Eeuo pipefail
cd "$(dirname "$0")"
source ./lib.sh

REALM="galaxyshop"

GITHUB_CLIENT_ID="${GITHUB_CLIENT_ID:-changeme}"
GITHUB_CLIENT_SECRET="${GITHUB_CLIENT_SECRET:-changeme}"
GOOGLE_CLIENT_ID="${GOOGLE_CLIENT_ID:-changeme}"
GOOGLE_CLIENT_SECRET="${GOOGLE_CLIENT_SECRET:-changeme}"

step "Identity Providers del realm '$REALM'"

registrar_idp() {
  local alias="$1" body="$2"
  if [ "$(kc_api_soft GET "/$REALM/identity-provider/instances/$alias")" = "200" ]; then
    kc_api PUT "/$REALM/identity-provider/instances/$alias" "$body" >/dev/null
    info "IdP '$alias' actualizado"
  else
    kc_api POST "/$REALM/identity-provider/instances" "$body" >/dev/null
    ok "IdP '$alias' registrado"
  fi
}

# --- GitHub -------------------------------------------------------------------
registrar_idp github "$(python3 - "$GITHUB_CLIENT_ID" "$GITHUB_CLIENT_SECRET" <<'PY'
import json, sys
print(json.dumps({
  "alias": "github",
  "displayName": "Ingresar con GitHub",
  "providerId": "github",
  "enabled": True,
  "trustEmail": True,
  "storeToken": False,
  "addReadTokenRoleOnCreate": False,
  "linkOnly": False,
  "firstBrokerLoginFlowAlias": "first broker login",
  "config": {
    "clientId": sys.argv[1],
    "clientSecret": sys.argv[2],
    "defaultScope": "user:email",
    "syncMode": "IMPORT",
    "useJwksUrl": "true"
  }
}))
PY
)"

# --- Google -------------------------------------------------------------------
registrar_idp google "$(python3 - "$GOOGLE_CLIENT_ID" "$GOOGLE_CLIENT_SECRET" <<'PY'
import json, sys
print(json.dumps({
  "alias": "google",
  "displayName": "Ingresar con Google",
  "providerId": "google",
  "enabled": True,
  "trustEmail": True,
  "storeToken": False,
  "linkOnly": False,
  "firstBrokerLoginFlowAlias": "first broker login",
  "config": {
    "clientId": sys.argv[1],
    "clientSecret": sys.argv[2],
    "defaultScope": "openid profile email",
    "syncMode": "IMPORT",
    "useJwksUrl": "true",
    "hostedDomain": ""
  }
}))
PY
)"

# --- Mappers: todo usuario que entra por un IdP social nace como cliente nuevo ---
mapper_grupo() {
  # 'oidc-hardcoded-group-idp-mapper' es el unico tipo de mapper de grupo que
  # aceptan los proveedores sociales: asigna el grupo a todo el que entre por ahi.
  local alias="$1"
  local body; body=$(python3 - "$alias" <<'PY'
import json, sys
print(json.dumps({
  "name": "alta-como-cliente-nuevo",
  "identityProviderAlias": sys.argv[1],
  "identityProviderMapper": "oidc-hardcoded-group-idp-mapper",
  "config": {"syncMode": "INHERIT", "group": "/Clientes/Nuevos"}
}))
PY
)
  local existe; existe=$(kc_api GET "/$REALM/identity-provider/instances/$alias/mappers" | pick_id name "alta-como-cliente-nuevo")
  if [ -n "$existe" ]; then
    info "mapper de grupo de '$alias' ya existia"
  else
    kc_api POST "/$REALM/identity-provider/instances/$alias/mappers" "$body" >/dev/null
    ok "mapper de '$alias': los usuarios sociales entran a /Clientes/Nuevos"
  fi
}
mapper_grupo github
mapper_grupo google

echo >&2
if [ "$GITHUB_CLIENT_ID" = "changeme" ] || [ "$GOOGLE_CLIENT_ID" = "changeme" ]; then
  warn "Los IdP quedaron registrados con credenciales de ejemplo."
  warn "Para probar el login social real, completa en el archivo .env:"
  warn "  GITHUB_CLIENT_ID / GITHUB_CLIENT_SECRET"
  warn "    -> https://github.com/settings/developers  (OAuth App)"
  warn "    -> Authorization callback URL:"
  warn "       $KC_URL/realms/$REALM/broker/github/endpoint"
  warn "  GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET"
  warn "    -> https://console.cloud.google.com/apis/credentials"
  warn "    -> URI de redireccion autorizado:"
  warn "       $KC_URL/realms/$REALM/broker/google/endpoint"
  warn "y vuelve a ejecutar este script."
else
  ok "IdP configurados con credenciales reales"
fi
