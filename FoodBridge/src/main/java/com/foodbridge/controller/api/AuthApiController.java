package com.foodbridge.controller.api;

import com.foodbridge.dto.RegisterForm;
import com.foodbridge.dto.UserDto;
import com.foodbridge.enums.Role;
import com.foodbridge.security.AppUserDetails;
import com.foodbridge.service.StatsService;
import com.foodbridge.service.UserService;
import com.foodbridge.dto.ImpactStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Authentication and statistics")
public class AuthApiController {

    private final UserService userService;
    private final StatsService stats;

    @Operation(summary = "Current logged-in user")
    @GetMapping("/auth/me")
    public UserDto me(@AuthenticationPrincipal AppUserDetails me) {
        return userService.get(me.getId());
    }

    @Operation(summary = "Register a donor, recipient or volunteer account (admins can never be created here)")
    @PostMapping("/auth/register")
    public ResponseEntity<UserDto> register(@Valid @RequestBody RegisterForm form) {
        if (form.getRole() == Role.ADMIN) {
            throw new com.foodbridge.exception.BusinessRuleException("This role cannot be registered.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(form));
    }

    @Operation(summary = "Public impact statistics calculated from real records")
    @GetMapping("/stats/impact")
    public ImpactStats impact() {
        return stats.impact();
    }
}
