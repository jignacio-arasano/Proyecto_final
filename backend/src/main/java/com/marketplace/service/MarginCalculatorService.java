package com.marketplace.service;

import com.marketplace.model.IibbRate;
import com.marketplace.model.InstallmentRate;
import com.marketplace.model.Operation;
import com.marketplace.model.PlatformCommission;
import com.marketplace.model.Product;
import com.marketplace.repository.IibbRateRepository;
import com.marketplace.repository.InstallmentRateRepository;
import com.marketplace.repository.PlatformCommissionRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
public class MarginCalculatorService {
    private final PlatformCommissionRepository commissionRepo;
    private final InstallmentRateRepository installmentRepo;
    private final IibbRateRepository iibbRepo;

    public MarginCalculatorService(PlatformCommissionRepository commissionRepo,
                                   InstallmentRateRepository installmentRepo,
                                   IibbRateRepository iibbRepo) {
        this.commissionRepo = commissionRepo;
        this.installmentRepo = installmentRepo;
        this.iibbRepo = iibbRepo;
    }

    public void calculate(Operation op, Product product) {
        // Envío que pagó el comprador. Es parte del total facturado y sirve de base
        // para el IIBB. Lo guardo aparte (buyerShippingCost) así si recalculo el
        // margen no se pisa el dato. Para operaciones viejas que no lo tienen, uso
        // el shippingCost como respaldo.
        BigDecimal buyerPaidShipping;
        if (op.getBuyerShippingCost() != null) {
            buyerPaidShipping = op.getBuyerShippingCost();
        } else if (op.getShippingCost() != null) {
            buyerPaidShipping = op.getShippingCost();
        } else {
            buyerPaidShipping = BigDecimal.ZERO;
        }

        BigDecimal grossRevenue = op.getUnitPrice()
                .multiply(BigDecimal.valueOf(op.getQuantity()));
        op.setGrossRevenue(grossRevenue);

        // --- Cargo por vender (comisión base de la categoría + recargo por cuotas) ---
        String category = product.getCategory() != null ? product.getCategory() : "Otras categorías";
        String installmentOption = product.getSaleModality() != null ? product.getSaleModality() : "Sin cuotas";

        // Comisión base de la categoría. Si no la encuentro, uso 13% por defecto.
        BigDecimal baseRate = new BigDecimal("0.1300");
        Optional<PlatformCommission> comision = commissionRepo.findByCategoryAndModality(category, "Base");
        if (comision.isPresent()) {
            baseRate = comision.get().getRate();
        }

        // Recargo por vender en cuotas. Si no hay, queda en cero.
        BigDecimal surchargeRate = BigDecimal.ZERO;
        Optional<InstallmentRate> recargo = installmentRepo.findByOptionName(installmentOption);
        if (recargo.isPresent()) {
            surchargeRate = recargo.get().getSurchargeRate();
        }

        BigDecimal commissionRate = baseRate.add(surchargeRate);
        op.setCommissionRate(commissionRate);
        BigDecimal commissionAmount = grossRevenue.multiply(commissionRate)
                .setScale(2, RoundingMode.HALF_UP);
        op.setCommissionAmount(commissionAmount);

        // --- IIBB según la provincia del comprador ---
        // La base es producto + envío que pagó el comprador (el total facturado).
        BigDecimal iibbRate = resolveIibbRate(op.getBuyerProvince());
        op.setIibbRate(iibbRate);
        BigDecimal iibbAmount = grossRevenue.add(buyerPaidShipping).multiply(iibbRate)
                .setScale(2, RoundingMode.HALF_UP);
        op.setIibbAmount(iibbAmount);

        // --- Envío y costo fijo según el precio del producto ---
        // Productos < $33.000: el envío lo paga el comprador (no es costo del vendedor),
        //   pero el vendedor se come un costo fijo por unidad (los tramos de ML).
        // Productos >= $33.000: ML le cobra al vendedor $6.080 fijos de envío.
        //   En ese caso no hay costo fijo por unidad.
        BigDecimal shipping;
        BigDecimal unitCostAmount;
        if (op.getUnitPrice().compareTo(new BigDecimal("33000")) < 0) {
            shipping = BigDecimal.ZERO;  // el comprador paga el envío
            unitCostAmount = resolveUnitCost(op.getUnitPrice(), op.getQuantity());
        } else {
            shipping = new BigDecimal("6080"); // costo fijo de envío del vendedor
            unitCostAmount = BigDecimal.ZERO;
        }
        op.setShippingCost(shipping);
        op.setUnitCostAmount(unitCostAmount);

        // --- Costo de compra del producto ---
        // Es lo que el vendedor pagó por la mercadería. Sin esto el "margen" no
        // refleja la ganancia real, solo lo que descuenta ML en comisiones e impuestos.
        BigDecimal costPrice = product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO;
        BigDecimal productCostAmount = costPrice
                .multiply(BigDecimal.valueOf(op.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
        op.setProductCostAmount(productCostAmount);

        // margen neto = ingreso bruto - envío - comisión - iibb - costo fijo ML - costo de compra
        BigDecimal netMargin = grossRevenue
                .subtract(shipping)
                .subtract(commissionAmount)
                .subtract(iibbAmount)
                .subtract(unitCostAmount)
                .subtract(productCostAmount)
                .setScale(2, RoundingMode.HALF_UP);
        op.setNetMargin(netMargin);
    }

    private BigDecimal resolveIibbRate(String buyerProvince) {
        // Si no tengo provincia (el PDF no la pudo leer), uso Buenos Aires como fallback.
        // Así respeto lo que el usuario configuró en la tabla en vez de hardcodear 3,50%.
        String provincia = (buyerProvince == null || buyerProvince.isBlank())
                ? "Buenos Aires"
                : buyerProvince;

        // Primero busco la provincia tal cual viene.
        Optional<IibbRate> exacta = iibbRepo.findByProvince(provincia);
        if (exacta.isPresent()) {
            return exacta.get().getRate();
        }

        // Si no apareció, pruebo sin distinguir mayúsculas/minúsculas.
        Optional<IibbRate> sinMayus = iibbRepo.findByProvinceIgnoreCase(provincia);
        if (sinMayus.isPresent()) {
            return sinMayus.get().getRate();
        }

        // Último recurso: si la provincia del PDF no matcheó con ninguna de la tabla,
        // busco Buenos Aires directamente para respetar lo que el usuario configuró.
        Optional<IibbRate> bsAs = iibbRepo.findByProvinceIgnoreCase("Buenos Aires");
        if (bsAs.isPresent()) {
            return bsAs.get().getRate();
        }

        // Solo llegamos acá si la tabla de IIBB está completamente vacía.
        return new BigDecimal("0.0350");
    }

    /**
     * Costo fijo por unidad vendida (Flex / acuerdo con comprador / retiro).
     * Solo aplica para productos con precio unitario menor a $33.000.
     * Fuente: Mercado Libre Argentina - Costos de vender (2026)
     */
    private BigDecimal resolveUnitCost(BigDecimal unitPrice, int quantity) {
        if (unitPrice == null) return BigDecimal.ZERO;

        BigDecimal costPerUnit;
        if (unitPrice.compareTo(new BigDecimal("16000")) < 0) {
            costPerUnit = new BigDecimal("1255");
        } else if (unitPrice.compareTo(new BigDecimal("24000")) < 0) {
            costPerUnit = new BigDecimal("2500");
        } else if (unitPrice.compareTo(new BigDecimal("33000")) < 0) {
            costPerUnit = new BigDecimal("3030");
        } else {
            return BigDecimal.ZERO; // sin costo fijo para productos >= $33.000
        }
        return costPerUnit.multiply(BigDecimal.valueOf(quantity));
    }
}
