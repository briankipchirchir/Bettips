package com.bettips.backend.service;

import com.bettips.backend.entity.Payment;
import com.bettips.backend.entity.Subscription;
import com.bettips.backend.entity.User;
import com.bettips.backend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayHeroService {

    private final PaymentRepository paymentRepository;
    private final SubscriptionService subscriptionService;
    private final SmsService smsService;
    private final TipService tipService;

    @Value("${payhero.api-username}")
    private String apiUsername;

    @Value("${payhero.api-password}")
    private String apiPassword;

    @Value("${payhero.channel-id}")
    private String channelId;

    @Value("${payhero.callback-url}")
    private String callbackUrl;

    private final WebClient webClient = WebClient.builder()
        .baseUrl("https://backend.payhero.co.ke")
        .build();

    private String getBasicAuth() {
        String credentials = apiUsername + ":" + apiPassword;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    @Transactional
    public String initiateStk(User user, String mpesaPhone, String smsPhone,
                              Subscription.PlanLevel planLevel,
                              Subscription.Duration duration) {
        int amount = subscriptionService.getPrice(planLevel, duration);
        String externalRef = "BETTIPS-" + System.currentTimeMillis();

        Payment payment = Payment.builder()
                .user(user)
                .amount(amount)
                .phoneNumber(mpesaPhone)
                .planLevel(planLevel)
                .duration(duration)
                .status(Payment.PaymentStatus.PENDING)
                .checkoutRequestId(externalRef)
                .build();
        paymentRepository.save(payment);

        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);
        body.put("phone_number", normalizePhone(mpesaPhone));
        body.put("channel_id", Integer.parseInt(channelId));
        body.put("provider", "m-pesa");
        body.put("external_reference", externalRef);
        body.put("customer_name", user.getFullName());
        body.put("callback_url", callbackUrl);

        // Fire and don't wait
        webClient.post()
                .uri("/api/v2/payments")
                .header("Authorization", getBasicAuth())
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .subscribe(
                        response -> {
                            log.info("PayHero STK response: {}", response);
                            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                                log.error("PayHero STK failed: {}", response);
                                paymentRepository.findByCheckoutRequestId(externalRef).ifPresent(p -> {
                                    p.setStatus(Payment.PaymentStatus.FAILED);
                                    paymentRepository.save(p);
                                });
                            } else {
                                log.info("STK push sent to {} ref: {}", mpesaPhone, externalRef);
                            }
                        },
                        error -> {
                            log.error("PayHero STK push error: {}", error.getMessage());
                            paymentRepository.findByCheckoutRequestId(externalRef).ifPresent(p -> {
                                p.setStatus(Payment.PaymentStatus.FAILED);
                                paymentRepository.save(p);
                            });
                        }
                );

        return externalRef; // user gets response in ~50ms
    }
    @Transactional
    public void handleCallback(Map<String, Object> payload) {
        try {
            log.info("PayHero callback received: {}", payload);

            // PayHero wraps everything inside "response"
            Map<String, Object> response = (Map<String, Object>) payload.get("response");
            if (response == null) {
                log.warn("PayHero callback missing response object");
                return;
            }

            String externalRef = String.valueOf(response.get("ExternalReference"));
            String status      = String.valueOf(response.get("Status"));      // "Failed" or "Success"
            String mpesaRef    = String.valueOf(response.get("MpesaReceiptNumber"));
            String resultCode  = String.valueOf(response.get("ResultCode"));

            if ("null".equals(externalRef) || externalRef == null) {
                log.warn("PayHero callback missing ExternalReference");
                return;
            }

            Payment payment = paymentRepository.findByCheckoutRequestId(externalRef)
                    .orElseThrow(() -> new RuntimeException("Payment not found: " + externalRef));

            if ("Success".equalsIgnoreCase(status)) {
                payment.setStatus(Payment.PaymentStatus.SUCCESS);
                payment.setMpesaRef(mpesaRef);
                payment.setCompletedAt(LocalDateTime.now());
                paymentRepository.save(payment);

                Subscription subscription = subscriptionService.activate(
                        payment.getUser(),
                        payment.getPlanLevel(),
                        payment.getDuration(),
                        payment.getAmount(),
                        mpesaRef
                );

                String confirmMsg = String.format(
                        "BetTips: Payment confirmed! KSH %d received. Your %s plan is active until %s. Ref: %s",
                        payment.getAmount(),
                        payment.getPlanLevel().name(),
                        subscription.getEndDate().toLocalDate().toString(),
                        mpesaRef != null ? mpesaRef : externalRef
                );
                smsService.sendSms(payment.getUser().getSmsNumber(), confirmMsg);
                tipService.sendTodaysTipsToNewSubscriber(payment.getUser(), payment.getPlanLevel(),subscription);

                log.info("Payment SUCCESS for user: {}, ref: {}", payment.getUser().getPhone(), externalRef);

            } else if ("Failed".equalsIgnoreCase(status)) {
                payment.setStatus(Payment.PaymentStatus.FAILED);
                paymentRepository.save(payment);

                // Don't SMS on user-cancelled (ResultCode 1032)
                if (!"1032".equals(resultCode)) {
                    smsService.sendSms(payment.getUser().getSmsNumber(),
                            "BetTips: Payment failed. Please try again or contact support.");
                }

                log.warn("Payment FAILED for ref: {} ResultCode: {} Desc: {}",
                        externalRef, resultCode, response.get("ResultDesc"));
            }

        } catch (Exception e) {
            log.error("Error handling PayHero callback: {}", e.getMessage());
        }
    }

    public Map<String, String> getPaymentStatus(String reference) {
        return paymentRepository.findByCheckoutRequestId(reference)
                .map(payment -> Map.of(
                        "status", payment.getStatus().name(), // "PENDING", "SUCCESS", "FAILED"
                        "reference", reference
                ))
                .orElse(Map.of("status", "NOT_FOUND", "reference", reference));
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        phone = phone.trim().replaceAll("\\s+", "");
        if (phone.startsWith("+254")) return phone.substring(1);
        if (phone.startsWith("254"))  return phone;
        if (phone.startsWith("07") || phone.startsWith("01")) return "254" + phone.substring(1);
        return "254" + phone;
    }
}
