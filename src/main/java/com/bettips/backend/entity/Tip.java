package com.bettips.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tips")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String league;

    @Column(nullable = false)
    private String fixture;

    @Column(nullable = false)
    private String kickoffTime;

    @Column(nullable = false)
    private LocalDate gameDate;

    @Column(nullable = false)
    private String prediction;

    @Column
    private String odds;

    @Column(columnDefinition = "TEXT")
    private String analysis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipLevel level;

    @Column(nullable = false)
    @Builder.Default
    private boolean sent = false;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum TipLevel {
        FREE, SILVER, GOLD, PLATINUM,VALUE_BETS
    }

    public enum TipStatus {
        PENDING, WON, LOST
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TipStatus status = TipStatus.PENDING;
}
