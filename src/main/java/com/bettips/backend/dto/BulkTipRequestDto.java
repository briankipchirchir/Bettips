package com.bettips.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkTipRequestDto {
    @NotEmpty
    @Valid
    private List<AdminTipRequestDto> tips;
}
