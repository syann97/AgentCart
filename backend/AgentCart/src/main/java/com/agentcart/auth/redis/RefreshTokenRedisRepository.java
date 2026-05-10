package com.agentcart.auth.redis;

import com.agentcart.auth.domain.RefreshToken;
import com.agentcart.auth.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class RefreshTokenRedisRepository implements RefreshTokenRepository {

    private static final String MEMBER_KEY_PREFIX = "rt:member:";
    private static final String TOKEN_KEY_PREFIX = "rt:token:";

    private final StringRedisTemplate redisTemplate;
    private final long refreshTokenExpirationMs;

    public RefreshTokenRedisRepository(
            StringRedisTemplate redisTemplate,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpirationMs) {
        this.redisTemplate = redisTemplate;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        long ttlSeconds = refreshTokenExpirationMs / 1000;
        redisTemplate.opsForValue().set(
                MEMBER_KEY_PREFIX + token.getMemberId(), token.getToken(), ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(
                TOKEN_KEY_PREFIX + token.getToken(), String.valueOf(token.getMemberId()), ttlSeconds, TimeUnit.SECONDS);
        return token;
    }

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        String memberIdStr = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);
        if (memberIdStr == null) {
            return Optional.empty();
        }
        return Optional.of(RefreshToken.builder()
                .memberId(Long.parseLong(memberIdStr))
                .token(token)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpirationMs / 1000))
                .build());
    }

    @Override
    public void deleteByMemberId(Long memberId) {
        String token = redisTemplate.opsForValue().get(MEMBER_KEY_PREFIX + memberId);
        if (token != null) {
            redisTemplate.delete(TOKEN_KEY_PREFIX + token);
        }
        redisTemplate.delete(MEMBER_KEY_PREFIX + memberId);
    }

    @Override
    public void delete(RefreshToken token) {
        redisTemplate.delete(MEMBER_KEY_PREFIX + token.getMemberId());
        redisTemplate.delete(TOKEN_KEY_PREFIX + token.getToken());
    }

    @Override
    public void deleteAll() {
        deleteKeysByPattern(MEMBER_KEY_PREFIX + "*");
        deleteKeysByPattern(TOKEN_KEY_PREFIX + "*");
    }

    @Override
    public long count() {
        Set<String> keys = redisTemplate.keys(MEMBER_KEY_PREFIX + "*");
        return keys == null ? 0 : keys.size();
    }

    private void deleteKeysByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
