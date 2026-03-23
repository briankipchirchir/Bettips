package com.bettips.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanLevel planLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Duration duration;

    @Column(nullable = false)
    private Integer amount;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime startDate = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime endDate;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = false;

    @Column
    private String mpesaRef;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum PlanLevel {
        SILVER, GOLD, PLATINUM, VALUE_BETS
    }

    public enum Duration {
        ONE_DAY, THREE_DAYS, ONE_WEEK, ONE_MONTH
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(endDate);
    }
}
