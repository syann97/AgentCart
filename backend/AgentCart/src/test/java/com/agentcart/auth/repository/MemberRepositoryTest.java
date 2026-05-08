package com.agentcart.auth.repository;

import com.agentcart.member.domain.Member;
import com.agentcart.member.domain.Role;
import com.agentcart.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
class MemberRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    private Member buildMember(String email) {
        return Member.builder()
                .email(email)
                .password("encodedPassword")
                .name("Test User")
                .role(Role.MEMBER)
                .build();
    }

    // ── findByEmail ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByEmail - existing email returns member")
    void findByEmail_existingEmail_returnsOptionalWithMember() {
        // Given
        memberRepository.save(buildMember("user@example.com"));

        // When
        Optional<Member> result = memberRepository.findByEmail("user@example.com");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("user@example.com");
        assertThat(result.get().getRole()).isEqualTo(Role.MEMBER);
        assertThat(result.get().getId()).isNotNull();
    }

    @Test
    @DisplayName("findByEmail - unknown email returns empty")
    void findByEmail_unknownEmail_returnsEmpty() {
        // When
        Optional<Member> result = memberRepository.findByEmail("nobody@example.com");

        // Then
        assertThat(result).isEmpty();
    }

    // ── Unique email constraint ────────────────────────────────────────────────

    @Test
    @DisplayName("Save - duplicate email violates unique constraint")
    void save_duplicateEmail_throwsException() {
        // Given
        memberRepository.saveAndFlush(buildMember("dup@example.com"));

        // When / Then
        assertThatThrownBy(() -> memberRepository.saveAndFlush(buildMember("dup@example.com")))
                .isInstanceOf(Exception.class);
    }

    // ── Metadata ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Save - createdAt is populated automatically")
    void save_createdAtIsSet() {
        // When
        Member saved = memberRepository.save(buildMember("ts@example.com"));

        // Then
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}