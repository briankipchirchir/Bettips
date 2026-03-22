package com.bettips.backend.controller;

import com.bettips.backend.dto.UserDto;
import com.bettips.backend.entity.User;
import com.bettips.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserDto> getProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userService.getUserProfile(user));
    }

    @PutMapping("/me/sms-number")
    public ResponseEntity<UserDto> updateSmsNumber(
            @AuthenticationPrincipal User user,
            @RequestParam String smsNumber) {
        return ResponseEntity.ok(userService.updateSmsNumber(user, smsNumber));
    }
}
