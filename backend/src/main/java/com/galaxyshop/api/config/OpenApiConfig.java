package com.galaxyshop.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI con boton "Authorize" contra Keycloak: permite probar la API
 * iniciando sesion con los usuarios del realm, sin salir del navegador.
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuer;

    @Bean
    public OpenAPI openAPI() {
        OAuthFlow authorizationCode = new OAuthFlow()
                .authorizationUrl(issuer + "/protocol/openid-connect/auth")
                .tokenUrl(issuer + "/protocol/openid-connect/token")
                .scopes(new Scopes()
                        .addString("openid", "Identidad del usuario")
                        .addString("profile", "Datos de perfil")
                        .addString("shop-orders", "Acceso a los pedidos")
                        .addString("shop-reports", "Acceso a los reportes"));

        return new OpenAPI()
                .info(new Info()
                        .title("GalaxyShop API")
                        .version("1.0.0")
                        .description("""
                                API REST de una tienda de comestibles asegurada con Keycloak 26,
                                Spring Security y OAuth2 / OpenID Connect.

                                Niveles de acceso:
                                  publico   /api/public/**   sin token
                                  privado   /api/orders/**   token valido
                                  especial  /api/admin/**    token + rol gs-admin
                                """)
                        .contact(new Contact().name("Proyecto final - Security with Keycloak")))
                .components(new Components().addSecuritySchemes("keycloak",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .description("Authorization Code + PKCE contra el realm galaxyshop")
                                .flows(new OAuthFlows().authorizationCode(authorizationCode))));
    }
}
