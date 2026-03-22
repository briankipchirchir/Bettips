package com.bettips.backend.service;

import com.bettips.backend.entity.Subscription;
import com.bettips.backend.entity.User;
import com.bettips.backend.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public Subscription activate(User user, Subscription.PlanLevel planLevel,
                                  Subscription.Duration duration, Integer amount,
                                  String mpesaRef) {
        // Deactivate any existing subscription
        subscriptionRepository.findTopByUserAndActiveTrueOrderByEndDateDesc(user)
            .ifPresent(s -> { s.setActive(false); subscriptionRepository.save(s); });

        LocalDateTime endDate = calculateEndDate(duration);

        Subscription sub = Subscription.builder()
            .user(user)
            .planLevel(planLevel)
            .duration(duration)
            .amount(amount)
            .endDate(endDate)
            .active(true)
            .mpesaRef(mpesaRef)
            .build();

        log.info("Activating {} subscription for {} until {}",
            planLevel, user.getPhone(), endDate);
        return subscriptionRepository.save(sub);
    }

    private LocalDateTime calculateEndDate(Subscription.Duration duration) {
        return switch (duration) {
            case ONE_DAY    -> LocalDateTime.now().plusDays(1);
            case THREE_DAYS -> LocalDateTime.now().plusDays(3);
            case ONE_WEEK   -> LocalDateTime.now().plusWeeks(1);
            case ONE_MONTH  -> LocalDateTime.now().plusMonths(1);
        };
    }

    // Run every hour to expire subscriptions
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void expireSubscriptions() {
        List<Subscription> expired = subscriptionRepository
            .findExpiredSubscriptions(LocalDateTime.now());
        expired.forEach(s -> {
            s.setActive(false);
            subscriptionRepository.save(s);
            log.info("Expired subscription for user: {}", s.getUser().getPhone());
        });
        if (!expired.isEmpty()) {
            log.info("Expired {} subscriptions", expired.size());
        }
    }

    public int getPrice(Subscription.PlanLevel plan, Subscription.Duration duration) {
        return switch (plan) {
            case SILVER -> switch (duration) {
                case ONE_DAY -> 50; case THREE_DAYS -> 120;
                case ONE_WEEK -> 250; case ONE_MONTH -> 800;
            };
            case GOLD -> switch (duration) {
                case ONE_DAY -> 70; case THREE_DAYS -> 180;
                case ONE_WEEK -> 380; case ONE_MONTH -> 1200;
            };
            case PLATINUM -> switch (duration) {
                case ONE_DAY -> 100; case THREE_DAYS -> 250;
                case ONE_WEEK -> 500; case ONE_MONTH -> 1800;
            };
        };
    }
}
