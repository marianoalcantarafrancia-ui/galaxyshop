# 6. Keycloak y Spring Security (Rúbrica #4)

> *"Crear un filtro general de seguridad (SecurityFilterChain) y realizar
> configuraciones de acceso público y privado utilizando contextos de rutas y
> roles; además de métodos (@PreAuthorize o @PostAuthorize). Los valores deben
> ser personalizados a nivel de constantes, archivos properties o base de datos."*

## 6.1 El problema de partida: Keycloak y Spring no hablan el mismo idioma

Spring Security, por defecto, construye las *authorities* a partir del claim
`scope`. Keycloak no pone ahí los roles: los reparte en dos sitios distintos.

```json
{
  "preferred_username": "cajero.lima",
  "realm_access":    { "roles": ["gs-clerk", "offline_access"] },
  "resource_access": { "shop-backend": { "roles": ["catalog-read", "order-create"] } },
  "scope": "openid profile email shop-profile",
  "grupos": ["/Tiendas/Lima/Caja"],
  "store": "LIM-001"
}
```

Sin traducción, `@PreAuthorize("hasRole('gs-clerk')")` **nunca se cumple**, por
mucho que el rol esté en el token. `KeycloakJwtAuthenticationConverter` es quien
lo resuelve, publicando tres familias de authorities:

| Origen en el token | Authority resultante |
|--------------------|----------------------|
| `realm_access.roles` | `ROLE_gs-clerk` |
| `resource_access.shop-backend.roles` | `ROLE_catalog-read` |
| `scope` | `SCOPE_profile` |

## 6.2 El filtro general

```java
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())      // API sin estado, autenticada por bearer token
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> {
                // 1. rutas públicas desde application.yml
                // 2. reglas desde la tabla security_rule
                // 3. reglas de negocio con constantes
                auth.anyRequest().authenticated();
            })
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtConverter)));
        return http.build();
    }
}
```

`csrf().disable()` no es un descuido: CSRF protege sesiones basadas en cookie.
Aquí no hay cookie de sesión —cada petición se autoriza con su propio bearer
token, que el atacante no puede inyectar desde otro sitio— y la política es
`STATELESS`.

## 6.3 Las tres vías de personalización

La rúbrica pide que los valores sean personalizables *"a nivel de constantes,
archivos properties o base de datos"*. Están implementadas **las tres**, cada
una para lo que mejor sirve.

### Vía 1 — Constantes: `SecurityRoles`

```java
public final class SecurityRoles {
    public static final String CLERK        = "gs-clerk";
    public static final String CATALOG_EDIT = "catalog-write";
    public static final String SCOPE_ORDER_CANCEL = "order:cancel";
    public static final String RES_PEDIDOS  = "Pedidos";
}
```

Para lo que debe comprobarse en compilación. Si el realm renombra un rol, se
corrige en un punto y no en veinte anotaciones. Las anotaciones las concatenan:

```java
@PreAuthorize("hasAnyRole('" + SecurityRoles.CATALOG_READ + "','" + SecurityRoles.ADMIN + "')")
```

### Vía 2 — Properties: `application.yml`

```yaml
app:
  security:
    public-paths:
      - /api/public/**
      - /swagger-ui/**
      - /actuator/health
    resource-client-id: shop-backend
    realm-roles-claim: realm_access
    principal-claim: preferred_username
    allowed-origins:
      - http://localhost:4200
    database-rules-enabled: true
    granular:
      enabled: true
      audience: shop-backend
      cache-seconds: 30
```

Lo lee `AppSecurityProperties` con `@ConfigurationProperties`. Para lo que cambia
entre entornos: orígenes CORS, URL del emisor, rutas abiertas.

### Vía 3 — Base de datos: tabla `security_rule`

```java
@Entity
public class SecurityRule {
    private String pattern;        // /api/reports/**
    private String method;         // GET  o  *
    private String requiredRoles;  // report-read,gs-auditor,gs-manager
    private boolean publicAccess;
    private int sortOrder;
}
```

`SecurityConfig` las lee al construir el filtro:

```java
for (SecurityRule regla : reglasRepo.findByEnabledTrueOrderBySortOrderAsc()) {
    var matcher = "*".equals(regla.getMethod())
            ? auth.requestMatchers(regla.getPattern())
            : auth.requestMatchers(HttpMethod.valueOf(regla.getMethod()), regla.getPattern());

    if (regla.isPublicAccess())            matcher.permitAll();
    else if (regla.rolesArray().length > 0) matcher.hasAnyRole(regla.rolesArray());
    else                                    matcher.authenticated();
}
```

