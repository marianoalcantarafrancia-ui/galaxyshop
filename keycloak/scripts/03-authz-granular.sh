#!/usr/bin/env bash
# =============================================================================
#  Rubrica #6 - Seguridad granular sobre el cliente 'shop-backend'
#  Modelo completo de Keycloak Authorization Services (UMA 2.0):
#     Scopes  ->  Resources  ->  Policies  ->  Permissions
#  La decision de acceso deja de vivir en el codigo y pasa a ser un dato
#  administrable desde la consola de Keycloak.
# =============================================================================
set -Eeuo pipefail
cd "$(dirname "$0")"
source ./lib.sh

REALM="galaxyshop"
CID=$(kc_api GET "/$REALM/clients?clientId=shop-backend" | pick_id clientId "shop-backend")
[ -n "$CID" ] || die "El cliente 'shop-backend' no existe. Ejecuta antes 01-realm-galaxyshop.sh"
AZ="/$REALM/clients/$CID/authz/resource-server"

# -----------------------------------------------------------------------------
step "1/5  Configuracion del resource server"
# -----------------------------------------------------------------------------
kc_api PUT "$AZ" '{
  "allowRemoteResourceManagement": true,
  "policyEnforcementMode": "ENFORCING",
  "decisionStrategy": "UNANIMOUS"
}' >/dev/null
ok "policyEnforcementMode=ENFORCING, decisionStrategy=UNANIMOUS"

# Keycloak crea un permiso por defecto que concede todo: lo retiramos para que
# el unico acceso posible sea el que definimos explicitamente abajo.
DEF_PERM=$(kc_api GET "$AZ/permission?name=Default%20Permission" | pick_id name "Default Permission")
if [ -n "$DEF_PERM" ]; then
  kc_api DELETE "$AZ/permission/$DEF_PERM" >/dev/null
  ok "eliminado el 'Default Permission' que concedia acceso total"
fi
DEF_RES=$(kc_api GET "$AZ/resource?name=Default%20Resource&exactName=true" | pick_id name "Default Resource")
if [ -n "$DEF_RES" ]; then
  kc_api DELETE "$AZ/resource/$DEF_RES" >/dev/null
  ok "eliminado el 'Default Resource' de ejemplo"
fi

# La 'Default Policy' que genera Keycloak es de tipo JavaScript. Al exportar el
# realm viaja en el JSON y, al reimportarlo, el servidor la rechaza con
# "Script upload is disabled" (subir scripts esta deshabilitado por seguridad).
# Como no se usa, se elimina: asi el export queda importable tal cual.
DEF_POL=$(kc_api GET "$AZ/policy?name=Default%20Policy" | pick_id name "Default Policy")
if [ -n "$DEF_POL" ]; then
  kc_api DELETE "$AZ/policy/$DEF_POL" >/dev/null
  ok "eliminada la 'Default Policy' de tipo JavaScript (impide reimportar el realm)"
fi

# -----------------------------------------------------------------------------
step "2/5  Scopes de autorizacion"
# -----------------------------------------------------------------------------
crear_scope() {
  local nombre="$1" icono="$2"
  local body; body=$(python3 -c "import json,sys;print(json.dumps({'name':sys.argv[1],'displayName':sys.argv[2]}))" "$nombre" "$icono")
  kc_api_soft POST "$AZ/scope" "$body" >/dev/null
  local id; id=$(kc_api GET "$AZ/scope?name=$(urlenc "$nombre")" | pick_id name "$nombre")
  [ -n "$id" ] || die "no se pudo crear el scope '$nombre'"
  ok "scope '$nombre'"
  echo "$id"
}

S_CAT_VIEW=$(crear_scope   "catalog:view"   "Ver el catalogo")
S_CAT_EDIT=$(crear_scope   "catalog:edit"   "Editar productos")
S_CAT_DEL=$(crear_scope    "catalog:delete" "Eliminar productos")
S_ORD_VIEW=$(crear_scope   "order:view"     "Ver pedidos")
S_ORD_CREATE=$(crear_scope "order:create"   "Registrar un pedido")
S_ORD_CANCEL=$(crear_scope "order:cancel"   "Anular un pedido")
S_REP_VIEW=$(crear_scope   "report:view"    "Ver reportes de venta")

