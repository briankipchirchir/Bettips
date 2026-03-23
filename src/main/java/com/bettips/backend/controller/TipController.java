package com.bettips.backend.controller;

import com.bettips.backend.dto.TipDto;
import com.bettips.backend.entity.Tip;
import com.bettips.backend.entity.User;
import com.bettips.backend.repository.TipRepository;
import com.bettips.backend.service.TipService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/tips")
@RequiredArgsConstructor
public class TipController {

    private final TipService tipService;
    private final TipRepository tipRepository;

    @GetMapping("/free")
    public ResponseEntity<List<TipDto>> getFreeTips(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(tipService.getFreeTips(
            date != null ? date : LocalDate.now()));
    }

    @GetMapping("/today")
    public ResponseEntity<List<TipDto>> getTodayFreeTips() {
        return ResponseEntity.ok(tipService.getFreeTips(LocalDate.now()));
    }

    @GetMapping("/premium")
    public ResponseEntity<List<TipDto>> getPremiumTips(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(tipService.getPremiumTips(
            date != null ? date : LocalDate.now(), user));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateTipStatus(
            @PathVariable String id,
            @RequestParam Tip.TipStatus status) {
        return ResponseEntity.ok(tipService.updateStatus(id, status));
    }

    @GetMapping("/won")
    public ResponseEntity<List<Tip>> getWonTips(@RequestParam String filter) {
        LocalDate today = LocalDate.now();
        List<Tip> tips;

        if ("week".equals(filter)) {
            LocalDate weekStart = today.with(DayOfWeek.MONDAY);
            tips = tipRepository.findByStatusAndGameDateBetween(Tip.TipStatus.WON, weekStart, today);
        } else if ("yesterday".equals(filter)) {
            tips = tipRepository.findByStatusAndGameDate(Tip.TipStatus.WON, today.minusDays(1));
        } else {
            tips = tipRepository.findByStatusAndGameDate(Tip.TipStatus.WON, today);
        }

        return ResponseEntity.ok(tips);
    }
}
