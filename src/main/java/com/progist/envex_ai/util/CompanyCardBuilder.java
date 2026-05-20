package com.progist.envex_ai.util;

/**
 * 프론트 #company / .envex-company-card 스타일용 HTML 카드 (raw HTML, 한 덩어리 전송).
 */
public final class CompanyCardBuilder {

    /** 프론트 강조 파싱용 — Booth No. : [[M02]] → [[…]] 안 문자열을 orange·bold 등으로 스타일 */
    public static final String BOOTH_MARKER_PREFIX = "[[";
    public static final String BOOTH_MARKER_SUFFIX = "]]";

    private CompanyCardBuilder() {
    }

    public static String wrapBoothMarker(String boothNumber) {
        if (boothNumber == null || boothNumber.isBlank()) {
            return "";
        }
        return BOOTH_MARKER_PREFIX + boothNumber.trim() + BOOTH_MARKER_SUFFIX;
    }

    public static String build(CompanyFacts facts) {
        if (facts == null || !facts.hasCardData()) {
            return "";
        }

        String name = attr(facts.companyName());
        String booth = attr(facts.boothNumber());
        String logo = attr(CompanyFacts.sanitizeLogoUrl(facts.logoUrl()));

        StringBuilder html = new StringBuilder();
        html.append("<div id=\"company\" class=\"envex-company-card\">\n");
        html.append("  <div class=\"envex-company-card__logo\">");
        if (!logo.isBlank()) {
            html.append("<img src=\"").append(logo).append("\" alt=\"").append(name).append(" 로고\">");
        }
        html.append("</div>\n");
        html.append("  <div class=\"envex-company-card__info\">\n");
        if (!booth.isBlank()) {
            html.append("    <p class=\"envex-company-card__booth\">Booth No. : ")
                    .append(wrapBoothMarker(booth))
                    .append("</p>\n");
        }
        if (!name.isBlank()) {
            html.append("    <p class=\"envex-company-card__name\">").append(name).append("</p>\n");
        }
        if (!booth.isBlank()) {
            html.append("    <a href=\"/map?booth=").append(booth).append("\" class=\"envex-company-card__btn\">부스 이동하기</a>\n");
        }
        html.append("  </div>\n");
        html.append("</div>");
        return html.toString();
    }

    /** 속성값에 들어갈 최소 이스케이프(따옴표·& 만) — 태그 깨짐 방지 */
    private static String attr(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replace("&", "&amp;")
                .replace("\"", "&quot;");
    }
}
