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

    private Member buildMember(String email, String nickname) {
        return Member.builder()
                .email(email)
                .password("encodedPassword")
                .name("Test User")
                .nickname(nickname)
                .role(Role.MEMBER)
                .build();
    }

    // ── findByEmail ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByEmail - existing email returns member")
    void findByEmail_existingEmail_returnsOptionalWithMember() {
        memberRepository.save(buildMember("user@example.com", "user1"));

        Optional<Member> result = memberRepository.findByEmail("user@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("user@example.com");
        assertThat(result.get().getNickname()).isEqualTo("user1");
        assertThat(result.get().getRole()).isEqualTo(Role.MEMBER);
        assertThat(result.get().getId()).isNotNull();
    }

    @Test
    @DisplayName("findByEmail - unknown email returns empty")
    void findByEmail_unknownEmail_returnsEmpty() {
        assertThat(memberRepository.findByEmail("nobody@example.com")).isEmpty();
    }

    // ── existsByEmail ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("existsByEmail - returns true for existing email")
    void existsByEmail_existingEmail_returnsTrue() {
        memberRepository.save(buildMember("exists@example.com", "existsnick"));
        assertThat(memberRepository.existsByEmail("exists@example.com")).isTrue();
    }

    @Test
    @DisplayName("existsByEmail - returns false for unknown email")
    void existsByEmail_unknownEmail_returnsFalse() {
        assertThat(memberRepository.existsByEmail("nobody@example.com")).isFalse();
    }

    // ── existsByNickname ───────────────────────────────────────────────────────

    @Test
    @DisplayName("existsByNickname - returns true for existing nickname")
    void existsByNickname_existingNickname_returnsTrue() {
        memberRepository.save(buildMember("a@example.com", "takenname"));
        assertThat(memberRepository.existsByNickname("takenname")).isTrue();
    }

    @Test
    @DisplayName("existsByNickname - returns false for unknown nickname")
    void existsByNickname_unknownNickname_returnsFalse() {
        assertThat(memberRepository.existsByNickname("unknownnick")).isFalse();
    }

    // ── Unique constraints ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Save - duplicate email violates unique constraint")
    void save_duplicateEmail_throwsException() {
        memberRepository.saveAndFlush(buildMember("dup@example.com", "nick1"));

        assertThatThrownBy(() -> memberRepository.saveAndFlush(buildMember("dup@example.com", "nick2")))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Save - duplicate nickname violates unique constraint")
    void save_duplicateNickname_throwsException() {
        memberRepository.saveAndFlush(buildMember("a@example.com", "samename"));

        assertThatThrownBy(() -> memberRepository.saveAndFlush(buildMember("b@example.com", "samename")))
                .isInstanceOf(Exception.class);
    }

    // ── BaseTimeEntity ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Save - createdAt and updatedAt are populated automatically")
    void save_timestampsAreSet() {
        Member saved = memberRepository.save(buildMember("ts@example.com", "tsnick"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}