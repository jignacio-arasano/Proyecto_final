package com.marketplace.controller;

import com.marketplace.model.IibbRate;
import com.marketplace.model.InstallmentRate;
import com.marketplace.model.PlatformCommission;
import com.marketplace.repository.IibbRateRepository;
import com.marketplace.repository.InstallmentRateRepository;
import com.marketplace.repository.PlatformCommissionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/params")
public class ParamsController {
    private final PlatformCommissionRepository commissionRepo;
    private final IibbRateRepository iibbRepo;
    private final InstallmentRateRepository installmentRepo;

    public ParamsController(PlatformCommissionRepository commissionRepo,
                            IibbRateRepository iibbRepo,
                            InstallmentRateRepository installmentRepo) {
        this.commissionRepo = commissionRepo;
        this.iibbRepo = iibbRepo;
        this.installmentRepo = installmentRepo;
    }

    @GetMapping("/commissions")
    public List<PlatformCommission> getCommissions() {
        return commissionRepo.findAll();
    }

    @PutMapping("/commissions/{id}")
    public ResponseEntity<PlatformCommission> updateCommission(@PathVariable Long id,
                                                               @RequestBody PlatformCommission body) {
        return commissionRepo.findById(id).map(existing -> {
            existing.setRate(body.getRate());
            return ResponseEntity.ok(commissionRepo.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/installments")
    public List<InstallmentRate> getInstallments() {
        return installmentRepo.findAll();
    }

    @PutMapping("/installments/{id}")
    public ResponseEntity<InstallmentRate> updateInstallment(@PathVariable Long id,
                                                              @RequestBody InstallmentRate body) {
        return installmentRepo.findById(id).map(existing -> {
            existing.setSurchargeRate(body.getSurchargeRate());
            return ResponseEntity.ok(installmentRepo.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/iibb")
    public List<IibbRate> getIibb() {
        return iibbRepo.findAll();
    }

    @PutMapping("/iibb/{id}")
    public ResponseEntity<IibbRate> updateIibb(@PathVariable Long id, @RequestBody IibbRate body) {
        return iibbRepo.findById(id).map(existing -> {
            existing.setRate(body.getRate());
            return ResponseEntity.ok(iibbRepo.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }
}
