package com.marketplace.controller;

import com.marketplace.model.Operation;
import com.marketplace.model.Product;
import com.marketplace.repository.OperationRepository;
import com.marketplace.repository.ProductRepository;
import com.marketplace.service.MarginCalculatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductRepository productRepo;
    private final OperationRepository operationRepo;
    private final MarginCalculatorService marginCalc;

    public ProductController(ProductRepository productRepo, OperationRepository operationRepo,
                            MarginCalculatorService marginCalc) {
        this.productRepo = productRepo;
        this.operationRepo = operationRepo;
        this.marginCalc = marginCalc;
    }

    @GetMapping
    public List<Product> getAll() {
        return productRepo.findAll();
    }

    @PostMapping
    public ResponseEntity<Product> create(@RequestBody Product product) {
        if (product.getSku() == null || product.getSku().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(productRepo.save(product));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Product> update(@PathVariable Long id, @RequestBody Product product) {
        return productRepo.findById(id).map(existing -> {
            existing.setName(product.getName());
            existing.setCategory(product.getCategory());
            existing.setSaleModality(product.getSaleModality());
            existing.setRestockThreshold(product.getRestockThreshold());
            existing.setCurrentStock(product.getCurrentStock());
            existing.setCostPrice(product.getCostPrice());
            Product saved = productRepo.save(existing);

            // Categoría, modalidad de cuotas y costo de compra inciden en el margen.
            // Recalculamos todas las operaciones de este producto para que el
            // dashboard refleje los nuevos valores sin tener que recargar los ZIPs.
            List<Operation> ops = operationRepo.findByProductId(saved.getId());
            for (Operation op : ops) {
                marginCalc.calculate(op, saved);
                operationRepo.save(op);
            }
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!productRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        // Primero desvinculamos las operaciones que referencian este producto
        // (evita violación de FK). El historial de operaciones se conserva.
        operationRepo.clearProductFromOperations(id);
        productRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
