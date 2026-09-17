#!/usr/bin/env bash
# =============================================================================
#  Realm principal: galaxyshop
#  Cubre Rubrica #2 (realms, clientes, usuarios, grupos y subgrupos, roles y
#  alcances) y Rubrica #9 (idioma, politica de contrasena, tiempos de token).
# =============================================================================
set -Eeuo pipefail
cd "$(dirname "$0")"
source ./lib.sh

REALM="galaxyshop"

# -----------------------------------------------------------------------------
step "1/7  Realm '$REALM' (Rubrica #9: idioma, password policy, tokens)"
# -----------------------------------------------------------------------------
REALM_JSON=$(cat <<'JSON'
{
  "realm": "galaxyshop",
  "displayName": "GalaxyShop",
  "displayNameHtml": "<div class=\"kc-logo-text\"><span>GalaxyShop</span></div>",
  "enabled": true,

  "loginTheme": "galaxyshop",
  "accountTheme": "keycloak.v3",
  "emailTheme": "keycloak",

  "internationalizationEnabled": true,
  "supportedLocales": ["es", "en"],
  "defaultLocale": "es",

  "passwordPolicy": "length(10) and upperCase(1) and lowerCase(1) and digits(1) and specialChars(1) and notUsername(undefined) and passwordHistory(3) and hashAlgorithm(argon2)",

  "accessTokenLifespan": 300,
  "accessTokenLifespanForImplicitFlow": 900,
  "ssoSessionIdleTimeout": 1800,
  "ssoSessionMaxLifespan": 36000,
  "offlineSessionIdleTimeout": 2592000,
  "clientSessionIdleTimeout": 1800,
  "clientSessionMaxLifespan": 36000,
  "revokeRefreshToken": true,
  "refreshTokenMaxReuse": 2,
  "ssoSessionIdleTimeoutRememberMe": 7200,
  "ssoSessionMaxLifespanRememberMe": 86400,

  "registrationAllowed": true,
  "registrationEmailAsUsername": false,
  "rememberMe": true,
  "verifyEmail": false,
  "resetPasswordAllowed": true,
  "editUsernameAllowed": false,
  "loginWithEmailAllowed": true,
  "duplicateEmailsAllowed": false,

  "bruteForceProtected": true,
  "permanentLockout": false,
  "maxFailureWaitSeconds": 900,
  "waitIncrementSeconds": 60,
  "quickLoginCheckMilliSeconds": 1000,
  "minimumQuickLoginWaitSeconds": 60,
  "maxDeltaTimeSeconds": 43200,
  "failureFactor": 5,

  "eventsEnabled": true,
  "eventsExpiration": 604800,
  "enabledEventTypes": [
    "LOGIN", "LOGIN_ERROR", "LOGOUT", "REGISTER", "REGISTER_ERROR",
    "UPDATE_PASSWORD", "REFRESH_TOKEN", "REFRESH_TOKEN_ERROR",
    "CODE_TO_TOKEN", "CODE_TO_TOKEN_ERROR", "IDENTITY_PROVIDER_LOGIN",
    "CLIENT_LOGIN", "CLIENT_LOGIN_ERROR", "PERMISSION_TOKEN"
  ],
  "adminEventsEnabled": true,
  "adminEventsDetailsEnabled": true
}
JSON
)

if [ "$(kc_api_soft GET "/$REALM")" = "200" ]; then
  kc_api PUT "/$REALM" "$REALM_JSON" >/dev/null
  ok "Realm '$REALM' actualizado"
else
  kc_api POST "" "$REALM_JSON" >/dev/null
  ok "Realm '$REALM' creado"
fi
info "idioma=es | passwordPolicy=min 10, 1 mayus, 1 minus, 1 digito, 1 especial, historial 3"
info "accessToken=300s | refresh idle=1800s | refreshTokenMaxReuse=2"

