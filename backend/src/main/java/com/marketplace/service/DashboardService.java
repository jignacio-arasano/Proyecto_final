package com.marketplace.service;

import com.marketplace.dto.DashboardDto;
import com.marketplace.dto.PredictionDto;
import com.marketplace.dto.ProductKpiDto;
import com.marketplace.model.Operation;
import com.marketplace.model.Upload;
import com.marketplace.repository.OperationRepository;
import com.marketplace.repository.UploadRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class DashboardService {
    private final OperationRepository operationRepo;
    private final PredictorService predictorService;
    private final UploadRepository uploadRepo;

    public DashboardService(OperationRepository operationRepo, PredictorService predictorService,
                            UploadRepository uploadRepo) {
        this.operationRepo = operationRepo;
        this.predictorService = predictorService;
        this.uploadRepo = uploadRepo;
    }

    public DashboardDto buildDashboard() {
        List<Operation> ops = operationRepo.findAllWithProduct();

        DashboardDto dto = new DashboardDto();
        dto.setTotalOperations(ops.size());

        // --- Totales generales ---
        // Recorro todas las operaciones una sola vez y voy sumando.
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalNetMargin = BigDecimal.ZERO;
        BigDecimal totalTheoreticalMargin = BigDecimal.ZERO;
        int negativeMarginCount = 0;

        for (Operation op : ops) {
            if (op.getGrossRevenue() != null) {
                totalRevenue = totalRevenue.add(op.getGrossRevenue());
            }
            if (op.getNetMargin() != null) {
                totalNetMargin = totalNetMargin.add(op.getNetMargin());

                // El margen teórico es lo que deposita ML: el margen real + el costo
                // de compra (porque ese costo no lo descuenta la plataforma).
                BigDecimal costoCompra = op.getProductCostAmount() != null
                        ? op.getProductCostAmount() : BigDecimal.ZERO;
                totalTheoreticalMargin = totalTheoreticalMargin.add(op.getNetMargin().add(costoCompra));

                if (op.getNetMargin().compareTo(BigDecimal.ZERO) < 0) {
                    negativeMarginCount++;
                }
            }
        }

        dto.setTotalRevenue(totalRevenue);
        dto.setTotalNetMargin(totalNetMargin);
        dto.setTotalTheoreticalMargin(totalTheoreticalMargin);
        dto.setNegativeMarginCount(negativeMarginCount);

        // Porcentajes de margen sobre el ingreso (solo si hubo ingresos, para no dividir por 0).
        if (totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
            dto.setAvgMarginPercent(totalNetMargin.divide(totalRevenue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
            dto.setAvgTheoreticalMarginPercent(totalTheoreticalMargin.divide(totalRevenue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
        } else {
            dto.setAvgMarginPercent(BigDecimal.ZERO);
            dto.setAvgTheoreticalMarginPercent(BigDecimal.ZERO);
        }

        // --- Indicadores por producto ---
        // Primero agrupo las operaciones por SKU del producto.
        Map<String, List<Operation>> byProduct = new HashMap<>();
        for (Operation op : ops) {
            if (op.getProduct() == null) continue;
            String sku = op.getProduct().getSku();
            if (!byProduct.containsKey(sku)) {
                byProduct.put(sku, new ArrayList<>());
            }
            byProduct.get(sku).add(op);
        }

        List<ProductKpiDto> kpis = new ArrayList<>();
        for (Map.Entry<String, List<Operation>> entry : byProduct.entrySet()) {
            List<Operation> productOps = entry.getValue();
            ProductKpiDto kpi = new ProductKpiDto();
            kpi.setSku(entry.getKey());
            kpi.setName(productOps.get(0).getProduct().getName());

            // Acumulo todos los totales de este producto en un solo recorrido.
            int qty = 0;
            BigDecimal rev = BigDecimal.ZERO;
            BigDecimal margin = BigDecimal.ZERO;
            boolean tieneMargenNegativo = false;
            BigDecimal totalShipping = BigDecimal.ZERO;
            BigDecimal totalCommission = BigDecimal.ZERO;
            BigDecimal sumCommissionRate = BigDecimal.ZERO;
            BigDecimal totalIibb = BigDecimal.ZERO;
            BigDecimal sumIibbRate = BigDecimal.ZERO;
            BigDecimal totalUnitCost = BigDecimal.ZERO;
            BigDecimal totalProductCost = BigDecimal.ZERO;

            for (Operation op : productOps) {
                if (op.getQuantity() != null) qty += op.getQuantity();
                if (op.getGrossRevenue() != null) rev = rev.add(op.getGrossRevenue());
                if (op.getNetMargin() != null) {
                    margin = margin.add(op.getNetMargin());
                    if (op.getNetMargin().compareTo(BigDecimal.ZERO) < 0) tieneMargenNegativo = true;
                }
                if (op.getShippingCost() != null) totalShipping = totalShipping.add(op.getShippingCost());
                if (op.getCommissionAmount() != null) totalCommission = totalCommission.add(op.getCommissionAmount());
                if (op.getCommissionRate() != null) sumCommissionRate = sumCommissionRate.add(op.getCommissionRate());
                if (op.getIibbAmount() != null) totalIibb = totalIibb.add(op.getIibbAmount());
                if (op.getIibbRate() != null) sumIibbRate = sumIibbRate.add(op.getIibbRate());
                if (op.getUnitCostAmount() != null) totalUnitCost = totalUnitCost.add(op.getUnitCostAmount());
                if (op.getProductCostAmount() != null) totalProductCost = totalProductCost.add(op.getProductCostAmount());
            }
            int opCount = productOps.size();

            kpi.setTotalQuantity(qty);
            kpi.setTotalRevenue(rev);
            kpi.setTotalNetMargin(margin);
            kpi.setHasNegativeMargin(tieneMargenNegativo);

            if (rev.compareTo(BigDecimal.ZERO) > 0) {
                kpi.setMarginPercent(margin.divide(rev, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
            }

            // Envío
            kpi.setTotalShipping(totalShipping);
            kpi.setAvgShipping(opCount > 0
                    ? totalShipping.divide(BigDecimal.valueOf(opCount), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);

            // Comisión de Mercado Libre
            kpi.setTotalCommission(totalCommission);
            kpi.setAvgCommissionRate(opCount > 0
                    ? sumCommissionRate.divide(BigDecimal.valueOf(opCount), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);

            // IIBB
            kpi.setTotalIibb(totalIibb);
            kpi.setAvgIibbRate(opCount > 0
                    ? sumIibbRate.divide(BigDecimal.valueOf(opCount), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);

            // Costo fijo por unidad y costo de compra
            kpi.setTotalUnitCost(totalUnitCost);
            kpi.setTotalProductCost(totalProductCost);
            kpi.setUnitCostPrice(productOps.get(0).getProduct().getCostPrice());

            // Margen teórico del producto = margen real + costo de compra
            BigDecimal theoreticalMargin = margin.add(totalProductCost);
            kpi.setTotalTheoreticalMargin(theoreticalMargin);
            if (rev.compareTo(BigDecimal.ZERO) > 0) {
                kpi.setTheoreticalMarginPercent(theoreticalMargin.divide(rev, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
            }

            kpis.add(kpi);
        }

        // Ordeno los productos de mayor a menor ingreso.
        kpis.sort((a, b) -> b.getTotalRevenue().compareTo(a.getTotalRevenue()));
        dto.setProductKpis(kpis);

        // --- Margen por período (eje = orden de carga, igual que la predicción) ---
        // El primer ZIP subido es "Mes 1", el segundo "Mes 2", etc. Así el gráfico y
        // la predicción usan la misma numeración de meses (y no la fecha de la factura).
        List<Upload> uploads = uploadRepo.findAllByOrderByUploadDateAsc();
        Map<Long, Integer> mesDeCadaUpload = new HashMap<>();
        for (int i = 0; i < uploads.size(); i++) {
            mesDeCadaUpload.put(uploads.get(i).getId(), i + 1);
        }

        // Sumo el margen neto de cada mes.
        Map<Integer, BigDecimal> margenPorMes = new TreeMap<>();
        for (Operation op : ops) {
            if (op.getUpload() == null || op.getNetMargin() == null) continue;
            Integer mes = mesDeCadaUpload.get(op.getUpload().getId());
            if (mes == null) continue; // upload ya borrado
            BigDecimal acumulado = margenPorMes.containsKey(mes)
                    ? margenPorMes.get(mes) : BigDecimal.ZERO;
            margenPorMes.put(mes, acumulado.add(op.getNetMargin()));
        }

        List<Map<String, Object>> marginByPeriod = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> e : margenPorMes.entrySet()) {
            Map<String, Object> punto = new HashMap<>();
            punto.put("period", "Mes " + e.getKey());
            punto.put("margin", e.getValue());
            marginByPeriod.add(punto);
        }
        dto.setMarginByPeriod(marginByPeriod);

        // --- Predicciones de demanda ---
        List<PredictionDto> allPredictions = predictorService.predictAll();
        dto.setPredictions(allPredictions);

        // De todas las predicciones me quedo solo con las que tienen alerta de stock.
        List<PredictionDto> stockAlerts = new ArrayList<>();
        for (PredictionDto p : allPredictions) {
            if (p.isAlert()) {
                stockAlerts.add(p);
            }
        }
        dto.setStockAlerts(stockAlerts);

        return dto;
    }
}
