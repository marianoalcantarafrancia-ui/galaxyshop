package com.galaxyshop.api.controller;

import com.galaxyshop.api.dto.Dtos.ReporteVentasDto;
import com.galaxyshop.api.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** ACCESO ESPECIAL: reportes para Gerencia y Auditoria. */
@RestController
@RequestMapping("/api/reports")
@SecurityRequirement(name = "keycloak")
@Tag(name = "4. Reportes", description = "Informacion agregada de ventas")
public class ReporteController {

    private final PedidoService pedidoService;

    public ReporteController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping("/sales")
    @Operation(summary = "Ventas por catalogo (scope report:view)")
    public List<ReporteVentasDto> ventas() {
        return pedidoService.reporteDeVentas();
    }
}