# -----------------------------------------------------------------------------
step "2/7  Roles de realm"
# -----------------------------------------------------------------------------
crear_rol_realm() {
  local nombre="$1" desc="$2"
  local body; body=$(python3 -c "import json,sys;print(json.dumps({'name':sys.argv[1],'description':sys.argv[2]}))" "$nombre" "$desc")
  local code; code=$(kc_api_soft POST "/$REALM/roles" "$body")
  case "$code" in
    201) ok   "rol de realm '$nombre'" ;;
    409) kc_api PUT "/$REALM/roles/$nombre" "$body" >/dev/null; info "rol de realm '$nombre' (ya existia, actualizado)" ;;
    *)   die  "no se pudo crear el rol '$nombre' (HTTP $code)" ;;
  esac
}

crear_rol_realm "gs-customer"  "Cliente de la tienda: compra y consulta sus propios pedidos"
crear_rol_realm "gs-clerk"     "Cajero: registra pedidos en tienda"
crear_rol_realm "gs-warehouse" "Almacenero: administra el catalogo y el stock"
crear_rol_realm "gs-auditor"   "Auditor: acceso de solo lectura a reportes"
crear_rol_realm "gs-manager"   "Gerente de tienda: supervisa operaciones y cancela pedidos"
crear_rol_realm "gs-admin"     "Administrador funcional de GalaxyShop"

# --- roles compuestos ---
componer() {
  local padre="$1"; shift
  local hijos_json="["
  for h in "$@"; do
    local r; r=$(kc_api GET "/$REALM/roles/$h")
    hijos_json="$hijos_json$r,"
  done
  hijos_json="${hijos_json%,}]"
  kc_api_soft POST "/$REALM/roles/$padre/composites" "$hijos_json" >/dev/null
  ok "rol compuesto '$padre' <- $*"
}
componer "gs-manager" "gs-clerk" "gs-warehouse"
componer "gs-admin"   "gs-manager" "gs-auditor"

# -----------------------------------------------------------------------------
step "3/7  Client scopes (alcances propios del realm)"
# -----------------------------------------------------------------------------
crear_client_scope() {
  local nombre="$1" desc="$2" tipo="$3"   # tipo: default | optional
  local body
  body=$(python3 - "$nombre" "$desc" <<'PY'
import json,sys
print(json.dumps({
  "name": sys.argv[1],
  "description": sys.argv[2],
  "protocol": "openid-connect",
  "attributes": {
    "include.in.token.scope": "true",
    "display.on.consent.screen": "true",
    "consent.screen.text": sys.argv[2]
  }
}))
PY
)
  local code; code=$(kc_api_soft POST "/$REALM/client-scopes" "$body")
  local id; id=$(kc_api GET "/$REALM/client-scopes" | pick_id name "$nombre")
  [ -n "$id" ] || die "no se pudo obtener el client scope '$nombre'"
  if [ "$tipo" = "default" ]; then
    kc_api_soft PUT "/$REALM/default-default-client-scopes/$id" "" >/dev/null
  else
    kc_api_soft PUT "/$REALM/default-optional-client-scopes/$id" "" >/dev/null
  fi
  [ "$code" = "201" ] && ok "client scope '$nombre' ($tipo)" || info "client scope '$nombre' ya existia"
  echo "$id"
}

SCOPE_PROFILE_ID=$(crear_client_scope "shop-profile" "Perfil comercial: sede y categoria de cliente" "default")
SCOPE_ORDERS_ID=$(crear_client_scope  "shop-orders"  "Acceso a los pedidos de GalaxyShop"           "optional")
SCOPE_REPORTS_ID=$(crear_client_scope "shop-reports" "Acceso a los reportes de venta"               "optional")

# --- mappers del scope shop-profile: exponen atributos de usuario en el token ---
add_mapper() {
  local scope_id="$1" body="$2"
  kc_api_soft POST "/$REALM/client-scopes/$scope_id/protocol-mappers/models" "$body" >/dev/null
}

