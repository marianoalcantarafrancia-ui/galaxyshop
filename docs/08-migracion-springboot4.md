# 8. Migración a Spring Boot 4 y Java 25

El back-end partió de Spring Boot 3.5 con Java 21 y se migró a **Spring Boot
4.1.1 sobre Java 25 (LTS)**, con Spring Framework 7 y Spring Security 7.

## 8.1 Resumen

| | Antes | Ahora |
|---|---|---|
| Java | 21 (bytecode 65) | **25 (bytecode 69)** |
| Spring Boot | 3.5.6 | **4.1.1** |
| Spring Framework | 6.2 | **7.x** |
| Spring Security | 6.5 | **7.1.1** |
| Hibernate | 6.6 | **7.4.5** |
| Jackson | 2 (`com.fasterxml.jackson`) | **3 (`tools.jackson`)** |
| springdoc-openapi | 2.8.13 | **3.1.1** |

## 8.2 Starters renombrados

Spring Boot 4 reorganizó los starters por módulo. Los antiguos siguen
existiendo pero están **obsoletos**:

```xml
<!-- Antes -->                          <!-- Ahora -->
spring-boot-starter-web                 spring-boot-starter-webmvc
spring-boot-starter-oauth2-resource-server
                                        spring-boot-starter-security-oauth2-resource-server
```

Además, `RestClient` —que es lo que usa `AuthorizationGranularService` para
consultar a Keycloak— pasó a tener su propio starter:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-restclient</artifactId>
</dependency>
```

Se aprovechó para **eliminar `keycloak-authz-client`**: estaba declarada desde
el principio pero el código nunca la usó —las consultas UMA se hacen con
`RestClient` contra el endpoint de token— así que era peso muerto.

## 8.3 Jackson 2 → Jackson 3

El cambio con más superficie. Jackson 3 cambia **el groupId y los paquetes**,
de `com.fasterxml.jackson` a `tools.jackson`, y `ObjectMapper` cede su sitio a
`JsonMapper`:

```java
// Antes
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

private final ObjectMapper json = new ObjectMapper();

// Ahora
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

private final JsonMapper json = JsonMapper.builder().build();
```

En este proyecto solo afectó a `AuthorizationGranularService`, que parsea a
mano la respuesta UMA de Keycloak. El resto de la serialización la hace Spring
y no requirió tocar nada: los *records* de `Dtos` siguen igual.

> Si hubiera hecho falta quedarse en Jackson 2, Spring Boot 4 ofrece el módulo
> `spring-boot-jackson2`. Aquí no se usó: migrar era preferible a arrastrar un
> módulo marcado como obsoleto desde el primer día.

## 8.4 Autoconfiguraciones de test movidas de paquete

`@SpringBootTest` ya no aporta `MockMvc` por sí solo, y la anotación cambió de
sitio:

```java
// Antes
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
// Ahora
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
```

Con dos starters de prueba nuevos:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-webmvc-test</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security-test</artifactId>
  <scope>test</scope>
</dependency>
```

## 8.5 Lo que no cambió

Vale la pena señalarlo, porque era el riesgo principal de la migración: **toda
la seguridad siguió compilando sin tocar una línea**.

- `SecurityFilterChain` y `authorizeHttpRequests(...)` con sus matchers.
- `@EnableMethodSecurity`, `@PreAuthorize` y `@PostAuthorize`, incluidas las
  expresiones que invocan beans (`@authorizationGranularService.permite(...)`).
- `KeycloakJwtAuthenticationConverter`, que traduce `realm_access` y
  `resource_access` a authorities.
- La configuración del resource server (`issuer-uri`, `audiences`).
- Las tres vías de personalización: constantes, properties y tabla
  `security_rule`.

Spring Security 7 mantuvo la API que usa este proyecto. El trabajo real estuvo
en los nombres de las dependencias y en Jackson.

## 8.6 Una trampa al cambiar de versión de Java

Tras actualizar el `pom.xml` y compilar, el bytecode **seguía siendo Java 21**:

```
major version: 65     # Java 21, no 25
```

La causa es que `mvn compile` es incremental: ninguna clase había cambiado, así
que no recompiló nada y quedaron los `.class` de la versión anterior. Hay que
forzar la limpieza:

```bash
mvn clean compile
```

```
major version: 69     # Java 25
```

Conviene comprobarlo explícitamente en cualquier cambio de versión de Java, en
lugar de dar por hecho que el `pom.xml` basta:

```bash
javap -verbose -cp target/classes com.galaxyshop.api.GalaxyShopApiApplication \
  | grep "major version"
```

## 8.7 Verificación posterior

La migración se dio por buena cuando las tres suites volvieron a pasar enteras
contra el entorno real:

| Suite | Resultado |
|-------|-----------|
| `mvn test` | 11 de 11 |
| `./verificar.sh` | 43 de 43 |
| Newman (Postman) | 52 peticiones, 82 aserciones, 0 fallos |

Y el arranque confirma la versión en ejecución:

```
Starting GalaxyShopApiApplication using Java 25.0.2 ...
Started GalaxyShopApiApplication in 2.9 seconds
```
