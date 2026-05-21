package com.progist.envex_ai.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 응답 본문에서 업체별 설명을 추출합니다.
 * "- **회사명** —", "**회사명** —", " - 회사명 —" 등 다양한 형식 지원.
 */
final class CompanyDescriptionExtractor {

    static final String LEADING_ORPHAN_KEY = "_leading";

    private static final Pattern INLINE_DASH = Pattern.compile(
            "\\s+-\\s*\\*{0,2}([^—\\n*]+?)\\*{0,2}\\s*(?:—|--|-)\\s*"
    );
    private static final Pattern BOLD_NAME_DESC = Pattern.compile(
            "(?m)^\\s*\\*{0,2}([^—\\n*]+?)\\*{0,2}\\s*(?:—|--|-)\\s*"
    );
    private static final Pattern LINE_BULLET = Pattern.compile(
            "(?m)^-\\s*\\*{0,2}([^—\\n]+?)\\*{0,2}\\s*(?:—|--|-)\\s*(.+?)(?=\\n-|\\n\\n|$)",
            Pattern.DOTALL
    );
    private static final Pattern OUTRO_HINT = Pattern.compile(
            "(이 업체|추가적인 정보|말씀해\\s*주세요|선택해\\s*보세요|도움이\\s*되)"
    );

    private CompanyDescriptionExtractor() {
    }

    record OrderedDescription(String factKey, String description, int textPosition) {
    }

    private record NameHit(String companyName, int nameStart, int descStart) {
    }

    /** AI 본문 등장 순서대로 업체별 설명 (동일 업체는 첫 매칭만) */
    static List<OrderedDescription> extractOrdered(String aiText, List<CompanyFacts> facts) {
        if (aiText == null || aiText.isBlank() || facts == null || facts.isEmpty()) {
            return List.of();
        }

        String text = aiText.trim();
        List<NameHit> hits = findNameHits(text, facts);
        List<OrderedDescription> ordered = new ArrayList<>();
        LinkedHashSet<String> seenKeys = new LinkedHashSet<>();

        for (int i = 0; i < hits.size(); i++) {
            NameHit hit = hits.get(i);
            int descEnd = (i + 1 < hits.size()) ? hits.get(i + 1).nameStart() : findTailEnd(text, hit.descStart());
            String desc = cleanDescription(text.substring(hit.descStart(), descEnd));
            if (desc.isBlank()) {
                continue;
            }
            String factKey = matchFactKey(hit.companyName(), facts);
            if (factKey == null || seenKeys.contains(factKey)) {
                continue;
            }
            seenKeys.add(factKey);
            ordered.add(new OrderedDescription(factKey, desc, hit.nameStart()));
        }

        Matcher lineMatcher = LINE_BULLET.matcher(text);
        while (lineMatcher.find()) {
            String name = lineMatcher.group(1).trim();
            String desc = cleanDescription(lineMatcher.group(2));
            String factKey = matchFactKey(name, facts);
            if (factKey == null || desc.isBlank() || seenKeys.contains(factKey)) {
                continue;
            }
            seenKeys.add(factKey);
            ordered.add(new OrderedDescription(factKey, desc, lineMatcher.start()));
        }

        ordered.sort((a, b) -> Integer.compare(a.textPosition(), b.textPosition()));
        return ordered;
    }

    static Map<String, String> descriptionsByFactKey(String aiText, List<CompanyFacts> facts) {
        Map<String, String> result = new LinkedHashMap<>();
        for (OrderedDescription block : extractOrdered(aiText, facts)) {
            result.putIfAbsent(block.factKey(), block.description());
        }

        String text = aiText == null ? "" : aiText.trim();
        List<NameHit> hits = findNameHits(text, facts);
        if (!hits.isEmpty() && hits.get(0).nameStart() > 0) {
            String leading = cleanDescription(text.substring(0, hits.get(0).nameStart()));
            if (!leading.isBlank() && !looksLikeOutro(leading) && !result.containsValue(leading)) {
                result.put(LEADING_ORPHAN_KEY, leading);
            }
        } else if (hits.isEmpty() && !text.isBlank() && !looksLikeOutro(text)) {
            result.put(LEADING_ORPHAN_KEY, cleanDescription(text));
        }
        return result;
    }

    static String extractOutro(String aiText, List<CompanyFacts> facts) {
        if (aiText == null || aiText.isBlank()) {
            return null;
        }
        String text = aiText.trim();
        int outroAt = indexOfOutro(text);
        if (outroAt < 0) {
            return null;
        }
        return cleanDescription(text.substring(outroAt));
    }

    private static List<NameHit> findNameHits(String text, List<CompanyFacts> facts) {
        List<NameHit> hits = new ArrayList<>();
        collectHits(hits, INLINE_DASH.matcher(text), facts);
        collectHits(hits, BOLD_NAME_DESC.matcher(text), facts);
        hits.sort((a, b) -> Integer.compare(a.nameStart(), b.nameStart()));
        return hits;
    }

    private static void collectHits(List<NameHit> hits, Matcher matcher, List<CompanyFacts> facts) {
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            if (name.isBlank() || looksLikeOutro(name) || matchFactKey(name, facts) == null) {
                continue;
            }
            hits.add(new NameHit(name, matcher.start(), matcher.end()));
        }
    }

    private static int findTailEnd(String text, int descStart) {
        int outroAt = indexOfOutro(text.substring(descStart));
        if (outroAt < 0) {
            return text.length();
        }
        return descStart + outroAt;
    }

    private static String matchFactKey(String companyName, List<CompanyFacts> facts) {
        String normalizedQuery = CompanyNameMatcher.normalize(companyName);
        if (normalizedQuery.isBlank()) {
            return null;
        }
        CompanyFacts best = null;
        int bestScore = 0;
        for (CompanyFacts fact : facts) {
            String normalizedCandidate = CompanyNameMatcher.normalize(fact.companyName());
            if (normalizedCandidate.isBlank()) {
                continue;
            }
            int score = score(normalizedQuery, normalizedCandidate);
            if (score > bestScore) {
                bestScore = score;
                best = fact;
            }
        }
        if (best != null && bestScore >= 2) {
            return CompanyFacts.factKey(best);
        }
        return null;
    }

    private static int score(String query, String candidate) {
        if (query.equals(candidate)) {
            return 100;
        }
        if (candidate.contains(query) || query.contains(candidate)) {
            return Math.min(query.length(), candidate.length()) + 10;
        }
        return 0;
    }

    private static String cleanDescription(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text
                .replaceAll("(?m)^-\\s*", "")
                .replaceAll("\\*\\*", "")
                .replaceAll("^[\\s—\\-]+", "")
                .trim();
        int outroAt = indexOfOutro(cleaned);
        if (outroAt > 0) {
            cleaned = cleaned.substring(0, outroAt).trim();
        }
        return cleaned.replaceAll("\\s{2,}", " ").trim();
    }

    private static boolean looksLikeOutro(String text) {
        return OUTRO_HINT.matcher(text).find();
    }

    private static int indexOfOutro(String text) {
        Matcher matcher = OUTRO_HINT.matcher(text);
        return matcher.find() ? matcher.start() : -1;
    }
}
