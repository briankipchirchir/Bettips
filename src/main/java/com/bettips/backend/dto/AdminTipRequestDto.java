package com.bettips.backend.dto;

import com.bettips.backend.entity.Tip;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class AdminTipRequestDto {
    @NotBlank private String league;
    @NotBlank private String fixture;
    @NotBlank private String kickoffTime;
    @NotNull  private LocalDate gameDate;
    @NotBlank private String prediction;
    private String odds;
    private String analysis;
    @NotNull  private Tip.TipLevel level;
}
