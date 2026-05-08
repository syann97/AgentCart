package com.agentcart.auth.repository;

import com.agentcart.auth.domain.RefreshToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
class RefreshTokenRepositoryTest {

    @Autowired
    private com.agentcart.auth.repository.RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TestEntityManager em;

    private RefreshToken buildToken(Long memberId, String tokenValue) {
        return RefreshToken.builder()
                .memberId(memberId)
                .token(tokenValue)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
    }

    // ── findByToken ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByToken - existing token returns RefreshToken")
    void findByToken_existingToken_returnsOptionalWithEntity() {
        // Given
        refreshTokenRepository.save(buildToken(1L, "my-refresh-token"));

        // When
        Optional<RefreshToken> result = refreshTokenRepository.findByToken("my-refresh-token");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getMemberId()).isEqualTo(1L);
        assertThat(result.get().getToken()).isEqualTo("my-refresh-token");
        assertThat(result.get().getId()).isNotNull();
    }

    @Test
    @DisplayName("findByToken - unknown token returns empty")
    void findByToken_unknownToken_returnsEmpty() {
        Optional<RefreshToken> result = refreshTokenRepository.findByToken("ghost-token");
        assertThat(result).isEmpty();
    }

    // ── deleteByMemberId ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteByMemberId - removes all tokens for the given member")
    void deleteByMemberId_removesAllTokensForMember() {
        // Given: two tokens for member 1, one for member 2
        refreshTokenRepository.save(buildToken(1L, "token-1a"));
        refreshTokenRepository.save(buildToken(1L, "token-1b"));
        refreshTokenRepository.save(buildToken(2L, "token-2a"));
        em.flush();
        em.clear();

        // When
        refreshTokenRepository.deleteByMemberId(1L);
        em.flush();
        em.clear();

        // Then: member 1's tokens are gone, member 2's token is intact
        assertThat(refreshTokenRepository.findByToken("token-1a")).isEmpty();
        assertThat(refreshTokenRepository.findByToken("token-1b")).isEmpty();
        assertThat(refreshTokenRepository.findByToken("token-2a")).isPresent();
    }

    @Test
    @DisplayName("deleteByMemberId - no-op when member has no tokens")
    void deleteByMemberId_noTokens_doesNothing() {
        // When / Then (no exception)
        assertThatCode(() -> {
            refreshTokenRepository.deleteByMemberId(999L);
            em.flush();
        }).doesNotThrowAnyException();
    }

    // ── isExpired helper ───────────────────────────────────────────────────────

    @Test
    @DisplayName("isExpired - returns false for future expiresAt")
    void isExpired_futureExpiry_returnsFalse() {
        RefreshToken token = buildToken(1L, "valid");
        assertThat(token.isExpired()).isFalse();
    }

    @Test
    @DisplayName("isExpired - returns true for past expiresAt")
    void isExpired_pastExpiry_returnsTrue() {
        RefreshToken expired = RefreshToken.builder()
                .memberId(1L)
                .token("expired")
                .expiresAt(LocalDateTime.now().minusSeconds(1))
                .build();
        assertThat(expired.isExpired()).isTrue();
    }
}