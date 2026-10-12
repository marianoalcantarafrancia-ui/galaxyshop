# GalaxyShop — Proyecto final del curso *Security with Keycloak*

Tienda de comestibles en línea asegurada de punta a punta con **Keycloak 26**,
**Spring Boot 4** y **Angular 20**, resolviendo los diez componentes de la
rúbrica del examen final.

| Capa | Tecnología |
|------|-----------|
| Identidad | Keycloak 26.4 sobre PostgreSQL 16 (Docker Compose) |
| Back-end | Spring Boot 4.1 · Java 25 · Spring Security 7 OAuth2 Resource Server |
| Front-end | Angular 20 · keycloak-js 26 |
| Pruebas | Colección Postman (52 peticiones, 82 aserciones) ejecutable con Newman |

El caso de negocio —catálogos, productos y pedidos de una tienda de
comestibles— está tomado del proyecto de ejemplo público
[`jannie-louwrens/spring-boot-keycloak-angular`](https://github.com/jannie-louwrens/spring-boot-keycloak-angular)
y actualizado aquí de Spring Boot 2 / Keycloak 4 a Spring Boot 4 / Keycloak 26.

---

## Puesta en marcha

Requisitos: Docker, **Java 25**, Maven 3.9+ y Node 20+.

```bash
git clone <este-repositorio> && cd repokeycloak
cp .env.example .env
docker compose up -d
```

El arranque **importa los dos realms ya configurados** desde
`keycloak/realms/*.json`: clientes, roles, grupos, usuarios con sus contraseñas,
políticas de autorización e identity providers. No hace falta configurar nada a
mano.

```bash
# Back-end  -> http://localhost:8080
cd backend && mvn spring-boot:run

# Front-end -> http://localhost:4200
cd frontend && npm install && npm start
```

| Servicio | URL |
|----------|-----|
| Aplicación web | http://localhost:4200 |
| API REST | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Consola de Keycloak | http://localhost:8081/admin — `admin` / `Admin#2026` |

### Reconfigurar Keycloak desde cero

Si se prefiere construir la configuración paso a paso en lugar de importarla:

```bash
rm keycloak/realms/*.json
docker compose down -v && docker compose up -d
./keycloak/scripts/provision.sh     # crea todo y vuelve a exportar los JSON
```

Los scripts son idempotentes: se pueden repetir sin duplicar nada.

---

## Usuarios de prueba

**Realm `galaxyshop`** — contraseña `Galaxy#2026ok`

| Usuario | Subgrupo | Rol de realm | Qué puede hacer |
|---------|----------|--------------|-----------------|
| `cliente.gold` | `/Clientes/Frecuentes` | `gs-customer` | comprar y ver sus pedidos (tier GOLD) |
| `cliente.basic` | `/Clientes/Nuevos` | `gs-customer` | igual, tier BASIC |
| `cajero.lima` | `/Tiendas/Lima/Caja` | `gs-clerk` | ver los pedidos de su sede |
| `almacen.lima` | `/Tiendas/Lima/Almacen` | `gs-warehouse` | editar el catálogo en horario laboral |
| `cajero.aqp` | `/Tiendas/Arequipa/Caja` | `gs-clerk` | igual que Lima, otra sede |
| `gerente.shop` | `/Corporativo/Gerencia` | `gs-manager` | anular pedidos y ver reportes |
| `auditor.shop` | `/Corporativo/Auditoria` | `gs-auditor` | solo lectura de reportes |
| `admin.shop` | `/Corporativo/TI` | `gs-admin` | todo, incluido eliminar productos |

**Realm `galaxyshop-partners`** — contraseña `Partner#2026ok`:
`proveedor.uno`, `proveedor.intl`.

> Credenciales de demostración, pensadas para un entorno local. En un despliegue
> real irían en un gestor de secretos y se forzaría el cambio en el primer acceso.

---

## Pruebas

```bash
# Pruebas unitarias del back-end (no necesitan Keycloak levantado)
cd backend && mvn test

# Matriz de acceso (público / privado / especial) + seguridad granular
./verificar.sh

# Colección completa de Postman
cd postman
npx newman run GalaxyShop.postman_collection.json -e GalaxyShop.postman_environment.json
```

| Suite | Qué cubre | Requiere |
|-------|-----------|----------|
| `mvn test` — 11 pruebas | el converter de roles y las reglas del filtro, con tokens simulados | nada |
| `./verificar.sh` — 43 comprobaciones | los diez puntos de la rúbrica contra el entorno real | Docker + API |
| Newman — 52 peticiones, 82 aserciones | flujos completos, incluidas las decisiones UMA | Docker + API |

También se puede importar la colección y el entorno en Postman y ejecutar la
carpeta *0. Autenticación* antes que las demás.

---

## Estructura del repositorio

```
.
├── docker-compose.yml           Keycloak 26 + 2 PostgreSQL persistentes
├── .env.example                 variables (credenciales de los IdP incluidas)
├── verificar.sh                 comprobación integral del entorno
│
├── keycloak/
│   ├── realms/                  JSON exportados: se importan al arrancar
│   ├── themes/galaxyshop/       tema de login personalizado
│   └── scripts/                 aprovisionamiento como código (Admin REST API)
│
├── backend/                     API REST Spring Boot 4
│   └── src/main/java/com/galaxyshop/api/
│       ├── config/              SecurityFilterChain, OpenAPI, carga inicial
│       ├── security/            constantes, converter de JWT, servicio granular
│       ├── controller/          endpoints públicos, privados y especiales
│       ├── service/             reglas con @PreAuthorize / @PostAuthorize
│       └── model/ repository/   dominio y persistencia
│
├── frontend/                    SPA Angular 20
│   └── src/app/
│       ├── core/                KeycloakService, guards, interceptor
│       └── pages/               vistas por nivel de acceso
│
├── postman/                     colección + entorno
└── docs/                        documentación del proyecto
```

---

## Documentación

| Documento | Contenido | Rúbrica |
|-----------|-----------|---------|
| [01 · Modelo conceptual de las APIs](docs/01-modelo-conceptual-apis.md) | dominio, niveles de acceso, catálogo de endpoints, flujos | #3 |
| [02 · Matriz RACI](docs/02-matriz-raci.md) | usuarios, grupos, subgrupos, roles y recursos | #3, #5 |
| [03 · Frontend Angular](docs/03-frontend-angular.md) | arquitectura del SPA y decisiones de diseño | #5 |
| [04 · Configuración de Keycloak](docs/04-configuracion-keycloak.md) | realms, clientes, IdP, tema, política de contraseñas | #1, #2, #7, #8, #9 |
| [05 · Seguridad granular](docs/05-seguridad-granular.md) | scopes, resources, policies y permissions | #6 |
| [06 · Spring Security](docs/06-spring-security.md) | filtro, anotaciones y las tres vías de personalización | #4 |
| [07 · Checklist de la rúbrica](docs/07-checklist-rubrica.md) | cada punto con su evidencia | todos |
| [08 · Migración a Spring Boot 4](docs/08-migracion-springboot4.md) | starters renombrados, Jackson 3 y Java 25 | — |

---

## Lo que demuestra el proyecto

**Tres niveles de acceso reales.** El escaparate se ve sin token; los pedidos
exigen sesión; los reportes y la administración exigen roles concretos. Las tres
situaciones están cubiertas por pruebas automáticas.

**Los roles se heredan de los grupos.** Ningún usuario tiene roles asignados a
mano: dar de alta a un cajero es ponerlo en `/Tiendas/Lima/Caja`. Los atributos
de la sede (`store`) y la categoría comercial (`tier`) viajan en el token desde
el subgrupo.

**La autorización no vive toda en el código.** Quién puede anular un pedido, o
en qué horario se puede editar el catálogo, son *Policies* de Keycloak: se
cambian en la consola y surten efecto en la siguiente petición.

**`@PostAuthorize` protege lo que `@PreAuthorize` no puede.** Para saber si un
pedido es propio hay que haberlo leído; por eso `cliente.basic` recibe 403 al
pedir el pedido de `cliente.gold`, aunque conozca su identificador.

**El entorno es reproducible.** `docker compose down -v && docker compose up -d`
reconstruye realms, usuarios, contraseñas y políticas desde los JSON versionados.
