package com.agentcart.auth.service;

import com.agentcart.auth.domain.RefreshToken;
import com.agentcart.auth.dto.AuthTokens;
import com.agentcart.auth.repository.RefreshTokenRepository;
import com.agentcart.auth.util.JwtUtil;
import com.agentcart.exception.AuthException;
import com.agentcart.exception.ErrorCode;
import com.agentcart.member.domain.Member;
import com.agentcart.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public String issueRefreshToken(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(ErrorCode.MEMBER_NOT_FOUND));

        // rotate: delete any existing token before issuing a new one
        refreshTokenRepository.deleteByMemberId(member.getId());

        String token = jwtUtil.generateRefreshToken(email);
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(jwtUtil.getRefreshTokenExpiration() / 1000);

        refreshTokenRepository.save(RefreshToken.builder()
                .memberId(member.getId())
                .token(token)
                .expiresAt(expiresAt)
                .build());

        return token;
    }

    @Transactional
    public AuthTokens refresh(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw new AuthException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        Member member = memberRepository.findById(stored.getMemberId())
                .orElseThrow(() -> new AuthException(ErrorCode.MEMBER_NOT_FOUND));

        // Rotate: old token is invalidated immediately, new token issued
        // Limits replay window if a refresh token is stolen
        refreshTokenRepository.delete(stored);

        String newAccessToken = jwtUtil.generateAccessToken(member.getEmail(), "ROLE_" + member.getRole().name());
        String newRefreshToken = jwtUtil.generateRefreshToken(member.getEmail());

        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(jwtUtil.getRefreshTokenExpiration() / 1000);
        refreshTokenRepository.save(RefreshToken.builder()
                .memberId(member.getId())
                .token(newRefreshToken)
                .expiresAt(expiresAt)
                .build());

        return new AuthTokens(newAccessToken, newRefreshToken);
    }
}