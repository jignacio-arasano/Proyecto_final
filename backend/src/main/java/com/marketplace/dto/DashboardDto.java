package com.marketplace.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class DashboardDto {
    private BigDecimal totalRevenue;
    private BigDecimal totalTheoreticalMargin;   // sin descontar costo de compra
    private BigDecimal avgTheoreticalMarginPercent;
    private BigDecimal totalNetMargin;           // margen real (descuenta costo de compra)
    private BigDecimal avgMarginPercent;
    private int totalOperations;
    private int negativeMarginCount;
    private List<ProductKpiDto> productKpis;
    private List<PredictionDto> predictions;   // todas las predicciones
    private List<PredictionDto> stockAlerts;   // solo las que tienen alert=true
    private List<Map<String, Object>> marginByPeriod;
}
