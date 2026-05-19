package com.agentcart.recommendation.service;

import com.agentcart.member.service.MemberService;
import com.agentcart.recommendation.dto.RecommendationHistoryResponse;
import com.agentcart.recommendation.repository.RecommendationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationHistoryRepository historyRepository;
    private final MemberService memberService;

    @Transactional(readOnly = true)
    public List<RecommendationHistoryResponse> getHistory(String email) {
        Long memberId = memberService.findByEmail(email).getId();
        return historyRepository.findTop20ByMemberIdOrderByRecommendedAtDesc(memberId)
                .stream()
                .map(RecommendationHistoryResponse::from)
                .toList();
    }
}