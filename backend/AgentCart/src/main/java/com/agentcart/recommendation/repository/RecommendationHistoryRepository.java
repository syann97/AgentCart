package com.agentcart.recommendation.repository;

import com.agentcart.recommendation.domain.RecommendationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecommendationHistoryRepository extends JpaRepository<RecommendationHistory, Long> {

    List<RecommendationHistory> findTop20ByMemberIdOrderByRecommendedAtDesc(Long memberId);
}