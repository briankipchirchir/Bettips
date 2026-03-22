package com.bettips.backend.dto;

import com.bettips.backend.entity.ValueBet;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

@Data
@Builder
public class ValueBetDto {
    private String id;
    private ValueBet.Category category;
    private Integer matchNumber;
    private String league;
    private LocalDate gameDate;
    private String fixture;
    private String pick;
    private String odds;
    private Integer confidence;
    private String analysis;
    private boolean sent;
}
