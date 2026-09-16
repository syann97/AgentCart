package com.agentcart.recommendation.unit;

import com.agentcart.recommendation.config.RecommendationChatOptionsFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;

class RecommendationChatOptionsFactoryTest {

    private final ToolCallback toolCallback = mock(ToolCallback.class);

    @Test
    void create_openAi_returnsOpenAiOptionsWithTools() {
        ToolCallingChatOptions options = new RecommendationChatOptionsFactory("openai")
                .create(0.0, List.of(toolCallback), Map.of("requestId", "openai-request"));

        assertThat(options).isInstanceOf(OpenAiChatOptions.class);
        assertThat(options.getTemperature()).isZero();
        assertThat(options.getToolCallbacks()).containsExactly(toolCallback);
        assertThat(options.getToolContext()).containsEntry("requestId", "openai-request");
    }

    @Test
    void create_anthropic_returnsAnthropicOptionsWithTools() {
        ToolCallingChatOptions options = new RecommendationChatOptionsFactory("anthropic")
                .create(0.0, List.of(toolCallback), Map.of("requestId", "anthropic-request"));

        assertThat(options).isInstanceOf(AnthropicChatOptions.class);
        assertThat(options.getTemperature()).isZero();
        assertThat(options.getToolCallbacks()).containsExactly(toolCallback);
        assertThat(options.getToolContext()).containsEntry("requestId", "anthropic-request");
    }

    @Test
    void constructor_unknownProvider_rejectsConfiguration() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RecommendationChatOptionsFactory("unknown"))
                .withMessageContaining("openai")
                .withMessageContaining("anthropic");
    }
}
