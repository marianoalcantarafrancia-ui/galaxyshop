package com.galaxyshop.api.controller;

import com.galaxyshop.api.dto.Dtos.PerfilDto;
import com.galaxyshop.api.security.AuthorizationGranularService;
import com.galaxyshop.api.security.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * ACCESO PRIVADO: devuelve la identidad efectiva del portador del token.
 * El frontend Angular lo usa para pintar el menu segun roles y permisos.
 */
@RestController
@RequestMapping("/api/me")
@SecurityRequirement(name = "keycloak")
@Tag(name = "6. Mi perfil", description = "Identidad y permisos del usuario autenticado")
public class PerfilController {

    private final UsuarioActual usuarioActual;
    private final AuthorizationGranularService granular;

    public PerfilController(UsuarioActual usuarioActual, AuthorizationGranularService granular) {
        this.usuarioActual = usuarioActual;
        this.granular = granular;
    }

    @GetMapping
    @Operation(summary = "Perfil, roles y permisos granulares vigentes")
    public PerfilDto perfil() {
        return new PerfilDto(
                usuarioActual.username(),
                usuarioActual.nombreCompleto(),
                usuarioActual.email(),
                usuarioActual.sede(),
                usuarioActual.categoria(),
                usuarioActual.grupos(),
                usuarioActual.authorities(),
                granular.permisosDelUsuario());
    }

    @GetMapping("/permissions")
    @Operation(summary = "Solo los permisos concedidos por Authorization Services (RPT)")
    public List<Map<String, Object>> permisos() {
        return granular.permisosDelUsuario();
    }
}
