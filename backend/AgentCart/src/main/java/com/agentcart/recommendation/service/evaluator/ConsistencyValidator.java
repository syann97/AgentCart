package com.agentcart.recommendation.service.evaluator;

import com.agentcart.recommendation.dto.SearchCandidate;
import org.springframework.stereotype.Component;

@Component
public class ConsistencyValidator {

    public static final double MIN_RRF_SCORE = 0.01;

    public boolean validate(SearchCandidate candidate) {
        return candidate.rrfScore() > MIN_RRF_SCORE;
    }
}