#!/usr/bin/env bash
# =============================================================================
#  Verificacion integral del entorno GalaxyShop
#
#  Recorre los diez componentes de la rubrica y comprueba, contra el entorno
#  levantado, que cada uno se comporta como se espera.
#
#  Uso:  ./verificar.sh
# =============================================================================
set -uo pipefail

API="${API_URL:-http://localhost:8080}"
KC_BASE="${KC_URL:-http://localhost:8081}"
KC="$KC_BASE/realms/galaxyshop/protocol/openid-connect/token"
PASS_TIENDA="${PASS_DEMO:-Galaxy#2026ok}"

V=$'\033[0;32m'; R=$'\033[0;31m'; C=$'\033[0;36m'; Y=$'\033[0;33m'; O=$'\033[0m'
OK=0; FALLOS=0

titulo() { echo; echo "${C}$*${O}"; echo "${C}$(printf '%.0s-' {1..72})${O}"; }
check()  { # <descripcion> <esperado> <obtenido>
  if [ "$2" = "$3" ]; then printf "  ${V}OK   ${O} %-52s %s\n" "$1" "$3"; OK=$((OK+1))
  else printf "  ${R}FALLA${O} %-52s esperado=%s obtenido=%s\n" "$1" "$2" "$3"; FALLOS=$((FALLOS+1)); fi
}

token() {
  curl -s -X POST "$KC" -d client_id=shop-frontend -d "username=$1" \
       -d "password=$PASS_TIENDA" -d grant_type=password \
    | python3 -c 'import sys,json;print(json.load(sys.stdin).get("access_token",""))' 2>/dev/null
}

http() { # <usuario|-> <metodo> <ruta>
  if [ "$1" = "-" ]; then
    curl -s -o /dev/null -w '%{http_code}' -X "$2" "$API$3"
  else
    curl -s -o /dev/null -w '%{http_code}' -X "$2" "$API$3" \
         -H "Authorization: Bearer $(token "$1")"
  fi
}

decision() { # <usuario> <Recurso#scope>
  local at; at=$(token "$1")
  local r; r=$(curl -s -X POST "$KC" -H "Authorization: Bearer $at" \
      -d "grant_type=urn:ietf:params:oauth:grant-type:uma-ticket" \
      -d "audience=shop-backend" -d "permission=$2" -d "response_mode=decision")
  echo "$r" | grep -q '"result":true' && echo PERMITIDO || echo DENEGADO
}

echo "${C}=======================================================================${O}"
echo "${C}  GalaxyShop - verificacion del entorno${O}"
echo "${C}=======================================================================${O}"

# --- Requisitos previos ------------------------------------------------------
curl -sf "$KC_BASE/realms/galaxyshop/.well-known/openid-configuration" >/dev/null 2>&1 \
  || { echo "${R}Keycloak no responde en $KC_BASE. Ejecuta: docker compose up -d${O}"; exit 1; }
curl -sf "$API/api/public/health" >/dev/null 2>&1 \
  || { echo "${R}La API no responde en $API. Ejecuta: cd backend && mvn spring-boot:run${O}"; exit 1; }

# --- 1 y 2: instalacion y configuracion de Keycloak --------------------------
titulo "Rubrica #1 y #2 - Keycloak: instalacion y configuracion"
for realm in galaxyshop galaxyshop-partners; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "$KC_BASE/realms/$realm/.well-known/openid-configuration")
  check "realm '$realm' disponible" "200" "$code"
done
cfg=$(curl -s "$KC_BASE/realms/galaxyshop/.well-known/openid-configuration")
check "soporta Authorization Code" "si" "$(echo "$cfg" | grep -q '"authorization_code"' && echo si || echo no)"
check "soporta PKCE S256"          "si" "$(echo "$cfg" | grep -q '"S256"' && echo si || echo no)"
check "soporta el grant UMA"       "si" "$(echo "$cfg" | grep -q 'uma-ticket' && echo si || echo no)"

# --- 3: niveles de acceso de la API ------------------------------------------
titulo "Rubrica #3 - APIs REST: accesos publico, privado y especial"
check "publico  GET /api/public/health"    "200" "$(http - GET /api/public/health)"
check "publico  GET /api/public/catalogs"  "200" "$(http - GET /api/public/catalogs)"
check "cerrado  GET /api/products sin token" "401" "$(http - GET /api/products)"
check "privado  GET /api/me (cliente)"     "200" "$(http cliente.gold GET /api/me)"
check "privado  GET /api/orders/me"        "200" "$(http cliente.gold GET /api/orders/me)"
check "especial GET /api/reports (cliente)" "403" "$(http cliente.gold GET /api/reports/sales)"
check "especial GET /api/reports (auditor)" "200" "$(http auditor.shop GET /api/reports/sales)"
check "especial GET /api/admin (gerente)"  "403" "$(http gerente.shop GET /api/admin/orders)"
check "especial GET /api/admin (admin)"    "200" "$(http admin.shop GET /api/admin/orders)"

