package com.agentcart.auth.unit;

import com.agentcart.auth.filter.JwtVerificationFilter;
import com.agentcart.auth.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class JwtVerificationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private JwtVerificationFilter filter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── Valid token ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid Bearer token - sets authentication in SecurityContext")
    void doFilter_validToken_setsAuthentication() throws Exception {
        // Given
        Claims claims = mock(Claims.class);
        given(claims.getSubject()).willReturn("user@example.com");
        given(claims.get("role", String.class)).willReturn("ROLE_MEMBER");
        given(jwtUtil.validateToken("valid.token")).willReturn(true);
        given(jwtUtil.parseClaims("valid.token")).willReturn(claims);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // When
        filter.doFilter(request, response, chain);

        // Then
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("user@example.com");
        assertThat(auth.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_MEMBER");
    }

    // ── No / invalid token ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Missing Authorization header - SecurityContext remains unauthenticated")
    void doFilter_noHeader_doesNotSetAuthentication() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // When
        filter.doFilter(request, response, chain);

        // Then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Invalid token - SecurityContext remains unauthenticated")
    void doFilter_invalidToken_doesNotSetAuthentication() throws Exception {
        // Given
        given(jwtUtil.validateToken("bad.token")).willReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // When
        filter.doFilter(request, response, chain);

        // Then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Authorization header without Bearer prefix - ignored, no authentication set")
    void doFilter_headerWithoutBearerPrefix_doesNotSetAuthentication() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // When
        filter.doFilter(request, response, chain);

        // Then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        then(jwtUtil).should(never()).validateToken(any());
    }

    // ── Filter bypass for public paths ─────────────────────────────────────────

    @Test
    @DisplayName("/api/auth/login - filter is skipped, JwtUtil is not invoked")
    void doFilter_loginPath_skipsFilter() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setServletPath("/api/auth/login");
        request.addHeader("Authorization", "Bearer some.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // When
        filter.doFilter(request, response, chain);

        // Then: filter was skipped, validateToken never called
        then(jwtUtil).should(never()).validateToken(any());
    }

    @Test
    @DisplayName("/api/auth/refresh - filter is skipped, JwtUtil is not invoked")
    void doFilter_refreshPath_skipsFilter() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        request.setServletPath("/api/auth/refresh");
        request.addHeader("Authorization", "Bearer some.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // When
        filter.doFilter(request, response, chain);

        // Then
        then(jwtUtil).should(never()).validateToken(any());
    }
}