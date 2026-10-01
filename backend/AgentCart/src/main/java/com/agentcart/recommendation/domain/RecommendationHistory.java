package com.agentcart.recommendation.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "recommendation_history", uniqueConstraints =
        @UniqueConstraint(name = "uk_recommendation_history_event_id", columnNames = "event_id"))
@Getter
@NoArgsConstructor
public class RecommendationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 255)
    private String eventId;

    @Column(nullable = false)
    private Long memberId;

    @Column(length = 500)
    private String query;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String productName;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private LocalDateTime recommendedAt;

    public static RecommendationHistory of(Long memberId, String query, Long productId,
                                           String productName, String reason, double score) {
        RecommendationHistory h = new RecommendationHistory();
        h.memberId = memberId;
        h.query = query;
        h.productId = productId;
        h.productName = productName;
        h.reason = reason;
        h.score = score;
        h.recommendedAt = LocalDateTime.now();
        return h;
    }

    public static RecommendationHistory ofEvent(String eventId, Long memberId, String query,
                                                Long productId, String productName, String reason, double score) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Recommendation eventId is required");
        }
        RecommendationHistory h = of(memberId, query, productId, productName, reason, score);
        h.eventId = eventId;
        return h;
    }
}
