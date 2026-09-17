package com.galaxyshop.api.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Traduce el JWT de Keycloak a authorities de Spring Security.
 *
 * Keycloak reparte los roles en dos sitios distintos y ninguno coincide con lo
 * que Spring espera por defecto (el claim 'scope'):
 *
 *   realm_access.roles                       -> roles del realm
 *   resource_access.{clientId}.roles         -> roles del cliente
 *
 * Sin este converter, @PreAuthorize("hasRole('gs-admin')") nunca se cumple.
 * Se publican tres familias de authorities:
 *   ROLE_gs-admin        (rol de realm)
 *   ROLE_catalog-write   (rol de cliente)
 *   SCOPE_profile        (scopes OAuth2 del token)
 */
@Component
public class KeycloakJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AppSecurityProperties propiedades;

    public KeycloakJwtAuthenticationConverter(AppSecurityProperties propiedades) {
        this.propiedades = propiedades;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.addAll(rolesDeRealm(jwt));
        authorities.addAll(rolesDeCliente(jwt));
        authorities.addAll(scopesOauth(jwt));

        String nombre = jwt.getClaimAsString(propiedades.getPrincipalClaim());
        if (nombre == null || nombre.isBlank()) {
            nombre = jwt.getSubject();
        }
        return new JwtAuthenticationToken(jwt, authorities, nombre);
    }

    /** realm_access.roles -> ROLE_xxx */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> rolesDeRealm(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim(propiedades.getRealmRolesClaim());
        if (realmAccess == null) {
            return List.of();
        }
        Object roles = realmAccess.get("roles");
        if (!(roles instanceof Collection<?> lista)) {
            return List.of();
        }
        return ((Collection<String>) lista).stream()
                .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority(SecurityRoles.conPrefijo(rol)))
                .toList();
    }

    /** resource_access.{clientId}.roles -> ROLE_xxx */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> rolesDeCliente(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess == null) {
            return List.of();
        }
        Object cliente = resourceAccess.get(propiedades.getResourceClientId());
        if (!(cliente instanceof Map<?, ?> mapa)) {
            return List.of();
        }
        Object roles = ((Map<String, Object>) mapa).get("roles");
        if (!(roles instanceof Collection<?> lista)) {
            return List.of();
        }
        return ((Collection<String>) lista).stream()
                .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority(SecurityRoles.conPrefijo(rol)))
                .toList();
    }

    /** scope -> SCOPE_xxx (lo que Spring genera por defecto, aqui preservado) */
    private Collection<GrantedAuthority> scopesOauth(Jwt jwt) {
        String scope = jwt.getClaimAsString("scope");
        if (scope == null || scope.isBlank()) {
            return List.of();
        }
        return Arrays.stream(scope.split(" "))
                .filter(s -> !s.isBlank())
                .map(s -> (GrantedAuthority) new SimpleGrantedAuthority("SCOPE_" + s))
                .toList();
    }
}
