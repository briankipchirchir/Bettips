package com.bettips.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
public class SmsService {

    @Value("${mobitech.api-key}")
    private String apiKey;

    @Value("${mobitech.sender-id}")
    private String senderId;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://app.mobitechtechnologies.com")
            .build();

    // Queue per phone number
    private final ConcurrentMap<String, BlockingQueue<Map<String, Object>>> smsQueues = new ConcurrentHashMap<>();
    // Track last usage per phone to clean up inactive queues
    private final ConcurrentMap<String, Instant> lastUsedMap = new ConcurrentHashMap<>();

    // Executor for sending SMSs sequentially
    private final ExecutorService queueExecutor = Executors.newCachedThreadPool();

    // Time after which inactive queues are removed (e.g., 10 minutes)
    private final long INACTIVE_QUEUE_TIMEOUT_MS = 10 * 60 * 1000;

    @Async("taskExecutor")
    public void sendSms(String phoneNumber, String message) {
        String normalized = normalizePhone(phoneNumber);

        Map<String, Object> body = Map.of(
                "mobile", normalized,
                "response_type", "json",
                "sender_name", senderId,
                "service_id", 0,
                "message", message
        );

        lastUsedMap.put(normalized, Instant.now());

        smsQueues.computeIfAbsent(normalized, k -> {
            BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
            queueExecutor.submit(() -> processQueue(normalized, queue));
            return queue;
        }).add(body);
    }

    private void processQueue(String phone, BlockingQueue<Map<String, Object>> queue) {
        try {
            while (true) {
                Map<String, Object> body = queue.poll(1, TimeUnit.SECONDS); // wait max 1s

                // Check for queue inactivity
                Instant lastUsed = lastUsedMap.getOrDefault(phone, Instant.now());
                if (queue.isEmpty() && Instant.now().minusMillis(INACTIVE_QUEUE_TIMEOUT_MS).isAfter(lastUsed)) {
                    smsQueues.remove(phone);
                    lastUsedMap.remove(phone);
                    log.info("Removed inactive SMS queue for {}", phone);
                    break;
                }

                if (body == null) continue; // no message, loop again

                int maxAttempts = 3;

                for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                    try {
                        String response = webClient.post()
                                .uri("/sms/sendsms")
                                .header("h_api_key", apiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(body)
                                .retrieve()
                                .bodyToMono(String.class)
                                .block();

                        log.info("Mobitech SMS sent to {} (attempt {}): {}", phone, attempt, response);
                        break;

                    } catch (Exception e) {
                        log.error("Attempt {} failed for {}: {}", attempt, phone, e.toString());
                        if (attempt < maxAttempts) {
                            long delay = 1000L * attempt;
                            Thread.sleep(delay);
                            log.info("Retrying SMS to {} after {}ms", phone, delay);
                        } else {
                            log.error("All {} attempts failed for {}", maxAttempts, phone);
                        }
                    }
                }

                Thread.sleep(500); // small delay between messages to same number
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("SMS queue processor interrupted for {}", phone, e);
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