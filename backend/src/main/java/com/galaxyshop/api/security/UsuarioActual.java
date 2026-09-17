package com.galaxyshop.api.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Lectura comoda del token del usuario que hace la peticion.
 * Se usa desde los controladores y desde las expresiones @PreAuthorize.
 */
@Component("usuarioActual")
public class UsuarioActual {

    public Optional<Jwt> token() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.of(jwtAuth.getToken());
        }
        return Optional.empty();
    }

    public String username() {
        return token().map(t -> t.getClaimAsString("preferred_username")).orElse("anonimo");
    }

    public String nombreCompleto() {
        return token().map(t -> t.getClaimAsString("name")).orElse(username());
    }

    public String email() {
        return token().map(t -> t.getClaimAsString("email")).orElse(null);
    }

    /** Sede del usuario; llega del atributo 'store' heredado de su grupo. */
    public String sede() {
        return token().map(t -> t.getClaimAsString("store")).orElse(null);
    }

    /** Categoria comercial del cliente (GOLD / BASIC). */
    public String categoria() {
        return token().map(t -> t.getClaimAsString("tier")).orElse("BASIC");
    }

    @SuppressWarnings("unchecked")
    public List<String> grupos() {
        return token()
                .map(t -> (List<String>) t.getClaim("grupos"))
                .orElse(List.of());
    }

    public List<String> authorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return List.of();
        }
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    public boolean tieneRol(String rol) {
        return authorities().contains(SecurityRoles.conPrefijo(rol));
    }

    /**
     * Se invoca desde @PostAuthorize para comprobar que un pedido devuelto
     * pertenece a quien lo pide.
     */
    public boolean esPropietario(String usernameDelRecurso) {
        return username().equals(usernameDelRecurso);
    }
}
