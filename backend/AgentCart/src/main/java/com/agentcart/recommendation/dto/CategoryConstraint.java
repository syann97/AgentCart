package com.agentcart.recommendation.dto;

import java.util.List;

public record CategoryConstraint(List<String> categories, String evidence, ConditionSource source) {

    public CategoryConstraint {
        categories = categories == null ? List.of() : List.copyOf(categories);
        if (categories.isEmpty()) {
            throw new IllegalArgumentException("At least one category is required");
        }
        if (source == null) {
            throw new IllegalArgumentException("Category condition source is required");
        }
    }
}
