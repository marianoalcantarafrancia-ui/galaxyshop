package com.galaxyshop.api.service;

import com.galaxyshop.api.dto.Dtos.*;
import com.galaxyshop.api.exception.ApiExceptions;
import com.galaxyshop.api.model.CustomerOrder;
import com.galaxyshop.api.model.OrderStatus;
import com.galaxyshop.api.model.Product;
import com.galaxyshop.api.repository.CustomerOrderRepository;
import com.galaxyshop.api.repository.ProductRepository;
import com.galaxyshop.api.security.SecurityRoles;
import com.galaxyshop.api.security.UsuarioActual;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Reglas de acceso a los pedidos.
 *
 * Aqui esta el ejemplo canonico de @PostAuthorize: para saber si un pedido es
 * "propio" hay que haberlo leido antes, de modo que la comprobacion solo puede
 * hacerse DESPUES de ejecutar el metodo.
 */
@Service
public class PedidoService {

    private final CustomerOrderRepository pedidos;
    private final ProductRepository productos;
    private final UsuarioActual usuarioActual;

    public PedidoService(CustomerOrderRepository pedidos, ProductRepository productos,
                         UsuarioActual usuarioActual) {
        this.pedidos = pedidos;
        this.productos = productos;
        this.usuarioActual = usuarioActual;
    }

    /** Cada cliente ve unicamente sus propios pedidos. */
    @PreAuthorize("hasAnyRole('" + SecurityRoles.ORDER_READ_OWN + "','"
                + SecurityRoles.ORDER_READ_ALL + "','" + SecurityRoles.ADMIN + "')")
    @Transactional(readOnly = true)
    public List<PedidoDto> misPedidos() {
        return pedidos.findByCustomerUsernameOrderByOrderDateDesc(usuarioActual.username())
                .stream().map(PedidoDto::de).toList();
    }

    /**
     * @PostAuthorize: el metodo se ejecuta, y solo se devuelve el resultado si
     * el pedido pertenece al solicitante o este tiene permiso de ver todos.
     * 'returnObject' es el valor que iba a retornarse.
     */
    @PreAuthorize("isAuthenticated()")
    @PostAuthorize("returnObject.cliente == authentication.name "
                 + "or hasAnyRole('" + SecurityRoles.ORDER_READ_ALL + "','" + SecurityRoles.ADMIN + "')")
    @Transactional(readOnly = true)
    public PedidoDto buscarPedido(UUID id) {
        return pedidos.findById(id).map(PedidoDto::de)
                .orElseThrow(() -> new ApiExceptions.NoEncontrado("No existe el pedido " + id));
    }

    /** Vista de tienda: los pedidos atendidos por la sede del usuario. */
    @PreAuthorize("hasAnyRole('" + SecurityRoles.ORDER_READ_ALL + "','" + SecurityRoles.ADMIN + "')")
    @Transactional(readOnly = true)
    public List<PedidoDto> pedidosDeMiSede() {
        String sede = usuarioActual.sede();
        if (sede == null) {
            return List.of();
        }
        return pedidos.findByStoreCodeOrderByOrderDateDesc(sede).stream().map(PedidoDto::de).toList();
    }

    @PreAuthorize("hasAnyRole('" + SecurityRoles.ORDER_READ_ALL + "','" + SecurityRoles.ADMIN + "')")
    @Transactional(readOnly = true)
    public List<PedidoDto> todosLosPedidos() {
        return pedidos.findAll().stream()
                .sorted(Comparator.comparing(CustomerOrder::getOrderDate).reversed())
                .map(PedidoDto::de).toList();
    }

    /**
     * Registrar un pedido exige el rol de cliente y que la Permission
     * 'Crear pedido' de Keycloak lo autorice.
     */
    @PreAuthorize("hasAnyRole('" + SecurityRoles.ORDER_CREATE + "','" + SecurityRoles.ADMIN + "') "
                + "and @authorizationGranularService.permite('" + SecurityRoles.RES_PEDIDOS
                + "','" + SecurityRoles.SCOPE_ORDER_CREATE + "')")
    @Transactional
    public PedidoDto registrarPedido(NuevoPedidoDto dto) {
        Product producto = productos.findById(dto.productoId())
                .orElseThrow(() -> new ApiExceptions.NoEncontrado("No existe el producto " + dto.productoId()));

        if (producto.getStock() < dto.cantidad()) {
            throw new ApiExceptions.ReglaDeNegocio(
                    "Stock insuficiente de '" + producto.getName() + "': quedan " + producto.getStock());
        }
        producto.setStock(producto.getStock() - dto.cantidad());
        productos.save(producto);

        CustomerOrder pedido = new CustomerOrder(usuarioActual.username(), producto,
                dto.cantidad(), usuarioActual.sede());
        return PedidoDto.de(pedidos.save(pedido));
    }

    /**
     * Anular exige la Permission 'Anular pedido', que en Keycloak combina
     * pertenencia al grupo /Corporativo/Gerencia Y estar en horario laboral.
     */
    @PreAuthorize("@authorizationGranularService.permite('" + SecurityRoles.RES_PEDIDOS
                + "','" + SecurityRoles.SCOPE_ORDER_CANCEL + "')")
    @Transactional
    public PedidoDto anularPedido(UUID id) {
        CustomerOrder pedido = pedidos.findById(id)
                .orElseThrow(() -> new ApiExceptions.NoEncontrado("No existe el pedido " + id));

        if (pedido.getStatus() == OrderStatus.ANULADO) {
            throw new ApiExceptions.ReglaDeNegocio("El pedido ya estaba anulado");
        }
        pedido.anular();
        return PedidoDto.de(pedidos.save(pedido));
    }

    /** Reporte agregado por catalogo. */
    @PreAuthorize("hasAnyRole('" + SecurityRoles.REPORT_READ + "','" + SecurityRoles.ADMIN + "') "
                + "and @authorizationGranularService.permite('" + SecurityRoles.RES_REPORTES
                + "','" + SecurityRoles.SCOPE_REPORT_VIEW + "')")
    @Transactional(readOnly = true)
    public List<ReporteVentasDto> reporteDeVentas() {
        return pedidos.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.REGISTRADO)
                .collect(java.util.stream.Collectors.groupingBy(CustomerOrder::getCatalogName))
                .entrySet().stream()
                .map(e -> new ReporteVentasDto(
                        e.getKey(),
                        e.getValue().size(),
                        e.getValue().stream().map(CustomerOrder::getTotal)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .sorted(Comparator.comparing(ReporteVentasDto::montoTotal).reversed())
                .toList();
    }
}
