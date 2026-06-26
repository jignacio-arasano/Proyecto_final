package com.marketplace.dto;

import com.marketplace.model.Product;
import lombok.Data;
import java.util.List;

/**
 * Payload del endpoint atómico POST /uploads/{uploadId}/apply-config.
 * Reúne en una sola petición todo lo que el modal de configuración necesita
 * persistir: los productos nuevos a crear y las fusiones de SKUs (alias) hacia
 * un producto ya existente o recién creado. El backend aplica todo dentro de
 * una única transacción, eliminando la ventana en la que el dashboard veía
 * datos parciales.
 */
@Data
public class ApplyConfigRequest {
    /** Productos primarios a crear (las tarjetas que NO se fusionan). */
    private List<Product> products;
    /** Fusiones: cada SKU origen se vincula al SKU destino. */
    private List<MergeConfig> merges;

    @Data
    public static class MergeConfig {
        private String fromSku;
        private String toSku;
    }
}
