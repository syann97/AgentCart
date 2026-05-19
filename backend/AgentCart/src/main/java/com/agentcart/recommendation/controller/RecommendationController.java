package com.agentcart.recommendation.controller;

import com.agentcart.common.ApiResponse;
import com.agentcart.recommendation.dto.RecommendationHistoryResponse;
import com.agentcart.recommendation.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<RecommendationHistoryResponse>>> getHistory(
            Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                recommendationService.getHistory(principal.getName())));
    }
}