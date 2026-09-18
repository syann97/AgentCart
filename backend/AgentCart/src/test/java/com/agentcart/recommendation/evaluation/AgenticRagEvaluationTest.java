package com.agentcart.recommendation.evaluation;

import com.agentcart.member.domain.Member;
import com.agentcart.member.domain.Role;
import com.agentcart.member.repository.MemberRepository;
import com.agentcart.order.domain.Order;
import com.agentcart.order.domain.OrderItem;
import com.agentcart.order.repository.OrderRepository;
import com.agentcart.product.domain.Product;
import com.agentcart.product.repository.ProductRepository;
import com.agentcart.recommendation.dto.AgentRecommendation;
import com.agentcart.recommendation.dto.RecommendationAgentResult;
import com.agentcart.recommendation.service.RecommendationAgentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "AGENTCART_EVALUATION_ENABLED", matches = "true")
class AgenticRagEvaluationTest {

    private static final String FIXTURE_PRODUCT = "스테인리스 냄비 3종 세트";
    private static final String FIXTURE_BRAND = "키친아트";

    @Autowired RecommendationAgentService agentService;
    @Autowired ProductRepository productRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired ObjectMapper objectMapper;
    @Autowired org.springframework.core.env.Environment environment;
    @Autowired @Qualifier("pgVectorJdbcTemplate") NamedParameterJdbcTemplate pgVectorJdbcTemplate;

    @Test
    void captureAgenticRagEvaluation() throws Exception {
        Path root = Path.of(System.getenv("AGENTCART_EVALUATION_ROOT")).toAbsolutePath().normalize();
        Path output = Path.of(System.getenv("AGENTCART_EVALUATION_OUTPUT")).toAbsolutePath().normalize();
        String agentCommit = System.getenv("AGENTCART_EVALUATION_AGENT_COMMIT");
        String runId = System.getenv("AGENTCART_EVALUATION_RUN_ID");
        RecommendationEvaluationArtifactSupport.requireValidRunId(runId, agentCommit);
        assertThat(output.startsWith(root)).isTrue();
        assertThat(output).doesNotExist();
        JsonNode queryDocument = objectMapper.readTree(
                Files.readString(root.resolve("evaluation/recommendation/v2/definition.json")));
        String querySelection = System.getenv("AGENTCART_EVALUATION_QUERY_IDS");
        Set<String> selectedQueryIds = querySelection == null || querySelection.isBlank()
                ? Set.of()
                : Set.of(querySelection.split(","));
        List<JsonNode> queries = StreamSupport.stream(queryDocument.get("queries").spliterator(), false)
                .filter(query -> selectedQueryIds.isEmpty() || selectedQueryIds.contains(query.get("id").asText()))
                .toList();
        if (!selectedQueryIds.isEmpty()) {
            assertThat(queries).hasSize(selectedQueryIds.size());
        }
        List<Product> products = productRepository.findAll().stream()
                .sorted(Comparator.comparing(Product::getId))
                .toList();
        int vectorCount = pgVectorJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_embeddings", Map.of(), Integer.class);
        int vectorProductCount = pgVectorJdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT product_id) FROM product_embeddings", Map.of(), Integer.class);

        assertThat(products).hasSize(500);
        assertThat(vectorCount).isEqualTo(500);
        assertThat(vectorProductCount).isEqualTo(500);

        Member member = memberRepository.save(Member.builder()
                .email("agent-evaluation-" + System.nanoTime() + "@agentcart.local")
                .password("evaluation-only")
                .name("Agent Evaluation")
                .nickname("agent-eval-" + System.nanoTime())
                .role(Role.MEMBER)
                .build());
        ObjectNode artifact = objectMapper.createObjectNode();
        ArrayNode evaluations = artifact.putArray("evaluations");

