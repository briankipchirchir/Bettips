package com.bettips.backend.controller;

import com.bettips.backend.dto.ValueBetDto;
import com.bettips.backend.entity.User;
import com.bettips.backend.entity.ValueBet;
import com.bettips.backend.service.UserService;
import com.bettips.backend.service.ValueBetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/value-bets")
@RequiredArgsConstructor
public class ValueBetController {

    private final ValueBetService valueBetService;
    private final UserService userService;

    @GetMapping("/{category}")
    public ResponseEntity<List<ValueBetDto>> getByCategory(
            @PathVariable String category,
            @AuthenticationPrincipal Jwt jwt) {
        User user = userService.getOrCreateUser(jwt);
        ValueBet.Category cat = ValueBet.Category.valueOf(category.toUpperCase().replace("-", "_"));
        return ResponseEntity.ok(valueBetService.getByCategory(cat, user));
    }
}
