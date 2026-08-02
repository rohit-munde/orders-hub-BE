package com.indiedev.orders_hub.auth.controller;

import com.indiedev.orders_hub.auth.response.AuthResponse;
import com.indiedev.orders_hub.auth.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/google")
    public AuthResponse loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequest request
    ) {
        return authService.loginWithGoogle(request.idToken(), request.serverAuthCode());
    }

    public record GoogleLoginRequest(
            @NotBlank(message = "Google ID token is required") String idToken,
            @NotBlank(message = "Google server auth code is required") String serverAuthCode
    ) {
    }
}
