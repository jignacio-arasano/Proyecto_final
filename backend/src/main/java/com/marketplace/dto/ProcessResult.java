package com.marketplace.dto;

import lombok.Data;
import java.util.List;

@Data
public class ProcessResult {
    private Long uploadId;
    private String status;
    private int totalDocs;
    private int validDocs;
    private int invalidDocs;
    private List<String> unknownSkus;
    private List<String> errors;
    private List<OperationDto> operations;
    private List<PredictionDto> predictions;
}
