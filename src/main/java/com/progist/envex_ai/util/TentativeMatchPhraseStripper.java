package com.progist.envex_ai.util;

import java.util.regex.Pattern;

/**
 * 질문 기업명과 일치할 때 AI가 붙이는 "혹시 OOO를 찾으시나요?" 확인 멘트 제거.
 */
final class TentativeMatchPhraseStripper {

    private static final Pattern TENTATIVE_LINE = Pattern.compile(
            "(?m)^\\s*(?:#+\\s*|\\*{0,2})?혹시.*?(?:을|를)?\\s*찾으시나요.*$"
    );
    private static final Pattern FOLLOWUP_GUIDE = Pattern.compile(
            "(?m)^\\s*아래에\\s*(?:해당\\s*)?업체(?:의)?\\s*정보를\\s*안내해\\s*드립니다\\.?\\s*$"
    );

    private TentativeMatchPhraseStripper() {
    }

    static String stripWhenNamesAlign(String text, String userMessage, CompanyFacts resolvedCompany) {
        if (text == null || text.isBlank() || resolvedCompany == null) {
            return text;
        }
        if (!namesAlign(userMessage, resolvedCompany.companyName())) {
            return text;
        }

        String normalized = text.replace("**", "").trim();
        if (!normalized.contains("혹시") || !normalized.contains("찾으시나요")) {
            return text;
        }

        String stripped = TENTATIVE_LINE.matcher(normalized).replaceAll("");
        stripped = FOLLOWUP_GUIDE.matcher(stripped).replaceAll("");
        return stripped.replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static boolean namesAlign(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        String a = CompanyNameMatcher.normalize(left);
        String b = CompanyNameMatcher.normalize(right);
        if (a.isBlank() || b.isBlank()) {
            return false;
        }
        return a.equals(b) || a.contains(b) || b.contains(a);
    }
}