add_mapper "$SCOPE_PROFILE_ID" '{
  "name": "tier", "protocol": "openid-connect",
  "protocolMapper": "oidc-usermodel-attribute-mapper",
  "config": { "user.attribute": "tier", "claim.name": "tier", "jsonType.label": "String",
              "id.token.claim": "true", "access.token.claim": "true", "userinfo.token.claim": "true" }
}'
add_mapper "$SCOPE_PROFILE_ID" '{
  "name": "store", "protocol": "openid-connect",
  "protocolMapper": "oidc-usermodel-attribute-mapper",
  "config": { "user.attribute": "store", "claim.name": "store", "jsonType.label": "String",
              "id.token.claim": "true", "access.token.claim": "true", "userinfo.token.claim": "true" }
}'
add_mapper "$SCOPE_PROFILE_ID" '{
  "name": "grupos", "protocol": "openid-connect",
  "protocolMapper": "oidc-group-membership-mapper",
  "config": { "claim.name": "grupos", "full.path": "true",
              "id.token.claim": "true", "access.token.claim": "true", "userinfo.token.claim": "true" }
}'
add_mapper "$SCOPE_PROFILE_ID" '{
  "name": "audiencia-shop-backend", "protocol": "openid-connect",
  "protocolMapper": "oidc-audience-mapper",
  "config": { "included.client.audience": "shop-backend",
              "id.token.claim": "false", "access.token.claim": "true" }
}'
ok "mappers de 'shop-profile': tier, store, grupos, audience=shop-backend"

add_mapper "$SCOPE_REPORTS_ID" '{
  "name": "nivel-reporte", "protocol": "openid-connect",
  "protocolMapper": "oidc-hardcoded-claim-mapper",
  "config": { "claim.name": "nivel_reporte", "claim.value": "detallado", "jsonType.label": "String",
              "id.token.claim": "false", "access.token.claim": "true" }
}'
ok "mapper de 'shop-reports': nivel_reporte"

# -----------------------------------------------------------------------------
step "4/7  Clientes del realm"
# -----------------------------------------------------------------------------
crear_cliente() {
  local clientId="$1" body="$2"
  local code; code=$(kc_api_soft POST "/$REALM/clients" "$body")
  local id; id=$(kc_api GET "/$REALM/clients?clientId=$clientId" | pick_id clientId "$clientId")
  [ -n "$id" ] || die "no se pudo obtener el cliente '$clientId'"
  if [ "$code" != "201" ]; then
    kc_api PUT "/$REALM/clients/$id" "$body" >/dev/null
    info "cliente '$clientId' ya existia (actualizado)"
  else
    ok "cliente '$clientId' creado"
  fi
  echo "$id"
}

# --- 4.1 shop-frontend: SPA Angular, cliente publico con PKCE -----------------
FRONTEND_ID=$(crear_cliente "shop-frontend" '{
  "clientId": "shop-frontend",
  "name": "GalaxyShop Web (Angular)",
  "description": "SPA publica que autentica usuarios con Authorization Code + PKCE",
  "enabled": true,
  "publicClient": true,
  "protocol": "openid-connect",
  "standardFlowEnabled": true,
  "directAccessGrantsEnabled": true,
  "implicitFlowEnabled": false,
  "serviceAccountsEnabled": false,
  "frontchannelLogout": true,
  "rootUrl": "http://localhost:4200",
  "baseUrl": "/",
  "redirectUris": ["http://localhost:4200/*"],
  "webOrigins": ["http://localhost:4200", "+"],
  "adminUrl": "http://localhost:4200",
  "attributes": {
    "pkce.code.challenge.method": "S256",
    "post.logout.redirect.uris": "http://localhost:4200/*",
    "access.token.lifespan": "300",
    "oauth2.device.authorization.grant.enabled": "false",
    "backchannel.logout.session.required": "true"
  },
  "defaultClientScopes": ["web-origins", "acr", "profile", "roles", "basic", "email", "shop-profile"],
  "optionalClientScopes": ["address", "phone", "offline_access", "microprofile-jwt", "shop-orders", "shop-reports"]
}')

