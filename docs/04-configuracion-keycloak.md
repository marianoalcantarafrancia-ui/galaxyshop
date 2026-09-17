# 4. Configuración de Keycloak (Rúbricas #1, #2, #7, #8 y #9)

## 4.1 Instalación (Rúbrica #1)

Keycloak 26.4 en Docker Compose, con **PostgreSQL persistente** y volumen
nombrado, más una segunda base de datos para la aplicación de negocio.

```yaml
keycloak:
  image: quay.io/keycloak/keycloak:26.4
  command: [start-dev, --import-realm]
  environment:
    KC_DB: postgres
    KC_DB_URL: jdbc:postgresql://keycloak-db:5432/keycloak
    TZ: America/Lima          # las Time Policies usan el reloj del servidor
  volumes:
    - ./keycloak/realms:/opt/keycloak/data/import:ro
    - ./keycloak/themes:/opt/keycloak/themes:ro
```

| Servicio | Contenedor | Puerto | Volumen |
|----------|-----------|--------|---------|
| Keycloak 26.4 | `gs-keycloak` | 8081 | — |
| PostgreSQL de Keycloak | `gs-keycloak-db` | 5433 | `keycloak-db-data` |
| PostgreSQL de la aplicación | `gs-app-db` | 5434 | `app-db-data` |

Los datos sobreviven a `docker compose restart` y a `down` (sin `-v`).

> **Zona horaria.** El contenedor arranca en UTC salvo que se indique otra cosa.
> Con la política de horario configurada de 06:00 a 22:59, en UTC la anulación
> de pedidos se rechazaba a partir de la una de la tarde en Lima. Fijar
> `TZ=America/Lima` alinea la regla con el horario real de las tiendas.

## 4.2 Dos realms (Rúbrica #2)

| | `galaxyshop` | `galaxyshop-partners` |
|---|---|---|
| Para quién | clientes y personal de la cadena | proveedores externos |
| Clientes OIDC | `shop-frontend`, `shop-backend`, `shop-batch` | `partner-portal`, `supplier-api` |
| Roles de realm | 6 (`gs-*`), dos compuestos | 2 (`supplier`, `supplier-manager`) |
| Roles de cliente | 9 en `shop-backend` | 3 en `supplier-api` |
| Grupos | 3 raíz + 7 subgrupos | 1 raíz + 2 subgrupos |
| Usuarios | 8 + 2 cuentas de servicio | 2 + 1 cuenta de servicio |
| Contraseña | mínimo 10 caracteres, historial 3 | mínimo 12 caracteres, historial 5 |
| Access token | 300 s | 180 s |
| Reuso del refresh token | 2 veces | ninguna |
| Authorization Services | sí, en `shop-backend` | no |

Son **directorios de identidades separados**: no comparten usuarios, ni claves,
ni sesión. La colección de Postman lo demuestra — un token del realm de
proveedores recibe **401** contra la API de la tienda.

### Clientes del realm principal

| Cliente | Tipo | Flujo | Para qué |
|---------|------|-------|----------|
| `shop-frontend` | público | Authorization Code + PKCE S256 | el SPA Angular |
| `shop-backend` | confidencial | resource server + Authorization Services | la API REST |
| `shop-batch` | confidencial | `client_credentials` | reposición nocturna de stock, sin usuario |

### Client scopes propios

| Scope | Tipo | Aporta al token |
|-------|------|-----------------|
| `shop-profile` | por defecto | `tier`, `store`, `grupos`, y la audiencia `shop-backend` |
| `shop-orders` | opcional | acceso a los pedidos |
| `shop-reports` | opcional | `nivel_reporte=detallado` |

### User Profile declarativo

Desde Keycloak 24 los atributos que no están declarados **se descartan al
guardar el usuario**. Por eso `tier`, `store` y `documento` se declaran
explícitamente; sin ese paso, el claim `store` llegaba vacío al token.

## 4.3 Identity Providers (Rúbrica #7)

Se registran **GitHub** y **Google** como proveedores de identidad del realm
`galaxyshop`. Ambos llevan un *hardcoded group mapper*: quien entra por login
social aterriza automáticamente en `/Clientes/Nuevos`, de modo que nace con el
rol `gs-customer` y puede comprar desde el primer minuto.

Las credenciales no se versionan: llegan por variables de entorno.

```bash
# .env
GITHUB_CLIENT_ID=...
GITHUB_CLIENT_SECRET=...
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
```

| Proveedor | Dónde se registra la aplicación | URL de retorno |
|-----------|--------------------------------|----------------|
| GitHub | https://github.com/settings/developers | `http://localhost:8081/realms/galaxyshop/broker/github/endpoint` |
| Google | https://console.cloud.google.com/apis/credentials | `http://localhost:8081/realms/galaxyshop/broker/google/endpoint` |

Tras completar el `.env`:

```bash
docker compose up -d keycloak
./keycloak/scripts/04-identity-providers.sh
```

