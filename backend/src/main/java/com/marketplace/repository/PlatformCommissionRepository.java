package com.marketplace.repository;

import com.marketplace.model.PlatformCommission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PlatformCommissionRepository extends JpaRepository<PlatformCommission, Long> {
    Optional<PlatformCommission> findByCategoryAndModality(String category, String modality);
}
