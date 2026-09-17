package com.galaxyshop.api.controller;

import com.galaxyshop.api.dto.Dtos.PedidoDto;
import com.galaxyshop.api.model.SecurityRule;
import com.galaxyshop.api.repository.SecurityRuleRepository;
import com.galaxyshop.api.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.galaxyshop.api.security.SecurityRoles.ADMIN;

/** ACCESO ESPECIAL: administracion, reservada al rol de realm gs-admin. */
@RestController
@RequestMapping("/api/admin")
@SecurityRequirement(name = "keycloak")
@Tag(name = "5. Administracion", description = "Operaciones reservadas a gs-admin")
public class AdminController {

    private final PedidoService pedidoService;
    private final SecurityRuleRepository reglas;

    public AdminController(PedidoService pedidoService, SecurityRuleRepository reglas) {
        this.pedidoService = pedidoService;
        this.reglas = reglas;
    }

    @GetMapping("/orders")
    @Operation(summary = "Todos los pedidos de la cadena")
    @PreAuthorize("hasRole('" + ADMIN + "')")
    public List<PedidoDto> todosLosPedidos() {
        return pedidoService.todosLosPedidos();
    }

    /** Muestra las reglas de seguridad que el filtro leyo de la base de datos. */
    @GetMapping("/security-rules")
    @Operation(summary = "Reglas de acceso vigentes almacenadas en base de datos")
    @PreAuthorize("hasRole('" + ADMIN + "')")
    public List<Map<String, Object>> reglasDeSeguridad() {
        return reglas.findByEnabledTrueOrderBySortOrderAsc().stream()
                .map(this::aMapa)
                .toList();
    }

    private Map<String, Object> aMapa(SecurityRule r) {
        return Map.of(
                "orden", r.getSortOrder(),
                "metodo", r.getMethod(),
                "patron", r.getPattern(),
                "publico", r.isPublicAccess(),
                "roles", r.getRequiredRoles(),
                "descripcion", r.getDescription() == null ? "" : r.getDescription());
    }
}
