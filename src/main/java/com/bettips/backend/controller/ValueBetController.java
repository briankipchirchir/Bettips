package com.bettips.backend.controller;

import com.bettips.backend.dto.ValueBetDto;
import com.bettips.backend.entity.User;
import com.bettips.backend.entity.ValueBet;
import com.bettips.backend.service.ValueBetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/value-bets")
@RequiredArgsConstructor
public class ValueBetController {

    private final ValueBetService valueBetService;

    @GetMapping("/{category}")
    public ResponseEntity<List<ValueBetDto>> getByCategory(
            @PathVariable String category,
            @AuthenticationPrincipal User user) {
        ValueBet.Category cat = ValueBet.Category.valueOf(
            category.toUpperCase().replace("-", "_"));
        return ResponseEntity.ok(valueBetService.getByCategory(cat, user));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateValueBetStatus(
            @PathVariable String id,
            @RequestParam ValueBet.BetStatus status) {
        return ResponseEntity.ok(valueBetService.updateStatus(id, status));
    }
}
