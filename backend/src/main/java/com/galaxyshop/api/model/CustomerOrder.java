package com.galaxyshop.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Pedido registrado por un cliente.
 *
 * 'customerUsername' guarda el preferred_username del token de Keycloak: es la
 * pieza que permite decidir, en @PostAuthorize, si un pedido es "propio".
 */
@Entity
@Table(name = "customer_order")
public class CustomerOrder {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "customer_username", nullable = false, length = 120)
    private String customerUsername;

    @Column(name = "product_name", nullable = false, length = 160)
    private String productName;

    @Column(name = "catalog_name", nullable = false, length = 120)
    private String catalogName;

    /** Sede que atiende el pedido; llega del claim 'store' del token. */
    @Column(name = "store_code", length = 40)
    private String storeCode;

    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.REGISTRADO;

    protected CustomerOrder() { }

    public CustomerOrder(String customerUsername, Product product, Integer quantity, String storeCode) {
        this.customerUsername = customerUsername;
        this.productName = product.getName();
        this.catalogName = product.getCatalog().getName();
        this.unitPrice = product.getUnitPrice();
        this.quantity = quantity;
        this.storeCode = storeCode;
        this.orderDate = LocalDateTime.now();
    }

    public BigDecimal getTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public void anular() { this.status = OrderStatus.ANULADO; }

    public UUID getId() { return id; }
    public String getCustomerUsername() { return customerUsername; }
    public String getProductName() { return productName; }
    public String getCatalogName() { return catalogName; }
    public String getStoreCode() { return storeCode; }
    public LocalDateTime getOrderDate() { return orderDate; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public OrderStatus getStatus() { return status; }
}
