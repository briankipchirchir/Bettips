package com.bettips.backend.dto;

import com.bettips.backend.entity.Tip;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

@Data
@Builder
public class TipDto {
    private String id;
    private String league;
    private String fixture;
    private String kickoffTime;
    private LocalDate gameDate;
    private String prediction;
    private String odds;
    private String analysis;
    private Tip.TipLevel level;
    private boolean sent;
}
