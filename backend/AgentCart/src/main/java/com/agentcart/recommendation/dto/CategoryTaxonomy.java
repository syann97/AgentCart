package com.agentcart.recommendation.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class CategoryTaxonomy {

    private static final Map<String, List<String>> ALIASES = new LinkedHashMap<>();

    static {
        ALIASES.put("캠핑·아웃도어", List.of("캠핑용품", "아웃도어용품"));
        ALIASES.put("문구·오피스", List.of("문구류", "사무용품"));
        ALIASES.put("주방용품", List.of("주방도구"));
        ALIASES.put("가전", List.of("가전제품"));
        ALIASES.put("뷰티·헬스", List.of("뷰티용품", "헬스용품"));
        ALIASES.put("패션·의류", List.of("패션의류", "의류"));
        ALIASES.put("여행용품", List.of("여행소품"));
        ALIASES.put("스포츠·피트니스", List.of("스포츠용품", "운동용품"));
        ALIASES.put("반려동물용품", List.of("반려용품", "펫용품"));
        ALIASES.put("유아동", List.of("유아용품", "아동용품"));
        ALIASES.put("디지털·IT기기", List.of("디지털기기", "IT기기"));
        ALIASES.put("생활용품", List.of("생필품"));
        ALIASES.put("인테리어·소품", List.of("인테리어소품", "인테리어 용품"));
    }

    private CategoryTaxonomy() {}

    public static List<String> names() {
        return List.copyOf(ALIASES.keySet());
    }

    public static Map<String, List<String>> aliases() {
        return Map.copyOf(ALIASES);
    }

    public static List<String> normalize(List<String> values) {
        if (values == null) return List.of();
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            canonicalName(value).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    public static java.util.Optional<String> canonicalName(String value) {
        if (value == null) return java.util.Optional.empty();
        String candidate = value.trim();
        if (ALIASES.containsKey(candidate)) return java.util.Optional.of(candidate);
        return ALIASES.entrySet().stream()
                .filter(entry -> entry.getValue().contains(candidate))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public static List<CategoryMatch> findExplicit(String query) {
        List<CategoryMatch> matches = new ArrayList<>();
        ALIASES.forEach((category, aliases) -> {
            Stream.concat(Stream.of(category), aliases.stream())
                    .filter(query::contains)
                    .max(java.util.Comparator.comparingInt(String::length))
                    .ifPresent(evidence -> matches.add(new CategoryMatch(category, evidence)));
        });
        return matches;
    }

    public record CategoryMatch(String category, String evidence) {}
}
