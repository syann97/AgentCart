package com.agentcart.recommendation.unit;

import com.agentcart.product.domain.Product;
import com.agentcart.recommendation.dto.LlmReasonResult;
import com.agentcart.recommendation.dto.SearchCandidate;
import com.agentcart.recommendation.dto.ValidatedCandidate;
import com.agentcart.recommendation.service.LlmReasoningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LlmReasoningServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private ChatResponse chatResponse;

    @Mock
    private Generation generation;

    @Mock
    private AssistantMessage assistantMessage;

    private LlmReasoningService service;

    @BeforeEach
    void setUp() {
        service = new LlmReasoningService();
        ReflectionTestUtils.setField(service, "chatModel", chatModel);
    }

    @Test
    @DisplayName("LLM 성공 - reason과 conditions 파싱")
    void generateReasons_success_parsesReasonAndConditions() {
        String llmResponse = "REASON: 고성능 노트북으로 업무에 적합합니다.\nCONDITIONS: 가벼운 무게|긴 배터리|고해상도 디스플레이";
        given(chatModel.call(any(Prompt.class))).willReturn(chatResponse);
        given(chatResponse.getResult()).willReturn(generation);
        given(generation.getOutput()).willReturn(assistantMessage);
        given(assistantMessage.getText()).willReturn(llmResponse);

        Map<Long, LlmReasonResult> result = service.generateReasons(List.of(candidate(1L)), "노트북");

        assertThat(result).containsKey(1L);
        LlmReasonResult reason = result.get(1L);
        assertThat(reason.reason()).isEqualTo("고성능 노트북으로 업무에 적합합니다.");
        assertThat(reason.conditions()).containsExactly("가벼운 무게", "긴 배터리", "고해상도 디스플레이");
        assertThat(reason.reason()).isNotBlank();
    }

    @Test
    @DisplayName("LLM 예외 발생 - fallback 반환")
    void generateReasons_llmThrowsException_returnsFallback() {
        given(chatModel.call(any(Prompt.class))).willThrow(new RuntimeException("API error"));

        Map<Long, LlmReasonResult> result = service.generateReasons(List.of(candidate(1L)), "노트북");

        assertThat(result).containsKey(1L);
        assertThat(result.get(1L).reason()).contains("전자제품 카테고리에서 검색된 상품입니다.");
        assertThat(result.get(1L).conditions()).isEmpty();
        assertThat(result.get(1L).reason()).isNotBlank();
    }

    @Test
    @DisplayName("ChatModel null - fallback 반환")
    void generateReasons_chatModelNull_returnsFallback() {
        LlmReasoningService nullChatModelService = new LlmReasoningService();

        Map<Long, LlmReasonResult> result = nullChatModelService.generateReasons(List.of(candidate(1L)), "노트북");

        assertThat(result).containsKey(1L);
        assertThat(result.get(1L).reason()).isEqualTo("전자제품 카테고리에서 검색된 상품입니다.");
    }

    @Test
    @DisplayName("LLM 응답 파싱 실패 (REASON 없음) - fallback 반환")
    void generateReasons_invalidFormat_returnsFallback() {
        given(chatModel.call(any(Prompt.class))).willReturn(chatResponse);
        given(chatResponse.getResult()).willReturn(generation);
        given(generation.getOutput()).willReturn(assistantMessage);
        given(assistantMessage.getText()).willReturn("이 상품은 좋습니다.");

        Map<Long, LlmReasonResult> result = service.generateReasons(List.of(candidate(1L)), "노트북");

        assertThat(result.get(1L).reason()).contains("카테고리에서 검색된 상품입니다.");
    }

    @Test
    @DisplayName("LLM IRRELEVANT 응답 - fallback으로 추천 이유 반환 (이중 필터링 금지)")
    void generateReasons_irrelevantResponse_returnsFallback() {
        given(chatModel.call(any(Prompt.class))).willReturn(chatResponse);
        given(chatResponse.getResult()).willReturn(generation);
        given(generation.getOutput()).willReturn(assistantMessage);
        given(assistantMessage.getText()).willReturn("IRRELEVANT");

        Map<Long, LlmReasonResult> result = service.generateReasons(List.of(candidate(1L)), "과일 선물");

        assertThat(result.get(1L).reason()).contains("카테고리에서 검색된 상품입니다.");
        assertThat(result.get(1L).conditions()).isEmpty();
    }

    @Test
    @DisplayName("여러 후보 병렬 처리 - 모두 결과 반환")
    void generateReasons_multipleCandidates_returnsAllResults() {
        String llmResponse = "REASON: 추천 이유입니다.\nCONDITIONS: 특징1|특징2";
        given(chatModel.call(any(Prompt.class))).willReturn(chatResponse);
        given(chatResponse.getResult()).willReturn(generation);
        given(generation.getOutput()).willReturn(assistantMessage);
        given(assistantMessage.getText()).willReturn(llmResponse);

        Map<Long, LlmReasonResult> result = service.generateReasons(
                List.of(candidate(1L), candidate(2L), candidate(3L)), "노트북");

        assertThat(result).hasSize(3);
        assertThat(result).containsKeys(1L, 2L, 3L);
    }

    @Test
    @DisplayName("프롬프트에 실제 가격 숫자 대신 가격대 레이블 포함 (#140 1계층)")
    void generateReasons_prompt_containsPriceTierInsteadOfRawPrice() {
        String llmResponse = "REASON: 추천 이유입니다.\nCONDITIONS: 특징1";
        given(chatModel.call(any(Prompt.class))).willReturn(chatResponse);
        given(chatResponse.getResult()).willReturn(generation);
        given(generation.getOutput()).willReturn(assistantMessage);
        given(assistantMessage.getText()).willReturn(llmResponse);

        service.generateReasons(List.of(candidate(1L)), "노트북");

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(chatModel).call(captor.capture());
        String prompt = captor.getValue().getContents();
        assertThat(prompt).contains("가격대: 중가");
        assertThat(prompt).doesNotContain("100000");
    }

    @Test
    @DisplayName("CONDITIONS의 가격 숫자 조건 필터링 (#140 2계층)")
    void generateReasons_conditionsWithRawPrice_filtered() {
        String llmResponse = "REASON: 추천 이유입니다.\nCONDITIONS: 가성비|79.99원|99,000 원|튼튼한 내구성";
        given(chatModel.call(any(Prompt.class))).willReturn(chatResponse);
        given(chatResponse.getResult()).willReturn(generation);
        given(generation.getOutput()).willReturn(assistantMessage);
        given(assistantMessage.getText()).willReturn(llmResponse);

        Map<Long, LlmReasonResult> result = service.generateReasons(List.of(candidate(1L)), "노트북");

        assertThat(result.get(1L).conditions()).containsExactly("가성비", "튼튼한 내구성");
    }

    private ValidatedCandidate candidate(long productId) {
        SearchCandidate sc = new SearchCandidate(productId, 1, 1, 0.0, 0.05);
        Product product = Product.builder()
                .name("테스트 상품").category("전자제품")
                .price(BigDecimal.valueOf(100000)).stock(10).build();
        return new ValidatedCandidate(sc, product);
    }
}