# --- 4.2 shop-backend: API REST, resource server + Authorization Services -----
BACKEND_ID=$(crear_cliente "shop-backend" '{
  "clientId": "shop-backend",
  "name": "GalaxyShop API (Spring Boot)",
  "description": "Resource server que valida el JWT y expone la seguridad granular (UMA 2.0)",
  "enabled": true,
  "publicClient": false,
  "protocol": "openid-connect",
  "clientAuthenticatorType": "client-secret",
  "secret": "shop-backend-secret-2026",
  "standardFlowEnabled": false,
  "directAccessGrantsEnabled": true,
  "serviceAccountsEnabled": true,
  "authorizationServicesEnabled": true,
  "attributes": {
    "access.token.lifespan": "300"
  },
  "defaultClientScopes": ["web-origins", "acr", "profile", "roles", "basic", "email"],
  "optionalClientScopes": ["address", "phone", "offline_access", "microprofile-jwt"]
}')

# --- 4.3 shop-batch: proceso desatendido (client_credentials) -----------------
BATCH_ID=$(crear_cliente "shop-batch" '{
  "clientId": "shop-batch",
  "name": "GalaxyShop Batch de reposicion",
  "description": "Cliente de maquina a maquina: repone stock por las noches (client_credentials)",
  "enabled": true,
  "publicClient": false,
  "protocol": "openid-connect",
  "clientAuthenticatorType": "client-secret",
  "secret": "shop-batch-secret-2026",
  "standardFlowEnabled": false,
  "directAccessGrantsEnabled": false,
  "serviceAccountsEnabled": true,
  "authorizationServicesEnabled": false,
  "defaultClientScopes": ["web-origins", "acr", "profile", "roles", "basic", "email"]
}')

# -----------------------------------------------------------------------------
step "5/7  Roles a nivel de cliente (shop-backend)"
# -----------------------------------------------------------------------------
crear_rol_cliente() {
  local nombre="$1" desc="$2"
  local body; body=$(python3 -c "import json,sys;print(json.dumps({'name':sys.argv[1],'description':sys.argv[2]}))" "$nombre" "$desc")
  local code; code=$(kc_api_soft POST "/$REALM/clients/$BACKEND_ID/roles" "$body")
  case "$code" in
    201) ok   "rol de cliente 'shop-backend:$nombre'" ;;
    409) info "rol de cliente 'shop-backend:$nombre' ya existia" ;;
    *)   die  "no se pudo crear el rol de cliente '$nombre' (HTTP $code)" ;;
  esac
}

crear_rol_cliente "catalog-read"    "Consultar catalogos y productos"
crear_rol_cliente "catalog-write"   "Crear y modificar productos del catalogo"
crear_rol_cliente "catalog-delete"  "Eliminar productos del catalogo"
crear_rol_cliente "order-read-own"  "Consultar unicamente los pedidos propios"
crear_rol_cliente "order-read-all"  "Consultar los pedidos de todos los clientes"
crear_rol_cliente "order-create"    "Registrar nuevos pedidos"
crear_rol_cliente "order-cancel"    "Anular pedidos existentes"
crear_rol_cliente "customer-read"   "Consultar la ficha de clientes"
crear_rol_cliente "report-read"     "Consultar reportes de venta"

# El batch solo necesita reponer stock -> se le asigna catalog-write a su service account
SA_BATCH=$(kc_api GET "/$REALM/clients/$BATCH_ID/service-account-user" | jqp "print(d['id'])")
ROL_CATALOG_WRITE=$(kc_api GET "/$REALM/clients/$BACKEND_ID/roles/catalog-write")
ROL_CATALOG_READ=$(kc_api GET "/$REALM/clients/$BACKEND_ID/roles/catalog-read")
kc_api_soft POST "/$REALM/users/$SA_BATCH/role-mappings/clients/$BACKEND_ID" "[$ROL_CATALOG_WRITE,$ROL_CATALOG_READ]" >/dev/null
ok "service account de 'shop-batch' -> catalog-read, catalog-write"

