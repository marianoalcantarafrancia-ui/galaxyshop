package com.galaxyshop.api.controller;

import com.galaxyshop.api.dto.Dtos.*;
import com.galaxyshop.api.service.CatalogoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ACCESO PUBLICO - no requiere token.
 *
 * Es el escaparate de la tienda: cualquier visitante ve el catalogo antes de
 * registrarse. Estas rutas se declaran en app.security.public-paths.
 */
@RestController
@RequestMapping("/api/public")
@Tag(name = "1. Publico", description = "Endpoints abiertos, sin autenticacion")
public class PublicoController {

    private final CatalogoService catalogoService;

    public PublicoController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping("/health")
    @Operation(summary = "Estado del servicio")
    public Map<String, Object> salud() {
        return Map.of(
                "servicio", "galaxyshop-api",
                "estado", "OPERATIVO",
                "fecha", LocalDateTime.now().toString());
    }

    @GetMapping("/catalogs")
    @Operation(summary = "Catalogos de la tienda (escaparate publico)")
    public List<CatalogoDto> catalogos() {
        return catalogoService.catalogosPublicos();
    }

    @GetMapping("/products")
    @Operation(summary = "Productos en venta (escaparate publico)")
    public List<ProductoDto> productos() {
        return catalogoService.productosPublicos();
    }
}
