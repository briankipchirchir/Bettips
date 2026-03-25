package com.bettips.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class SmsService {

    @Value("${mobitech.api-key}")
    private String apiKey;

    @Value("${mobitech.sender-id}")
    private String senderId;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://app.mobitechtechnologies.com")
            .build();

    @Async("taskExecutor")
    public void sendSms(String phoneNumber, String message) {
        String normalized = normalizePhone(phoneNumber);
        log.info("Sending SMS via Mobitech to {}", normalized);

        Map<String, Object> body = new HashMap<>();
        body.put("mobile", normalized);
        body.put("response_type", "json");
        body.put("sender_name", senderId);
        body.put("service_id", 0); // optional depending on your account
        body.put("message", message);

        try {
            String response = webClient.post()
                    .uri("/sms/sendsms")
                    .header("h_api_key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Mobitech SMS sent: {}", response);

        } catch (Exception e) {
            log.error("Mobitech SMS failed for {}: {}", normalized, e.getMessage());

            try {
                Thread.sleep(1000); // wait 1 sec before retry

                webClient.post()
                        .uri("/sms/sendsms")
                        .header("h_api_key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                log.info("Retry SMS success for {}", normalized);

            } catch (Exception ex) {
                log.error("Retry failed for {}", normalized);
            }
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