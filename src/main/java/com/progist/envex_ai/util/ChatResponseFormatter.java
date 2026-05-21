package com.progist.envex_ai.util;

import reactor.core.publisher.Flux;

import java.util.regex.Pattern;

/**
 * 스트리밍 청크의 줄바꿈을 프론트 마크다운에서도 보이게 보정합니다.
 * (마크다운은 단일 \n 을 공백으로 처리하므로 &lt;br&gt; 로 변환)
 */
public final class ChatResponseFormatter {

    private static final String BR = "<br>";
    private static final Pattern FORMAL_SENTENCE_WITH_SPACE = Pattern.compile(
            "((?:습니다|입니다|합니다|된다|한다|였습니다|있습니다|드립니다|주세요|세요))" +
                    "(\\.)([ \\t]+)(?!<br>)(?=[^\\n<])"
    );
    private static final Pattern FORMAL_SENTENCE_TIGHT = Pattern.compile(
            "((?:습니다|입니다|합니다|된다|한다|였습니다|있습니다|드립니다|주세요|세요))" +
                    "(\\.)(?=[가-힣A-Za-z0-9*\\-])"
    );
    /** 회사명 — 설명, 항목 — 내용 구분용 (em dash · en dash) */
    private static final Pattern EM_DASH_SEPARATOR = Pattern.compile("[ \\t]*[—–][ \\t]*");
    /** 실현합니다. - 주력 제품 및 핵심 기술 */
    private static final Pattern PERIOD_DASH_SECTION = Pattern.compile("\\.([ \\t]*)-([ \\t]*)");
    /** 문장 뒤 [회사 소개], [주력 제품 및 핵심 기술] */
    private static final Pattern INLINE_BRACKET_SECTION = Pattern.compile(
            "(?<!<br>)(?<!\n)([ \\t]+)\\[(?=[가-힣A-Za-z])"
    );
    private static final Pattern SINGLE_NEWLINE = Pattern.compile("(?<!<br>)(?<!\n)\n(?!\n)");

    private ChatResponseFormatter() {
    }

    public static Flux<String> format(Flux<String> source) {
        return source.map(ChatResponseFormatter::formatChunk);
    }

    static String formatChunk(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return chunk;
        }
        if (chunk.contains("envex-company-card") || chunk.contains("<div id=\"company\"")) {
            return chunk;
        }

        String text = chunk.replace("\r\n", "\n");
        text = insertBreaksAfterEmDash(text);
        text = insertBreaksBeforeSectionMarkers(text);
        text = insertBreaksAfterFormalSentences(text);
        return SINGLE_NEWLINE.matcher(text).replaceAll(BR + "\n");
    }

    private static String insertBreaksAfterEmDash(String text) {
        return EM_DASH_SEPARATOR.matcher(text).replaceAll(BR + "\n");
    }

    private static String insertBreaksBeforeSectionMarkers(String text) {
        String withDashSections = PERIOD_DASH_SECTION.matcher(text)
                .replaceAll("." + BR + "\n-$2");
        return INLINE_BRACKET_SECTION.matcher(withDashSections)
                .replaceAll(BR + "\n[");
    }

    private static String insertBreaksAfterFormalSentences(String text) {
        String withSpaces = FORMAL_SENTENCE_WITH_SPACE.matcher(text).replaceAll("$1$2" + BR + "\n");
        return FORMAL_SENTENCE_TIGHT.matcher(withSpaces).replaceAll("$1$2" + BR + "\n");
    }
}
