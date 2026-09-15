package com.agentcart.recommendation.service;

import com.agentcart.recommendation.dto.CategoryConstraint;
import com.agentcart.recommendation.dto.CategoryTaxonomy;
import com.agentcart.recommendation.dto.ConditionSource;
import com.agentcart.recommendation.dto.InterpretationStatus;
import com.agentcart.recommendation.dto.PriceRange;
import com.agentcart.recommendation.dto.RecommendationRequestContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RecommendationRequestContextFactory {

    private static final String AMOUNT = "(\\d[\\d,]*)\\s*(만\\s*원?|원)?";
    private static final Pattern RANGE = Pattern.compile(
            AMOUNT + "\\s*(?:~|～|-|에서)\\s*" + AMOUNT + "(?:\\s*(?:사이|까지))?");
    private static final Pattern BOUND = Pattern.compile(
            "(\\d[\\d,]*)\\s*(만\\s*원?|원)\\s*(이상|이하)");

    public RecommendationRequestContext create(String query, Long memberId) {
        PriceParse price = parsePrice(query);
        CategoryConstraint category = parseCategories(query);
        InterpretationStatus status = price.conflicting()
                ? InterpretationStatus.CLARIFICATION_REQUIRED
                : InterpretationStatus.READY;
        return new RecommendationRequestContext(query, memberId, price.range(), category, status);
    }

    private PriceParse parsePrice(String query) {
        Long min = null;
        Long max = null;
        List<String> evidence = new ArrayList<>();

        Matcher rangeMatcher = RANGE.matcher(query);
        while (rangeMatcher.find()) {
            String leftUnit = rangeMatcher.group(2);
            String rightUnit = rangeMatcher.group(4);
            if (leftUnit == null && rightUnit == null) continue;
            long left = amount(rangeMatcher.group(1), leftUnit != null ? leftUnit : rightUnit);
            long right = amount(rangeMatcher.group(3), rightUnit != null ? rightUnit : leftUnit);
            min = min == null ? left : Math.max(min, left);
            max = max == null ? right : Math.min(max, right);
            evidence.add(rangeMatcher.group());
        }

        Matcher boundMatcher = BOUND.matcher(query);
        while (boundMatcher.find()) {
            if (overlapsAny(boundMatcher.start(), boundMatcher.end(), rangeMatcher(query))) continue;
            long value = amount(boundMatcher.group(1), boundMatcher.group(2));
            if ("이상".equals(boundMatcher.group(3))) {
                min = min == null ? value : Math.max(min, value);
            } else {
                max = max == null ? value : Math.min(max, value);
            }
            evidence.add(boundMatcher.group());
        }

        if (min == null && max == null) return new PriceParse(null, false);
        PriceRange range = new PriceRange(min, max, String.join(" / ", evidence), ConditionSource.EXPLICIT);
        return new PriceParse(range, min != null && max != null && min > max);
    }

    private List<int[]> rangeMatcher(String query) {
        List<int[]> spans = new ArrayList<>();
        Matcher matcher = RANGE.matcher(query);
        while (matcher.find()) spans.add(new int[]{matcher.start(), matcher.end()});
        return spans;
    }

    private boolean overlapsAny(int start, int end, List<int[]> spans) {
        return spans.stream().anyMatch(span -> start < span[1] && end > span[0]);
    }

    private long amount(String digits, String unit) {
        long value = Long.parseLong(digits.replace(",", ""));
        return unit != null && unit.replace(" ", "").startsWith("만") ? Math.multiplyExact(value, 10_000L) : value;
    }

    private CategoryConstraint parseCategories(String query) {
        List<CategoryTaxonomy.CategoryMatch> matches = CategoryTaxonomy.findExplicit(query);
        if (matches.isEmpty()) return null;
        return new CategoryConstraint(
                matches.stream().map(CategoryTaxonomy.CategoryMatch::category).distinct().toList(),
                matches.stream().map(CategoryTaxonomy.CategoryMatch::evidence).distinct().reduce((a, b) -> a + " / " + b).orElse(""),
                ConditionSource.EXPLICIT);
    }

    private record PriceParse(PriceRange range, boolean conflicting) {}
}
