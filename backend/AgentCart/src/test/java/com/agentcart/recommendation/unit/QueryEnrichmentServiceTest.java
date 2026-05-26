package com.agentcart.recommendation.unit;

import com.agentcart.recommendation.dto.EnrichedQuery;
import com.agentcart.recommendation.service.QueryEnrichmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryEnrichmentServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock ChatModel chatModel;
    @Mock ChatResponse chatResponse;
    @Mock Generation generation;
    @Mock AssistantMessage assistantMessage;

    private QueryEnrichmentService service;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        service = new QueryEnrichmentService(redisTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(service, "chatModel", chatModel);
    }

    @Test
    @DisplayName("캐시 hit - LLM 호출 없이 캐시 결과 반환")
    void enrich_cacheHit_returnsCachedWithoutLlm() {
        given(valueOps.get(anyString()))
                .willReturn("{\"enrichedQuery\":\"유아 아기 장난감\",\"bm25Keywords\":\"baby toy infant\",\"categories\":[\"완구\"]}");

        EnrichedQuery result = service.enrich("돌잔치 선물");

        assertThat(result.enrichedQuery()).isEqualTo("유아 아기 장난감");
        assertThat(result.bm25Keywords()).isEqualTo("baby toy infant");
        assertThat(result.categories()).containsExactly("완구");
        verifyNoInteractions(chatModel);
    }

    @Test
    @DisplayName("캐시 miss + LLM 성공 - 결과 파싱 및 캐시 저장")
    void enrich_cacheMiss_callsLlmAndCachesResult() {
        given(valueOps.get(anyString())).willReturn(null);
        given(chatModel.call(any(Prompt.class))).willReturn(chatResponse);
        given(chatResponse.getResult()).willReturn(generation);
        given(generation.getOutput()).willReturn(assistantMessage);
        given(assistantMessage.getText())
                .willReturn("{\"enrichedQuery\":\"노트북 컴퓨터\",\"bm25Keywords\":\"laptop computer notebook\",\"categories\":[\"전자제품\"]}");

        EnrichedQuery result = service.enrich("노트북");

        assertThat(result.enrichedQuery()).isEqualTo("노트북 컴퓨터");
        assertThat(result.bm25Keywords()).isEqualTo("laptop computer notebook");
        assertThat(result.categories()).containsExactly("전자제품");
        verify(valueOps).set(anyString(), anyString(), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("LLM 실패 - 원본 쿼리로 fallback")
    void enrich_llmThrowsException_returnsOriginalQuery() {
        given(valueOps.get(anyString())).willReturn(null);
        given(chatModel.call(any(Prompt.class))).willThrow(new RuntimeException("API error"));

        EnrichedQuery result = service.enrich("노트북");

        assertThat(result.enrichedQuery()).isEqualTo("노트북");
        assertThat(result.categories()).isEmpty();
    }

    @Test
    @DisplayName("LLM 응답 JSON 파싱 실패 - 원본 쿼리로 fallback")
    void enrich_invalidJsonResponse_returnsOriginalQuery() {
        given(valueOps.get(anyString())).willReturn(null);
        given(chatModel.call(any(Prompt.class))).willReturn(chatResponse);
        given(chatResponse.getResult()).willReturn(generation);
        given(generation.getOutput()).willReturn(assistantMessage);
        given(assistantMessage.getText()).willReturn("죄송합니다, 이해하지 못했습니다.");

        EnrichedQuery result = service.enrich("노트북");

        assertThat(result.enrichedQuery()).isEqualTo("노트북");
    }

    @Test
    @DisplayName("ChatModel null - 원본 쿼리로 fallback")
    void enrich_chatModelNull_returnsOriginalQuery() {
        given(valueOps.get(anyString())).willReturn(null);
        QueryEnrichmentService serviceWithNullChat = new QueryEnrichmentService(redisTemplate, new ObjectMapper());

        EnrichedQuery result = serviceWithNullChat.enrich("노트북");

        assertThat(result.enrichedQuery()).isEqualTo("노트북");
        assertThat(result.categories()).isEmpty();
    }

    @Test
    @DisplayName("LLM 응답에 JSON 앞뒤 텍스트 포함 - JSON 부분만 파싱")
    void enrich_jsonEmbeddedInText_extractsJson() {
        given(valueOps.get(anyString())).willReturn(null);
        given(chatModel.call(any(Prompt.class))).willReturn(chatResponse);
        given(chatResponse.getResult()).willReturn(generation);
        given(generation.getOutput()).willReturn(assistantMessage);
        given(assistantMessage.getText())
                .willReturn("물론이죠!\n{\"enrichedQuery\":\"운동화 스니커즈\",\"bm25Keywords\":\"sneakers running shoes\",\"categories\":[\"신발\"]}\n감사합니다.");

        EnrichedQuery result = service.enrich("운동화");

        assertThat(result.enrichedQuery()).isEqualTo("운동화 스니커즈");
        assertThat(result.categories()).containsExactly("신발");
    }

    @Test
    @DisplayName("프롬프트에 번들 타입 추론 금지 지침 포함")
    void enrich_prompt_containsBundleTypeRestriction() {
        given(valueOps.get(anyString())).willReturn(null);
        given(chatModel.call(any(Prompt.class))).willReturn(chatResponse);
        given(chatResponse.getResult()).willReturn(generation);
        given(generation.getOutput()).willReturn(assistantMessage);
        given(assistantMessage.getText())
                .willReturn("{\"enrichedQuery\":\"사과 키위 딸기 신선 과일\",\"bm25Keywords\":\"apple kiwi strawberry fresh fruit\",\"categories\":[\"groceries\"]}");
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);

        service.enrich("과일 선물");

        verify(chatModel).call(captor.capture());
        String promptContent = captor.getValue().getInstructions().get(0).getText();
        assertThat(promptContent).contains("묶음 상품");
    }

    @Test
    @DisplayName("동일 쿼리 두 번 요청 - 두 번째는 캐시에서 반환")
    void enrich_sameQueryTwice_secondCallUsesCache() {
        given(valueOps.get(anyString()))
                .willReturn(null)
                .willReturn("{\"enrichedQuery\":\"노트북 컴퓨터\",\"bm25Keywords\":\"laptop computer\",\"categories\":[]}");
        given(chatModel.call(any(Prompt.class))).willReturn(chatResponse);
        given(chatResponse.getResult()).willReturn(generation);
        given(generation.getOutput()).willReturn(assistantMessage);
        given(assistantMessage.getText()).willReturn("{\"enrichedQuery\":\"노트북 컴퓨터\",\"bm25Keywords\":\"laptop computer\",\"categories\":[]}");

        service.enrich("노트북");
        service.enrich("노트북");

        verify(chatModel, times(1)).call(any(Prompt.class));
    }
}