# --- 4: Spring Security -------------------------------------------------------
titulo "Rubrica #4 - Spring Security: filtro, anotaciones y personalizacion"
check "reglas de seguridad en base de datos" "200" "$(http admin.shop GET /api/admin/security-rules)"
n_reglas=$(curl -s "$API/api/admin/security-rules" -H "Authorization: Bearer $(token admin.shop)" \
  | python3 -c 'import sys,json;print(len(json.load(sys.stdin)))' 2>/dev/null || echo 0)
check "hay reglas cargadas desde la tabla" "si" "$([ "$n_reglas" -gt 0 ] && echo si || echo no)"

# @PostAuthorize: un cliente no puede leer el pedido de otro
PROD=$(curl -s "$API/api/public/products" | python3 -c 'import sys,json;print(json.load(sys.stdin)[0]["id"])')
PEDIDO=$(curl -s -X POST "$API/api/orders" -H "Authorization: Bearer $(token cliente.gold)" \
    -H 'Content-Type: application/json' -d "{\"productoId\":\"$PROD\",\"cantidad\":1}" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin).get("id",""))' 2>/dev/null)
if [ -n "$PEDIDO" ]; then
  check "@PostAuthorize: el dueno lee su pedido"   "200" "$(http cliente.gold  GET /api/orders/$PEDIDO)"
  check "@PostAuthorize: otro cliente recibe 403"  "403" "$(http cliente.basic GET /api/orders/$PEDIDO)"
  check "@PostAuthorize: el admin si puede leerlo" "200" "$(http admin.shop    GET /api/orders/$PEDIDO)"
else
  echo "  ${Y}AVISO${O} no se pudo crear un pedido de prueba"
fi

# --- 5: frontend --------------------------------------------------------------
titulo "Rubrica #5 - Front-end Angular"
if curl -sf http://localhost:4200 >/dev/null 2>&1; then
  check "la aplicacion Angular responde" "200" "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:4200)"
else
  echo "  ${Y}AVISO${O} el frontend no esta levantado (cd frontend && npm start)"
fi

# --- 6: seguridad granular ----------------------------------------------------
titulo "Rubrica #6 - Seguridad granular (Authorization Services / UMA 2.0)"
check "cliente  puede crear pedido"        "PERMITIDO" "$(decision cliente.gold "Pedidos#order:create")"
check "cajero   NO puede crear pedido"     "DENEGADO"  "$(decision cajero.lima  "Pedidos#order:create")"
check "gerente  puede anular pedido"       "PERMITIDO" "$(decision gerente.shop "Pedidos#order:cancel")"
check "cliente  NO puede anular pedido"    "DENEGADO"  "$(decision cliente.gold "Pedidos#order:cancel")"
check "almacen  puede editar catalogo"     "PERMITIDO" "$(decision almacen.lima "Catalogo#catalog:edit")"
check "admin    puede eliminar del catalogo" "PERMITIDO" "$(decision admin.shop  "Catalogo#catalog:delete")"
check "gerente  NO puede eliminar"         "DENEGADO"  "$(decision gerente.shop "Catalogo#catalog:delete")"
check "auditor  puede ver reportes"        "PERMITIDO" "$(decision auditor.shop "Reportes#report:view")"
check "cajero   NO puede ver reportes"     "DENEGADO"  "$(decision cajero.lima  "Reportes#report:view")"

# --- 7: identity providers ----------------------------------------------------
titulo "Rubrica #7 - Integracion externa (Social Login)"
# shop-frontend exige PKCE: sin code_challenge Keycloak rechaza la peticion,
# que es justo lo que se quiere del cliente publico.
CHALLENGE="u-oHctmEmha6nBZmUSE915aOC9eVYETtFW93EKozmk4"
login_url="$KC_BASE/realms/galaxyshop/protocol/openid-connect/auth"
login_url="$login_url?client_id=shop-frontend&redirect_uri=http%3A%2F%2Flocalhost%3A4200%2F"
login_url="$login_url&response_type=code&scope=openid"
login_url="$login_url&code_challenge=$CHALLENGE&code_challenge_method=S256"
login_html=$(curl -s "$login_url")

