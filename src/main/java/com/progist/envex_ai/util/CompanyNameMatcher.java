package com.progist.envex_ai.util;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class CompanyNameMatcher {

    private CompanyNameMatcher() {
    }

    static CompanyFacts match(List<CompanyFacts> candidates, String queryName, Set<String> usedKeys) {
        if (candidates == null || candidates.isEmpty() || queryName == null || queryName.isBlank()) {
            return null;
        }
        String normalizedQuery = normalize(queryName);
        if (normalizedQuery.isBlank()) {
            return null;
        }

        CompanyFacts best = null;
        int bestScore = 0;

        for (CompanyFacts candidate : candidates) {
            String key = CompanyFacts.factKey(candidate);
            if (usedKeys.contains(key)) {
                continue;
            }
            String normalizedCandidate = normalize(candidate.companyName());
            if (normalizedCandidate.isBlank()) {
                continue;
            }

            int score = score(normalizedQuery, normalizedCandidate);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        if (best != null && bestScore >= 2) {
            usedKeys.add(CompanyFacts.factKey(best));
            return best;
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

    static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name
                .replaceAll("\\*+", "")
                .replaceAll("\\(주\\)|（주）|주식회사|\\(유\\)|유한회사|㈜", "")
                .replaceAll("[\\s·•,]", "")
                .toLowerCase(Locale.ROOT);
    }
}
