package com.agentcart.auth.dto;

import com.agentcart.member.domain.Member;

public record MemberResponse(
        Long id,
        String email,
        String name,
        String nickname,
        String role
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getName(),
                member.getNickname(),
                member.getRole().name()
        );
    }
}