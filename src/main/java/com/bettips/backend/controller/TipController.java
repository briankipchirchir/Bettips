package com.bettips.backend.controller;

import com.bettips.backend.dto.TipDto;
import com.bettips.backend.entity.User;
import com.bettips.backend.service.TipService;
import com.bettips.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/tips")
@RequiredArgsConstructor
public class TipController {

    private final TipService tipService;
    private final UserService userService;

    // Public — free tips
    @GetMapping("/free")
    public ResponseEntity<List<TipDto>> getFreeTips(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(tipService.getFreeTips(
            date != null ? date : LocalDate.now()));
    }

    // Authenticated — today's free tips preview for home page
    @GetMapping("/today")
    public ResponseEntity<List<TipDto>> getTodayFreeTips() {
        return ResponseEntity.ok(tipService.getFreeTips(LocalDate.now()));
    }

    // Subscribers — premium tips based on plan
    @GetMapping("/premium")
    public ResponseEntity<List<TipDto>> getPremiumTips(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        User user = userService.getOrCreateUser(jwt);
        return ResponseEntity.ok(tipService.getPremiumTips(
            date != null ? date : LocalDate.now(), user));
    }
}
