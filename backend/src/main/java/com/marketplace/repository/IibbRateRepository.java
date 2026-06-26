package com.marketplace.repository;

import com.marketplace.model.IibbRate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IibbRateRepository extends JpaRepository<IibbRate, Long> {
    Optional<IibbRate> findByProvince(String province);
    Optional<IibbRate> findByProvinceIgnoreCase(String province);
}
