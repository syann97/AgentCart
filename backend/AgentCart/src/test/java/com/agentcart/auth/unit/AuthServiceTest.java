package com.agentcart.auth.unit;

import com.agentcart.auth.domain.RefreshToken;
import com.agentcart.auth.dto.AuthTokens;
import com.agentcart.auth.repository.RefreshTokenRepository;
import com.agentcart.auth.service.AuthService;
import com.agentcart.auth.util.JwtUtil;
import com.agentcart.exception.AuthException;
import com.agentcart.exception.ErrorCode;
import com.agentcart.member.domain.Member;
import com.agentcart.member.domain.Role;
import com.agentcart.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private Member member;

    @BeforeEach
    void setUp() {
        member = Member.builder()
                .email("user@example.com")
                .password("hashedPw")
                .name("Test User")
                .role(Role.MEMBER)
                .build();
        ReflectionTestUtils.setField(member, "id", 1L);
    }

    // ── issueRefreshToken ──────────────────────────────────────────────────────

    @Test
    @DisplayName("issueRefreshToken - deletes old token and persists new one (rotation)")
    void issueRefreshToken_rotatesAndPersistsToken() {
        // Given
        given(memberRepository.findByEmail("user@example.com")).willReturn(Optional.of(member));
        given(jwtUtil.generateRefreshToken("user@example.com")).willReturn("new-refresh-token");
        given(jwtUtil.getRefreshTokenExpiration()).willReturn(604800000L);

        // When
        String result = authService.issueRefreshToken("user@example.com");

        // Then
        then(refreshTokenRepository).should().deleteByMemberId(1L);
        then(refreshTokenRepository).should().save(argThat(rt ->
                rt.getMemberId().equals(1L) && rt.getToken().equals("new-refresh-token")));
        assertThat(result).isEqualTo("new-refresh-token");
    }

    @Test
    @DisplayName("issueRefreshToken - throws MEMBER_NOT_FOUND when member does not exist")
    void issueRefreshToken_memberNotFound_throwsAuthException() {
        // Given
        given(memberRepository.findByEmail("ghost@example.com")).willReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> authService.issueRefreshToken("ghost@example.com"))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }

    // ── refresh ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("refresh - returns new access and refresh tokens, deletes old token")
    void refresh_validToken_returnsNewPair() {
        // Given
        RefreshToken stored = RefreshToken.builder()
                .memberId(1L)
                .token("old-refresh-token")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        given(refreshTokenRepository.findByToken("old-refresh-token")).willReturn(Optional.of(stored));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(jwtUtil.generateAccessToken("user@example.com", "ROLE_MEMBER")).willReturn("new-access-token");
        given(jwtUtil.generateRefreshToken("user@example.com")).willReturn("new-refresh-token");
        given(jwtUtil.getRefreshTokenExpiration()).willReturn(604800000L);

        // When
        AuthTokens tokens = authService.refresh("old-refresh-token");

        // Then: old token is deleted (rotation)
        then(refreshTokenRepository).should().delete(stored);
        // Then: new tokens are issued
        assertThat(tokens.accessToken()).isEqualTo("new-access-token");
        assertThat(tokens.refreshToken()).isEqualTo("new-refresh-token");
        // Then: new refresh token is persisted
        then(refreshTokenRepository).should().save(argThat(rt ->
                rt.getMemberId().equals(1L) && rt.getToken().equals("new-refresh-token")));
    }

    @Test
    @DisplayName("refresh - throws INVALID_REFRESH_TOKEN when token not found in DB")
    void refresh_tokenNotFound_throwsInvalidException() {
        // Given
        given(refreshTokenRepository.findByToken("unknown-token")).willReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> authService.refresh("unknown-token"))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    @DisplayName("refresh - throws EXPIRED_REFRESH_TOKEN and deletes expired token")
    void refresh_expiredToken_throwsExpiredExceptionAndDeletesToken() {
        // Given
        RefreshToken expired = RefreshToken.builder()
                .memberId(1L)
                .token("expired-token")
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();
        given(refreshTokenRepository.findByToken("expired-token")).willReturn(Optional.of(expired));

        // When / Then
        assertThatThrownBy(() -> authService.refresh("expired-token"))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.EXPIRED_REFRESH_TOKEN));

        // Expired token must be deleted
        then(refreshTokenRepository).should().delete(expired);
    }

    @Test
    @DisplayName("refresh - throws MEMBER_NOT_FOUND when member is deleted after token was issued")
    void refresh_memberNotFound_throwsMemberNotFoundException() {
        // Given
        RefreshToken stored = RefreshToken.builder()
                .memberId(99L)
                .token("orphan-token")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        given(refreshTokenRepository.findByToken("orphan-token")).willReturn(Optional.of(stored));
        given(memberRepository.findById(99L)).willReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> authService.refresh("orphan-token"))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }
}