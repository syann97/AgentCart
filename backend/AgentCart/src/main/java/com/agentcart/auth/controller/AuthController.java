package com.agentcart.auth.controller;

import com.agentcart.auth.dto.AuthTokens;
import com.agentcart.auth.dto.TokenResponse;
import com.agentcart.auth.service.AuthService;
import com.agentcart.auth.util.JwtUtil;
import com.agentcart.exception.AuthException;
import com.agentcart.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    // POST /api/auth/login is handled by JwtLoginFilter (not declared here)

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String oldRefreshToken = extractRefreshTokenCookie(request);
        AuthTokens tokens = authService.refresh(oldRefreshToken);

        // Refresh token was rotated in service — overwrite the cookie with the new token
        ResponseCookie rotatedCookie = ResponseCookie.from("refresh_token", tokens.refreshToken())
                .httpOnly(true)
                .secure(true)
                .path("/api/auth/refresh")
                .maxAge(Duration.ofMillis(jwtUtil.getRefreshTokenExpiration()))
                .sameSite("Strict")
                .build();
        response.setHeader(HttpHeaders.SET_COOKIE, rotatedCookie.toString());

        return ResponseEntity.ok(new TokenResponse(tokens.accessToken(), jwtUtil.getAccessTokenExpiration()));
    }

    private String extractRefreshTokenCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            throw new AuthException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return Arrays.stream(cookies)
                .filter(c -> "refresh_token".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_REFRESH_TOKEN));
    }
}