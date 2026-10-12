# 7. Checklist de la rúbrica

Mapa de cada punto evaluable con la evidencia concreta que lo respalda.
Cada componente vale 2 puntos.

---

## ✅ 1. Keycloak — Instalación

> *Instalar Keycloak en ambiente de desarrollo o producción utilizando el
> binario (.jar), Docker o Docker Compose; de preferencia configurarlo con una
> base de datos persistente (PostgreSQL, MySQL u otros).*

| Evidencia | Dónde |
|-----------|-------|
| Docker Compose con Keycloak 26.4 | [`docker-compose.yml`](../docker-compose.yml) |
| PostgreSQL 16 persistente con volumen nombrado | servicio `keycloak-db`, volumen `keycloak-db-data` |
| Segunda base de datos para la aplicación | servicio `app-db` |
| Healthcheck real contra `/health/ready` | `docker-compose.yml`, servicio `keycloak` |
| Zona horaria del negocio (`America/Lima`) | necesaria para las Time Policies |

**Cómo se comprueba:** `docker compose up -d` y luego
`docker compose ps` — los tres contenedores quedan *healthy*.

---

## ✅ 2. Keycloak — Configuración

> *Crear 2 o más realms con sus respectivos clientes, usuarios, grupos y
> subgrupos, roles y alcances; a nivel de clientes también crear roles y
> alcances a utilizar.*

| Requisito | Implementación |
|-----------|---------------|
| 2 realms | `galaxyshop` y `galaxyshop-partners` |
| Clientes | 3 + 2 (`shop-frontend`, `shop-backend`, `shop-batch`, `partner-portal`, `supplier-api`) |
| Usuarios | 8 + 2, más 3 cuentas de servicio |
| Grupos y **subgrupos** | `/Tiendas/Lima/Caja`, `/Tiendas/Lima/Almacen`, `/Tiendas/Arequipa/Caja`, `/Clientes/Frecuentes`, `/Clientes/Nuevos`, `/Corporativo/{Gerencia,TI,Auditoria}`, `/Proveedores/{Nacionales,Internacionales}` |
| Roles de realm | 6 + 2, con **roles compuestos** |
| **Roles a nivel de cliente** | 9 en `shop-backend`, 3 en `supplier-api` |
| **Alcances (client scopes)** | `shop-profile`, `shop-orders`, `shop-reports` con sus mappers |

**Detalle:** ningún rol se asigna directamente a un usuario; todos se heredan
del subgrupo.

Documento: [04-configuracion-keycloak.md](04-configuracion-keycloak.md) ·
Scripts: [`01-realm-galaxyshop.sh`](../keycloak/scripts/01-realm-galaxyshop.sh), [`02-realm-partners.sh`](../keycloak/scripts/02-realm-partners.sh)

---

## ✅ 3. APIs REST — Spring Boot (Back-end)

> *Diseñar un modelo conceptual o diagrama de las APIs del caso a asegurar;
> considerar accesos públicos, privados y especiales. Las APIs deben gestionar
> recursos o un proceso de negocio; crear una matriz RACI utilizando usuarios,
> grupos y subgrupos, roles y recursos.*

| Requisito | Evidencia |
|-----------|-----------|
| Modelo conceptual y diagramas | [01-modelo-conceptual-apis.md](01-modelo-conceptual-apis.md) |
| Proceso de negocio | ciclo de venta de una tienda de comestibles |
| 19 endpoints en tres niveles | catálogo de endpoints en el mismo documento |
| **Matriz RACI** | [02-matriz-raci.md](02-matriz-raci.md), §2.4 |
| Documentación viva | Swagger UI en http://localhost:8080/swagger-ui.html |

---

## ✅ 4. Keycloak & Spring Security

> *Crear un filtro general de seguridad (SecurityFilterChain) y realizar
> configuraciones de acceso público y privado utilizando contextos de rutas y
> roles; además de métodos (@PreAuthorize o @PostAuthorize). Los valores deben
> ser personalizados a nivel de constantes, archivos properties o base de datos.*

