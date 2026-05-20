package com.progist.envex_ai.util;

import reactor.core.publisher.Flux;

/**
 * 스트리밍 청크의 줄바꿈을 프론트 마크다운에서도 보이게 보정합니다.
 * (마크다운은 단일 \n 을 공백으로 처리하므로 &lt;br&gt; 로 변환)
 */
public final class ChatResponseFormatter {

    private ChatResponseFormatter() {
    }

    public static Flux<String> format(Flux<String> source) {
        return source.map(ChatResponseFormatter::formatChunk);
    }

    static String formatChunk(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return chunk;
        }

        String text = chunk.replace("\r\n", "\n");
        return text.replaceAll("(?<!\n)\n(?!\n)", "<br>\n");
    }
}
