package com.agentcart.recommendation.service.evaluator;

import com.agentcart.product.domain.Product;
import com.agentcart.recommendation.dto.SearchCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LlmCrossValidator {

    @Autowired(required = false)
    private ChatModel chatModel;

    public void validate(SearchCandidate candidate, Product product, String query) {
        if (chatModel == null) return;
        try {
            String promptText = String.format(
                    "Query: %s\nProduct: %s (%s)\nIs this product relevant to the query? Answer YES or NO only.",
                    query, product.getName(), product.getCategory());
            String response = chatModel.call(new Prompt(promptText))
                    .getResult().getOutput().getText();
            boolean relevant = response != null && response.trim().toUpperCase().startsWith("YES");
            log.info("LlmCrossValidator: productId={} relevant={} query='{}'",
                    candidate.productId(), relevant, query);
        } catch (Exception e) {
            log.warn("LlmCrossValidator failed for productId={}: {}", candidate.productId(), e.getMessage());
        }
    }
}