#!/usr/bin/env bash
# =============================================================================
#  Segundo realm: galaxyshop-partners
#  La rubrica pide 2 o mas realms. Este realm aisla a los proveedores externos:
#  viven en su propio directorio de identidades, con sus propias politicas,
#  y nunca comparten sesion con los usuarios de la tienda.
# =============================================================================
set -Eeuo pipefail
cd "$(dirname "$0")"
source ./lib.sh

REALM="galaxyshop-partners"

step "1/5  Realm '$REALM'"
REALM_JSON=$(cat <<'JSON'
{
  "realm": "galaxyshop-partners",
  "displayName": "GalaxyShop Partners",
  "enabled": true,
  "loginTheme": "galaxyshop",
  "internationalizationEnabled": true,
  "supportedLocales": ["es", "en"],
  "defaultLocale": "es",
  "passwordPolicy": "length(12) and upperCase(1) and lowerCase(1) and digits(1) and specialChars(1) and notUsername(undefined) and passwordHistory(5)",
  "accessTokenLifespan": 180,
  "ssoSessionIdleTimeout": 900,
  "ssoSessionMaxLifespan": 14400,
  "clientSessionIdleTimeout": 900,
  "revokeRefreshToken": true,
  "refreshTokenMaxReuse": 0,
  "registrationAllowed": false,
  "resetPasswordAllowed": true,
  "rememberMe": false,
  "loginWithEmailAllowed": true,
  "bruteForceProtected": true,
  "failureFactor": 3,
  "eventsEnabled": true,
  "adminEventsEnabled": true
}
JSON
)
if [ "$(kc_api_soft GET "/$REALM")" = "200" ]; then
  kc_api PUT "/$REALM" "$REALM_JSON" >/dev/null; ok "Realm '$REALM' actualizado"
else
  kc_api POST "" "$REALM_JSON" >/dev/null; ok "Realm '$REALM' creado"
fi
info "politica mas estricta: 12 caracteres, historial 5, sin reuso de refresh token"

step "2/5  Roles de realm"
for par in "supplier:Proveedor: consulta ordenes de compra dirigidas a su empresa" \
           "supplier-manager:Responsable del proveedor: ademas confirma despachos"; do
  n="${par%%:*}"; d="${par#*:}"
  body=$(python3 -c "import json,sys;print(json.dumps({'name':sys.argv[1],'description':sys.argv[2]}))" "$n" "$d")
  code=$(kc_api_soft POST "/$REALM/roles" "$body")
  [ "$code" = "201" ] && ok "rol '$n'" || info "rol '$n' ya existia"
done
# supplier-manager incluye a supplier
kc_api_soft POST "/$REALM/roles/supplier-manager/composites" "[$(kc_api GET "/$REALM/roles/supplier")]" >/dev/null
ok "rol compuesto 'supplier-manager' <- supplier"

step "3/5  Clientes"
crear_cliente_p() {
  local clientId="$1" body="$2"
  local code; code=$(kc_api_soft POST "/$REALM/clients" "$body")
  local id; id=$(kc_api GET "/$REALM/clients?clientId=$clientId" | pick_id clientId "$clientId")
  [ -n "$id" ] || die "no se pudo obtener el cliente '$clientId'"
  [ "$code" = "201" ] && ok "cliente '$clientId' creado" || { kc_api PUT "/$REALM/clients/$id" "$body" >/dev/null; info "cliente '$clientId' actualizado"; }
  echo "$id"
}

PORTAL_ID=$(crear_cliente_p "partner-portal" '{
  "clientId": "partner-portal",
  "name": "Portal de Proveedores",
  "description": "SPA publica del portal de proveedores (Authorization Code + PKCE)",
  "enabled": true, "publicClient": true, "protocol": "openid-connect",
  "standardFlowEnabled": true, "directAccessGrantsEnabled": true, "implicitFlowEnabled": false,
  "rootUrl": "http://localhost:4200", "baseUrl": "/partners",
  "redirectUris": ["http://localhost:4200/*"],
  "webOrigins": ["http://localhost:4200"],
  "attributes": { "pkce.code.challenge.method": "S256", "post.logout.redirect.uris": "http://localhost:4200/*" }
}')

SUPPLIER_API_ID=$(crear_cliente_p "supplier-api" '{
  "clientId": "supplier-api",
  "name": "API de Abastecimiento",
  "description": "Resource server del realm de proveedores",
  "enabled": true, "publicClient": false, "protocol": "openid-connect",
  "clientAuthenticatorType": "client-secret", "secret": "supplier-api-secret-2026",
  "standardFlowEnabled": false, "directAccessGrantsEnabled": true, "serviceAccountsEnabled": true
}')