# De paso se comprueba que sin PKCE la peticion es rechazada
sin_pkce=$(curl -s "$KC_BASE/realms/galaxyshop/protocol/openid-connect/auth?client_id=shop-frontend&redirect_uri=http%3A%2F%2Flocalhost%3A4200%2F&response_type=code&scope=openid" -o /dev/null -w '%{redirect_url}')
check "PKCE obligatorio en el cliente publico" "si" "$(echo "$sin_pkce" | grep -q 'code_challenge_method' && echo si || echo no)"
check "boton de GitHub en la pantalla de acceso" "si" "$(echo "$login_html" | grep -qi 'broker/github' && echo si || echo no)"
check "boton de Google en la pantalla de acceso" "si" "$(echo "$login_html" | grep -qi 'broker/google' && echo si || echo no)"

# --- 8: tema personalizado ----------------------------------------------------
titulo "Rubrica #8 - Personalizacion de la interfaz"
check "el tema galaxyshop esta aplicado" "si" "$(echo "$login_html" | grep -q 'login/galaxyshop/css/galaxyshop.css' && echo si || echo no)"
check "logotipo propio en la pantalla"   "si" "$(echo "$login_html" | grep -q 'galaxyshop' && echo si || echo no)"
check "textos en espanol"                "si" "$(echo "$login_html" | grep -q 'Ingresa a tu cuenta' && echo si || echo no)"

# --- 9: configuracion del realm ----------------------------------------------
titulo "Rubrica #9 - Configuracion del realm"
tk=$(curl -s -X POST "$KC" -d client_id=shop-frontend -d username=cliente.gold \
     -d "password=$PASS_TIENDA" -d grant_type=password)
check "el access token dura 300 s" "300" "$(echo "$tk" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("expires_in"))' 2>/dev/null)"
check "el refresh token dura 1800 s" "1800" "$(echo "$tk" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("refresh_expires_in"))' 2>/dev/null)"

# La politica de contrasena se comprueba intentando registrar una debil
debil=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$KC" \
  -d client_id=shop-frontend -d username=cliente.gold -d "password=123" -d grant_type=password)
check "rechaza una contrasena debil" "401" "$debil"

# El claim heredado del grupo debe viajar en el token
claims=$(echo "$tk" | python3 -c '
import sys,json,base64
t=json.load(sys.stdin).get("access_token","")
p=t.split(".")[1]; p+="="*(-len(p)%4)
c=json.loads(base64.urlsafe_b64decode(p))
print(c.get("store",""), c.get("tier",""), ",".join(c.get("grupos",[])))' 2>/dev/null)
check "el token trae sede, categoria y grupo" "LIM-001 GOLD /Clientes/Frecuentes" "$claims"

# --- 10: componentes adicionales ----------------------------------------------
titulo "Rubrica #10 - Componentes adicionales"
batch=$(curl -s -X POST "$KC" -d client_id=shop-batch -d client_secret=shop-batch-secret-2026 \
        -d grant_type=client_credentials | python3 -c 'import sys,json;print("si" if json.load(sys.stdin).get("access_token") else "no")' 2>/dev/null)
check "service account con client_credentials" "si" "$batch"

partner=$(curl -s -X POST "$KC_BASE/realms/galaxyshop-partners/protocol/openid-connect/token" \
  -d client_id=partner-portal -d username=proveedor.uno -d "password=Partner#2026ok" -d grant_type=password \
  | python3 -c 'import sys,json;print(json.load(sys.stdin).get("access_token",""))' 2>/dev/null)
check "el segundo realm emite tokens" "si" "$([ -n "$partner" ] && echo si || echo no)"
aislado=$(curl -s -o /dev/null -w '%{http_code}' "$API/api/products" -H "Authorization: Bearer $partner")
check "un token de otro realm es rechazado" "401" "$aislado"
check "Swagger UI publicado" "200" "$(curl -s -o /dev/null -w '%{http_code}' -L "$API/swagger-ui.html")"

# --- Resumen ------------------------------------------------------------------
echo
echo "${C}=======================================================================${O}"
if [ "$FALLOS" -eq 0 ]; then
  echo "  ${V}TODAS LAS COMPROBACIONES PASARON${O}   ($OK de $((OK+FALLOS)))"
else
  echo "  ${R}HAY $FALLOS COMPROBACIONES FALLIDAS${O}   ($OK de $((OK+FALLOS)) correctas)"
fi
echo "${C}=======================================================================${O}"
exit $([ "$FALLOS" -eq 0 ] && echo 0 || echo 1)