# -----------------------------------------------------------------------------
step "3/5  Resources (recursos protegidos)"
# -----------------------------------------------------------------------------
crear_resource() {
  local nombre="$1" tipo="$2" uris="$3" scopes="$4"
  local body; body=$(python3 - "$nombre" "$tipo" "$uris" "$scopes" <<'PY'
import json,sys
n,t,u,s = sys.argv[1:5]
print(json.dumps({
  "name": n, "displayName": n, "type": t,
  "uris": u.split(","),
  "scopes": [{"name": x} for x in s.split(",")],
  "ownerManagedAccess": False
}))
PY
)
  kc_api_soft POST "$AZ/resource" "$body" >/dev/null
  local id; id=$(kc_api GET "$AZ/resource?name=$(urlenc "$nombre")&exactName=true" | pick_id name "$nombre")
  [ -n "$id" ] || die "no se pudo crear el resource '$nombre'"
  ok "resource '$nombre'  [$uris]"
  echo "$id"
}

R_CATALOGO=$(crear_resource "Catalogo" "urn:galaxyshop:resources:catalog" \
  "/api/catalogs,/api/catalogs/*,/api/products,/api/products/*" \
  "catalog:view,catalog:edit,catalog:delete")

R_PEDIDOS=$(crear_resource "Pedidos" "urn:galaxyshop:resources:order" \
  "/api/orders,/api/orders/*" \
  "order:view,order:create,order:cancel")

R_REPORTES=$(crear_resource "Reportes" "urn:galaxyshop:resources:report" \
  "/api/reports,/api/reports/*" \
  "report:view")

# -----------------------------------------------------------------------------
step "4/5  Policies (quien puede)"
# -----------------------------------------------------------------------------
crear_policy() {
  local tipo="$1" nombre="$2" body="$3"
  kc_api_soft POST "$AZ/policy/$tipo" "$body" >/dev/null
  local id; id=$(kc_api GET "$AZ/policy?name=$(urlenc "$nombre")" | pick_id name "$nombre")
  [ -n "$id" ] || die "no se pudo crear la policy '$nombre'"
  ok "policy [$tipo] '$nombre'"
  echo "$id"
}

# --- policies por rol de realm ---
P_CLIENTE=$(crear_policy role "Rol Cliente" '{
  "name": "Rol Cliente",
  "description": "Aplica a cualquier usuario con el rol de realm gs-customer",
  "logic": "POSITIVE", "decisionStrategy": "UNANIMOUS",
  "roles": [{"id": "gs-customer", "required": true}]
}')

P_TIENDA=$(crear_policy role "Rol Personal de Tienda" '{
  "name": "Rol Personal de Tienda",
  "description": "Cajeros o almaceneros (basta uno de los dos roles)",
  "logic": "POSITIVE", "decisionStrategy": "AFFIRMATIVE",
  "roles": [{"id": "gs-clerk", "required": false}, {"id": "gs-warehouse", "required": false}]
}')

P_ADMIN=$(crear_policy role "Rol Administrador" '{
  "name": "Rol Administrador",
  "description": "Solo administradores funcionales de GalaxyShop",
  "logic": "POSITIVE", "decisionStrategy": "UNANIMOUS",
  "roles": [{"id": "gs-admin", "required": true}]
}')

# --- policies por grupo ---
P_GERENCIA=$(crear_policy group "Grupo Gerencia" '{
  "name": "Grupo Gerencia",
  "description": "Miembros del subgrupo /Corporativo/Gerencia",
  "logic": "POSITIVE",
  "groupsClaim": "",
  "groups": [{"path": "/Corporativo/Gerencia", "extendChildren": false}]
}')

P_AUDITORIA=$(crear_policy group "Grupo Auditoria" '{
  "name": "Grupo Auditoria",
  "description": "Miembros del subgrupo /Corporativo/Auditoria",
  "logic": "POSITIVE",
  "groupsClaim": "",
  "groups": [{"path": "/Corporativo/Auditoria", "extendChildren": false}]
}')

# --- policy por horario: las anulaciones solo se permiten en horario de oficina ---
P_HORARIO=$(crear_policy time "Horario Laboral" '{
  "name": "Horario Laboral",
  "description": "Operacion permitida unicamente entre las 06:00 y las 22:59",
  "logic": "POSITIVE",
  "hour": "6", "hourEnd": "22"
}')

