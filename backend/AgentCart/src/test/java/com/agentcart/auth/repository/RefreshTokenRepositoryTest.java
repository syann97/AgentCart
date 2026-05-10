package com.agentcart.auth.repository;

import com.agentcart.auth.domain.RefreshToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class RefreshTokenRepositoryTest {

    private RefreshToken buildToken(Long memberId, String tokenValue, LocalDateTime expiresAt) {
        return RefreshToken.builder()
                .memberId(memberId)
                .token(tokenValue)
                .expiresAt(expiresAt)
                .build();
    }

    @Test
    @DisplayName("isExpired - returns false for future expiresAt")
    void isExpired_futureExpiry_returnsFalse() {
        RefreshToken token = buildToken(1L, "valid", LocalDateTime.now().plusDays(7));
        assertThat(token.isExpired()).isFalse();
    }

    @Test
    @DisplayName("isExpired - returns true for past expiresAt")
    void isExpired_pastExpiry_returnsTrue() {
        RefreshToken expired = buildToken(1L, "expired", LocalDateTime.now().minusSeconds(1));
        assertThat(expired.isExpired()).isTrue();
    }

    @Test
    @DisplayName("builder - sets memberId and token correctly")
    void builder_setsFields() {
        RefreshToken token = buildToken(42L, "my-token", LocalDateTime.now().plusDays(1));
        assertThat(token.getMemberId()).isEqualTo(42L);
        assertThat(token.getToken()).isEqualTo("my-token");
    }
}