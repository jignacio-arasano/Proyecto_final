package com.marketplace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.dto.PredictionDto;
import com.marketplace.model.Product;
import com.marketplace.model.Upload;
import com.marketplace.repository.OperationRepository;
import com.marketplace.repository.ProductRepository;
import com.marketplace.repository.UploadRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@Service
public class PredictorService {
    private final OperationRepository operationRepo;
    private final ProductRepository productRepo;
    private final UploadRepository uploadRepo;
    private final ObjectMapper objectMapper;

    // La URL del servicio de predicción (Python) la leo del application.properties.
    @Value("${app.predictor.url}")
    private String predictorUrl;

    public PredictorService(OperationRepository operationRepo, ProductRepository productRepo,
                            UploadRepository uploadRepo) {
        this.operationRepo = operationRepo;
        this.productRepo = productRepo;
        this.uploadRepo = uploadRepo;
        this.objectMapper = new ObjectMapper();
    }

    public List<PredictionDto> predictAll() {
        List<Product> products = productRepo.findAll();
        List<PredictionDto> results = new ArrayList<>();

        // Los meses van por orden de carga: el primer ZIP que subo es el mes 1,
        // el segundo es el mes 2, y así. Si borro el primero, el segundo queda como
        // mes 1. Por eso traigo los uploads ordenados por la fecha en que se cargaron.
        List<Upload> uploads = uploadRepo.findAllByOrderByUploadDateAsc();

        // Si todavía no subí ningún ZIP, no hay nada para predecir.
        if (uploads.isEmpty()) {
            for (Product product : products) {
                results.add(emptyPrediction(product));
            }
            return results;
        }

        // A cada upload le pongo su número de mes (el primero es 1, el segundo 2, etc).
        Map<Long, Integer> mesDeCadaUpload = new HashMap<>();
        for (int i = 0; i < uploads.size(); i++) {
            mesDeCadaUpload.put(uploads.get(i).getId(), i + 1);
        }

        // Para cada producto guardo cuántas unidades vendió en cada mes.
        // Es un mapa de mapas: sku -> (mes -> cantidad vendida).
        Map<String, Map<Integer, Long>> ventasPorProducto = new HashMap<>();
        for (Object[] fila : operationRepo.sumQuantityBySkuAndUpload()) {
            String sku = (String) fila[0];
            Long uploadId = (Long) fila[1];
            long cantidad = ((Number) fila[2]).longValue();

            Integer mes = mesDeCadaUpload.get(uploadId);
            if (mes == null) continue; // este upload ya se borró, lo salteo

            // Si es la primera venta de este producto, le creo su mapita.
            if (!ventasPorProducto.containsKey(sku)) {
                ventasPorProducto.put(sku, new HashMap<>());
            }
            Map<Integer, Long> ventasPorMes = ventasPorProducto.get(sku);

            // Sumo, por si ya había ventas anotadas en ese mismo mes.
            long acumulado = 0;
            if (ventasPorMes.containsKey(mes)) {
                acumulado = ventasPorMes.get(mes);
            }
            ventasPorMes.put(mes, acumulado + cantidad);
        }

        // Ahora armo, para cada producto, la lista [{period, quantity}, ...]
        // ordenada del mes más viejo al más nuevo.
        Map<String, List<Map<String, Object>>> historialPorProducto = new HashMap<>();
        for (String sku : ventasPorProducto.keySet()) {
            Map<Integer, Long> ventasPorMes = ventasPorProducto.get(sku);

            // Ordeno los números de mes de menor a mayor.
            List<Integer> meses = new ArrayList<>(ventasPorMes.keySet());
            Collections.sort(meses);

            List<Map<String, Object>> historial = new ArrayList<>();
            for (Integer mes : meses) {
                Map<String, Object> punto = new HashMap<>();
                punto.put("period", String.valueOf(mes));
                punto.put("quantity", ventasPorMes.get(mes));
                historial.add(punto);
            }
            historialPorProducto.put(sku, historial);
        }

        // Por último le pido la predicción al servicio de Python para cada producto.
        for (Product product : products) {
            List<Map<String, Object>> historial = historialPorProducto.get(product.getSku());
            if (historial == null) {
                historial = new ArrayList<>(); // este producto todavía no vendió nada
            }
            results.add(callPredictor(product, historial));
        }
        return results;
    }