Para lo que un administrador debe poder tocar sin pedir un despliegue. Se
consulta en vivo con `GET /api/admin/security-rules` y se ve en la pantalla
*Administración* del frontend.

**Alcance honesto de esta vía:** las reglas se leen al construir el filtro, es
decir, al arrancar la aplicación. Cambiar una fila exige reiniciar el servicio
—no recompilar ni redesplegar, que es lo que la rúbrica busca—. Para que el
cambio surta efecto sin reinicio hay que recurrir a la seguridad granular de
Keycloak (Rúbrica #6), que sí se evalúa en cada petición.

## 6.4 Seguridad a nivel de método

### `@PreAuthorize` — antes de ejecutar

```java
@PreAuthorize("hasAnyRole('" + ORDER_CREATE + "','" + ADMIN + "') "
            + "and @authorizationGranularService.permite('Pedidos','order:create')")
public PedidoDto registrarPedido(NuevoPedidoDto dto) { ... }
```

Dos condiciones encadenadas: el rol se comprueba con el token en memoria
(inmediato) y el permiso granular lo resuelve Keycloak (regla administrable).

### `@PostAuthorize` — después de ejecutar

Hay decisiones que **no pueden tomarse antes**: para saber si un pedido es
"propio" hay que haberlo leído.

```java
@PreAuthorize("isAuthenticated()")
@PostAuthorize("returnObject.cliente == authentication.name "
             + "or hasAnyRole('" + ORDER_READ_ALL + "','" + ADMIN + "')")
public PedidoDto buscarPedido(UUID id) {
    return pedidos.findById(id).map(PedidoDto::de).orElseThrow(...);
}
```

El método se ejecuta, y Spring descarta el resultado si no cumple la condición.
`cliente.basic` pidiendo el pedido de `cliente.gold` recibe **403**, aunque
conozca el UUID exacto. Está verificado en la colección de Postman
(*Pedido ajeno - debe dar 403*).

## 6.5 Resumen de anotaciones

| Servicio | Método | Anotación |
|----------|--------|-----------|
| `CatalogoService` | `listarProductos` | `@PreAuthorize` rol + scope `catalog:view` |
| `CatalogoService` | `crearProducto` | `@PreAuthorize` scope `catalog:edit` |
| `CatalogoService` | `eliminarProducto` | `@PreAuthorize` rol + scope `catalog:delete` |
| `PedidoService` | `misPedidos` | `@PreAuthorize` rol `order-read-own` |
| `PedidoService` | `buscarPedido` | `@PreAuthorize` + **`@PostAuthorize`** |
| `PedidoService` | `registrarPedido` | `@PreAuthorize` rol + scope `order:create` |
| `PedidoService` | `anularPedido` | `@PreAuthorize` scope `order:cancel` |
| `PedidoService` | `reporteDeVentas` | `@PreAuthorize` rol + scope `report:view` |
| `AdminController` | `todosLosPedidos` | `@PreAuthorize` rol `gs-admin` |

## 6.6 Validación del token

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8081/realms/galaxyshop
          audiences:
            - shop-backend
```

Tres comprobaciones automáticas en cada petición:

1. **Firma** — contra las claves públicas del realm (JWKS, cacheadas).
2. **Emisor** — debe ser exactamente el realm `galaxyshop`. Un token del realm
   de proveedores es rechazado con 401, aunque sea válido en su propio realm.
3. **Audiencia** — el token debe incluir `shop-backend` en `aud`. Lo garantiza
   el *audience mapper* del client scope `shop-profile`.

## 6.7 Respuestas de error

`ManejadorDeErrores` uniforma las respuestas:

| Situación | HTTP | Cuerpo |
|-----------|------|--------|
| sin token o token inválido | 401 | respuesta estándar de Spring Security |
| `@PreAuthorize` / `@PostAuthorize` / granular deniegan | 403 | `{"codigo":403,"error":"Forbidden","mensaje":"No cuenta con los permisos necesarios..."}` |
| recurso inexistente | 404 | `{"codigo":404,...}` |
| validación de entrada | 400 | detalle campo a campo |
| regla de negocio (stock, pedido ya anulado) | 409 | `{"codigo":409,...}` |

El 403 nunca revela **qué** policy denegó el acceso: esa información se queda
en el log del servidor.
