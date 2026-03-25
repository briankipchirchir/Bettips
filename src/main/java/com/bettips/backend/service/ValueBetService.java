package com.bettips.backend.service;

import java.util.ArrayList;
import java.util.Map;

import com.bettips.backend.dto.AdminValueBetRequestDto;
import com.bettips.backend.dto.ValueBetDto;
import com.bettips.backend.entity.Subscription;
import com.bettips.backend.entity.User;
import com.bettips.backend.entity.ValueBet;
import com.bettips.backend.repository.SubscriptionRepository;
import com.bettips.backend.repository.ValueBetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValueBetService {

    private final ValueBetRepository valueBetRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SmsService smsService;

    public List<ValueBetDto> getByCategory(ValueBet.Category category, User user) {
        List<ValueBet> bets = valueBetRepository.findByCategoryOrderByMatchNumberAsc(category);
        boolean isSubscribed = subscriptionRepository
            .findTopByUserAndActiveTrueOrderByEndDateDesc(user)
            .map(s -> !s.isExpired() && (
                s.getPlanLevel() == Subscription.PlanLevel.VALUE_BETS ||
                s.getPlanLevel() == Subscription.PlanLevel.SILVER ||
                s.getPlanLevel() == Subscription.PlanLevel.GOLD ||
                s.getPlanLevel() == Subscription.PlanLevel.PLATINUM
            ))
            .orElse(false);

        return bets.stream()
            .map(b -> {
                ValueBetDto dto = toDto(b);
                if (!isSubscribed) dto.setAnalysis("Subscribe to unlock full analysis");
                return dto;
            })
            .collect(Collectors.toList());
    }

    // Admin creates value bet — automatically sends SMS to all active subscribers
    @Transactional
    @CacheEvict(value = "valueBets", allEntries = true)
    public ValueBetDto create(AdminValueBetRequestDto dto) {
        ValueBet bet = ValueBet.builder()
            .category(dto.getCategory())
            .matchNumber(dto.getMatchNumber())
            .league(dto.getLeague())
            .gameDate(dto.getGameDate())
            .fixture(dto.getFixture())
            .pick(dto.getPick())
            .odds(dto.getOdds())
            .confidence(dto.getConfidence())
            .analysis(dto.getAnalysis())
            .build();

        bet = valueBetRepository.save(bet);
        log.info("Value bet created: {} - {}", bet.getFixture(), bet.getCategory());

        // Auto-send to all active subscribers immediately
        sendToAllSubscribers(bet);

        return toDto(bet);
    }

    @Transactional
    public ValueBetDto update(String id, AdminValueBetRequestDto dto) {
        ValueBet bet = valueBetRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("ValueBet not found"));
        bet.setMatchNumber(dto.getMatchNumber());
        bet.setLeague(dto.getLeague());
        bet.setGameDate(dto.getGameDate());
        bet.setFixture(dto.getFixture());
        bet.setPick(dto.getPick());
        bet.setOdds(dto.getOdds());
        bet.setConfidence(dto.getConfidence());
        bet.setAnalysis(dto.getAnalysis());
        return toDto(valueBetRepository.save(bet));
    }

    @Transactional
    public void delete(String id) {
        valueBetRepository.deleteById(id);
    }

    private void sendToAllSubscribers(ValueBet bet) {
        List<Subscription> activeSubs = subscriptionRepository.findAll().stream()
            .filter(s -> s.isActive() && !s.isExpired())
            .collect(Collectors.toList());

        if (activeSubs.isEmpty()) {
            log.info("No active subscribers for value bet: {}", bet.getFixture());
            bet.setSent(true);
            valueBetRepository.save(bet);
            return;
        }

        String message = String.format(
            "BetTips %s\nMatch %d: %s\nPick: %s | Odds: %s | Confidence: %d%%\n%s",
            bet.getCategory().name().replace("_", " "),
            bet.getMatchNumber(),
            bet.getFixture(),
            bet.getPick(),
            bet.getOdds() != null ? bet.getOdds() : "N/A",
            bet.getConfidence(),
            bet.getAnalysis() != null ? bet.getAnalysis() : ""
        ).trim();

        int sentCount = 0;
        for (Subscription sub : activeSubs) {
            smsService.sendSms(sub.getUser().getSmsNumber(), message);
            sentCount++;
        }

        bet.setSent(true);
        valueBetRepository.save(bet);
        log.info("Auto-sent value bet '{}' to {} subscribers", bet.getFixture(), sentCount);
    }


    @CacheEvict(value = "tips", allEntries = true)
    public List<ValueBetDto> getAll(ValueBet.Category category) {
        return valueBetRepository.findByCategoryOrderByMatchNumberAsc(category)
            .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public ValueBet updateStatus(String betId, ValueBet.BetStatus status) {
        ValueBet bet = valueBetRepository.findById(betId)
                .orElseThrow(() -> new RuntimeException("ValueBet not found: " + betId));
        bet.setStatus(status);
        return valueBetRepository.save(bet);
    }


    @Transactional
    @CacheEvict(value = "valueBets", allEntries = true)
    public List<ValueBetDto> createBulk(List<AdminValueBetRequestDto> dtos) {
        List<ValueBet> savedBets = new ArrayList<>();

        for (AdminValueBetRequestDto dto : dtos) {
            ValueBet bet = ValueBet.builder()
                    .category(dto.getCategory())
                    .matchNumber(dto.getMatchNumber())
                    .league(dto.getLeague())
                    .gameDate(dto.getGameDate())
                    .fixture(dto.getFixture())
                    .pick(dto.getPick())
                    .odds(dto.getOdds())
                    .confidence(dto.getConfidence())
                    .analysis(dto.getAnalysis())
                    .build();
            savedBets.add(valueBetRepository.save(bet));
            log.info("Bulk value bet saved: {} - {}", bet.getFixture(), bet.getCategory());
        }

        // Group by category — send one bundled SMS per category
        Map<ValueBet.Category, List<ValueBet>> byCategory = savedBets.stream()
                .collect(Collectors.groupingBy(ValueBet::getCategory));

        byCategory.forEach(this::sendBundledValueBetSms);

        return savedBets.stream().map(this::toDto).collect(Collectors.toList());
    }

    private void sendBundledValueBetSms(ValueBet.Category category, List<ValueBet> bets) {
        List<Subscription> activeSubs = subscriptionRepository.findAll().stream()
                .filter(s -> s.isActive() && !s.isExpired())
                .collect(Collectors.toList());

        if (activeSubs.isEmpty()) {
            log.info("No active subscribers for bundled {} value bets", category);
            bets.forEach(b -> { b.setSent(true); valueBetRepository.save(b); });
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("BetTips %s (%d bets)\n\n",
                category.name().replace("_", " "), bets.size()));

        for (int i = 0; i < bets.size(); i++) {
            ValueBet bet = bets.get(i);
            sb.append(String.format("%d. %s | %s\n   Pick: %s @ %s (%d%% conf)\n\n",
                    i + 1,
                    bet.getLeague(),
                    bet.getFixture(),
                    bet.getPick(),
                    bet.getOdds() != null ? bet.getOdds() : "N/A",
                    bet.getConfidence()
            ));
        }

        String message = sb.toString().trim();
        int sentCount = 0;
        for (Subscription sub : activeSubs) {
            smsService.sendSms(sub.getUser().getSmsNumber(), message);
            sentCount++;
        }

        bets.forEach(b -> { b.setSent(true); valueBetRepository.save(b); });
        log.info("Sent bundled {} value bets ({} games) to {} subscribers",
                category, bets.size(), sentCount);
    }

    public ValueBetDto toDto(ValueBet b) {
        return ValueBetDto.builder()
            .id(b.getId())
            .category(b.getCategory())
            .matchNumber(b.getMatchNumber())
            .league(b.getLeague())
            .gameDate(b.getGameDate())
            .fixture(b.getFixture())
            .pick(b.getPick())
            .odds(b.getOdds())
            .confidence(b.getConfidence())
            .analysis(b.getAnalysis())
            .sent(b.isSent())
            .build();
    }
}
