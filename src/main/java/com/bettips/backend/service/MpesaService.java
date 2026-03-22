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
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MpesaService {

    private final PaymentRepository paymentRepository;
    private final SubscriptionService subscriptionService;
    private final SmsService smsService;
    private final TipService tipService;

    @Value("${daraja.consumer-key}")
    private String consumerKey;

    @Value("${daraja.consumer-secret}")
    private String consumerSecret;

    @Value("${daraja.passkey}")
    private String passkey;

    @Value("${daraja.shortcode}")
    private String shortcode;

    @Value("${daraja.callback-url}")
    private String callbackUrl;

    @Value("${daraja.base-url}")
    private String baseUrl;

    private final WebClient webClient = WebClient.builder().build();

    private String getAccessToken() {
        String credentials = Base64.getEncoder()
            .encodeToString((consumerKey + ":" + consumerSecret).getBytes());
        Map<?, ?> response = webClient.get()
            .uri(baseUrl + "/oauth/v1/generate?grant_type=client_credentials")
            .header("Authorization", "Basic " + credentials)
            .retrieve()
            .bodyToMono(Map.class)
            .block();
        return response != null ? (String) response.get("access_token") : null;
    }

    @Transactional
    public String initiateStk(User user, String mpesaPhone, String smsPhone,
                               Subscription.PlanLevel planLevel,
                               Subscription.Duration duration) {
        int amount = subscriptionService.getPrice(planLevel, duration);
        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String password = Base64.getEncoder().encodeToString(
            (shortcode + passkey + timestamp).getBytes());

        Payment payment = Payment.builder()
            .user(user)
            .amount(amount)
            .phoneNumber(mpesaPhone)
            .planLevel(planLevel)
            .duration(duration)
            .status(Payment.PaymentStatus.PENDING)
            .build();
        paymentRepository.save(payment);

        Map<String, Object> body = new HashMap<>();
        body.put("BusinessShortCode", shortcode);
        body.put("Password", password);
        body.put("Timestamp", timestamp);
        body.put("TransactionType", "CustomerPayBillOnline");
        body.put("Amount", amount);
        body.put("PartyA", normalizePhone(mpesaPhone));
        body.put("PartyB", shortcode);
        body.put("PhoneNumber", normalizePhone(mpesaPhone));
        body.put("CallBackURL", callbackUrl);
        body.put("AccountReference", "BetTips");
        body.put("TransactionDesc", planLevel.name() + " - " + duration.name());

        try {
            String token = getAccessToken();
            Map<?, ?> response = webClient.post()
                .uri(baseUrl + "/mpesa/stkpush/v1/processrequest")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            if (response != null && "0".equals(String.valueOf(response.get("ResponseCode")))) {
                String checkoutRequestId = (String) response.get("CheckoutRequestID");
                payment.setCheckoutRequestId(checkoutRequestId);
                paymentRepository.save(payment);
                log.info("STK push sent to {}, CheckoutRequestID: {}", mpesaPhone, checkoutRequestId);
                return checkoutRequestId;
            }
        } catch (Exception e) {
            log.error("STK push failed: {}", e.getMessage());
            payment.setStatus(Payment.PaymentStatus.FAILED);
            paymentRepository.save(payment);
        }
        return null;
    }

    @Transactional
    public void handleCallback(Map<String, Object> payload) {
        try {
            Map<?, ?> body = (Map<?, ?>) payload.get("Body");
            Map<?, ?> stkCallback = (Map<?, ?>) body.get("stkCallback");
            String checkoutRequestId = (String) stkCallback.get("CheckoutRequestID");
            int resultCode = ((Number) stkCallback.get("ResultCode")).intValue();

            Payment payment = paymentRepository.findByCheckoutRequestId(checkoutRequestId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + checkoutRequestId));

            if (resultCode == 0) {
                // Extract M-Pesa ref
                Map<?, ?> metadata = (Map<?, ?>) stkCallback.get("CallbackMetadata");
                java.util.List<?> items = (java.util.List<?>) metadata.get("Item");
                String mpesaRef = items.stream()
                    .filter(i -> "MpesaReceiptNumber".equals(((Map<?, ?>) i).get("Name")))
                    .map(i -> String.valueOf(((Map<?, ?>) i).get("Value")))
                    .findFirst().orElse("UNKNOWN");

                payment.setStatus(Payment.PaymentStatus.SUCCESS);
                payment.setMpesaRef(mpesaRef);
                payment.setCompletedAt(LocalDateTime.now());
                paymentRepository.save(payment);

                // 1. Activate subscription
                Subscription subscription = subscriptionService.activate(
                    payment.getUser(),
                    payment.getPlanLevel(),
                    payment.getDuration(),
                    payment.getAmount(),
                    mpesaRef
                );

                // 2. Send payment confirmation SMS
                String confirmMsg = String.format(
                    "BetTips: Payment confirmed! KSH %d received. " +
                    "Your %s plan is active until %s. Ref: %s",
                    payment.getAmount(),
                    payment.getPlanLevel().name(),
                    subscription.getEndDate().toLocalDate().toString(),
                    mpesaRef
                );
                smsService.sendSms(payment.getUser().getSmsNumber(), confirmMsg);

                // 3. Automatically send today's tips to the new subscriber
                tipService.sendTodaysTipsToNewSubscriber(
                    payment.getUser(),
                    payment.getPlanLevel()
                );

                log.info("Payment success + tips sent for user: {}, ref: {}",
                    payment.getUser().getEmail(), mpesaRef);

            } else {
                payment.setStatus(Payment.PaymentStatus.FAILED);
                paymentRepository.save(payment);

                // Notify user of failed payment
                smsService.sendSms(payment.getUser().getSmsNumber(),
                    "BetTips: Payment failed. Please try again or contact support.");

                log.warn("Payment failed for checkout: {}", checkoutRequestId);
            }
        } catch (Exception e) {
            log.error("Error handling M-Pesa callback: {}", e.getMessage());
        }
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
