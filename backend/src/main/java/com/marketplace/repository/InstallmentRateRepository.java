package com.marketplace.repository;

import com.marketplace.model.InstallmentRate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InstallmentRateRepository extends JpaRepository<InstallmentRate, Long> {
    Optional<InstallmentRate> findByOptionName(String optionName);
}
