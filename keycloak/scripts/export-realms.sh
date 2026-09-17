#!/usr/bin/env bash
# =============================================================================
#  Exporta los realms a keycloak/realms/*.json
#
#  Estos archivos son el entregable de configuracion que pide la rubrica y, a
#  la vez, lo que docker-compose importa al arrancar (--import-realm): quien
#  clone el repositorio levanta el entorno completo con un solo comando.
# =============================================================================
set -Eeuo pipefail
cd "$(dirname "$0")"
source ./lib.sh

CONTENEDOR="${KC_CONTAINER:-gs-keycloak}"
DESTINO="$(cd .. && pwd)/realms"
TMP_CONTENEDOR="/tmp/kc-export"

step "Exportando realms desde el contenedor '$CONTENEDOR'"

docker exec "$CONTENEDOR" rm -rf "$TMP_CONTENEDOR" 2>/dev/null || true
docker exec "$CONTENEDOR" /opt/keycloak/bin/kc.sh export \
    --dir "$TMP_CONTENEDOR" \
    --users realm_file \
    --http-management-port 9100 \
    >/dev/null 2>&1 \
  || die "Fallo el export. Revisa: docker logs $CONTENEDOR"

mkdir -p "$DESTINO"
for realm in galaxyshop galaxyshop-partners; do
  docker cp "$CONTENEDOR:$TMP_CONTENEDOR/${realm}-realm.json" "$DESTINO/${realm}-realm.json" >/dev/null
  # Formato estable (indentado y claves ordenadas) para que el diff de git sea legible
  python3 - "$DESTINO/${realm}-realm.json" <<'PY'
import json, sys
ruta = sys.argv[1]
with open(ruta, encoding="utf-8") as f:
    data = json.load(f)
with open(ruta, "w", encoding="utf-8") as f:
    json.dump(data, f, indent=2, ensure_ascii=False, sort_keys=True)
    f.write("\n")
PY
  ok "$(basename "$DESTINO")/${realm}-realm.json  ($(du -h "$DESTINO/${realm}-realm.json" | cut -f1))"
done

docker exec "$CONTENEDOR" rm -rf "$TMP_CONTENEDOR" 2>/dev/null || true

echo >&2
info "Para reconstruir el entorno desde cero con esta configuracion:"
info "  docker compose down -v && docker compose up -d"
