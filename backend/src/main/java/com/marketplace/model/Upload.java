package com.marketplace.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "uploads")
@Data
public class Upload {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filename;

    @Column(name = "upload_date")
    private LocalDateTime uploadDate = LocalDateTime.now();

    private String status = "pending";

    @Column(name = "total_docs")
    private Integer totalDocs = 0;

    @Column(name = "valid_docs")
    private Integer validDocs = 0;

    @Column(name = "invalid_docs")
    private Integer invalidDocs = 0;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;
}
