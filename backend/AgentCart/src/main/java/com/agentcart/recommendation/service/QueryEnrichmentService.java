package com.agentcart.recommendation.service;

import com.agentcart.recommendation.dto.EnrichedQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class QueryEnrichmentService {

    private static final String CACHE_PREFIX = "rec:query:";
    private static final long CACHE_TTL_MINUTES = 5;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    @Qualifier("openAiChatModel")
    private ChatModel chatModel;

    public QueryEnrichmentService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public EnrichedQuery enrich(String query) {
        String cacheKey = CACHE_PREFIX + hash(query);

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("QueryEnrichment cache hit: query='{}'", query);
            return fromJson(cached, query);
        }

        EnrichedQuery result = callLlm(query);
        cacheResult(cacheKey, result);
        return result;
    }

    private EnrichedQuery callLlm(String query) {
        if (chatModel == null) {
            log.debug("ChatModel not available — falling back to original query");
            return fallback(query);
        }
        try {
            String promptText = String.format("""
                    You are a shopping search expert.
                    Analyze the following search query and expand it with specific English product keywords.
                    Rules:
                    - Output ONLY JSON, no explanation.
                    - enrichedQuery must contain specific product names, materials, or use-case terms (e.g. "cat food dog toy pet leash" not "gift accessories smart").
                    - Avoid generic tech terms (smart, wireless, premium, portable) unless the query is specifically about electronics.
                    - Focus on the actual product category the user wants.
                    Format: {"enrichedQuery": "space separated english keywords", "categories": ["category1", "category2"]}
                    Query: %s""", query);
            String response = chatModel.call(new Prompt(promptText))
                    .getResult().getOutput().getText();
            return parseJson(response, query);
        } catch (Exception e) {
            log.warn("QueryEnrichment LLM call failed: {}", e.getMessage());
            return fallback(query);
        }
    }

    private EnrichedQuery parseJson(String json, String originalQuery) {
        if (json == null || json.isBlank()) return fallback(originalQuery);
        try {
            String trimmed = json.trim();
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start == -1 || end == -1) return fallback(originalQuery);
            return objectMapper.readValue(trimmed.substring(start, end + 1), EnrichedQuery.class);
        } catch (Exception e) {
            log.warn("QueryEnrichment JSON parsing failed — falling back: {}", e.getMessage());
            return fallback(originalQuery);
        }
    }

    private void cacheResult(String cacheKey, EnrichedQuery result) {
        try {
            redisTemplate.opsForValue().set(
                    cacheKey, objectMapper.writeValueAsString(result),
                    CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("QueryEnrichment cache write failed: {}", e.getMessage());
        }
    }

    private EnrichedQuery fromJson(String json, String originalQuery) {
        EnrichedQuery result = parseJson(json, originalQuery);
        return result;
    }

    private EnrichedQuery fallback(String query) {
        return new EnrichedQuery(query, List.of());
    }

    private String hash(String query) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(query.toLowerCase().trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(query.toLowerCase().trim().hashCode());
        }
    }
}