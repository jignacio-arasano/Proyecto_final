package com.marketplace.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Table(name = "iibb_rates")
@Data
public class IibbRate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String province;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal rate;
}
