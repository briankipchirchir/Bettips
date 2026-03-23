package com.bettips.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "value_bets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValueBet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false)
    private Integer matchNumber;

    @Column(nullable = false)
    private String league;

    @Column(nullable = false)
    private LocalDate gameDate;

    @Column(nullable = false)
    private String fixture;

    @Column(nullable = false)
    private String pick;

    @Column
    private String odds;

    @Column(nullable = false)
    private Integer confidence;

    @Column(columnDefinition = "TEXT")
    private String analysis;

    @Column(nullable = false)
    @Builder.Default
    private boolean sent = false;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Category {
        SPORTPESA, BETIKA, CORRECT_SCORE, GOAL_RANGE
    }

    public enum BetStatus {
        PENDING, WON, LOST
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BetStatus status = BetStatus.PENDING;
}
