package com.agentcart.auth.repository;

import com.agentcart.auth.domain.RefreshToken;

import java.util.Optional;

public interface RefreshTokenRepository {
    Optional<RefreshToken> findByToken(String token);
    void deleteByMemberId(Long memberId);
    RefreshToken save(RefreshToken token);
    void delete(RefreshToken token);
    void deleteAll();
    long count();
}