# Roles a nivel de cliente
for par in "purchase-order-read:Consultar ordenes de compra" \
           "purchase-order-confirm:Confirmar el despacho de una orden" \
           "invoice-upload:Cargar comprobantes de pago"; do
  n="${par%%:*}"; d="${par#*:}"
  body=$(python3 -c "import json,sys;print(json.dumps({'name':sys.argv[1],'description':sys.argv[2]}))" "$n" "$d")
  code=$(kc_api_soft POST "/$REALM/clients/$SUPPLIER_API_ID/roles" "$body")
  [ "$code" = "201" ] && ok "rol de cliente 'supplier-api:$n'" || info "rol 'supplier-api:$n' ya existia"
done

# Mapper de audiencia para que el token del portal sirva contra supplier-api
kc_api_soft POST "/$REALM/clients/$PORTAL_ID/protocol-mappers/models" '{
  "name": "audiencia-supplier-api", "protocol": "openid-connect",
  "protocolMapper": "oidc-audience-mapper",
  "config": { "included.client.audience": "supplier-api", "id.token.claim": "false", "access.token.claim": "true" }
}' >/dev/null
ok "mapper de audiencia 'supplier-api' en partner-portal"

step "4/5  Grupos y subgrupos"
crear_grupo_p() {
  local padre_id="$1" nombre="$2" attrs="$3"
  local body; body=$(python3 -c "
import json,sys
print(json.dumps({'name': sys.argv[1], 'attributes': json.loads(sys.argv[2])}))" "$nombre" "$attrs")
  local id
  if [ -z "$padre_id" ]; then
    kc_api_soft POST "/$REALM/groups" "$body" >/dev/null
    id=$(kc_api GET "/$REALM/groups?search=$nombre&exact=true" | pick_id name "$nombre")
  else
    kc_api_soft POST "/$REALM/groups/$padre_id/children" "$body" >/dev/null
    id=$(kc_api GET "/$REALM/groups/$padre_id/children" | pick_id name "$nombre")
  fi
  [ -n "$id" ] || die "no se pudo crear el grupo '$nombre'"
  kc_api PUT "/$REALM/groups/$id" "$body" >/dev/null
  echo "$id"
}

G_PROV=$(crear_grupo_p "" "Proveedores" '{"unidad":["Abastecimiento"]}')
ok "grupo /Proveedores"

G_NAC=$(crear_grupo_p "$G_PROV" "Nacionales" '{"moneda":["PEN"],"pais":["PE"]}')
kc_api_soft POST "/$REALM/groups/$G_NAC/role-mappings/realm" "[$(kc_api GET "/$REALM/roles/supplier")]" >/dev/null
kc_api_soft POST "/$REALM/groups/$G_NAC/role-mappings/clients/$SUPPLIER_API_ID" \
  "[$(kc_api GET "/$REALM/clients/$SUPPLIER_API_ID/roles/purchase-order-read"),$(kc_api GET "/$REALM/clients/$SUPPLIER_API_ID/roles/invoice-upload")]" >/dev/null
ok "subgrupo /Proveedores/Nacionales -> supplier + purchase-order-read, invoice-upload"

G_INT=$(crear_grupo_p "$G_PROV" "Internacionales" '{"moneda":["USD"],"pais":["CL"]}')
kc_api_soft POST "/$REALM/groups/$G_INT/role-mappings/realm" "[$(kc_api GET "/$REALM/roles/supplier-manager")]" >/dev/null
kc_api_soft POST "/$REALM/groups/$G_INT/role-mappings/clients/$SUPPLIER_API_ID" \
  "[$(kc_api GET "/$REALM/clients/$SUPPLIER_API_ID/roles/purchase-order-read"),$(kc_api GET "/$REALM/clients/$SUPPLIER_API_ID/roles/purchase-order-confirm")]" >/dev/null
ok "subgrupo /Proveedores/Internacionales -> supplier-manager + purchase-order-read, purchase-order-confirm"

step "5/5  Usuarios"
PASS_PARTNER="${PASS_PARTNER:-Partner#2026ok}"
crear_usuario_p() {
  local u="$1" n="$2" a="$3" e="$4" g="$5"
  local body; body=$(python3 - "$u" "$n" "$a" "$e" "$g" "$PASS_PARTNER" <<'PY'
import json,sys
u,n,a,e,g,p=sys.argv[1:7]
print(json.dumps({"username":u,"firstName":n,"lastName":a,"email":e,"enabled":True,
  "emailVerified":True,"groups":[g],
  "credentials":[{"type":"password","value":p,"temporary":False}]}))
PY
)
  local code; code=$(kc_api_soft POST "/$REALM/users" "$body")
  [ "$code" = "201" ] && ok "usuario '$u' -> $g" || info "usuario '$u' ya existia"
}
crear_usuario_p "proveedor.uno"   "Pablo"  "Vargas"  "proveedor.uno@partners.test"   "/Proveedores/Nacionales"
crear_usuario_p "proveedor.intl"  "Paula"  "Contreras" "proveedor.intl@partners.test" "/Proveedores/Internacionales"

echo >&2
ok "Realm '$REALM' aprovisionado. Contrasena de los proveedores: $PASS_PARTNER"
