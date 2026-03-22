package com.bettips.backend.controller;

import com.bettips.backend.dto.*;
import com.bettips.backend.entity.ValueBet;
import com.bettips.backend.repository.PaymentRepository;
import com.bettips.backend.repository.SubscriptionRepository;
import com.bettips.backend.repository.UserRepository;
import com.bettips.backend.service.TipService;
import com.bettips.backend.service.ValueBetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminController {

    private final TipService tipService;
    private final ValueBetService valueBetService;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;

    // ── Dashboard Stats ──
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(Map.of(
            "totalUsers",          userRepository.count(),
            "activeSubscriptions", subscriptionRepository.countActiveSubscriptions(),
            "totalRevenue",        paymentRepository.totalRevenue() != null ? paymentRepository.totalRevenue() : 0,
            "successfulPayments",  paymentRepository.countSuccessful()
        ));
    }

    // ── Tips ──
    @GetMapping("/tips")
    public ResponseEntity<List<TipDto>> getTips(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(tipService.getAllTips(
            date != null ? date : LocalDate.now()));
    }

    // Admin posts tip → automatically sends SMS to all eligible subscribers
    @PostMapping("/tips")
    public ResponseEntity<TipDto> createTip(@Valid @RequestBody AdminTipRequestDto dto) {
        return ResponseEntity.ok(tipService.createTip(dto));
    }

    @PutMapping("/tips/{id}")
    public ResponseEntity<TipDto> updateTip(
            @PathVariable String id,
            @Valid @RequestBody AdminTipRequestDto dto) {
        return ResponseEntity.ok(tipService.updateTip(id, dto));
    }

    @DeleteMapping("/tips/{id}")
    public ResponseEntity<Void> deleteTip(@PathVariable String id) {
        tipService.deleteTip(id);
        return ResponseEntity.noContent().build();
    }

    // ── Value Bets ──
    @GetMapping("/value-bets/{category}")
    public ResponseEntity<List<ValueBetDto>> getValueBets(@PathVariable String category) {
        ValueBet.Category cat = ValueBet.Category.valueOf(
            category.toUpperCase().replace("-", "_"));
        return ResponseEntity.ok(valueBetService.getAll(cat));
    }

    // Admin posts value bet → automatically sends SMS to all active subscribers
    @PostMapping("/value-bets")
    public ResponseEntity<ValueBetDto> createValueBet(
            @Valid @RequestBody AdminValueBetRequestDto dto) {
        return ResponseEntity.ok(valueBetService.create(dto));
    }

    @PutMapping("/value-bets/{id}")
    public ResponseEntity<ValueBetDto> updateValueBet(
            @PathVariable String id,
            @Valid @RequestBody AdminValueBetRequestDto dto) {
        return ResponseEntity.ok(valueBetService.update(id, dto));
    }

    @DeleteMapping("/value-bets/{id}")
    public ResponseEntity<Void> deleteValueBet(@PathVariable String id) {
        valueBetService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Users ──
    @GetMapping("/users")
    public ResponseEntity<?> getUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    // ── Payments ──
    @GetMapping("/payments")
    public ResponseEntity<?> getPayments() {
        return ResponseEntity.ok(paymentRepository.findAll());
    }
}
