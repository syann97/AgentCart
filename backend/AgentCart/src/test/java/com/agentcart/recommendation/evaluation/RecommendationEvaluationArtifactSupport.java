package com.agentcart.recommendation.evaluation;

import com.agentcart.product.domain.Product;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RecommendationEvaluationArtifactSupport {
    private static final Pattern RUN_ID = Pattern.compile(
            "agentic-rag-([0-9a-f]{7,40})-(\\d{8}T\\d{6}Z)-r([1-9]\\d*)");

    private RecommendationEvaluationArtifactSupport() {}

    static void requireValidRunId(String runId, String commit) {
        Matcher matcher = RUN_ID.matcher(runId == null ? "" : runId);
        if (!matcher.matches() || commit == null || !commit.startsWith(matcher.group(1))) {
            throw new IllegalArgumentException(
                    "run ID must contain the target commit, UTC timestamp and positive repetition");
        }
    }

    static String stableKey(Product product) {
        return product.getName() + "|" + (product.getBrand() == null ? "" : product.getBrand());
    }

    static String immutableFactsSha256(List<Product> products) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Product product : products.stream().sorted(Comparator.comparing(
                RecommendationEvaluationArtifactSupport::stableKey)).toList()) {
            update(digest, product.getName());
            update(digest, product.getBrand());
            update(digest, product.getDescription());
            update(digest, product.getPrice().stripTrailingZeros().toPlainString());
            update(digest, product.getCategory());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
