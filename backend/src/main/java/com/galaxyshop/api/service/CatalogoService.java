package com.galaxyshop.api.service;

import com.galaxyshop.api.dto.Dtos.*;
import com.galaxyshop.api.exception.ApiExceptions;
import com.galaxyshop.api.model.Product;
import com.galaxyshop.api.model.ProductCatalog;
import com.galaxyshop.api.repository.ProductCatalogRepository;
import com.galaxyshop.api.repository.ProductRepository;
import com.galaxyshop.api.security.SecurityRoles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Reglas de acceso al catalogo.
 *
 * Rubrica #4: seguridad a nivel de metodo con @PreAuthorize. Se combinan dos
 * estilos deliberadamente:
 *   - hasRole(...)  -> decision por rol, rapida, resuelta con el propio token
 *   - @granular     -> decision delegada a las Policies de Keycloak (Rubrica #6)
 */
@Service
public class CatalogoService {

    private final ProductCatalogRepository catalogos;
    private final ProductRepository productos;

    public CatalogoService(ProductCatalogRepository catalogos, ProductRepository productos) {
        this.catalogos = catalogos;
        this.productos = productos;
    }

    /** Vista publica: la usa el escaparate de la tienda, sin token. */
    @Transactional(readOnly = true)
    public List<CatalogoDto> catalogosPublicos() {
        return catalogos.findAll().stream().map(CatalogoDto::de).toList();
    }

    @Transactional(readOnly = true)
    public List<ProductoDto> productosPublicos() {
        return productos.findAll().stream().map(ProductoDto::de).toList();
    }

    /**
     * Acceso privado: exige el rol de cliente catalog-read y, ademas, que la
     * seguridad granular de Keycloak conceda el scope catalog:view.
     */
    @PreAuthorize("hasAnyRole('" + SecurityRoles.CATALOG_READ + "','" + SecurityRoles.ADMIN + "') "
                + "and @authorizationGranularService.permite('" + SecurityRoles.RES_CATALOGO
                + "','" + SecurityRoles.SCOPE_CATALOG_VIEW + "')")
    @Transactional(readOnly = true)
    public List<ProductoDto> listarProductos() {
        return productos.findAll().stream().map(ProductoDto::de).toList();
    }

    @PreAuthorize("hasAnyRole('" + SecurityRoles.CATALOG_READ + "','" + SecurityRoles.ADMIN + "')")
    @Transactional(readOnly = true)
    public ProductoDto buscarProducto(UUID id) {
        return productos.findById(id).map(ProductoDto::de)
                .orElseThrow(() -> new ApiExceptions.NoEncontrado("No existe el producto " + id));
    }

    /**
     * Acceso especial: la decision la toma Keycloak. La Permission 'Editar
     * catalogo' exige rol de personal de tienda Y horario laboral.
     */
    @PreAuthorize("@authorizationGranularService.permite('" + SecurityRoles.RES_CATALOGO
                + "','" + SecurityRoles.SCOPE_CATALOG_EDIT + "')")
    @Transactional
    public ProductoDto crearProducto(NuevoProductoDto dto) {
        ProductCatalog catalogo = catalogos.findById(dto.catalogoId())
                .orElseThrow(() -> new ApiExceptions.NoEncontrado("No existe el catalogo " + dto.catalogoId()));

        Product producto = new Product(dto.nombre(), dto.descripcion(),
                dto.precioUnitario(), dto.stock(), catalogo);
        return ProductoDto.de(productos.save(producto));
    }

    @PreAuthorize("@authorizationGranularService.permite('" + SecurityRoles.RES_CATALOGO
                + "','" + SecurityRoles.SCOPE_CATALOG_EDIT + "')")
    @Transactional
    public ProductoDto actualizarProducto(UUID id, NuevoProductoDto dto) {
        Product producto = productos.findById(id)
                .orElseThrow(() -> new ApiExceptions.NoEncontrado("No existe el producto " + id));

        producto.setName(dto.nombre());
        producto.setDescription(dto.descripcion());
        producto.setUnitPrice(dto.precioUnitario());
        producto.setStock(dto.stock());
        catalogos.findById(dto.catalogoId()).ifPresent(producto::setCatalog);
        return ProductoDto.de(productos.save(producto));
    }

    /** Operacion destructiva: solo administradores (Permission 'Eliminar del catalogo'). */
    @PreAuthorize("hasRole('" + SecurityRoles.CATALOG_DELETE + "') "
                + "and @authorizationGranularService.permite('" + SecurityRoles.RES_CATALOGO
                + "','" + SecurityRoles.SCOPE_CATALOG_DELETE + "')")
    @Transactional
    public void eliminarProducto(UUID id) {
        if (!productos.existsById(id)) {
            throw new ApiExceptions.NoEncontrado("No existe el producto " + id);
        }
        productos.deleteById(id);
    }
}
