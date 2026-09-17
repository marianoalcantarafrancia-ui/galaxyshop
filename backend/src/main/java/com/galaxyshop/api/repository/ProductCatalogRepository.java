package com.galaxyshop.api.repository;

import com.galaxyshop.api.model.ProductCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductCatalogRepository extends JpaRepository<ProductCatalog, UUID> {
    Optional<ProductCatalog> findByNameIgnoreCase(String name);
}