# --- policies agregadas ---
P_CLI_O_TIENDA=$(crear_policy aggregate "Cliente o Personal de Tienda" "$(python3 - "$P_CLIENTE" "$P_TIENDA" <<'PY'
import json,sys
print(json.dumps({
  "name": "Cliente o Personal de Tienda",
  "description": "Basta con ser cliente registrado O personal de tienda",
  "logic": "POSITIVE", "decisionStrategy": "AFFIRMATIVE",
  "policies": [sys.argv[1], sys.argv[2]]
}))
PY
)")

P_GER_HORARIO=$(crear_policy aggregate "Gerencia en Horario Laboral" "$(python3 - "$P_GERENCIA" "$P_HORARIO" <<'PY'
import json,sys
print(json.dumps({
  "name": "Gerencia en Horario Laboral",
  "description": "Debe cumplirse todo: pertenecer a Gerencia Y estar en horario laboral",
  "logic": "POSITIVE", "decisionStrategy": "UNANIMOUS",
  "policies": [sys.argv[1], sys.argv[2]]
}))
PY
)")

P_GER_O_AUD=$(crear_policy aggregate "Gerencia o Auditoria" "$(python3 - "$P_GERENCIA" "$P_AUDITORIA" <<'PY'
import json,sys
print(json.dumps({
  "name": "Gerencia o Auditoria",
  "description": "Gerencia o Auditoria pueden consultar los reportes",
  "logic": "POSITIVE", "decisionStrategy": "AFFIRMATIVE",
  "policies": [sys.argv[1], sys.argv[2]]
}))
PY
)")

# -----------------------------------------------------------------------------
step "5/5  Permissions (que puede hacerse sobre que)"
# -----------------------------------------------------------------------------
crear_permission() {
  local nombre="$1" desc="$2" resource="$3" scope="$4" estrategia="$5"; shift 5
  local body; body=$(python3 - "$nombre" "$desc" "$resource" "$scope" "$estrategia" "$@" <<'PY'
import json,sys
n,d,r,s,e = sys.argv[1:6]
print(json.dumps({
  "name": n, "description": d, "type": "scope",
  "logic": "POSITIVE", "decisionStrategy": e,
  "resources": [r], "scopes": [s], "policies": list(sys.argv[6:])
}))
PY
)
  kc_api_soft POST "$AZ/permission/scope" "$body" >/dev/null
  local id; id=$(kc_api GET "$AZ/permission?name=$(urlenc "$nombre")" | pick_id name "$nombre")
  [ -n "$id" ] || die "no se pudo crear el permiso '$nombre'"
  ok "permission '$nombre'"
}

crear_permission "Ver catalogo" \
  "Cualquier cliente registrado o personal de tienda puede ver el catalogo" \
  "$R_CATALOGO" "$S_CAT_VIEW" "AFFIRMATIVE" "$P_CLI_O_TIENDA"

crear_permission "Editar catalogo" \
  "Solo el personal de tienda y unicamente en horario laboral" \
  "$R_CATALOGO" "$S_CAT_EDIT" "UNANIMOUS" "$P_TIENDA" "$P_HORARIO"

crear_permission "Eliminar del catalogo" \
  "Operacion destructiva reservada a los administradores" \
  "$R_CATALOGO" "$S_CAT_DEL" "UNANIMOUS" "$P_ADMIN"

crear_permission "Ver pedidos" \
  "Clientes (los propios) y personal de tienda (los de su sede)" \
  "$R_PEDIDOS" "$S_ORD_VIEW" "AFFIRMATIVE" "$P_CLI_O_TIENDA"

crear_permission "Crear pedido" \
  "Solo clientes registrados generan pedidos" \
  "$R_PEDIDOS" "$S_ORD_CREATE" "UNANIMOUS" "$P_CLIENTE"

crear_permission "Anular pedido" \
  "Solo Gerencia y dentro del horario laboral" \
  "$R_PEDIDOS" "$S_ORD_CANCEL" "UNANIMOUS" "$P_GER_HORARIO"

crear_permission "Ver reportes" \
  "Gerencia o Auditoria" \
  "$R_REPORTES" "$S_REP_VIEW" "AFFIRMATIVE" "$P_GER_O_AUD"

echo >&2
ok "Seguridad granular configurada: 7 scopes, 3 resources, 8 policies, 7 permissions"
