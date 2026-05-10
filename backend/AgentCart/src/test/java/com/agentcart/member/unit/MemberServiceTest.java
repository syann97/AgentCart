package com.agentcart.member.unit;

import com.agentcart.auth.dto.RegisterRequest;
import com.agentcart.exception.AuthException;
import com.agentcart.exception.ErrorCode;
import com.agentcart.member.domain.Member;
import com.agentcart.member.domain.Role;
import com.agentcart.member.repository.MemberRepository;
import com.agentcart.member.service.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MemberService memberService;

    private RegisterRequest buildRequest(String email, String nickname) {
        RegisterRequest request = new RegisterRequest();
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "password", "password123");
        ReflectionTestUtils.setField(request, "name", "Test User");
        ReflectionTestUtils.setField(request, "nickname", nickname);
        return request;
    }

    @Test
    @DisplayName("register - saves member with encoded password")
    void register_validRequest_savesMember() {
        RegisterRequest request = buildRequest("user@example.com", "testnick");
        given(memberRepository.existsByEmail("user@example.com")).willReturn(false);
        given(memberRepository.existsByNickname("testnick")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("encodedPw");
        given(memberRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        Member result = memberService.register(request);

        assertThat(result.getEmail()).isEqualTo("user@example.com");
        assertThat(result.getNickname()).isEqualTo("testnick");
        assertThat(result.getPassword()).isEqualTo("encodedPw");
        assertThat(result.getRole()).isEqualTo(Role.MEMBER);
    }

    @Test
    @DisplayName("register - throws DUPLICATE_EMAIL when email already exists")
    void register_duplicateEmail_throwsException() {
        RegisterRequest request = buildRequest("dup@example.com", "nick");
        given(memberRepository.existsByEmail("dup@example.com")).willReturn(true);

        assertThatThrownBy(() -> memberService.register(request))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_EMAIL));
    }

    @Test
    @DisplayName("register - throws DUPLICATE_NICKNAME when nickname already exists")
    void register_duplicateNickname_throwsException() {
        RegisterRequest request = buildRequest("new@example.com", "taken");
        given(memberRepository.existsByEmail("new@example.com")).willReturn(false);
        given(memberRepository.existsByNickname("taken")).willReturn(true);

        assertThatThrownBy(() -> memberService.register(request))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_NICKNAME));
    }

    @Test
    @DisplayName("findByEmail - returns member when found")
    void findByEmail_existingMember_returnsMember() {
        Member member = Member.builder()
                .email("user@example.com")
                .password("pw")
                .name("Name")
                .nickname("nick")
                .role(Role.MEMBER)
                .build();
        given(memberRepository.findByEmail("user@example.com")).willReturn(Optional.of(member));

        Member result = memberService.findByEmail("user@example.com");

        assertThat(result.getEmail()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("findByEmail - throws MEMBER_NOT_FOUND when not found")
    void findByEmail_notFound_throwsException() {
        given(memberRepository.findByEmail("ghost@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.findByEmail("ghost@example.com"))
                .isInstanceOf(AuthException.class)
                .satisfies(ex -> assertThat(((AuthException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }
}