| Requisito | Implementación |
|-----------|---------------|
| `SecurityFilterChain` | [`SecurityConfig.java`](../backend/src/main/java/com/galaxyshop/api/config/SecurityConfig.java) |
| Rutas públicas y privadas | `permitAll()` / `authenticated()` / `hasAnyRole()` |
| Conversión de roles de Keycloak | [`KeycloakJwtAuthenticationConverter.java`](../backend/src/main/java/com/galaxyshop/api/security/KeycloakJwtAuthenticationConverter.java) |
| `@PreAuthorize` | 9 métodos de servicio |
| **`@PostAuthorize`** | `PedidoService.buscarPedido()` — impide leer pedidos ajenos |
| **Constantes** | [`SecurityRoles.java`](../backend/src/main/java/com/galaxyshop/api/security/SecurityRoles.java) |
| **Properties** | `app.security.*` en [`application.yml`](../backend/src/main/resources/application.yml) |
| **Base de datos** | entidad `SecurityRule` + tabla `security_rule` |

Documento: [06-spring-security.md](06-spring-security.md)

---

## ✅ 5. App Web — Angular (Front-end)

> *Diseñar un modelo conceptual o diagrama de la aplicación web en Angular del
> caso a asegurar; considerar accesos públicos y privados. Crear una matriz RACI
> utilizando usuarios, grupos y subgrupos, roles y recursos.*

| Requisito | Evidencia |
|-----------|-----------|
| Modelo conceptual y diagramas | [03-frontend-angular.md](03-frontend-angular.md) |
| Angular 20 + keycloak-js 26 | `frontend/` |
| Accesos públicos | `/`, `/catalogo` sin guard |
| Accesos privados | `authGuard` en `/pedidos`, `/perfil` |
| Accesos especiales | `rolesGuard` en `/reportes`, `/administracion` |
| **Matriz RACI del frontend** | [02-matriz-raci.md](02-matriz-raci.md), §2.6 |
| Menú y botones por rol y permiso | `app.html`, `catalogo.ts`, `pedidos.ts` |

---

## ✅ 6. Seguridad Granular

> *Implementar seguridad granular en un cliente específico, creando sus
> respectivos Scopes, Resources, Policy & Permission. Realizar las pruebas en
> back-end o front-end realizando las configuraciones respectivas.*

| Requisito | Implementación |
|-----------|---------------|
| Cliente específico | `shop-backend`, con Authorization Services |
| **Scopes** | 7 |
| **Resources** | 3 (Catalogo, Pedidos, Reportes) |
| **Policies** | 9 — de rol, de grupo, **de tiempo** y agregadas |
| **Permissions** | 7 permisos por scope |
| Pruebas en back-end | `@PreAuthorize("@authorizationGranularService.permite(...)")` |
| Pruebas en front-end | `keycloak.puede(recurso, scope)` dibuja u oculta botones |
| Pruebas en Postman | carpeta *5. Seguridad granular (UMA 2.0)*, 10 peticiones |

Documento: [05-seguridad-granular.md](05-seguridad-granular.md) ·
Script: [`03-authz-granular.sh`](../keycloak/scripts/03-authz-granular.sh)

---

## ✅ 7. Integración Externa

> *Configurar Keycloak para implementar autenticación con Identity Providers
> (IdPs) – Social Login (utilizar GitHub, LinkedIn, Facebook, Google u otros).*

| Requisito | Implementación |
|-----------|---------------|
| IdP GitHub | registrado en el realm `galaxyshop` |
| IdP Google | registrado en el realm `galaxyshop` |
| Alta automática | *hardcoded group mapper* → `/Clientes/Nuevos` |
| Credenciales fuera del repositorio | variables de entorno en `.env` |

Los botones aparecen en la pantalla de acceso; para completar el intercambio
real hace falta registrar las aplicaciones OAuth y rellenar el `.env`.

Script: [`04-identity-providers.sh`](../keycloak/scripts/04-identity-providers.sh)

---

## ✅ 8. Personalización UI

> *Personalizar la interface de usuario (UI) de Keycloak, crear un tema
> personalizado con colores, logos e imágenes de una empresa en particular.*

| Requisito | Implementación |
|-----------|---------------|
| Tema propio | `keycloak/themes/galaxyshop/login/` |
| **Colores** | degradado corporativo, azul de marca, botones y focos |
| **Logo** | SVG propio, en variante clara y oscura |
| **Imágenes** | fondo degradado; iconos sociales normalizados |
| Textos en español | `messages_es.properties` |
| Modo claro y oscuro | el CSS respeta el esquema del sistema |

