package com.agentcart.recommendation.controller;

import com.agentcart.common.ApiResponse;
import com.agentcart.member.service.MemberService;
import com.agentcart.recommendation.dto.RecommendationHistoryResponse;
import com.agentcart.recommendation.service.RecommendationService;
import com.agentcart.recommendation.service.RecommendationStreamConnection;
import com.agentcart.recommendation.service.RecommendationStreamService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.Principal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@Validated
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final RecommendationStreamService streamService;
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
    public SseEmitter stream(@RequestParam @NotBlank String query, Principal principal) {
        Long memberId = memberService.findByEmail(principal.getName()).getId();
        SseEmitter emitter = new SseEmitter(60_000L);
        RecommendationStreamConnection connection = new RecommendationStreamConnection();

        emitter.onCompletion(connection::onCompletion);
        emitter.onTimeout(connection::onTimeout);
        emitter.onError(connection::onError);

        Thread.ofVirtual().start(() -> {
            try {
                streamService.stream(query, memberId, connection.cancellationToken(),
                        event -> emitter.send(SseEmitter.event().data(event, MediaType.APPLICATION_JSON)));
                emitter.complete();
            } catch (Exception e) {
                log.warn("SSE stream failed for query='{}': {}", query, e.getMessage());
                connection.cancel();
                emitter.complete();
            }
        });

        return emitter;
    }
}
