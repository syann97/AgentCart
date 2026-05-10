package com.agentcart.auth.handler;

import com.agentcart.auth.dto.LoginResponse;
import com.agentcart.auth.dto.MemberResponse;
import com.agentcart.auth.service.AuthService;
import com.agentcart.auth.util.JwtUtil;
import com.agentcart.common.ApiResponse;
import com.agentcart.member.domain.Member;
import com.agentcart.member.service.MemberService;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final AuthService authService;
    private final MemberService memberService;
    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String email = userDetails.getUsername();
        String role = userDetails.getAuthorities().iterator().next().getAuthority();

        String accessToken = jwtUtil.generateAccessToken(email, role);
        String refreshToken = authService.issueRefreshToken(email);

        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/api/auth/refresh")
                .maxAge(Duration.ofMillis(jwtUtil.getRefreshTokenExpiration()))
                .sameSite("Strict")
                .build();
        response.setHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        Member member = memberService.findByEmail(email);
        LoginResponse loginResponse = new LoginResponse(
                accessToken, "Bearer", jwtUtil.getAccessTokenExpiration(), MemberResponse.from(member));

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.ok(loginResponse));
    }
}