        try {
            artifact.put("schemaVersion", 2);
            artifact.put("kind", selectedQueryIds.isEmpty()
                    ? "real-model-agentic-rag-raw-run"
                    : "real-model-agentic-rag-focused-run");
            artifact.put("runId", runId);
            artifact.put("capturedAt", OffsetDateTime.now().toString());
            artifact.put("agentCommit", agentCommit);
            String chatProvider = environment.getRequiredProperty("spring.ai.model.chat");
            artifact.put("chatProvider", chatProvider);
            artifact.put("chatModelConfigured",
                    environment.getRequiredProperty("spring.ai." + chatProvider + ".chat.model"));
            artifact.put("embeddingModel", "bge-m3");
            artifact.put("embeddingDimensions", 1024);
            artifact.put("snapshotId", "recommendation-catalog-2026-09-15");
            artifact.set("selectedQueryIds", objectMapper.valueToTree(
                    queries.stream().map(query -> query.get("id").asText()).toList()));
            ObjectNode inputs = artifact.putObject("inputHashes");
            inputs.put("definitionSha256", fileDigest(root.resolve("evaluation/recommendation/v2/definition.json")));
            inputs.put("labelsSha256", fileDigest(root.resolve("evaluation/recommendation/v2/labels.json")));
            inputs.put("scorerSha256", fileDigest(root.resolve(
                    "scripts/evaluation/score_recommendation_run.py")));
            ObjectNode catalog = artifact.putObject("catalogSnapshot");
            catalog.put("source", "MYSQL_PRE_RUN_READ");
            catalog.put("observedAt", OffsetDateTime.now().toString());
            catalog.put("productCount", products.size());
            catalog.put("immutableFactsSha256",
                    RecommendationEvaluationArtifactSupport.immutableFactsSha256(products));
            ObjectNode runtime = artifact.putObject("runtimeSnapshot");
            runtime.put("mysqlProducts", products.size());
            runtime.put("minimumProductId", products.getFirst().getId());
            runtime.put("maximumProductId", products.getLast().getId());
            runtime.put("sortedCommaSeparatedProductIdsSha256", productIdDigest(products));
            runtime.put("pgvectorRows", vectorCount);
            runtime.put("pgvectorProductIds", vectorProductCount);
            artifact.putArray("promptSources")
                    .add("backend/AgentCart/src/main/java/com/agentcart/recommendation/service/RecommendationAgentService.java")
                    .add("backend/AgentCart/src/main/java/com/agentcart/recommendation/service/SearchCatalogService.java");

            for (JsonNode queryNode : queries) {
                String queryId = queryNode.get("id").asText();
                Order fixtureOrder = queryId.equals("C04") ? createRecentOrder(member, products) : null;
                try {
                    RecommendationAgentResult result = agentService.recommend(
                            queryNode.get("query").asText(), member.getId());
                    List<Long> resultIds = result.recommendations().stream()
                            .map(AgentRecommendation::productId).toList();
                    Map<Long, Product> observedProducts = productRepository.findAllById(resultIds).stream()
                            .collect(Collectors.toMap(Product::getId, Function.identity()));
                    evaluations.add(toEvaluation(queryNode, result, products, observedProducts,
                            OffsetDateTime.now()));
                } finally {
                    if (fixtureOrder != null) orderRepository.delete(fixtureOrder);
                }
            }
        } finally {
            orderRepository.deleteAll(orderRepository.findAll().stream()
                    .filter(order -> order.getMember().getId().equals(member.getId())).toList());
            memberRepository.delete(member);
        }

        Files.createDirectories(output.getParent());
        Files.writeString(output,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(artifact) + System.lineSeparator());
    }

    private Order createRecentOrder(Member member, List<Product> products) {
        Product product = products.stream()
                .filter(candidate -> candidate.getName().equals(FIXTURE_PRODUCT)
                        && candidate.getBrand().equals(FIXTURE_BRAND))
                .findFirst()
                .orElseThrow();
        Order order = new Order(member, product.getPrice(), member.getName(), "01000000000", "evaluation", null);
        order.addItem(new OrderItem(order, product, 1));
        return orderRepository.saveAndFlush(order);
    }

    private ObjectNode toEvaluation(JsonNode query, RecommendationAgentResult result,
                                    List<Product> snapshotProducts,
                                    Map<Long, Product> observedProducts,
                                    OffsetDateTime observedAt) {
        ObjectNode evaluation = objectMapper.createObjectNode();
        evaluation.put("queryId", query.get("id").asText());
        evaluation.put("family", query.get("family").asText());
        evaluation.put("query", query.get("query").asText());
        evaluation.put("outcome", result.outcome().name());
        evaluation.put("actionCode", result.actionCode().name());
        evaluation.put("message", result.message());
        ArrayNode results = evaluation.putArray("results");
        for (AgentRecommendation recommendation : result.recommendations()) {
            Product snapshot = snapshotProducts.stream()
                    .filter(candidate -> candidate.getId().equals(recommendation.productId()))
                    .findFirst().orElseThrow();
            Product observed = observedProducts.get(recommendation.productId());
            ObjectNode product = results.addObject();
            product.put("productId", recommendation.productId());
            product.put("name", recommendation.productName());
            product.put("brand", recommendation.brand());
            product.put("description", snapshot.getDescription());
            product.put("price", recommendation.price());
            product.put("category", recommendation.category());
            product.put("score", recommendation.score());
            product.put("reason", recommendation.reason());
            product.set("evidenceIds", objectMapper.valueToTree(recommendation.evidenceIds()));
            ObjectNode observation = product.putObject("policyObservation");
            observation.put("source", "POST_RESPONSE_DATABASE_READ");
            observation.put("observedAt", observedAt.toString());
            observation.put("productId", recommendation.productId());
            observation.put("productKey", RecommendationEvaluationArtifactSupport.stableKey(snapshot));
            if (observed == null) {
                observation.putNull("status");
                observation.putNull("stock");
            } else {
                observation.put("status", observed.getStatus().name());
                observation.put("stock", observed.getStock());
            }
        }
        evaluation.put("resultCount", result.recommendations().size());
        evaluation.put("searchCount", result.searchCount());
        evaluation.put("llmCallAttempts", result.llmCallCount());
        evaluation.put("promptTokens", result.promptTokens());
        evaluation.put("completionTokens", result.completionTokens());
        evaluation.put("totalTokens", result.totalTokens());
        evaluation.set("chatModels", objectMapper.valueToTree(result.chatModels()));
        evaluation.put("latencyMillis", result.elapsedMillis());
        return evaluation;
    }

    private String productIdDigest(List<Product> products) throws Exception {
        String ids = products.stream().map(product -> product.getId().toString())
                .reduce((left, right) -> left + "," + right).orElse("");
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(ids.getBytes(StandardCharsets.UTF_8)));
    }

    private String fileDigest(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
    }
}
