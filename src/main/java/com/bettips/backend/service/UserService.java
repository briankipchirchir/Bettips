package com.bettips.backend.service;

import com.bettips.backend.dto.SubscriptionDto;
import com.bettips.backend.dto.UserDto;
import com.bettips.backend.entity.Subscription;
import com.bettips.backend.entity.User;
import com.bettips.backend.repository.SubscriptionRepository;
import com.bettips.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;

    public UserDto getUserProfile(User user) {
        Optional<Subscription> activeSub = subscriptionRepository
            .findTopByUserAndActiveTrueOrderByEndDateDesc(user);

        return UserDto.builder()
            .id(user.getId())
            .fullName(user.getFullName())
            .phone(user.getPhone())
            .smsNumber(user.getSmsNumber())
            .role(user.getRole())
            .createdAt(user.getCreatedAt())
            .activeSubscription(activeSub.map(this::toSubscriptionDto).orElse(null))
            .build();
    }

    @Transactional
    public UserDto updateSmsNumber(User user, String smsNumber) {
        user.setSmsNumber(smsNumber);
        userRepository.save(user);
        return getUserProfile(user);
    }

    public SubscriptionDto toSubscriptionDto(Subscription s) {
        return SubscriptionDto.builder()
            .id(s.getId())
            .planLevel(s.getPlanLevel())
            .duration(s.getDuration())
            .amount(s.getAmount())
            .startDate(s.getStartDate())
            .endDate(s.getEndDate())
            .active(s.isActive())
            .mpesaRef(s.getMpesaRef())
            .build();
    }
}
