package com.marketplace.service;

import com.marketplace.dto.OperationDto;
import com.marketplace.dto.ProcessResult;
import com.marketplace.model.Operation;
import com.marketplace.model.Product;
import com.marketplace.model.Upload;
import com.marketplace.repository.OperationRepository;
import com.marketplace.repository.ProductRepository;
import com.marketplace.repository.UploadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

@Service
public class UploadService {
    private final UploadRepository uploadRepo;
    private final OperationRepository operationRepo;
    private final ProductRepository productRepo;
    private final PdfParserService pdfParser;
    private final MarginCalculatorService marginCalc;
    private final PredictorService predictorService;

    public UploadService(UploadRepository uploadRepo, OperationRepository operationRepo,
                         ProductRepository productRepo, PdfParserService pdfParser,
                         MarginCalculatorService marginCalc, PredictorService predictorService) {
        this.uploadRepo = uploadRepo;
        this.operationRepo = operationRepo;
        this.productRepo = productRepo;
        this.pdfParser = pdfParser;
        this.marginCalc = marginCalc;
        this.predictorService = predictorService;
    }

    @Transactional
    public ProcessResult process(MultipartFile file) throws Exception {
        // Creo el registro del upload (el ZIP) en estado "procesando".
        Upload upload = new Upload();
        upload.setFilename(file.getOriginalFilename());
        upload.setStatus("processing");
        upload = uploadRepo.save(upload);

        ProcessResult result = new ProcessResult();
        result.setUploadId(upload.getId());

        List<String> errors = new ArrayList<>();
        List<OperationDto> operations = new ArrayList<>();

        // Productos nuevos (que no están en la BD) para pedir su configuración.
        // sku -> nombre. Uso LinkedHashMap para respetar el orden en que aparecen.
        Map<String, String> unknownSkuMap = new LinkedHashMap<>();
        // Para no repetir el mismo producto cuando viene escrito un poco distinto:
        // nombre normalizado -> sku que ya guardé en unknownSkuMap.
        Map<String, String> normToCanonicalSku = new LinkedHashMap<>();

        int total = 0, valid = 0, invalid = 0;
        LocalDate minDate = null, maxDate = null;

        byte[] zipBytes = file.getBytes();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            try {
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;

                    String name = entry.getName().toLowerCase();
                    if (!name.endsWith(".pdf")) continue; // solo me interesan los PDF

                    total++;
                    byte[] pdfBytes = zis.readAllBytes();
                    try {
                        // Leo la factura y extraigo los datos.
                        OperationDto parsed = pdfParser.parse(new ByteArrayInputStream(pdfBytes), entry.getName());

                        String sku = parsed.getProductSku();
                        if (sku == null || sku.isBlank()) {
                            invalid++;
                            errors.add(entry.getName() + ": no se pudo identificar el producto");
                            continue;
                        }

                        // Busco si el producto ya existe en la base.
                        Product product = buscarProducto(sku, parsed.getProductName());

                        // Si no existe, lo anoto para pedir su configuración después.
                        // Lo dedupe por nombre normalizado, así no aparece dos veces.
                        if (product == null) {
                            String norm = normalizeName(parsed.getProductName());
                            if (!normToCanonicalSku.containsKey(norm)) {
                                unknownSkuMap.put(sku, parsed.getProductName());
                                normToCanonicalSku.put(norm, sku);
                            }
                        }

                        Operation op = toEntity(parsed, upload, product);

                        if (product != null) {
                            // Producto conocido: calculo el margen, guardo y descuento stock.
                            marginCalc.calculate(op, product);
                            operationRepo.save(op);
                            deductStock(product, op.getQuantity());
                            operations.add(toDto(op));
                            valid++;

                            // Voy guardando el rango de fechas de las facturas.
                            if (op.getInvoiceDate() != null) {
                                if (minDate == null || op.getInvoiceDate().isBefore(minDate)) minDate = op.getInvoiceDate();
                                if (maxDate == null || op.getInvoiceDate().isAfter(maxDate)) maxDate = op.getInvoiceDate();
                            }
                        } else {
                            // Producto todavía sin configurar: guardo la operación a medias,
                            // con el sku detectado, para vincularla luego del modal de config.
                            op.setPendingSku(sku);
                            operationRepo.save(op);
                            operations.add(parsed);
                            invalid++;
                        }
                    } catch (Exception e) {
                        // Algún PDF que no se pudo leer (corrupto, formato raro, etc).
                        invalid++;
                        errors.add(entry.getName() + ": " + e.getMessage());
                    }
                    zis.closeEntry();
                }
            } catch (ZipException e) {
                // El archivo no es un ZIP válido o está corrupto. Lo marco como vacío
                // para que el front muestre un mensaje claro en lugar de un error genérico.
                upload.setStatus("invalid");
                upload.setTotalDocs(0);
                upload.setValidDocs(0);
                upload.setInvalidDocs(0);
                uploadRepo.save(upload);
                result.setStatus("invalid");
                result.setTotalDocs(0);
                result.setValidDocs(0);
                result.setInvalidDocs(0);
                result.setErrors(errors);
                result.setOperations(operations);
                result.setUnknownSkus(new ArrayList<>());
                return result;
            }
        }

        upload.setTotalDocs(total);
        upload.setValidDocs(valid);
        upload.setInvalidDocs(invalid);
        upload.setPeriodStart(minDate);
        upload.setPeriodEnd(maxDate);

        // Paso el mapa de productos nuevos a la lista "sku|nombre" que espera el front.
        List<String> unknownSkus = new ArrayList<>();
        for (Map.Entry<String, String> e : unknownSkuMap.entrySet()) {
            unknownSkus.add(e.getKey() + "|" + e.getValue());
        }

        // El ZIP no tenía ningún PDF (estaba vacío o solo traía otros tipos de archivo).
        if (total == 0) {
            upload.setStatus("empty");
            uploadRepo.save(upload);
            result.setStatus("empty");
            result.setTotalDocs(0);
            result.setValidDocs(0);
            result.setInvalidDocs(0);
            result.setUnknownSkus(unknownSkus);
            result.setErrors(errors);
            result.setOperations(operations);
            return result;
        }

        // Tenía PDFs pero ninguno tenía formato de factura de ML (0 válidos, 0 productos nuevos).
        // Pasa cuando el usuario sube PDFs que no son facturas (exámenes, documentos, etc).
        if (valid == 0 && unknownSkus.isEmpty()) {
            upload.setStatus("no_invoices");
            uploadRepo.save(upload);
            result.setStatus("no_invoices");
            result.setTotalDocs(total);
            result.setValidDocs(0);
            result.setInvalidDocs(invalid);
            result.setUnknownSkus(unknownSkus);
            result.setErrors(errors);
            result.setOperations(operations);
            return result;
        }

        // Si hay productos nuevos, el front tiene que mostrar el modal de configuración.
        String status = unknownSkus.isEmpty() ? "completed" : "needs_config";
        upload.setStatus(status);
        uploadRepo.save(upload);

        result.setStatus(status);
        result.setTotalDocs(total);
        result.setValidDocs(valid);
        result.setInvalidDocs(invalid);
        result.setUnknownSkus(unknownSkus);
        result.setErrors(errors);
        result.setOperations(operations);

        // Si no quedó nada por configurar, ya puedo calcular las predicciones.
        if (unknownSkus.isEmpty()) {
            result.setPredictions(predictorService.predictAll());
        }

        return result;
    }

    /**
     * Busca el producto de una factura probando, en orden, cuatro formas:
     *   1. Por SKU exacto.
     *   2. Por nombre exacto (sin importar mayúsculas/acentos).
     *   3. Por prefijo del nombre (el PDF a veces agrega medidas o variantes).
     *   4. Por un vínculo manual que hice en una carga anterior con ese SKU.
     * Devuelve null si no lo encuentra por ninguna.
     */
    private Product buscarProducto(String sku, String nombre) {
        // 1. Por SKU exacto.
        Optional<Product> porSku = productRepo.findBySku(sku);
        if (porSku.isPresent()) {
            return porSku.get();
        }

        // 2. Por nombre exacto (case-insensitive).
        if (nombre != null && !nombre.isBlank()) {
            Optional<Product> porNombre = productRepo.findByNameIgnoreCase(nombre.trim());
            if (porNombre.isPresent()) {
                return porNombre.get();
            }
        }

        // 3. Por prefijo: si el nombre guardado es prefijo del nuevo (o al revés) y
        //    la parte en común tiene al menos 10 caracteres, lo tomo como el mismo.
        if (nombre != null && !nombre.isBlank()) {
            String normNuevo = normalizeName(nombre);
            for (Product p : productRepo.findAll()) {
                String normP = normalizeName(p.getName());
                int minLen = Math.min(normNuevo.length(), normP.length());
                if (minLen >= 10 && (normNuevo.startsWith(normP) || normP.startsWith(normNuevo))) {
                    return p;
                }
            }
        }

        // 4. ¿Ya vinculé este SKU a mano antes? Reuso ese producto.
        List<Operation> previas = operationRepo.findByPendingSkuAndProductIsNotNull(sku);
        if (!previas.isEmpty()) {
            return previas.get(0).getProduct();
        }

        return null;
    }

    /**
     * Aplica toda la configuración del modal de una sola vez (en una transacción):
     * crea los productos nuevos, aplica las fusiones de SKUs y reprocesa las
     * operaciones pendientes. Así el dashboard nunca ve datos a medias: o están
     * todos los cambios o ninguno.
     */
    @Transactional
    public ProcessResult applyConfig(Long uploadId, List<Product> products,
                                     List<com.marketplace.dto.ApplyConfigRequest.MergeConfig> merges) {
        // 1. Creo los productos nuevos (con las mismas validaciones que el alta normal).
        if (products != null) {
            for (Product p : products) {
                if (p.getSku() == null || p.getSku().isBlank()) {
                    throw new RuntimeException("Un producto no tiene SKU; no se puede guardar.");
                }
                if (p.getName() == null || p.getName().isBlank()) {
                    throw new RuntimeException("El producto " + p.getSku() + " no tiene nombre.");
                }
                productRepo.save(p);
            }
        }

        // 2. Aplico las fusiones (un sku que en realidad es otro producto ya existente).
        if (merges != null) {
            for (com.marketplace.dto.ApplyConfigRequest.MergeConfig m : merges) {
                if (m.getFromSku() == null || m.getFromSku().isBlank()
                        || m.getToSku() == null || m.getToSku().isBlank()) {
                    throw new RuntimeException("Una fusión no tiene fromSku/toSku válidos.");
                }
                linkSku(uploadId, m.getFromSku(), m.getToSku());
            }
        }

        // 3. Reproceso lo que quedó pendiente y marco la carga como completada.
        return reprocessAfterConfig(uploadId);
    }

    @Transactional
    public ProcessResult reprocessAfterConfig(Long uploadId) {
        List<Operation> ops = operationRepo.findByUploadId(uploadId);
        for (Operation op : ops) {
            // Caso 1: la operación ya tenía producto pero le faltaba calcular el margen.
            if (op.getProduct() != null && op.getNetMargin() == null) {
                marginCalc.calculate(op, op.getProduct());
                operationRepo.save(op);
            }
            // Caso 2: la operación quedó a medias (sin producto) porque el producto
            // no estaba configurado. Ahora intento encontrarlo por el sku pendiente.
            else if (op.getProduct() == null && op.getPendingSku() != null) {
                String pendingSku = op.getPendingSku();
                String normSku = normalizeName(pendingSku.replace("-", " "));

                Product product = null;

                // 1. Por SKU exacto.
                Optional<Product> porSku = productRepo.findBySku(pendingSku);
                if (porSku.isPresent()) {
                    product = porSku.get();
                } else {
                    // 2. Por prefijo del nombre.
                    for (Product p : productRepo.findAll()) {
                        String normP = normalizeName(p.getName());
                        int minLen = Math.min(normSku.length(), normP.length());
                        if (minLen >= 10 && (normSku.startsWith(normP) || normP.startsWith(normSku))) {
                            product = p;
                            break;
                        }
                    }
                    // 3. Por un vínculo manual previo con ese mismo SKU.
                    if (product == null) {
                        List<Operation> previas = operationRepo.findByPendingSkuAndProductIsNotNull(pendingSku);
                        if (!previas.isEmpty()) {
                            product = previas.get(0).getProduct();
                        }
                    }
                }

                if (product != null) {
                    op.setProduct(product);
                    op.setPendingSku(null);
                    marginCalc.calculate(op, product);
                    operationRepo.save(op);
                    deductStock(product, op.getQuantity());
                }
            }
        }

        // Actualizo el estado y los contadores de la carga. Como recién vinculé
        // operaciones que estaban pendientes, vuelvo a contar cuántas quedaron válidas
        // (si no, el historial seguiría mostrando el conteo viejo).
        int finalValid = 0;
        for (Operation op : ops) {
            if (op.getProduct() != null) {
                finalValid++;
            }
        }

        Upload u = uploadRepo.findById(uploadId).orElse(null);
        if (u != null) {
            u.setStatus("completed");
            u.setValidDocs(finalValid);
            u.setInvalidDocs(u.getTotalDocs() != null ? u.getTotalDocs() - finalValid : 0);
            uploadRepo.save(u);
        }

        ProcessResult result = new ProcessResult();
        result.setUploadId(uploadId);
        result.setStatus("completed");
        result.setPredictions(predictorService.predictAll());
        return result;
    }

    /**
     * Vincula todas las operaciones pendientes con {@code fromSku} al producto de
     * {@code toSku}. Sirve cuando dos publicaciones distintas de Mercado Libre son
     * en realidad el mismo artículo y el usuario quiere sumar sus ventas juntas.
     */
    @Transactional
    public void linkSku(Long uploadId, String fromSku, String toSku) {
        Product targetProduct = productRepo.findBySku(toSku)
                .orElseThrow(() -> new RuntimeException("Producto destino no encontrado: " + toSku));

        // Vinculo las operaciones de esta carga que tengan ese sku pendiente.
        List<Operation> ops = operationRepo.findByUploadId(uploadId);
        for (Operation op : ops) {
            if (fromSku.equals(op.getPendingSku())) {
                op.setProduct(targetProduct);
                // Dejo el pendingSku a propósito, como registro del alias: en cargas
                // futuras lo voy a usar para resolver este sku solo, sin preguntar.
                marginCalc.calculate(op, targetProduct);
                operationRepo.save(op);
                deductStock(targetProduct, op.getQuantity());
            }
        }

        // Si el mismo sku quedó huérfano en cargas anteriores, lo vinculo también
        // para que esas ventas no queden sin contar.
        List<Operation> orphans = operationRepo.findByPendingSkuAndProductIsNull(fromSku);
        for (Operation orphan : orphans) {
            // Solo las de OTRAS cargas (las de esta ya las procesé arriba).
            if (!uploadId.equals(orphan.getUpload().getId())) {
                orphan.setProduct(targetProduct);
                marginCalc.calculate(orphan, targetProduct);
                operationRepo.save(orphan);
                deductStock(targetProduct, orphan.getQuantity());
            }
        }
    }

    /**
     * Borra una carga (ZIP) con todas sus operaciones. Antes de borrar, le devuelve
     * al stock las unidades que cada operación había descontado, para que el
     * inventario quede consistente.
     */
    @Transactional
    public void deleteUpload(Long uploadId) {
        List<Operation> ops = operationRepo.findByUploadId(uploadId);
        for (Operation op : ops) {
            // Solo descontaron stock las operaciones que tenían producto.
            if (op.getProduct() != null) {
                restoreStock(op.getProduct(), op.getQuantity());
            }
        }
        operationRepo.deleteByUploadId(uploadId);
        uploadRepo.deleteById(uploadId);
    }

    // Le devuelve al stock las unidades de una operación que se borra.
    private void restoreStock(Product product, Integer quantity) {
        if (product == null || quantity == null || quantity <= 0) return;
        int current = product.getCurrentStock() != null ? product.getCurrentStock() : 0;
        product.setCurrentStock(current + quantity);
        productRepo.save(product);
    }

    // Convierte los datos parseados del PDF en una entidad Operation.
    private Operation toEntity(OperationDto dto, Upload upload, Product product) {
        Operation op = new Operation();
        op.setUpload(upload);
        op.setProduct(product);
        op.setInvoiceNumber(dto.getInvoiceNumber());
        op.setInvoiceDate(dto.getInvoiceDate());
        op.setBuyerName(dto.getBuyerName());
        op.setBuyerDoc(dto.getBuyerDoc());
        op.setBuyerProvince(dto.getBuyerProvince());
        op.setQuantity(dto.getQuantity() != null ? dto.getQuantity() : 1);
        op.setUnitPrice(dto.getUnitPrice());
        // El envío parseado es lo que paga el comprador. Lo guardo en buyerShippingCost
        // (que no se vuelve a tocar) y también en shippingCost; el cálculo de margen
        // después pisa shippingCost con el costo real del vendedor.
        op.setBuyerShippingCost(dto.getShippingCost());
        op.setShippingCost(dto.getShippingCost());
        op.setPaymentMethod(dto.getPaymentMethod());
        return op;
    }

    // Convierte una entidad Operation en su DTO para devolverla al front.
    private OperationDto toDto(Operation op) {
        OperationDto dto = new OperationDto();
        dto.setId(op.getId());
        dto.setInvoiceNumber(op.getInvoiceNumber());
        dto.setInvoiceDate(op.getInvoiceDate());
        dto.setBuyerName(op.getBuyerName());
        dto.setBuyerProvince(op.getBuyerProvince());
        dto.setQuantity(op.getQuantity());
        dto.setUnitPrice(op.getUnitPrice());
        dto.setShippingCost(op.getShippingCost());
        dto.setCommissionRate(op.getCommissionRate());
        dto.setCommissionAmount(op.getCommissionAmount());
        dto.setIibbRate(op.getIibbRate());
        dto.setIibbAmount(op.getIibbAmount());
        dto.setGrossRevenue(op.getGrossRevenue());
        dto.setNetMargin(op.getNetMargin());
        dto.setPaymentMethod(op.getPaymentMethod());
        if (op.getProduct() != null) {
            dto.setProductSku(op.getProduct().getSku());
            dto.setProductName(op.getProduct().getName());
        }
        return dto;
    }

    // Descuenta del stock del producto las unidades vendidas. El stock nunca baja de 0.
    private void deductStock(Product product, Integer quantity) {
        if (product == null || quantity == null || quantity <= 0) return;
        int current = product.getCurrentStock() != null ? product.getCurrentStock() : 0;
        product.setCurrentStock(Math.max(0, current - quantity));
        productRepo.save(product);
    }

    /**
     * Normaliza un nombre para comparar productos: lo pasa a minúsculas, le saca
     * los acentos y los símbolos, y deja un solo espacio entre palabras. Así
     * "Mandíbula" y "Mandibula" (o con/sin coma) cuentan como el mismo producto.
     */
    private String normalizeName(String name) {
        if (name == null) return "";
        return java.text.Normalizer
                .normalize(name.trim().toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .strip();
    }
}
