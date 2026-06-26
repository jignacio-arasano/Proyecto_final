package com.marketplace.repository;

import com.marketplace.model.Upload;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UploadRepository extends JpaRepository<Upload, Long> {
    List<Upload> findAllByOrderByUploadDateDesc();
    List<Upload> findAllByOrderByUploadDateAsc();
}