# -----------------------------------------------------------------------------
step "6/7  Grupos y subgrupos (con roles y atributos heredados)"
# -----------------------------------------------------------------------------
# crear_grupo <ruta-padre|""> <nombre> <json-attributes>  -> imprime el id
crear_grupo() {
  local padre_id="$1" nombre="$2" attrs="${3:-{\}}"
  local body; body=$(python3 -c "
import json,sys
print(json.dumps({'name': sys.argv[1], 'attributes': json.loads(sys.argv[2])}))" "$nombre" "$attrs")
  local path code id
  if [ -z "$padre_id" ]; then
    kc_api_soft POST "/$REALM/groups" "$body" >/dev/null
    id=$(kc_api GET "/$REALM/groups?search=$nombre&exact=true" | pick_id name "$nombre")
  else
    kc_api_soft POST "/$REALM/groups/$padre_id/children" "$body" >/dev/null
    id=$(kc_api GET "/$REALM/groups/$padre_id/children" | pick_id name "$nombre")
  fi
  [ -n "$id" ] || die "no se pudo crear/obtener el grupo '$nombre'"
  # asegura los atributos aun si el grupo ya existia
  kc_api PUT "/$REALM/groups/$id" "$body" >/dev/null
  echo "$id"
}

# asignar_roles_grupo <group_id> <realm_roles...> -- <client_roles...>
asignar_rol_realm_a_grupo() {
  local gid="$1"; shift
  local arr="["
  for r in "$@"; do arr="$arr$(kc_api GET "/$REALM/roles/$r"),"; done
  arr="${arr%,}]"
  kc_api_soft POST "/$REALM/groups/$gid/role-mappings/realm" "$arr" >/dev/null
}
asignar_rol_cliente_a_grupo() {
  local gid="$1"; shift
  local arr="["
  for r in "$@"; do arr="$arr$(kc_api GET "/$REALM/clients/$BACKEND_ID/roles/$r"),"; done
  arr="${arr%,}]"
  kc_api_soft POST "/$REALM/groups/$gid/role-mappings/clients/$BACKEND_ID" "$arr" >/dev/null
}

# --- /Tiendas -----------------------------------------------------------------
G_TIENDAS=$(crear_grupo "" "Tiendas" '{"unidad":["Operaciones"]}')
ok "grupo /Tiendas"

G_LIMA=$(crear_grupo "$G_TIENDAS" "Lima" '{"store":["LIM-001"],"ciudad":["Lima"]}')
G_LIMA_CAJA=$(crear_grupo "$G_LIMA" "Caja" '{"funcion":["Caja"]}')
asignar_rol_realm_a_grupo   "$G_LIMA_CAJA" "gs-clerk"
asignar_rol_cliente_a_grupo "$G_LIMA_CAJA" "catalog-read" "order-create" "order-read-all" "customer-read"
ok "subgrupo /Tiendas/Lima/Caja -> gs-clerk + catalog-read, order-create, order-read-all, customer-read"

G_LIMA_ALM=$(crear_grupo "$G_LIMA" "Almacen" '{"funcion":["Almacen"]}')
asignar_rol_realm_a_grupo   "$G_LIMA_ALM" "gs-warehouse"
asignar_rol_cliente_a_grupo "$G_LIMA_ALM" "catalog-read" "catalog-write"
ok "subgrupo /Tiendas/Lima/Almacen -> gs-warehouse + catalog-read, catalog-write"

G_AQP=$(crear_grupo "$G_TIENDAS" "Arequipa" '{"store":["AQP-001"],"ciudad":["Arequipa"]}')
G_AQP_CAJA=$(crear_grupo "$G_AQP" "Caja" '{"funcion":["Caja"]}')
asignar_rol_realm_a_grupo   "$G_AQP_CAJA" "gs-clerk"
asignar_rol_cliente_a_grupo "$G_AQP_CAJA" "catalog-read" "order-create" "order-read-all"
ok "subgrupo /Tiendas/Arequipa/Caja -> gs-clerk + catalog-read, order-create, order-read-all"

# --- /Clientes ----------------------------------------------------------------
G_CLIENTES=$(crear_grupo "" "Clientes" '{"unidad":["Comercial"]}')
ok "grupo /Clientes"

G_CLI_GOLD=$(crear_grupo "$G_CLIENTES" "Frecuentes" '{"tier":["GOLD"],"descuento":["15"]}')
asignar_rol_realm_a_grupo   "$G_CLI_GOLD" "gs-customer"
asignar_rol_cliente_a_grupo "$G_CLI_GOLD" "catalog-read" "order-create" "order-read-own"
ok "subgrupo /Clientes/Frecuentes -> gs-customer (tier=GOLD)"

G_CLI_NEW=$(crear_grupo "$G_CLIENTES" "Nuevos" '{"tier":["BASIC"],"descuento":["0"]}')
asignar_rol_realm_a_grupo   "$G_CLI_NEW" "gs-customer"
asignar_rol_cliente_a_grupo "$G_CLI_NEW" "catalog-read" "order-create" "order-read-own"
ok "subgrupo /Clientes/Nuevos -> gs-customer (tier=BASIC)"

# --- /Corporativo -------------------------------------------------------------
G_CORP=$(crear_grupo "" "Corporativo" '{"unidad":["Sede Central"]}')
ok "grupo /Corporativo"

G_CORP_GER=$(crear_grupo "$G_CORP" "Gerencia" '{"store":["CENTRAL"]}')
asignar_rol_realm_a_grupo   "$G_CORP_GER" "gs-manager"
asignar_rol_cliente_a_grupo "$G_CORP_GER" "catalog-read" "catalog-write" "order-read-all" "order-cancel" "customer-read" "report-read"
ok "subgrupo /Corporativo/Gerencia -> gs-manager + 6 roles de cliente"

G_CORP_TI=$(crear_grupo "$G_CORP" "TI" '{"store":["CENTRAL"]}')
asignar_rol_realm_a_grupo   "$G_CORP_TI" "gs-admin"
asignar_rol_cliente_a_grupo "$G_CORP_TI" "catalog-read" "catalog-write" "catalog-delete" "order-read-all" "order-cancel" "customer-read" "report-read"
ok "subgrupo /Corporativo/TI -> gs-admin + 7 roles de cliente"

G_CORP_AUD=$(crear_grupo "$G_CORP" "Auditoria" '{"store":["CENTRAL"]}')
asignar_rol_realm_a_grupo   "$G_CORP_AUD" "gs-auditor"
asignar_rol_cliente_a_grupo "$G_CORP_AUD" "catalog-read" "order-read-all" "report-read"
ok "subgrupo /Corporativo/Auditoria -> gs-auditor + catalog-read, order-read-all, report-read"


# -----------------------------------------------------------------------------
step "7/8  User Profile declarativo (atributos propios del negocio)"
# -----------------------------------------------------------------------------
# Desde Keycloak 24 el User Profile es obligatorio: los atributos que no estan
# declarados se descartan silenciosamente al guardar el usuario. Aqui se
# declaran los tres atributos del caso para que lleguen al token.
PERFIL_ACTUAL=$(kc_api GET "/$REALM/users/profile")
PERFIL_NUEVO=$(python3 - "$PERFIL_ACTUAL" <<'PY'
import json, sys

perfil = json.loads(sys.argv[1])
existentes = {a["name"] for a in perfil.get("attributes", [])}

nuevos = [
    {
        "name": "tier",
        "displayName": "Categoria de cliente",
        "multivalued": False,
        "permissions": {"view": ["admin", "user"], "edit": ["admin"]},
        "validations": {"options": {"options": ["BASIC", "GOLD", "PLATINUM"]}},
        "annotations": {"inputType": "select"},
        "required": None,
    },
    {
        "name": "store",
        "displayName": "Sede asignada",
        "multivalued": False,
        "permissions": {"view": ["admin", "user"], "edit": ["admin"]},
        "validations": {"length": {"min": 2, "max": 40}},
        "annotations": {"inputType": "text"},
        "required": None,
    },
    {
        "name": "documento",
        "displayName": "Documento de identidad",
        "multivalued": False,
        "permissions": {"view": ["admin", "user"], "edit": ["admin", "user"]},
        "validations": {"length": {"min": 8, "max": 12}},
        "annotations": {"inputType": "text"},
        "required": None,
    },
]

for attr in nuevos:
    if attr["name"] not in existentes:
        perfil.setdefault("attributes", []).append(
            {k: v for k, v in attr.items() if v is not None})

print(json.dumps(perfil))
PY
)
kc_api PUT "/$REALM/users/profile" "$PERFIL_NUEVO" >/dev/null
ok "atributos declarados: tier, store, documento"

# -----------------------------------------------------------------------------
step "8/8  Usuarios"
# -----------------------------------------------------------------------------
PASS_DEMO="${PASS_DEMO:-Galaxy#2026ok}"

crear_usuario() {
  local username="$1" nombre="$2" apellido="$3" email="$4" grupo_path="$5"
  local body
  body=$(python3 - "$username" "$nombre" "$apellido" "$email" "$grupo_path" "$PASS_DEMO" <<'PY'
import json,sys
u,n,a,e,g,p = sys.argv[1:7]
print(json.dumps({
  "username": u, "firstName": n, "lastName": a, "email": e,
  "enabled": True, "emailVerified": True,
  "groups": [g],
  "credentials": [{"type": "password", "value": p, "temporary": False}]
}))
PY
)
  local code; code=$(kc_api_soft POST "/$REALM/users" "$body")
  local id; id=$(kc_api GET "/$REALM/users?username=$username&exact=true" | pick_id username "$username")
  [ -n "$id" ] || die "no se pudo crear/obtener el usuario '$username'"
  if [ "$code" != "201" ]; then
    info "usuario '$username' ya existia"
  else
    ok "usuario '$username' -> $grupo_path"
  fi
  echo "$id"
}

crear_usuario "admin.shop"   "Ana"    "Delgado"  "admin.shop@galaxyshop.test"   "/Corporativo/TI"        >/dev/null
crear_usuario "gerente.shop" "Gabriel" "Ramos"   "gerente.shop@galaxyshop.test" "/Corporativo/Gerencia"  >/dev/null
crear_usuario "auditor.shop" "Aurora" "Quispe"   "auditor.shop@galaxyshop.test" "/Corporativo/Auditoria" >/dev/null
crear_usuario "cajero.lima"  "Carla"  "Mendoza"  "cajero.lima@galaxyshop.test"  "/Tiendas/Lima/Caja"     >/dev/null
crear_usuario "almacen.lima" "Alonso" "Prado"    "almacen.lima@galaxyshop.test" "/Tiendas/Lima/Almacen"  >/dev/null
crear_usuario "cajero.aqp"   "Cecilia" "Torres"  "cajero.aqp@galaxyshop.test"   "/Tiendas/Arequipa/Caja" >/dev/null
U_GOLD=$(crear_usuario "cliente.gold"  "Gonzalo" "Huaman" "cliente.gold@galaxyshop.test"  "/Clientes/Frecuentes")
U_BASIC=$(crear_usuario "cliente.basic" "Beatriz" "Silva" "cliente.basic@galaxyshop.test" "/Clientes/Nuevos")

# Atributos propios del usuario (ademas de los heredados del grupo)
kc_api PUT "/$REALM/users/$U_GOLD" '{
  "username": "cliente.gold", "firstName": "Gonzalo", "lastName": "Huaman",
  "email": "cliente.gold@galaxyshop.test", "enabled": true, "emailVerified": true,
  "attributes": { "tier": ["GOLD"], "store": ["LIM-001"], "documento": ["44556677"] }
}' >/dev/null
kc_api PUT "/$REALM/users/$U_BASIC" '{
  "username": "cliente.basic", "firstName": "Beatriz", "lastName": "Silva",
  "email": "cliente.basic@galaxyshop.test", "enabled": true, "emailVerified": true,
  "attributes": { "tier": ["BASIC"], "store": ["AQP-001"], "documento": ["33221100"] }
}' >/dev/null
ok "atributos tier/store/documento aplicados a los clientes"

echo
ok "Realm '$REALM' aprovisionado. Contrasena de todos los usuarios demo: $PASS_DEMO"
