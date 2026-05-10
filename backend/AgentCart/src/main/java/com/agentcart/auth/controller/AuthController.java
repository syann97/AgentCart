package com.agentcart.auth.controller;

import com.agentcart.auth.dto.*;
import com.agentcart.auth.service.AuthService;
import com.agentcart.auth.util.JwtUtil;
import com.agentcart.common.ApiResponse;
import com.agentcart.exception.AuthException;
import com.agentcart.exception.ErrorCode;
import com.agentcart.member.domain.Member;
import com.agentcart.member.service.MemberService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Duration;
import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MemberService memberService;
    private final JwtUtil jwtUtil;

    // POST /api/auth/login is handled by JwtLoginFilter

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<MemberResponse>> register(@Valid @RequestBody RegisterRequest request) {
        Member member = memberService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(MemberResponse.from(member)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(HttpServletRequest request,
                                                              HttpServletResponse response) {
        String oldRefreshToken = extractRefreshTokenCookie(request);
        AuthTokens tokens = authService.refresh(oldRefreshToken);

        ResponseCookie rotatedCookie = ResponseCookie.from("refresh_token", tokens.refreshToken())
                .httpOnly(true)
                .secure(true)
                .path("/api/auth/refresh")
                .maxAge(Duration.ofMillis(jwtUtil.getRefreshTokenExpiration()))
                .sameSite("Strict")
                .build();
        response.setHeader(HttpHeaders.SET_COOKIE, rotatedCookie.toString());

        return ResponseEntity.ok(ApiResponse.ok(
                new TokenResponse(tokens.accessToken(), jwtUtil.getAccessTokenExpiration())));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(Principal principal) {
        authService.logout(principal.getName());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> me(Principal principal) {
        Member member = memberService.findByEmail(principal.getName());
        return ResponseEntity.ok(ApiResponse.ok(MemberResponse.from(member)));
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