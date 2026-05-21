package com.agentcart.recommendation.unit;

import com.agentcart.product.domain.Product;
import com.agentcart.recommendation.dto.SearchCandidate;
import com.agentcart.recommendation.service.evaluator.LlmCrossValidator;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LlmCrossValidatorTest {

    @Mock private ChatModel chatModel;
    @Mock private ChatResponse chatResponse;
    @Mock private Generation generation;
    @Mock private AssistantMessage assistantMessage;

    private LlmCrossValidator validator;

    @BeforeEach
    void setUp() {
        validator = new LlmCrossValidator();
        ReflectionTestUtils.setField(validator, "chatModel", chatModel);
    }

    @Test
    @DisplayName("LLM YES 응답 - 검증 통과")
    void validate_llmReturnsYes_returnsTrue() {
        mockLlmResponse("YES");

        boolean result = validator.validate(candidate(1L), product("사과 선물 세트", "신선한 과일 선물"), "fruit gift");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("LLM NO 응답 - 검증 실패")
    void validate_llmReturnsNo_returnsFalse() {
        mockLlmResponse("NO");

        boolean result = validator.validate(candidate(1L), product("전자제품", null), "fruit gift");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("description이 프롬프트에 포함됨")
    void validate_productWithDescription_includesDescriptionInPrompt() {
        mockLlmResponse("YES");
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);

        validator.validate(candidate(1L), product("사과", "달콤한 제주 사과 선물 세트"), "fruit gift");

        verify(chatModel).call(captor.capture());
        String promptContent = captor.getValue().getInstructions().get(0).getText();
        assertThat(promptContent).contains("달콤한 제주 사과 선물 세트");
    }

    @Test
    @DisplayName("description null - NullPointerException 없이 정상 처리")
    void validate_productWithNullDescription_handledSafely() {
        mockLlmResponse("YES");

        boolean result = validator.validate(candidate(1L), product("사과", null), "fruit gift");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("ChatModel null - fallback true 반환")
    void validate_chatModelNull_returnsTrue() {
        LlmCrossValidator nullModelValidator = new LlmCrossValidator();

        boolean result = nullModelValidator.validate(candidate(1L), product("사과", "신선한 사과"), "fruit gift");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("LLM 예외 발생 - fallback true 반환")
    void validate_llmThrowsException_returnsTrue() {
        given(chatModel.call(any(Prompt.class))).willThrow(new RuntimeException("API error"));

        boolean result = validator.validate(candidate(1L), product("사과", "신선한 사과"), "fruit gift");

        assertThat(result).isTrue();
    }

    private void mockLlmResponse(String text) {
        given(chatModel.call(any(Prompt.class))).willReturn(chatResponse);
        given(chatResponse.getResult()).willReturn(generation);
        given(generation.getOutput()).willReturn(assistantMessage);
        given(assistantMessage.getText()).willReturn(text);
    }

    private SearchCandidate candidate(long productId) {
        return new SearchCandidate(productId, 1, 1, 0.0, 0.5);
    }

    private Product product(String name, String description) {
        return Product.builder()
                .name(name).category("식품").description(description)
                .price(BigDecimal.valueOf(10000)).stock(10).build();
    }
}