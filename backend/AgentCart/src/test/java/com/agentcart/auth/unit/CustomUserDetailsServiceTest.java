package com.agentcart.auth.unit;

import com.agentcart.auth.service.CustomUserDetailsService;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    private Member member;

    @BeforeEach
    void setUp() {
        member = Member.builder()
                .email("user@example.com")
                .password("encodedPassword")
                .name("Test User")
                .nickname("testuser")
                .role(Role.MEMBER)
                .build();
    }

    @Test
    @DisplayName("Existing member - returns UserDetails with correct username and authorities")
    void loadUserByUsername_success() {
        // Given
        given(memberRepository.findByEmail("user@example.com")).willReturn(Optional.of(member));

        // When
        UserDetails result = userDetailsService.loadUserByUsername("user@example.com");

        // Then
        assertThat(result.getUsername()).isEqualTo("user@example.com");
        assertThat(result.getPassword()).isEqualTo("encodedPassword");
        assertThat(result.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_MEMBER");
    }

    @Test
    @DisplayName("ADMIN member - authority is ROLE_ADMIN")
    void loadUserByUsername_adminMember_returnsAdminAuthority() {
        // Given
        Member admin = Member.builder()
                .email("admin@example.com")
                .password("encodedPw")
                .name("Admin")
                .nickname("adminuser")
                .role(Role.ADMIN)
                .build();
        given(memberRepository.findByEmail("admin@example.com")).willReturn(Optional.of(admin));

        // When
        UserDetails result = userDetailsService.loadUserByUsername("admin@example.com");

        // Then
        assertThat(result.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("Unknown email - throws UsernameNotFoundException")
    void loadUserByUsername_notFound_throwsException() {
        // Given
        given(memberRepository.findByEmail("unknown@example.com")).willReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("unknown@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
