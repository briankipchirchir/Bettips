package com.bettips.backend.dto;

import com.bettips.backend.entity.ValueBet;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class AdminValueBetRequestDto {
    @NotNull  private ValueBet.Category category;
    @NotNull  private Integer matchNumber;
    @NotBlank private String league;
    @NotNull  private LocalDate gameDate;
    @NotBlank private String fixture;
    @NotBlank private String pick;
    private String odds;
    @NotNull  private Integer confidence;
    private String analysis;
}
