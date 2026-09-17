package com.galaxyshop.api.model;

import jakarta.persistence.*;
import java.util.UUID;

/** Categoria comercial bajo la que se agrupan los productos (Panaderia, Lacteos...). */
@Entity
@Table(name = "product_catalog")
public class ProductCatalog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(length = 4000)
    private String description;

    protected ProductCatalog() { }

    public ProductCatalog(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
}
