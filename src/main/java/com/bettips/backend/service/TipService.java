package com.bettips.backend.service;

import com.bettips.backend.dto.AdminTipRequestDto;
import com.bettips.backend.dto.TipDto;
import com.bettips.backend.entity.SentTip;
import com.bettips.backend.entity.Subscription;
import com.bettips.backend.entity.Tip;
import com.bettips.backend.entity.User;
import com.bettips.backend.repository.SentTipRepository;
import com.bettips.backend.repository.SubscriptionRepository;
import com.bettips.backend.repository.TipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TipService {

    private final TipRepository tipRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SmsService smsService;
    private final SentTipRepository sentTipRepository;

    @Cacheable(value = "tips", key = "'free:' + #date")
    public List<TipDto> getFreeTips(LocalDate date) {
        return tipRepository.findByGameDateAndLevel(date, Tip.TipLevel.FREE)
            .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Cacheable(value = "tips", key = "'premium:' + #date + ':' + #user.id")
    public List<TipDto> getPremiumTips(LocalDate date, User user) {
        Optional<Subscription> activeSub = subscriptionRepository
            .findTopByUserAndActiveTrueOrderByEndDateDesc(user);

        if (activeSub.isEmpty() || activeSub.get().isExpired()) {
            return getFreeTips(date);
        }

        Subscription.PlanLevel plan = activeSub.get().getPlanLevel();
        return tipRepository.findByGameDate(date).stream()
            .filter(tip -> canAccessTip(tip.getLevel(), plan))
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    // Admin creates tip — automatically sends SMS to all eligible subscribers
    @Transactional
    @CacheEvict(value = "tips", allEntries = true)
    public TipDto createTip(AdminTipRequestDto dto) {
        Tip tip = Tip.builder()
            .league(dto.getLeague())
            .fixture(dto.getFixture())
            .kickoffTime(dto.getKickoffTime())
            .gameDate(dto.getGameDate())
            .prediction(dto.getPrediction())
            .odds(dto.getOdds())
            .analysis(dto.getAnalysis())
            .level(dto.getLevel())
            .build();

        tip = tipRepository.save(tip);
        log.info("Tip created: {} - {}", tip.getFixture(), tip.getLevel());

        // Auto-send SMS to all eligible subscribers immediately
        sendToEligibleSubscribers(tip);

        return toDto(tip);
    }

    @Transactional
    public TipDto updateTip(String id, AdminTipRequestDto dto) {
        Tip tip = tipRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Tip not found"));
        tip.setLeague(dto.getLeague());
        tip.setFixture(dto.getFixture());
        tip.setKickoffTime(dto.getKickoffTime());
        tip.setGameDate(dto.getGameDate());
        tip.setPrediction(dto.getPrediction());
        tip.setOdds(dto.getOdds());
        tip.setAnalysis(dto.getAnalysis());
        tip.setLevel(dto.getLevel());
        return toDto(tipRepository.save(tip));
    }

    @Transactional
    @CacheEvict(value = "tips", allEntries = true)
    public void deleteTip(String id) {
        tipRepository.deleteById(id);
    }

    public void sendTodaysTipsToNewSubscriber(User user, Subscription.PlanLevel planLevel) {
        List<Tip> todaysTips = tipRepository.findByGameDate(LocalDate.now());

        List<Tip> eligibleTips = todaysTips.stream()
                .filter(tip -> canAccessTip(tip.getLevel(), planLevel))
                .filter(tip -> !sentTipRepository.existsByUserAndTipId(user, tip.getId())) // skip already sent
                .collect(Collectors.toList());

        if (eligibleTips.isEmpty()) {
            log.info("No tips available today for new subscriber: {}", user.getPhone());
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("BetTips - Welcome! Here are today's tips:\n\n");
        for (Tip tip : eligibleTips) {
            sb.append(String.format("%s\n%s → %s @ %s\n\n",
                    tip.getLeague(), tip.getFixture(),
                    tip.getPrediction(), tip.getOdds() != null ? tip.getOdds() : "N/A"));

            // Record sent
            sentTipRepository.save(SentTip.builder()
                    .user(user)
                    .tipId(tip.getId())
                    .build());
        }
        sb.append("Good luck! 🍀");

        smsService.sendSms(user.getSmsNumber(), sb.toString());
        log.info("Sent {} tips to new subscriber: {}", eligibleTips.size(), user.getPhone());
    }

    private void sendToEligibleSubscribers(Tip tip) {
        if (tip.getLevel() == Tip.TipLevel.FREE) {
            log.info("Free tip — no SMS needed for: {}", tip.getFixture());
            tip.setSent(true);
            tipRepository.save(tip);
            return;
        }

        List<Subscription> activeSubscriptions = subscriptionRepository
                .findAllActiveSubscriptions(LocalDateTime.now())
                .stream()
                .filter(s -> canAccessTip(tip.getLevel(), s.getPlanLevel()))
                .collect(Collectors.toList());

        if (activeSubscriptions.isEmpty()) {
            log.info("No eligible subscribers for tip: {}", tip.getFixture());
            tip.setSent(true);
            tipRepository.save(tip);
            return;
        }

        String message = buildTipSms(tip);
        int sentCount = 0;

        for (Subscription sub : activeSubscriptions) {
            User user = sub.getUser();

            // Skip if already sent this tip to this user
            if (sentTipRepository.existsByUserAndTipId(user, tip.getId())) {
                log.info("Tip '{}' already sent to {}, skipping", tip.getFixture(), user.getPhone());
                continue;
            }

            smsService.sendSms(user.getSmsNumber(), message);

            // Record that this user received this tip
            sentTipRepository.save(SentTip.builder()
                    .user(user)
                    .tipId(tip.getId())
                    .build());

            sentCount++;
        }

        tip.setSent(true);
        tipRepository.save(tip);
        log.info("Auto-sent tip '{}' to {} subscribers", tip.getFixture(), sentCount);
    }

    private String buildTipSms(Tip tip) {
        return String.format(
            "BetTips %s\n%s | %s\nPick: %s | Odds: %s\n%s",
            tip.getLevel().name(),
            tip.getLeague(),
            tip.getFixture(),
            tip.getPrediction(),
            tip.getOdds() != null ? tip.getOdds() : "N/A",
            tip.getAnalysis() != null ? tip.getAnalysis() : ""
        ).trim();
    }

    // TipService.java — update canAccessTip
    private boolean canAccessTip(Tip.TipLevel tipLevel, Subscription.PlanLevel userPlan) {
        if (tipLevel == Tip.TipLevel.FREE) return true;
        return switch (userPlan) {
            case PLATINUM   -> true;
            case GOLD       -> tipLevel != Tip.TipLevel.PLATINUM;
            case SILVER     -> tipLevel == Tip.TipLevel.SILVER;
            case VALUE_BETS -> tipLevel == Tip.TipLevel.VALUE_BETS; // fix
            case FREE, NONE -> false;
        };
    }

    @Cacheable(value = "tips", key = "'all:' + #date")
    public List<TipDto> getAllTips(LocalDate date) {
        return tipRepository.findByGameDate(date)
            .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public Tip updateStatus(String tipId, Tip.TipStatus status) {
        Tip tip = tipRepository.findById(tipId)
                .orElseThrow(() -> new RuntimeException("Tip not found: " + tipId));
        tip.setStatus(status);
        return tipRepository.save(tip);
    }

    public TipDto toDto(Tip tip) {
        return TipDto.builder()
            .id(tip.getId())
            .league(tip.getLeague())
            .fixture(tip.getFixture())
            .kickoffTime(tip.getKickoffTime())
            .gameDate(tip.getGameDate())
            .prediction(tip.getPrediction())
            .odds(tip.getOdds())
            .analysis(tip.getAnalysis())
            .level(tip.getLevel())
            .sent(tip.isSent())
            .build();
    }
}
