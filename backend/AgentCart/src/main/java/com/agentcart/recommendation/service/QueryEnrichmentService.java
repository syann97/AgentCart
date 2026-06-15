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

    // 상품 카탈로그의 실제 카테고리 값(고정). LLM이 임의 카테고리를 만들지 않도록 폐쇄형 분류로 제약.
    private static final String CATEGORY_TAXONOMY =
            "캠핑·아웃도어, 문구·오피스, 주방용품, 가전, 뷰티·헬스, 패션·의류, 여행용품, "
            + "스포츠·피트니스, 반려동물용품, 유아동, 디지털·IT기기, 생활용품, 인테리어·소품";

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
                    당신은 쇼핑 검색 전문가입니다.
                    아래 검색어를 분석하여 두 가지 버전의 키워드로 확장하세요.
                    규칙:
                    - JSON만 출력하세요. 설명 없이.
                    - enrichedQuery: 한국어로 의미를 확장한 키워드 (벡터 검색 및 AI 추천 이유 생성에 사용)
                    - bm25Keywords: 한국어로 의미를 확장한 키워드 (한국어 상품명/설명 키워드 매칭에 사용)
                    - 쇼핑몰 카탈로그에 실제로 존재할 법한 개별 상품명 또는 성분 수준의 키워드로 확장하세요.
                    - 검색어에 명시되지 않은 경우 묶음 상품(선물세트, 번들)으로 추론하지 마세요.
                    - 검색어가 전자기기에 관한 것이 아니라면 스마트, 무선, 프리미엄 같은 일반 수식어는 추가하지 마세요.
                    - 포장 방식이 아닌 사용자가 원하는 실제 개별 상품에 집중하세요.
                    - minPrice / maxPrice: 검색어에 가격 조건이 있으면 원(KRW) 단위 정수로 추출하세요. 예: "10만원 이하" → maxPrice 100000, "5만원 이상" → minPrice 50000, "5만~15만원" → minPrice 50000, maxPrice 150000. 가격 조건이 없으면 null로 두세요.
                    - 가격 조건 문구(예: "10만원 이하")는 enrichedQuery, bm25Keywords 키워드에 포함하지 마세요.
                    - categories: 아래 고정 카테고리 목록 중에서만 0개 이상 선택하세요. 목록에 없는 새 카테고리명을 만들지 말고, 적합한 것이 없으면 빈 배열 []로 두세요. 검색어 의미에 가장 가까운 카테고리를 고르세요(예: "여자 악세사리" → 패션·의류).
                    카테고리 목록: %s
                    형식: {"enrichedQuery": "공백으로 구분된 한국어 키워드", "bm25Keywords": "공백으로 구분된 한국어 키워드", "categories": ["패션·의류"], "minPrice": null, "maxPrice": null}
                    검색어: %s""", CATEGORY_TAXONOMY, query);
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
        return new EnrichedQuery(query, query, List.of(), null, null);
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