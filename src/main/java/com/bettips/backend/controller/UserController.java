package com.bettips.backend.controller;

import com.bettips.backend.dto.UserDto;
import com.bettips.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserDto> getProfile(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.getUserProfile(jwt));
    }

    @PutMapping("/me/sms-number")
    public ResponseEntity<UserDto> updateSmsNumber(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String smsNumber) {
        return ResponseEntity.ok(userService.updateSmsNumber(jwt, smsNumber));
    }
}
