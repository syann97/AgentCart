package com.agentcart.auth.unit;

import com.agentcart.auth.provider.JwtAuthenticationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationProviderTest {

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private JwtAuthenticationProvider provider;

    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        userDetails = new User(
                "user@example.com",
                "hashedPassword",
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
        );
    }

    @Test
    @DisplayName("Valid credentials - returns authenticated token with authorities")
    void authenticate_validCredentials_returnsAuthenticatedToken() {
        // Given
        given(userDetailsService.loadUserByUsername("user@example.com")).willReturn(userDetails);
        given(passwordEncoder.matches("rawPassword", "hashedPassword")).willReturn(true);

        // When
        Authentication result = provider.authenticate(
                new UsernamePasswordAuthenticationToken("user@example.com", "rawPassword"));

        // Then
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getName()).isEqualTo("user@example.com");
        assertThat(result.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_MEMBER");
    }

    @Test
    @DisplayName("Wrong password - throws BadCredentialsException")
    void authenticate_wrongPassword_throwsBadCredentials() {
        // Given
        given(userDetailsService.loadUserByUsername("user@example.com")).willReturn(userDetails);
        given(passwordEncoder.matches("wrongPassword", "hashedPassword")).willReturn(false);

        // When / Then
        assertThatThrownBy(() -> provider.authenticate(
                new UsernamePasswordAuthenticationToken("user@example.com", "wrongPassword")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("Unknown email - propagates UsernameNotFoundException")
    void authenticate_unknownEmail_throwsUsernameNotFoundException() {
        // Given
        given(userDetailsService.loadUserByUsername("unknown@example.com"))
                .willThrow(new UsernameNotFoundException("not found"));

        // When / Then
        assertThatThrownBy(() -> provider.authenticate(
                new UsernamePasswordAuthenticationToken("unknown@example.com", "any")))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("Supports UsernamePasswordAuthenticationToken")
    void supports_returnsTrue_forUsernamePasswordAuthenticationToken() {
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
    }

    @Test
    @DisplayName("Does not support other authentication types")
    void supports_returnsFalse_forOtherTypes() {
        assertThat(provider.supports(Authentication.class)).isFalse();
    }
}