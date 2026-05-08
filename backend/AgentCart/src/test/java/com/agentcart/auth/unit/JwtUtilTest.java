package com.agentcart.auth.unit;

import com.agentcart.auth.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JwtUtilTest {

    private static final String SECRET = "test-secret-key-minimum-32-chars-for-hs256";
    private static final long ACCESS_EXPIRY = 1800000L;
    private static final long REFRESH_EXPIRY = 604800000L;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, ACCESS_EXPIRY, REFRESH_EXPIRY);
    }

    // ── Access token ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Access token contains correct email, role and type claims")
    void generateAccessToken_containsCorrectClaims() {
        // Given / When
        String token = jwtUtil.generateAccessToken("user@example.com", "ROLE_MEMBER");

        // Then
        Claims claims = jwtUtil.parseClaims(token);
        assertThat(claims.getSubject()).isEqualTo("user@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("ROLE_MEMBER");
        assertThat(claims.get("type", String.class)).isEqualTo("access");
    }

    @Test
    @DisplayName("Access token is valid right after creation")
    void generateAccessToken_isValid() {
        String token = jwtUtil.generateAccessToken("user@example.com", "ROLE_MEMBER");
        assertThat(jwtUtil.validateToken(token)).isTrue();
    }

    // ── Refresh token ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Refresh token contains correct subject and type claim")
    void generateRefreshToken_containsCorrectClaims() {
        // Given / When
        String token = jwtUtil.generateRefreshToken("user@example.com");

        // Then
        Claims claims = jwtUtil.parseClaims(token);
        assertThat(claims.getSubject()).isEqualTo("user@example.com");
        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
    }

    @Test
    @DisplayName("Refresh token is valid right after creation")
    void generateRefreshToken_isValid() {
        String token = jwtUtil.generateRefreshToken("user@example.com");
        assertThat(jwtUtil.validateToken(token)).isTrue();
    }

    // ── Token validation ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Tampered token fails validation")
    void validateToken_returnsFalse_forTamperedToken() {
        assertThat(jwtUtil.validateToken("tampered.token.here")).isFalse();
    }

    @Test
    @DisplayName("Empty string fails validation")
    void validateToken_returnsFalse_forEmptyString() {
        assertThat(jwtUtil.validateToken("")).isFalse();
    }

    @Test
    @DisplayName("Expired token fails validation")
    void validateToken_returnsFalse_forExpiredToken() throws InterruptedException {
        // Given: 1 ms expiry
        JwtUtil shortLived = new JwtUtil(SECRET, 1L, 1L);
        String token = shortLived.generateAccessToken("user@example.com", "ROLE_MEMBER");

        Thread.sleep(10);

        // Then
        assertThat(shortLived.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("Token signed with a different secret fails validation")
    void validateToken_returnsFalse_forTokenSignedWithDifferentSecret() {
        JwtUtil other = new JwtUtil("other-secret-key-minimum-32-chars-for-hs256", ACCESS_EXPIRY, REFRESH_EXPIRY);
        String foreign = other.generateAccessToken("user@example.com", "ROLE_MEMBER");

        assertThat(jwtUtil.validateToken(foreign)).isFalse();
    }

    // ── Constructor guard ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Constructor rejects secret shorter than 32 bytes")
    void constructor_shortSecret_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new JwtUtil("too-short", ACCESS_EXPIRY, REFRESH_EXPIRY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }

    // ── Expiry getters ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAccessTokenExpiration returns configured value")
    void getAccessTokenExpiration_returnsConfiguredValue() {
        assertThat(jwtUtil.getAccessTokenExpiration()).isEqualTo(ACCESS_EXPIRY);
    }

    @Test
    @DisplayName("getRefreshTokenExpiration returns configured value")
    void getRefreshTokenExpiration_returnsConfiguredValue() {
        assertThat(jwtUtil.getRefreshTokenExpiration()).isEqualTo(REFRESH_EXPIRY);
    }
}
