package com.galaxyshop.api.dto;

import com.galaxyshop.api.model.CustomerOrder;
import com.galaxyshop.api.model.Product;
import com.galaxyshop.api.model.ProductCatalog;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Objetos de transporte de la API. */
public final class Dtos {

    private Dtos() { }

    public record CatalogoDto(UUID id, String nombre, String descripcion) {
        public static CatalogoDto de(ProductCatalog c) {
            return new CatalogoDto(c.getId(), c.getName(), c.getDescription());
        }
    }

    public record ProductoDto(UUID id, String nombre, String descripcion,
                              BigDecimal precioUnitario, Integer stock,
                              String catalogo, UUID catalogoId) {
        public static ProductoDto de(Product p) {
            return new ProductoDto(p.getId(), p.getName(), p.getDescription(),
                    p.getUnitPrice(), p.getStock(),
                    p.getCatalog().getName(), p.getCatalog().getId());
        }
    }

    public record NuevoProductoDto(
            @NotBlank(message = "el nombre es obligatorio")
            @Size(max = 160) String nombre,
            @Size(max = 4000) String descripcion,
            @NotNull @DecimalMin(value = "0.01", message = "el precio debe ser mayor que cero")
            BigDecimal precioUnitario,
            @NotNull @Min(0) Integer stock,
            @NotNull UUID catalogoId) { }

    public record NuevoPedidoDto(
            @NotNull(message = "indique el producto") UUID productoId,
            @NotNull @Min(value = 1, message = "la cantidad minima es 1")
            @Max(value = 100, message = "la cantidad maxima por pedido es 100")
            Integer cantidad) { }

    public record PedidoDto(UUID id, String cliente, String producto, String catalogo,
                            String sede, Integer cantidad, BigDecimal precioUnitario,
                            BigDecimal total, String estado, LocalDateTime fecha) {
        public static PedidoDto de(CustomerOrder o) {
            return new PedidoDto(o.getId(), o.getCustomerUsername(), o.getProductName(),
                    o.getCatalogName(), o.getStoreCode(), o.getQuantity(), o.getUnitPrice(),
                    o.getTotal(), o.getStatus().name(), o.getOrderDate());
        }
    }

    public record PerfilDto(String usuario, String nombre, String email, String sede,
                            String categoria, List<String> grupos, List<String> roles,
                            List<Map<String, Object>> permisosGranulares) { }

    public record ReporteVentasDto(String catalogo, long pedidos, BigDecimal montoTotal) { }

    public record ErrorDto(int codigo, String error, String mensaje, String ruta) { }
}
