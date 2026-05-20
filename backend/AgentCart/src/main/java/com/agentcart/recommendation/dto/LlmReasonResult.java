package com.agentcart.recommendation.dto;

import java.util.List;

public record LlmReasonResult(String reason, List<String> conditions, boolean relevant) {}