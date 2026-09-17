package com.galaxyshop.api.controller;

import com.galaxyshop.api.dto.Dtos.*;
import com.galaxyshop.api.service.CatalogoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * ACCESO PRIVADO Y ESPECIAL sobre el catalogo.
 *   GET    -> privado  (rol catalog-read)
 *   POST   -> especial (scope catalog:edit, con policy de horario)
 *   DELETE -> especial (scope catalog:delete, solo administradores)
 */
@RestController
@RequestMapping("/api")
@SecurityRequirement(name = "keycloak")
@Tag(name = "2. Catalogo", description = "Gestion de catalogos y productos")
public class CatalogoController {

    private final CatalogoService catalogoService;

    public CatalogoController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping("/catalogs")
    @Operation(summary = "Lista los catalogos (requiere catalog-read)")
    public List<CatalogoDto> catalogos() {
        return catalogoService.catalogosPublicos();
    }

    @GetMapping("/products")
    @Operation(summary = "Lista los productos (requiere catalog-read + scope catalog:view)")
    public List<ProductoDto> productos() {
        return catalogoService.listarProductos();
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "Detalle de un producto")
    public ProductoDto producto(@PathVariable UUID id) {
        return catalogoService.buscarProducto(id);
    }

    @PostMapping("/products")
    @Operation(summary = "Registra un producto (scope catalog:edit, solo en horario laboral)")
    public ResponseEntity<ProductoDto> crear(@Valid @RequestBody NuevoProductoDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogoService.crearProducto(dto));
    }

    @PutMapping("/products/{id}")
    @Operation(summary = "Actualiza un producto (scope catalog:edit)")
    public ProductoDto actualizar(@PathVariable UUID id, @Valid @RequestBody NuevoProductoDto dto) {
        return catalogoService.actualizarProducto(id, dto);
    }

    @DeleteMapping("/products/{id}")
    @Operation(summary = "Elimina un producto (scope catalog:delete, solo gs-admin)")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        catalogoService.eliminarProducto(id);
        return ResponseEntity.noContent().build();
    }
}
