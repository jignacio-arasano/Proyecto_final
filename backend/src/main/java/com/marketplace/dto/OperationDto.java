package com.marketplace.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class OperationDto {
    private Long id;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private String productSku;
    private String productName;
    private String buyerName;
    private String buyerDoc;
    private String buyerProvince;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal shippingCost;
    private BigDecimal commissionRate;
    private BigDecimal commissionAmount;
    private BigDecimal iibbRate;
    private BigDecimal iibbAmount;
    private BigDecimal grossRevenue;
    private BigDecimal netMargin;
    private String paymentMethod;
}
