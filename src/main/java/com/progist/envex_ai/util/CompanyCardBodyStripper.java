package com.progist.envex_ai.util;

import java.util.regex.Pattern;

/** 카드가 있을 때 본문에 중복되는 부스·연락처·지도 버튼 줄 제거 */
final class CompanyCardBodyStripper {

    private static final Pattern METADATA_LINE = Pattern.compile(
            "(?m)^\\s*[-*•]?\\s*\\*{0,2}(?:부스\\s*번호|연락처|홈페이지)\\*{0,2}\\s*[:：].*$\\n?"
    );
    private static final Pattern BOOTH_MAP_CTA = Pattern.compile(
            "(?m)^\\s*(?:📍\\s*)?부스\\s*지도로\\s*이동하기\\s*$\\n?"
    );
    private static final Pattern MARKDOWN_BOOTH_LINK = Pattern.compile(
            "(?m)^\\s*\\[[^\\]]*]\\([^)]*map\\?booth=[^)]+\\)\\s*$\\n?"
    );

    private CompanyCardBodyStripper() {
    }

    static String stripRedundantFields(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String stripped = text;
        stripped = METADATA_LINE.matcher(stripped).replaceAll("");
        stripped = BOOTH_MAP_CTA.matcher(stripped).replaceAll("");
        stripped = MARKDOWN_BOOTH_LINK.matcher(stripped).replaceAll("");
        return stripped.replaceAll("\\n{3,}", "\n\n").trim();
    }
}
