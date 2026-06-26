package com.marketplace.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class PredictionDto {
    private String productSku;
    private String productName;
    private Integer currentStock;
    private Integer predictedDemand;
    private Integer restockThreshold;
    private boolean alert;
    private String message;
    private Integer monthsOfData;  // cuántos meses de historial se usaron
    // Desglose por período de carga: [{period: "1", quantity: 17}, …]
    // Permite a quien revisa ver de dónde sale la demanda estimada.
    private List<Map<String, Object>> salesHistory;
}
