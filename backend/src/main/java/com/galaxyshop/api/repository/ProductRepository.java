package com.galaxyshop.api.repository;

import com.galaxyshop.api.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByCatalogId(UUID catalogId);
    List<Product> findByNameContainingIgnoreCase(String fragmento);
}
