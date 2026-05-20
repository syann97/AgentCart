package com.agentcart.recommendation.service;

import com.agentcart.product.domain.Product;
import com.agentcart.recommendation.dto.LlmReasonResult;
import com.agentcart.recommendation.dto.ValidatedCandidate;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LlmReasoningService {

    @Autowired(required = false)
    @Qualifier("openAiChatModel")
    private ChatModel chatModel;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore semaphore = new Semaphore(3);

    public Map<Long, LlmReasonResult> generateReasons(List<ValidatedCandidate> candidates, String enrichedQuery) {
        List<CompletableFuture<Map.Entry<Long, LlmReasonResult>>> futures = candidates.stream()
                .map(c -> CompletableFuture
                        .supplyAsync(() -> doGenerate(c, enrichedQuery), executor)
                        .orTimeout(10, TimeUnit.SECONDS)
                        .exceptionally(e -> {
                            log.warn("LlmReasoningService timeout/failure for productId={}: {}",
                                    c.candidate().productId(),
                                    e.getCause() != null ? e.getCause().getMessage() : e.getClass().getSimpleName());
                            return fallbackEntry(c);
                        }))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map.Entry<Long, LlmReasonResult> doGenerate(ValidatedCandidate candidate, String enrichedQuery) {
        Long productId = candidate.candidate().productId();
        if (chatModel == null) {
            return fallbackEntry(candidate);
        }
        try {
            semaphore.acquire();
            try {
                String promptText = buildPrompt(enrichedQuery, candidate.product());
                String response = chatModel.call(new Prompt(promptText))
                        .getResult().getOutput().getText();
                return Map.entry(productId, parse(response, candidate.product()));
            } finally {
                semaphore.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallbackEntry(candidate);
        } catch (Exception e) {
            log.warn("LlmReasoningService generation failed for productId={}: {}", productId, e.getMessage());
            return fallbackEntry(candidate);
        }
    }

    private String buildPrompt(String enrichedQuery, Product product) {
        return String.format("""
                사용자 검색어: %s
                상품 정보:
                - 이름: %s
                - 설명: %s
                - 카테고리: %s
                - 브랜드: %s
                - 가격: %s원

                이 상품이 검색어와 관련이 있으면 추천 이유를 한국어로 작성하세요.
                관련이 없으면 첫 줄에 IRRELEVANT 라고만 작성하세요.
                형식:
                REASON: <추천 이유 한 문장>
                CONDITIONS: <특징1>|<특징2>|<특징3>""",
                enrichedQuery,
                product.getName(),
                product.getDescription() != null ? product.getDescription() : "",
                product.getCategory(),
                product.getBrand() != null ? product.getBrand() : "",
                product.getPrice());
    }

    private LlmReasonResult parse(String response, Product product) {
        if (response == null || response.isBlank()) {
            return fallback(product);
        }
        if (response.trim().toUpperCase().startsWith("IRRELEVANT")) {
            log.info("LlmReasoningService: IRRELEVANT product='{}'", product.getName());
            return new LlmReasonResult("", List.of(), false);
        }
        try {
            String reason = null;
            List<String> conditions = new ArrayList<>();
            for (String line : response.lines().toList()) {
                if (line.startsWith("REASON:")) {
                    reason = line.substring("REASON:".length()).trim();
                } else if (line.startsWith("CONDITIONS:")) {
                    String condStr = line.substring("CONDITIONS:".length()).trim();
                    for (String c : condStr.split("\\|")) {
                        String trimmed = c.trim();
                        if (!trimmed.isBlank()) conditions.add(trimmed);
                    }
                }
            }
            if (reason == null || reason.isBlank()) {
                return fallback(product);
            }
            return new LlmReasonResult(reason, conditions, true);
        } catch (Exception e) {
            return fallback(product);
        }
    }

    private LlmReasonResult fallback(Product product) {
        return new LlmReasonResult(product.getCategory() + " 카테고리에서 검색된 상품입니다.", List.of(), true);
    }

    private Map.Entry<Long, LlmReasonResult> fallbackEntry(ValidatedCandidate candidate) {
        return Map.entry(candidate.candidate().productId(), fallback(candidate.product()));
    }

    @PreDestroy
    void shutdownExecutor() {
        executor.shutdown();
    }
}