Aplicado con `"loginTheme": "galaxyshop"` en el realm.

---

## ✅ 9. Configuración

> *Configurar Keycloak de acuerdo a los siguientes detalles: idioma español,
> personalización de la contraseña de los usuarios (mínimo 10 caracteres,
> 1 letra mayúscula, 1 minúscula, y 1 dígito como mínimo, incluir caracteres
> especiales a considerar: @%&#.+/!=?¿), tiempo de duración del token y refresh
> token, además número de veces a reutilizar.*

| Requisito | Valor configurado |
|-----------|-------------------|
| **Idioma español** | `defaultLocale: es`, `supportedLocales: [es, en]` |
| **Mínimo 10 caracteres** | `length(10)` |
| **1 mayúscula** | `upperCase(1)` |
| **1 minúscula** | `lowerCase(1)` |
| **1 dígito** | `digits(1)` |
| **Caracteres especiales** | `specialChars(1)` |
| *(refuerzo)* | `notUsername`, `passwordHistory(3)`, `hashAlgorithm(argon2)` |
| **Duración del token** | `accessTokenLifespan: 300` s |
| **Duración del refresh token** | `ssoSessionIdleTimeout: 1800` s / `ssoSessionMaxLifespan: 36000` s |
| **Veces a reutilizar** | **`refreshTokenMaxReuse: 2`** con `revokeRefreshToken: true` |

Verificable en Postman: *0. Autenticación → Refresh token*.

---

## ✅ 10. Otros — valor agregado

> *Uso de componentes de Keycloak adicionales como valor agregado que el alumno
> aporte en su entrenamiento.*

| Componente | Para qué se usa |
|------------|-----------------|
| **Roles compuestos** | `gs-admin` → `gs-manager` → `gs-clerk` + `gs-warehouse` |
| **Service accounts** | `shop-batch` con `client_credentials`: reposición sin usuario |
| **Atributos de grupo heredados** | `store` y `tier` llegan al token desde el subgrupo |
| **User Profile declarativo** | `tier`, `store` y `documento` declarados; sin esto Keycloak los descarta |
| **Protocol mappers propios** | atributo, pertenencia a grupos, audiencia y claim fijo |
| **Audience mapper** | la API solo acepta tokens dirigidos a `shop-backend` |
| **Time Policy** | las anulaciones solo dentro del horario laboral |
| **Aggregate Policies** | combinación Y/O de policies |
| **Brute force detection** | 5 intentos (3 en el realm de proveedores) |
| **Eventos y admin events** | auditoría activada con retención de 7 días |
| **Argon2** | hash de contraseñas recomendado en Keycloak 26 |
| **Aprovisionamiento como código** | scripts idempotentes sobre la Admin REST API |
| **Export/import reproducible** | `docker compose down -v && up -d` reconstruye todo |
| **Swagger UI con OAuth2 + PKCE** | probar la API iniciando sesión desde el navegador |
| **Colección Postman con aserciones** | 52 peticiones, 82 aserciones, ejecutable con Newman |
| **Pruebas unitarias de seguridad** | 11 pruebas con MockMvc y tokens simulados, sin necesitar Keycloak |
| **Script de verificación integral** | `verificar.sh`: 43 comprobaciones sobre el entorno real |
| **Stack al día** | Spring Boot 4.1 sobre Java 25 LTS, Spring Security 7 y Jackson 3 ([migración documentada](08-migracion-springboot4.md)) |

---

## Resumen

| # | Componente | Estado |
|---|-----------|:------:|
| 1 | Keycloak (Instalación) | ✅ |
| 2 | Keycloak (Configuración) | ✅ |
| 3 | APIs REST — Spring Boot | ✅ |
| 4 | Keycloak & Spring Security | ✅ |
| 5 | App Web — Angular | ✅ |
| 6 | Seguridad Granular | ✅ |
| 7 | Integración Externa | ✅ *(requiere credenciales OAuth reales para la demo en vivo)* |
| 8 | Personalización UI | ✅ |
| 9 | Configuración | ✅ |
| 10 | Otros | ✅ |
