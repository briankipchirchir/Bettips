package com.bettips.backend.dto;

import com.bettips.backend.entity.Subscription;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class SubscriptionDto {
    private String id;
    private Subscription.PlanLevel planLevel;
    private Subscription.Duration duration;
    private Integer amount;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private boolean active;
    private String mpesaRef;
}
