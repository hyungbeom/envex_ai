package com.progist.envex_ai.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 챗봇 가드레일 — 현재 욕설·비방·공격적 표현만 적용 (나머지 규칙은 비활성).
 */
public final class ChatGuardrails {

    public static final String REJECTION_MESSAGE =
            "죄송합니다. 욕설이나 비방 표현이 포함되어 있어 답변할 수 없습니다. 다시 입력해 주세요.";

    /** 욕설·비방·공격적 언행만 (프롬프트 인젝션·오프토픽 등은 미적용) */
    private static final List<Pattern> ABUSE_PATTERNS = List.of(
            Pattern.compile("(?i)(너|니|네가|넌)\\s*(바보|멍청|쓰레기|씨발|시발|병신|개새|좆|개같|미친놈|븅신)"),
            Pattern.compile("(씨발|시발|병신|개새끼|좆|지랄|닥쳐|꺼져)"),
            Pattern.compile("(장난해\\?|미쳤니\\?|똑바로\\s*안\\s*할래|너\\s*바보|짜증\\s*나니까|제대로\\s*대답해|개\\s*같이)")
    );

    private ChatGuardrails() {
    }

    /** 시스템 프롬프트용 가드레일 — 현재 비활성 */
    public static String guardrailsPrompt() {
        return "";
    }

    /** AI 본문 후처리 — 현재 비활성 */
    public static String stripMisplacedRejection(String aiText, String userMessage) {
        return aiText;
    }

    public static boolean shouldHardReject(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String normalized = userMessage.trim();
        for (Pattern pattern : ABUSE_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                return true;
            }
        }
        return false;
    }
}