    // Predicción vacía, para los productos que todavía no tienen ventas.
    private PredictionDto emptyPrediction(Product product) {
        PredictionDto dto = new PredictionDto();
        dto.setProductSku(product.getSku());
        dto.setProductName(product.getName());
        dto.setCurrentStock(product.getCurrentStock());
        dto.setRestockThreshold(product.getRestockThreshold());
        dto.setPredictedDemand(null);
        dto.setAlert(false);
        dto.setMonthsOfData(0);
        dto.setMessage("Sin ventas registradas");
        dto.setSalesHistory(new ArrayList<>());
        return dto;
    }

    private PredictionDto callPredictor(Product product, List<Map<String, Object>> history) {
        PredictionDto dto = new PredictionDto();
        dto.setProductSku(product.getSku());
        dto.setProductName(product.getName());
        dto.setCurrentStock(product.getCurrentStock());
        dto.setRestockThreshold(product.getRestockThreshold());
        dto.setSalesHistory(history);

        // Si el producto no tiene ventas, no hay nada que predecir.
        if (history.isEmpty()) {
            dto.setPredictedDemand(null);
            dto.setAlert(false);
            dto.setMonthsOfData(0);
            dto.setMessage("Sin ventas registradas");
            return dto;
        }

        try {
            // Armo los datos que le voy a mandar al servicio de Python.
            int stock = product.getCurrentStock() != null ? product.getCurrentStock() : 0;
            int umbral = product.getRestockThreshold() != null ? product.getRestockThreshold() : 5;

            Map<String, Object> payload = new HashMap<>();
            payload.put("product_sku", product.getSku());
            payload.put("sales_history", history);
            payload.put("current_stock", stock);
            payload.put("restock_threshold", umbral);

            String body = objectMapper.writeValueAsString(payload);

            // Hago el POST al servicio de predicción.
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(predictorUrl + "/predict"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Map<?, ?> result = objectMapper.readValue(response.body(), Map.class);

            dto.setMonthsOfData(history.size());

            // Si Python no devolvió una predicción, muestro el aviso y salgo.
            Object predRaw = result.get("predicted_demand");
            if (predRaw == null) {
                dto.setPredictedDemand(null);
                dto.setAlert(false);
                dto.setMessage("Sin datos suficientes para predecir");
                return dto;
            }

            int predicted = ((Number) predRaw).intValue();
            boolean alert = (boolean) result.get("alert");

            // Python me puede decir cuántos meses usó; si vino el dato, lo guardo.
            Object monthsRaw = result.get("months_of_data");
            if (monthsRaw != null) {
                dto.setMonthsOfData(((Number) monthsRaw).intValue());
            }

            dto.setPredictedDemand(predicted);
            dto.setAlert(alert);

            // Armo el mensaje según el caso.
            if (alert) {
                dto.setMessage("Stock bajo. Se recomienda reponer al menos " + predicted + " unidades.");
            } else if (history.size() == 1) {
                dto.setMessage("Predicción basada en 1 mes (tendencia plana)");
            } else {
                dto.setMessage("Stock suficiente para el próximo período.");
            }
        } catch (Exception e) {
            // Si el servicio de Python no está levantado o falla, no quiero que se
            // caiga el dashboard: dejo la predicción vacía con un mensaje.
            dto.setPredictedDemand(null);
            dto.setAlert(false);
            dto.setMonthsOfData(history.size());
            dto.setMessage("Servicio de predicción no disponible (" + e.getClass().getSimpleName() + ")");
        }
        return dto;
    }
}
