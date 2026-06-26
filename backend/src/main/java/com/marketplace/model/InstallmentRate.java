package com.marketplace.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Table(name = "installment_rates")
@Data
public class InstallmentRate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "option_name", unique = true, nullable = false)
    private String optionName;

    // Recargo adicional sobre la comisión base por ofrecer esta opción de cuotas
    @Column(name = "surcharge_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal surchargeRate;
}
