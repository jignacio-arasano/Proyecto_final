package com.marketplace.repository;

import com.marketplace.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySku(String sku);
    boolean existsBySku(String sku);

    // Búsqueda por nombre exacto ignorando mayúsculas (para detectar el mismo producto
    // aunque el parser haya extraído el texto con variaciones menores entre facturas)
    Optional<Product> findByNameIgnoreCase(String name);
}
