package com.galaxyshop.api.controller;

import com.galaxyshop.api.dto.Dtos.*;
import com.galaxyshop.api.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** ACCESO PRIVADO a los pedidos del usuario autenticado. */
@RestController
@RequestMapping("/api/orders")
@SecurityRequirement(name = "keycloak")
@Tag(name = "3. Pedidos", description = "Registro y consulta de pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping("/me")
    @Operation(summary = "Mis pedidos (rol order-read-own)")
    public List<PedidoDto> misPedidos() {
        return pedidoService.misPedidos();
    }

    @GetMapping("/store")
    @Operation(summary = "Pedidos atendidos por mi sede (rol order-read-all)")
    public List<PedidoDto> pedidosDeMiSede() {
        return pedidoService.pedidosDeMiSede();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un pedido; @PostAuthorize valida que sea propio")
    public PedidoDto pedido(@PathVariable UUID id) {
        return pedidoService.buscarPedido(id);
    }

    @PostMapping
    @Operation(summary = "Registra un pedido (scope order:create)")
    public ResponseEntity<PedidoDto> registrar(@Valid @RequestBody NuevoPedidoDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.registrarPedido(dto));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Anula un pedido (scope order:cancel: Gerencia y en horario laboral)")
    public PedidoDto anular(@PathVariable UUID id) {
        return pedidoService.anularPedido(id);
    }
}
