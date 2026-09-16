package com.agentcart.recommendation.config;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class RecommendationChatOptionsFactory {

    private final Provider provider;

    public RecommendationChatOptionsFactory(@Value("${spring.ai.model.chat:openai}") String provider) {
        this.provider = Provider.from(provider);
    }

    public ToolCallingChatOptions create(
            double temperature, List<ToolCallback> toolCallbacks, Map<String, Object> toolContext) {
        return switch (provider) {
            case OPENAI -> OpenAiChatOptions.builder()
                    .temperature(temperature)
                    .toolCallbacks(toolCallbacks)
                    .toolContext(toolContext)
                    .build();
            case ANTHROPIC -> AnthropicChatOptions.builder()
                    .temperature(temperature)
                    .toolCallbacks(toolCallbacks)
                    .toolContext(toolContext)
                    .build();
        };
    }

    enum Provider {
        OPENAI,
        ANTHROPIC;

        static Provider from(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new IllegalArgumentException(
                        "spring.ai.model.chat must be either 'openai' or 'anthropic': " + value, e);
            }
        }
    }
}
