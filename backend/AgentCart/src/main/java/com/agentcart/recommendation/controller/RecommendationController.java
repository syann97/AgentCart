package com.agentcart.recommendation.controller;

import com.agentcart.common.ApiResponse;
import com.agentcart.member.service.MemberService;
import com.agentcart.recommendation.dto.RecommendationHistoryResponse;
import com.agentcart.recommendation.dto.RecommendationResult;
import com.agentcart.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final MemberService memberService;

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<RecommendationHistoryResponse>>> getHistory(
            Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                recommendationService.getHistory(principal.getName())));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter stream(@RequestParam String query, Principal principal) {
        SseEmitter emitter = new SseEmitter(60_000L);

        Thread.ofVirtual().start(() -> {
            try {
                Long memberId = memberService.findByEmail(principal.getName()).getId();
                List<RecommendationResult> results = recommendationService.recommend(query, memberId);
                for (RecommendationResult result : results) {
                    emitter.send(SseEmitter.event()
                            .data(Map.of("type", "complete", "data", result),
                                    MediaType.APPLICATION_JSON));
                }
                emitter.complete();
            } catch (Exception e) {
                log.warn("SSE stream failed for query='{}': {}", query, e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}