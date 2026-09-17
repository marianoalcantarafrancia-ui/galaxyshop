package com.galaxyshop.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Producto del catalogo, con su precio y su vigencia comercial. */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 4000)
    private String description;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer stock = 0;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_catalog_id", nullable = false)
    private ProductCatalog catalog;

    protected Product() { }

    public Product(String name, String description, BigDecimal unitPrice,
                   Integer stock, ProductCatalog catalog) {
        this.name = name;
        this.description = description;
        this.unitPrice = unitPrice;
        this.stock = stock;
        this.catalog = catalog;
        this.effectiveDate = LocalDate.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public LocalDate getExpirationDate() { return expirationDate; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public Integer getStock() { return stock; }
    public ProductCatalog getCatalog() { return catalog; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public void setStock(Integer stock) { this.stock = stock; }
    public void setExpirationDate(LocalDate expirationDate) { this.expirationDate = expirationDate; }
    public void setCatalog(ProductCatalog catalog) { this.catalog = catalog; }
}
