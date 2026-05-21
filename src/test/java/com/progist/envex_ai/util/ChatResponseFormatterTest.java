package com.progist.envex_ai.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatResponseFormatterTest {

    @Test
    void insertsBrAfterFormalSentenceEndings() {
        String input = "1987년부터 환경기술을 연구해 온 기업입니다. 슬러지 처리 솔루션을 제공합니다.";

        String formatted = ChatResponseFormatter.formatChunk(input);

        assertTrue(formatted.contains("기업입니다.<br>"), formatted);
        assertTrue(formatted.contains("제공합니다."), formatted);
        assertFalse(formatted.contains("기업입니다. 슬러지"), formatted);
    }

    @Test
    void insertsBrWhenSentencesAreConcatenatedWithoutSpace() {
        String input = "전문 기업입니다.슬러지 솔루션을 제공합니다.";

        String formatted = ChatResponseFormatter.formatChunk(input);

        assertTrue(formatted.contains("기업입니다.<br>"), formatted);
    }

    @Test
    void insertsBrBeforeDashSectionAfterSentence() {
        String input = """
                토탈 솔루션을 통해 성공적인 ESG 경영을 실현합니다. - 주력 제품 및 핵심 기술
                경유 자동차 배출 가스 중 NOx 규제가 강화됨에 따라
                """;

        String formatted = ChatResponseFormatter.formatChunk(input);

        assertTrue(formatted.contains("실현합니다.<br>"), formatted);
        assertTrue(formatted.contains("<br>\n- 주력 제품"), formatted);
        assertFalse(formatted.contains("실현합니다. - 주력"), formatted);
    }

    @Test
    void insertsBrBeforeBracketSectionHeading() {
        String input = "기업 소개 문단입니다. [주력 제품 및 핵심 기술]\nNOx 분석 장비를 제공합니다.";

        String formatted = ChatResponseFormatter.formatChunk(input);

        assertTrue(formatted.contains("문단입니다.<br>"), formatted);
        assertTrue(formatted.contains("<br>\n[주력 제품"), formatted);
    }

    @Test
    void insertsBrAfterEmDashSeparator() {
        String input = "주식회사 블루원 —35년 이상의 기술경험을 바탕으로 차세대 슬러지 처리 솔루션을 개발했습니다.";

        String formatted = ChatResponseFormatter.formatChunk(input);

        assertTrue(formatted.contains("블루원<br>"), formatted);
        assertTrue(formatted.contains("35년 이상"), formatted);
        assertFalse(formatted.contains("블루원 —35년"), formatted);
    }

    @Test
    void convertsSingleNewlineWithoutDoublingExistingBr() {
        String input = "첫 줄입니다.<br>\n둘째 줄입니다.";

        String formatted = ChatResponseFormatter.formatChunk(input);

        assertFalse(formatted.contains("<br>\n<br>"), formatted);
    }

    @Test
    void skipsCompanyCardHtml() {
        String card = "<div id=\"company\" class=\"envex-company-card\">Booth</div>";

        assertTrue(ChatResponseFormatter.formatChunk(card).equals(card));
    }
}
