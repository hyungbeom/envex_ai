package com.progist.envex_ai.util;

import java.util.regex.Pattern;

/** 단일 업체 답변 본문의 부스 번호를 카드·DB와 동일하게 맞춥니다. */
final class BoothNumberCorrector {

    private static final Pattern BOOTH_LINE = Pattern.compile(
            "(?i)(부스\\s*번호\\s*[:：]\\s*)([A-Z][A-Z0-9\\-]*)"
    );

    private BoothNumberCorrector() {
    }

    static String alignWithResolvedBooth(String text, CompanyFacts facts) {
        if (text == null || text.isBlank() || facts == null || facts.boothNumber() == null || facts.boothNumber().isBlank()) {
            return text;
        }
        String canonical = facts.boothNumber().trim();
        return BOOTH_LINE.matcher(text).replaceAll("$1" + canonical);
    }
}
