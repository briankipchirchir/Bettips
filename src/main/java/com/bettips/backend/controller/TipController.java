package com.bettips.backend.controller;

import com.bettips.backend.dto.TipDto;
import com.bettips.backend.entity.Tip;
import com.bettips.backend.entity.User;
import com.bettips.backend.service.TipService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/tips")
@RequiredArgsConstructor
public class TipController {

    private final TipService tipService;

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
}
