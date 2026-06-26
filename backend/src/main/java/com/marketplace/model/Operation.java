package com.marketplace.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "operations")
@Data
public class Operation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "upload_id")
    private Upload upload;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "buyer_name")
    private String buyerName;

    @Column(name = "buyer_doc")
    private String buyerDoc;

    @Column(name = "buyer_province")
    private String buyerProvince;

    private Integer quantity;

    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    // Costo de envío que paga el COMPRADOR (parseado del PDF). Es base imponible de
    // IIBB. Se fija una sola vez al parsear y nunca se sobreescribe, para que el
    // recálculo de márgenes (al editar un producto) sea idempotente.
    @Column(name = "buyer_shipping_cost", precision = 12, scale = 2)
    private BigDecimal buyerShippingCost;

    // Costo de envío que absorbe el VENDEDOR (0 si el comprador paga, $6.080 si el
    // precio >= $33.000). Lo escribe el cálculo de margen y lo usa el desglose del dashboard.
    @Column(name = "shipping_cost", precision = 12, scale = 2)
    private BigDecimal shippingCost;

    @Column(name = "commission_rate", precision = 5, scale = 4)
    private BigDecimal commissionRate;

    @Column(name = "commission_amount", precision = 12, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "iibb_rate", precision = 5, scale = 4)
    private BigDecimal iibbRate;

    @Column(name = "iibb_amount", precision = 12, scale = 2)
    private BigDecimal iibbAmount;

    @Column(name = "gross_revenue", precision = 12, scale = 2)
    private BigDecimal grossRevenue;

    // Costo fijo por unidad vendida (aplica solo si precio < $33.000)
    @Column(name = "unit_cost_amount", precision = 12, scale = 2)
    private BigDecimal unitCostAmount;

    // Costo de compra/adquisición acumulado de esta operación (costo unitario del
    // producto × cantidad). Se descuenta del margen para reflejar rentabilidad real.
    @Column(name = "product_cost_amount", precision = 12, scale = 2)
    private BigDecimal productCostAmount;

    @Column(name = "net_margin", precision = 12, scale = 2)
    private BigDecimal netMargin;

    @Column(name = "payment_method")
    private String paymentMethod;

    // SKU detectado durante el parsing cuando el producto aún no estaba configurado.
    // Permite re-vincular la operación al producto correcto en el reprocessing.
    @Column(name = "pending_sku")
    private String pendingSku;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
