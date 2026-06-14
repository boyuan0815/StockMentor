package net.boyuan.stockmentor.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.boyuan.stockmentor.auth.dto.AuthUserResponse;
import net.boyuan.stockmentor.auth.dto.RegisterRequest;
import net.boyuan.stockmentor.auth.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthUserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthUserResponse login() {
        return authService.loginCurrentUser();
    }

    @GetMapping("/me")
    public AuthUserResponse me() {
        return authService.getCurrentUser();
    }
}
