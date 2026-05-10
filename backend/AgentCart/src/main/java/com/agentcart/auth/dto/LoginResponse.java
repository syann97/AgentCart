package com.agentcart.auth.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        MemberResponse member
) {}