package com.galaxyshop.api;

import com.galaxyshop.api.security.KeycloakJwtAuthenticationConverter;
import com.galaxyshop.api.security.SecurityRoles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas del filtro de seguridad sin necesidad de un Keycloak levantado.
 *
 * Se fabrican tokens con la misma forma que emite Keycloak (realm_access y
 * resource_access) y se comprueba que el converter los traduce a authorities
 * y que las reglas del SecurityFilterChain deciden como se espera.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeguridadTest {

    @Autowired
    MockMvc mockMvc;

    /** El mismo converter que usa la aplicacion en produccion. */
    @Autowired
    KeycloakJwtAuthenticationConverter converter;

    /**
     * Construye un token con el mismo reparto de roles que hace Keycloak y lo
     * pasa por el converter real de la aplicacion.
     *
     * Es importante no dejar que MockMvc derive las authorities por su cuenta:
     * su converter por defecto solo mira el claim 'scope' y nunca veria los
     * roles de Keycloak. Al usar el converter real, estas pruebas tambien
     * verifican que la traduccion de roles funciona.
     */
    private RequestPostProcessor tokenDe(String usuario,
                                         List<String> rolesDeRealm,
                                         List<String> rolesDeCliente) {
        Jwt token = Jwt.withTokenValue("token-de-prueba")
                .header("alg", "RS256")
                .claim("preferred_username", usuario)
                .claim("realm_access", Map.of("roles", rolesDeRealm))
                .claim("resource_access", Map.of("shop-backend", Map.of("roles", rolesDeCliente)))
                .claim("scope", "openid profile email")
                .build();

        return jwt().jwt(token).authorities(converter.convert(token).getAuthorities());
    }

    // -------------------------------------------------------------------------
    // Acceso publico
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("El escaparate publico responde sin token")
    void escaparatePublicoSinToken() throws Exception {
        mockMvc.perform(get("/api/public/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/public/catalogs")).andExpect(status().isOk());
        mockMvc.perform(get("/api/public/products")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Una ruta privada sin token responde 401")
    void rutaPrivadaSinTokenEs401() throws Exception {
        mockMvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/orders/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reports/sales")).andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Conversion de los roles del token
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Los roles de realm del token llegan como authorities")
    void rolesDeRealmSeConviertenEnAuthorities() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                        .with(tokenDe("admin.shop", List.of(SecurityRoles.ADMIN), List.of())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Los roles de cliente del token llegan como authorities")
    void rolesDeClienteSeConviertenEnAuthorities() throws Exception {
        mockMvc.perform(get("/api/catalogs")
                        .with(tokenDe("cajero.lima",
                                List.of(SecurityRoles.CLERK),
                                List.of(SecurityRoles.CATALOG_READ))))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // Acceso especial
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Un cliente no entra a la administracion")
    void clienteNoEntraAAdministracion() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                        .with(tokenDe("cliente.gold",
                                List.of(SecurityRoles.CUSTOMER),
                                List.of(SecurityRoles.ORDER_CREATE))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Gerencia tampoco entra a la administracion: es exclusiva de gs-admin")
    void gerenteNoEntraAAdministracion() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                        .with(tokenDe("gerente.shop",
                                List.of(SecurityRoles.MANAGER),
                                List.of(SecurityRoles.ORDER_READ_ALL))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El auditor consulta reportes; el cajero no")
    void reportesSoloParaPerfilesAutorizados() throws Exception {
        mockMvc.perform(get("/api/reports/sales")
                        .with(tokenDe("auditor.shop",
                                List.of(SecurityRoles.AUDITOR),
                                List.of(SecurityRoles.REPORT_READ))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/reports/sales")
                        .with(tokenDe("cajero.lima",
                                List.of(SecurityRoles.CLERK),
                                List.of(SecurityRoles.CATALOG_READ))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sin el rol de lectura del catalogo no se listan productos")
    void sinRolDeCatalogoNoSeListanProductos() throws Exception {
        mockMvc.perform(get("/api/products")
                        .with(tokenDe("sin.roles", List.of(), List.of())))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // Perfil del usuario
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Cualquier usuario autenticado consulta su propio perfil")
    void perfilPropioSiempreAccesible() throws Exception {
        mockMvc.perform(get("/api/me")
                        .with(tokenDe("cliente.basic",
                                List.of(SecurityRoles.CUSTOMER), List.of())))
                .andExpect(status().isOk());
    }
}
