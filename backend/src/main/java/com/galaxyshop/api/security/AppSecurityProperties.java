package com.galaxyshop.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Rubrica #4 - segunda via de personalizacion: ARCHIVO PROPERTIES.
 *
 * Todo lo que en otro proyecto estaria incrustado en SecurityConfig (rutas
 * publicas, nombre del claim de roles, cliente cuyos roles interesan, origenes
 * CORS) se declara en application.yml bajo el prefijo 'app.security'.
 */
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

    /** Rutas que no exigen token. */
    private List<String> publicPaths = new ArrayList<>();

    /** Cliente de Keycloak del que se leen los roles en resource_access. */
    private String resourceClientId = "shop-backend";

    /** Claim del token que contiene los roles de realm. */
    private String realmRolesClaim = "realm_access";

    /** Claim del token que se usa como nombre de usuario. */
    private String principalClaim = "preferred_username";

    /** Origenes permitidos para CORS. */
    private List<String> allowedOrigins = new ArrayList<>();

    /** Si esta activo, las reglas de la tabla security_rule se suman al filtro. */
    private boolean databaseRulesEnabled = true;

    /** Configuracion de la seguridad granular (Authorization Services). */
    private Granular granular = new Granular();

    public static class Granular {
        /** Activa las consultas UMA contra Keycloak. */
        private boolean enabled = true;
        /** Identificador del resource server en Keycloak. */
        private String audience = "shop-backend";
        /** Segundos que se cachea una decision de autorizacion. */
        private long cacheSeconds = 30;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAudience() { return audience; }
        public void setAudience(String audience) { this.audience = audience; }
        public long getCacheSeconds() { return cacheSeconds; }
        public void setCacheSeconds(long cacheSeconds) { this.cacheSeconds = cacheSeconds; }
    }

    public List<String> getPublicPaths() { return publicPaths; }
    public void setPublicPaths(List<String> publicPaths) { this.publicPaths = publicPaths; }
    public String getResourceClientId() { return resourceClientId; }
    public void setResourceClientId(String resourceClientId) { this.resourceClientId = resourceClientId; }
    public String getRealmRolesClaim() { return realmRolesClaim; }
    public void setRealmRolesClaim(String realmRolesClaim) { this.realmRolesClaim = realmRolesClaim; }
    public String getPrincipalClaim() { return principalClaim; }
    public void setPrincipalClaim(String principalClaim) { this.principalClaim = principalClaim; }
    public List<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    public boolean isDatabaseRulesEnabled() { return databaseRulesEnabled; }
    public void setDatabaseRulesEnabled(boolean databaseRulesEnabled) { this.databaseRulesEnabled = databaseRulesEnabled; }
    public Granular getGranular() { return granular; }
    public void setGranular(Granular granular) { this.granular = granular; }
}