Los botones **Ingresar con GitHub** y **Ingresar con Google** aparecen en la
pantalla de acceso aunque las credenciales sean de ejemplo; solo el intercambio
con el proveedor real requiere credenciales válidas.

## 4.4 Tema personalizado (Rúbrica #8)

`keycloak/themes/galaxyshop/login/` — hereda de `keycloak.v2` y **añade** una
hoja de estilos en lugar de reescribir el tema:

```properties
parent=keycloak.v2
import=common/keycloak
styles=css/login.css css/galaxyshop.css
locales=es,en
```

| Archivo | Qué aporta |
|---------|-----------|
| `resources/css/galaxyshop.css` | degradado corporativo, tarjeta redondeada, botón azul de marca, iconos sociales acotados |
| `resources/img/logo-galaxyshop.svg` | logotipo (carrito orbitado) sobre fondo claro |
| `resources/img/logo-galaxyshop-claro.svg` | variante para la cabecera sobre el degradado oscuro |
| `messages/messages_es.properties` | textos en español, incluidos los mensajes de la política de contraseñas |
| `messages/messages_en.properties` | equivalente en inglés |

El tema respeta el modo claro y el oscuro del sistema: PatternFly resuelve el
contraste y el CSS propio solo aporta los colores de marca, con un azul más
luminoso cuando el navegador pide modo oscuro.

## 4.5 Configuración del realm (Rúbrica #9)

### Idioma

```json
"internationalizationEnabled": true,
"supportedLocales": ["es", "en"],
"defaultLocale": "es"
```

### Política de contraseñas

```
length(10) and upperCase(1) and lowerCase(1) and digits(1)
and specialChars(1) and notUsername(undefined)
and passwordHistory(3) and hashAlgorithm(argon2)
```

| Requisito de la rúbrica | Cómo se cumple |
|-------------------------|----------------|
| mínimo 10 caracteres | `length(10)` |
| 1 letra mayúscula | `upperCase(1)` |
| 1 letra minúscula | `lowerCase(1)` |
| 1 dígito | `digits(1)` |
| caracteres especiales `@%&#.+/!=?¿` | `specialChars(1)` |

Se añaden dos refuerzos: la contraseña no puede ser igual al usuario
(`notUsername`) ni repetir las 3 últimas (`passwordHistory(3)`), y el hash usa
**Argon2**, el algoritmo por defecto recomendado en Keycloak 26.

### Tiempos de token y reutilización

| Parámetro | Valor | Significado |
|-----------|-------|-------------|
| `accessTokenLifespan` | 300 s | el access token vive 5 minutos |
| `ssoSessionIdleTimeout` | 1800 s | media hora sin actividad cierra la sesión |
| `ssoSessionMaxLifespan` | 36000 s | 10 horas como máximo absoluto |
| `clientSessionIdleTimeout` | 1800 s | inactividad por cliente |
| `revokeRefreshToken` | `true` | cada refresh emite uno nuevo y revoca la cadena |
| **`refreshTokenMaxReuse`** | **2** | **número de veces que se admite reutilizar el mismo refresh token** |
| `offlineSessionIdleTimeout` | 30 días | sesiones sin conexión |

### Protección contra fuerza bruta

5 intentos fallidos bloquean la cuenta temporalmente (espera creciente, hasta 15
minutos). En el realm de proveedores el umbral baja a 3.

## 4.6 Scripts de aprovisionamiento

```
keycloak/scripts/
├── provision.sh                 orquestador: ejecuta todo en orden
├── lib.sh                       cliente de la Admin REST API
├── 01-realm-galaxyshop.sh       realm, roles, scopes, clientes, grupos, usuarios
├── 02-realm-partners.sh         segundo realm
├── 03-authz-granular.sh         scopes, resources, policies y permissions
├── 04-identity-providers.sh     GitHub y Google
└── export-realms.sh             vuelca los realms a keycloak/realms/*.json
```

Todos son **idempotentes**: se pueden volver a ejecutar sin duplicar nada.

```bash
docker compose up -d
./keycloak/scripts/provision.sh
```

## 4.7 Exportación e importación

`export-realms.sh` ejecuta `kc.sh export --users realm_file` dentro del
contenedor y deja los JSON en `keycloak/realms/`, que es justo el directorio que
`docker compose` monta como `/opt/keycloak/data/import`. Por eso:

```bash
docker compose down -v && docker compose up -d
```

reconstruye el entorno completo —realms, clientes, roles, grupos, usuarios con
sus contraseñas, políticas de autorización e IdP— **sin volver a ejecutar los
scripts**.

> **Dos detalles que hubo que resolver para que el ciclo funcione.**
>
> 1. `kc.sh export` intenta abrir el puerto de gestión 9000, que ya ocupa el
>    servidor en marcha. Se le asigna `--http-management-port 9100`.
> 2. Al habilitar Authorization Services, Keycloak crea una *Default Policy* de
>    tipo JavaScript. Al reimportar, el servidor la rechaza con
>    *"Script upload is disabled"* y **el realm entero no se importa**. Como no
>    se usa, el script `03` la elimina; así el export queda importable tal cual.
