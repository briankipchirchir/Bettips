package com.bettips.backend.dto;

import com.bettips.backend.entity.User;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class UserDto {
    private String id;
    private String fullName;
    private String phone;
    private String smsNumber;
    private User.UserRole role;
    private LocalDateTime createdAt;
    private SubscriptionDto activeSubscription;
}
