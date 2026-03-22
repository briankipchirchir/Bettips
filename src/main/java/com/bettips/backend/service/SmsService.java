package com.bettips.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@Slf4j
public class SmsService {

    @Value("${africastalking.api-key}")
    private String apiKey;

    @Value("${africastalking.username}")
    private String username;

    @Value("${africastalking.sender-id}")
    private String senderId;

    private final WebClient webClient = WebClient.builder()
        .baseUrl("https://api.africastalking.com")
        .build();

    public void sendSms(String phoneNumber, String message) {
        // Normalize phone number to +254 format
        String normalized = normalizePhone(phoneNumber);
        log.info("Sending SMS to {}", normalized);

        try {
            String response = webClient.post()
                .uri("/version1/messaging")
                .header("apiKey", apiKey)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .bodyValue("username=" + username +
                    "&to=" + normalized +
                    "&message=" + message +
                    (senderId != null ? "&from=" + senderId : ""))
                .retrieve()
                .bodyToMono(String.class)
                .block();
            log.info("SMS sent successfully to {}: {}", normalized, response);
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", normalized, e.getMessage());
        }
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        phone = phone.trim().replaceAll("\\s+", "");
        if (phone.startsWith("+254")) return phone;
        if (phone.startsWith("254")) return "+" + phone;
        if (phone.startsWith("07") || phone.startsWith("01")) {
            return "+254" + phone.substring(1);
        }
        return "+254" + phone;
    }
}
