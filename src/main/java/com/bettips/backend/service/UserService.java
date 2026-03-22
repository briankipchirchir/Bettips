package com.bettips.backend.service;

import com.bettips.backend.dto.SubscriptionDto;
import com.bettips.backend.dto.UserDto;
import com.bettips.backend.entity.Subscription;
import com.bettips.backend.entity.User;
import com.bettips.backend.repository.SubscriptionRepository;
import com.bettips.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public User getOrCreateUser(Jwt jwt) {
        String keycloakId = jwt.getSubject();
        return userRepository.findByKeycloakId(keycloakId)
            .orElseGet(() -> {
                User user = User.builder()
                    .keycloakId(keycloakId)
                    .email(jwt.getClaimAsString("email"))
                    .fullName(jwt.getClaimAsString("given_name") + " " + jwt.getClaimAsString("family_name"))
                    .phone(jwt.getClaimAsString("phone_number") != null ? jwt.getClaimAsString("phone_number") : "")
                    .smsNumber(jwt.getClaimAsString("phone_number") != null ? jwt.getClaimAsString("phone_number") : "")
                    .build();
                log.info("Creating new user: {}", user.getEmail());
                return userRepository.save(user);
            });
    }

    public UserDto getUserProfile(Jwt jwt) {
        User user = getOrCreateUser(jwt);
        Optional<Subscription> activeSub = subscriptionRepository
            .findTopByUserAndActiveTrueOrderByEndDateDesc(user);

        return UserDto.builder()
            .id(user.getId())
            .email(user.getEmail())
            .fullName(user.getFullName())
            .phone(user.getPhone())
            .smsNumber(user.getSmsNumber())
            .role(user.getRole())
            .createdAt(user.getCreatedAt())
            .activeSubscription(activeSub.map(this::toSubscriptionDto).orElse(null))
            .build();
    }

    @Transactional
    public UserDto updateSmsNumber(Jwt jwt, String smsNumber) {
        User user = getOrCreateUser(jwt);
        user.setSmsNumber(smsNumber);
        userRepository.save(user);
        return getUserProfile(jwt);
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
