package com.marketplace.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductKpiDto {
    private String sku;
    private String name;
    private int totalQuantity;
    private BigDecimal totalRevenue;
    private BigDecimal totalNetMargin;
    private BigDecimal marginPercent;
    private boolean hasNegativeMargin;

    // Desglose de costos acumulados
    private BigDecimal totalShipping;       // envío total
    private BigDecimal avgShipping;         // envío promedio por venta
    private BigDecimal totalCommission;     // comisiones ML total
    private BigDecimal avgCommissionRate;   // tasa de comisión promedio (porcentaje)
    private BigDecimal totalIibb;           // IIBB total
    private BigDecimal avgIibbRate;         // tasa IIBB promedio (porcentaje)
    private BigDecimal totalUnitCost;       // costo fijo ML total
    private BigDecimal totalProductCost;      // costo de compra total (adquisición)
    private BigDecimal unitCostPrice;         // costo de compra unitario configurado

    // Margen teórico = ingreso bruto − costos ML (comisión + IIBB + envío + costo fijo)
    // Es lo que ML deposita al vendedor, sin descontar lo que pagó por la mercadería.
    // Margen real = margen teórico − costo de compra (rentabilidad verdadera).
    private BigDecimal totalTheoreticalMargin;
    private BigDecimal theoreticalMarginPercent;
}
