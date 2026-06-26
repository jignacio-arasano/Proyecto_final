package com.marketplace.controller;

import com.marketplace.dto.ApplyConfigRequest;
import com.marketplace.dto.ProcessResult;
import com.marketplace.repository.UploadRepository;
import com.marketplace.service.UploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {
    private final UploadService uploadService;
    private final UploadRepository uploadRepo;

    public UploadController(UploadService uploadService, UploadRepository uploadRepo) {
        this.uploadService = uploadService;
        this.uploadRepo = uploadRepo;
    }

    @PostMapping("/process")
    public ResponseEntity<?> processZip(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("El archivo está vacío");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".zip")) {
            return ResponseEntity.badRequest().body("Solo se aceptan archivos ZIP");
        }
        try {
            ProcessResult result = uploadService.process(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error procesando el archivo: " + e.getMessage());
        }
    }

    @PostMapping("/{uploadId}/reprocess")
    public ResponseEntity<?> reprocess(@PathVariable Long uploadId) {
        try {
            ProcessResult result = uploadService.reprocessAfterConfig(uploadId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    /**
     * Vincula todas las operaciones pendientes con {@code fromSku} al producto
     * {@code toSku} (que ya debe existir en la BD, creado por POST /products).
     * Permite unificar dos listings de ML que corresponden al mismo producto físico.
     */
    @PostMapping("/{uploadId}/link-sku")
    public ResponseEntity<?> linkSku(@PathVariable Long uploadId,
                                     @RequestBody Map<String, String> body) {
        String fromSku = body.get("fromSku");
        String toSku   = body.get("toSku");
        if (fromSku == null || fromSku.isBlank() || toSku == null || toSku.isBlank()) {
            return ResponseEntity.badRequest().body("Se requieren fromSku y toSku");
        }
        try {
            uploadService.linkSku(uploadId, fromSku, toSku);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error vinculando SKU: " + e.getMessage());
        }
    }

    /**
     * Endpoint atómico: crea los productos nuevos, aplica las fusiones de SKU y
     * reprocesa las operaciones pendientes en UNA sola transacción. Reemplaza la
     * secuencia previa de N×/products + M×/link-sku + /reprocess, que dejaba al
     * dashboard ver estados parciales mientras corría.
     */
    @PostMapping("/{uploadId}/apply-config")
    public ResponseEntity<?> applyConfig(@PathVariable Long uploadId,
                                         @RequestBody ApplyConfigRequest request) {
        try {
            ProcessResult result = uploadService.applyConfig(
                    uploadId, request.getProducts(), request.getMerges());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error aplicando la configuración: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> listUploads() {
        return ResponseEntity.ok(uploadRepo.findAllByOrderByUploadDateDesc());
    }

    @GetMapping("/{id}/operations")
    public ResponseEntity<?> getOperations(@PathVariable Long id) {
        return ResponseEntity.ok(
            uploadRepo.findById(id).map(u -> u).orElse(null)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUpload(@PathVariable Long id) {
        if (!uploadRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        try {
            uploadService.deleteUpload(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error eliminando el ZIP: " + e.getMessage());
        }
    }
}
