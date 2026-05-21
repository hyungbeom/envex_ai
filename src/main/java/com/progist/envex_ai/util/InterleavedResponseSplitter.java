package com.progist.envex_ai.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 카드+설명 블록 단위로 SSE 청크 분리 (프론트 스트리밍 시 순서 유지) */
public final class InterleavedResponseSplitter {

    private static final Pattern COMPANY_BLOCK = Pattern.compile(
            "(<div id=\"company\" class=\"envex-company-card\">[\\s\\S]*?</div>\\s*)([\\s\\S]*?)(?=<div id=\"company\" class=\"envex-company-card\">|$)",
            Pattern.CASE_INSENSITIVE
    );

    private InterleavedResponseSplitter() {
    }

    public static List<String> split(String interleaved) {
        if (interleaved == null || interleaved.isBlank()) {
            return List.of();
        }
        List<String> blocks = new ArrayList<>();
        Matcher matcher = COMPANY_BLOCK.matcher(interleaved);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String block = (matcher.group(1) + matcher.group(2)).trim();
            if (!block.isBlank()) {
                blocks.add(block);
            }
        }
        if (!found) {
            blocks.add(interleaved.trim());
        }
        return blocks;
    }
}
