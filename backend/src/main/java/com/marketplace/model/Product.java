package com.marketplace.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Data
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String sku;

    @Column(nullable = false)
    private String name;

    private String category;

    @Column(name = "sale_modality")
    private String saleModality;

    @Column(name = "restock_threshold")
    private Integer restockThreshold = 5;

    @Column(name = "current_stock")
    private Integer currentStock = 0;

    // Costo de compra/adquisición por unidad. Se descuenta del margen neto
    // para reflejar la rentabilidad real (no solo comisiones e impuestos).
    @Column(name = "cost_price", precision = 12, scale = 2)
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
