package com.bettips.backend.controller;

import com.bettips.backend.dto.PaymentRequestDto;
import com.bettips.backend.entity.User;
import com.bettips.backend.service.PayHeroService;
import com.bettips.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PayHeroService payHeroService;
    private final UserService userService;

    @PostMapping("/mpesa/stk")
    public ResponseEntity<?> initiatePayment(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PaymentRequestDto dto) {

        if (!dto.getSmsPhone().equals(dto.getMpesaPhone())) {
            userService.updateSmsNumber(user, dto.getSmsPhone());
        }

        String ref = payHeroService.initiateStk(
            user,
            dto.getMpesaPhone(),
            dto.getSmsPhone(),
            dto.getPlanLevel(),
            dto.getDuration()
        );

        if (ref != null) {
            return ResponseEntity.ok(Map.of(
                "message", "STK push sent. Enter your M-Pesa PIN to complete payment.",
                "checkoutRequestId", ref
            ));
        }
        return ResponseEntity.badRequest()
            .body(Map.of("message", "Failed to initiate payment. Please try again."));
    }

    @PostMapping("/mpesa/callback")
    public ResponseEntity<String> mpesaCallback(@RequestBody Map<String, Object> payload) {
        payHeroService.handleCallback(payload);
        return ResponseEntity.ok("OK");
    }
}
