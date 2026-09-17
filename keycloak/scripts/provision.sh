#!/usr/bin/env bash
# =============================================================================
#  Aprovisionamiento completo de Keycloak para GalaxyShop
#
#  Uso:
#     ./keycloak/scripts/provision.sh            # configura todo y exporta
#     ./keycloak/scripts/provision.sh --no-export
#
#  Los scripts son idempotentes: se pueden volver a ejecutar sin romper nada.
# =============================================================================
set -Eeuo pipefail
cd "$(dirname "$0")"

RAIZ="$(cd ../.. && pwd)"
[ -f "$RAIZ/.env" ] && { set -a; source "$RAIZ/.env"; set +a; }

source ./lib.sh

step "Esperando a que Keycloak responda en $KC_URL"
for i in $(seq 1 60); do
  if curl -sf "$KC_URL/realms/master/.well-known/openid-configuration" >/dev/null 2>&1; then
    ok "Keycloak disponible"; break
  fi
  [ "$i" = "60" ] && die "Keycloak no respondio. Ejecuta antes: docker compose up -d"
  sleep 2
done

./01-realm-galaxyshop.sh
./02-realm-partners.sh
./03-authz-granular.sh
./04-identity-providers.sh

if [ "${1:-}" != "--no-export" ]; then
  ./export-realms.sh
fi

echo >&2
step "Aprovisionamiento terminado"
cat >&2 <<'RESUMEN'
  Consola de administracion : http://localhost:8081/admin   (admin / Admin#2026)
  Realm principal           : http://localhost:8081/realms/galaxyshop
  Realm de proveedores      : http://localhost:8081/realms/galaxyshop-partners

  Usuarios de la tienda     : contrasena  Galaxy#2026ok
    admin.shop     /Corporativo/TI          gs-admin
    gerente.shop   /Corporativo/Gerencia    gs-manager
    auditor.shop   /Corporativo/Auditoria   gs-auditor
    cajero.lima    /Tiendas/Lima/Caja       gs-clerk
    almacen.lima   /Tiendas/Lima/Almacen    gs-warehouse
    cajero.aqp     /Tiendas/Arequipa/Caja   gs-clerk
    cliente.gold   /Clientes/Frecuentes     gs-customer  (tier GOLD)
    cliente.basic  /Clientes/Nuevos         gs-customer  (tier BASIC)

  Usuarios de proveedores   : contrasena  Partner#2026ok
    proveedor.uno   /Proveedores/Nacionales        supplier
    proveedor.intl  /Proveedores/Internacionales   supplier-manager
RESUMEN
