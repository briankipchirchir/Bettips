package com.bettips.backend.dto;

import com.bettips.backend.entity.Subscription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentRequestDto {
    @NotBlank
    private String mpesaPhone;

    @NotBlank
    private String smsPhone;

    @NotNull
    private Subscription.PlanLevel planLevel;

    @NotNull
    private Subscription.Duration duration;
}
