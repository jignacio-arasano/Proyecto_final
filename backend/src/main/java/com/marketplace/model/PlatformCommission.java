package com.marketplace.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Table(name = "platform_commissions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"category", "modality"})
})
@Data
public class PlatformCommission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String modality;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal rate;
}
