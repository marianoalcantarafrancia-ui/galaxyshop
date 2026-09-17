package com.galaxyshop.api.config;

import com.galaxyshop.api.model.SecurityRule;
import com.galaxyshop.api.repository.SecurityRuleRepository;
import com.galaxyshop.api.security.AppSecurityProperties;
import com.galaxyshop.api.security.KeycloakJwtAuthenticationConverter;
import com.galaxyshop.api.security.SecurityRoles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Rubrica #4 - Filtro general de seguridad.
 *
 * Un unico SecurityFilterChain resuelve los tres niveles de acceso del caso:
 *
 *   PUBLICO   sin token         catalogo de la tienda, health, documentacion
 *   PRIVADO   token valido      operaciones del usuario autenticado
 *   ESPECIAL  token + rol       administracion, reportes y anulaciones
 *
 * Las reglas se arman combinando las tres fuentes que pide la rubrica:
 *   1. CONSTANTES        SecurityRoles
 *   2. PROPERTIES        app.security.* en application.yml
 *   3. BASE DE DATOS     tabla security_rule
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final AppSecurityProperties propiedades;
    private final KeycloakJwtAuthenticationConverter jwtConverter;
    private final SecurityRuleRepository reglasRepo;

    public SecurityConfig(AppSecurityProperties propiedades,
                          KeycloakJwtAuthenticationConverter jwtConverter,
                          SecurityRuleRepository reglasRepo) {
        this.propiedades = propiedades;
        this.jwtConverter = jwtConverter;
        this.reglasRepo = reglasRepo;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            // API sin estado: cada peticion se autoriza con su propio bearer token
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> {

                // -------------------------------------------------------------
                // FUENTE 2: rutas publicas declaradas en application.yml
                // -------------------------------------------------------------
                for (String ruta : propiedades.getPublicPaths()) {
                    auth.requestMatchers(ruta).permitAll();
                }
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                // -------------------------------------------------------------
                // FUENTE 3: reglas administrables desde la base de datos
                // -------------------------------------------------------------
                if (propiedades.isDatabaseRulesEnabled()) {
                    aplicarReglasDeBaseDeDatos(auth);
                }

                // -------------------------------------------------------------
                // FUENTE 1: reglas de negocio expresadas con constantes
                // -------------------------------------------------------------

                // Catalogo: leer lo puede cualquiera autenticado con catalog-read;
                // escribir exige el rol de cliente correspondiente.
                auth.requestMatchers(HttpMethod.GET, "/api/catalogs/**", "/api/products/**")
                        .hasAnyRole(SecurityRoles.CATALOG_READ, SecurityRoles.ADMIN);
                auth.requestMatchers(HttpMethod.POST, "/api/products/**")
                        .hasAnyRole(SecurityRoles.CATALOG_WRITE, SecurityRoles.ADMIN);
                auth.requestMatchers(HttpMethod.PUT, "/api/products/**")
                        .hasAnyRole(SecurityRoles.CATALOG_WRITE, SecurityRoles.ADMIN);
                auth.requestMatchers(HttpMethod.DELETE, "/api/products/**")
                        .hasAnyRole(SecurityRoles.CATALOG_DELETE, SecurityRoles.ADMIN);

                // Pedidos: el detalle fino se resuelve por metodo (@PreAuthorize)
                auth.requestMatchers("/api/orders/**").authenticated();

                // Reportes: acceso especial
                auth.requestMatchers("/api/reports/**")
                        .hasAnyRole(SecurityRoles.REPORT_READ, SecurityRoles.AUDITOR,
                                    SecurityRoles.MANAGER, SecurityRoles.ADMIN);

                // Administracion: solo el rol de realm gs-admin
                auth.requestMatchers("/api/admin/**").hasRole(SecurityRoles.ADMIN);

                // Cualquier otra ruta exige token valido
                auth.anyRequest().authenticated();
            })

            // Validacion del JWT emitido por Keycloak
            .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))

            .exceptionHandling(Customizer.withDefaults());

        return http.build();
    }

    /**
     * Publica en el filtro las reglas activas de la tabla security_rule.
     * Permite abrir o cerrar un endpoint sin tocar el codigo ni recompilar.
     */
    private void aplicarReglasDeBaseDeDatos(
            org.springframework.security.config.annotation.web.configurers
                    .AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {

        List<SecurityRule> reglas;
        try {
            reglas = reglasRepo.findByEnabledTrueOrderBySortOrderAsc();
        } catch (Exception e) {
            log.warn("No se pudieron leer las reglas de seguridad de la base de datos: {}", e.getMessage());
            return;
        }

        for (SecurityRule regla : reglas) {
            var matcher = "*".equals(regla.getMethod())
                    ? auth.requestMatchers(regla.getPattern())
                    : auth.requestMatchers(HttpMethod.valueOf(regla.getMethod()), regla.getPattern());

            if (regla.isPublicAccess()) {
                matcher.permitAll();
                log.info("Regla BD [{}] {} {} -> PUBLICO", regla.getSortOrder(), regla.getMethod(), regla.getPattern());
            } else if (regla.rolesArray().length > 0) {
                matcher.hasAnyRole(regla.rolesArray());
                log.info("Regla BD [{}] {} {} -> roles {}", regla.getSortOrder(), regla.getMethod(),
                        regla.getPattern(), regla.getRequiredRoles());
            } else {
                matcher.authenticated();
                log.info("Regla BD [{}] {} {} -> AUTENTICADO", regla.getSortOrder(), regla.getMethod(), regla.getPattern());
            }
        }
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(propiedades.getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
