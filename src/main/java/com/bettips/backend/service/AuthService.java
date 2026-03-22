package com.bettips.backend.service;

import com.bettips.backend.dto.*;
import com.bettips.backend.entity.User;
import com.bettips.backend.repository.SubscriptionRepository;
import com.bettips.backend.repository.UserRepository;
import com.bettips.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public AuthResponseDto register(RegisterRequestDto dto) {
        // Normalize phone number
        String phone = normalizePhone(dto.getPhone());

        if (userRepository.existsByPhone(phone)) {
            throw new RuntimeException("Phone number already registered");
        }

        User user = User.builder()
            .fullName(dto.getFullName())
            .phone(phone)
            .smsNumber(dto.getSmsNumber() != null ? normalizePhone(dto.getSmsNumber()) : phone)
            .password(passwordEncoder.encode(dto.getPassword()))
            .role(User.UserRole.USER)
            .build();

        user = userRepository.save(user);
        log.info("New user registered: {}", user.getPhone());
        return buildAuthResponse(user);
    }

    public AuthResponseDto login(LoginRequestDto dto) {
        String phone = normalizePhone(dto.getPhone());

        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(phone, dto.getPassword())
        );

        User user = userRepository.findByPhone(phone)
            .orElseThrow(() -> new RuntimeException("User not found"));

        log.info("User logged in: {}", user.getPhone());
        return buildAuthResponse(user);
    }

    public AuthResponseDto refreshToken(String refreshToken) {
        if (!jwtService.isTokenValid(refreshToken) || jwtService.isTokenExpired(refreshToken)) {
            throw new RuntimeException("Invalid or expired refresh token");
        }

        String userId = jwtService.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        return buildAuthResponse(user);
    }

    private AuthResponseDto buildAuthResponse(User user) {
        String accessToken  = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return AuthResponseDto.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .expiresIn(86400000)
            .user(UserDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .smsNumber(user.getSmsNumber())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build())
            .build();
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        phone = phone.trim().replaceAll("\\s+", "");
        if (phone.startsWith("+254")) return phone;
        if (phone.startsWith("254")) return "+" + phone;
        if (phone.startsWith("07") || phone.startsWith("01")) return "+254" + phone.substring(1);
        return "+254" + phone;
    }
}
