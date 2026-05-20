package com.progist.envex_ai.util;

import org.springframework.ai.document.Document;

import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 부스 지도(/map?booth=...) 링크를 항상 HTML a 태그로 교정합니다.
 * OpenAI 스트림이 끝난 뒤 전체 텍스트를 한 번에 변환해 img 마크다운 누출을 방지합니다.
 */
public final class BoothMapLinkNormalizer {

    private static final int STREAM_CHUNK_SIZE = 24;
    private static final String STANDARD_BOOTH_LABEL = "📍 부스 지도로 이동하기";

    private static final Pattern BOOTH_URL = Pattern.compile("/?map\\?booth=[^\"'\\s>)]+", Pattern.CASE_INSENSITIVE);

    private static final Pattern HTML_IMG_BOOTH = Pattern.compile(
            "<img\\b[^>]*\\bsrc=[\"']([^\"']*map\\?booth=[^\"']+)[\"'][^>]*/?>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MARKDOWN_IMAGE_BOOTH = Pattern.compile(
            "!\\[[^\\]]*]\\([^)]*map\\?booth=[^)]+\\)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MARKDOWN_LINK_BOOTH = Pattern.compile(
            "(?<!\\!)\\[[^\\]]*]\\(([^)]*map\\?booth=[^)]+)\\)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern HTML_ANCHOR_BOOTH = Pattern.compile(
            "<a\\b[^>]*\\bhref=[\"']([^\"']*map\\?booth=[^\"']+)[\"'][^>]*>[\\s\\S]*?</a>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BOOTH_NUMBER_IN_TEXT = Pattern.compile(
            "(?i)(?:■\\s*)?부스\\s*(?:번호|위치|No\\.?)?\\s*[:：]\\s*([A-Z0-9][A-Z0-9\\-]*[A-Z0-9])"
    );
    private static final Pattern BOOTH_INLINE_MENTION = Pattern.compile(
            "(?i)부스[^\\n]{0,20}?([A-Z]\\d{1,3}(?:-[A-Z0-9]+)?)"
    );

    private BoothMapLinkNormalizer() {
    }

    /**
     * OpenAI 스트림 전체를 수집한 뒤 부스 링크·줄바꿈을 보정하고 다시 스트리밍합니다.
     *
     * @param searchContext 벡터 DB에서 가져온 회사 정보 (부스 번호 추출용)
     * @param appendBoothButton true면 부스 번호가 있을 때 버튼을 자동 추가
     */
    public static Flux<String> normalizeForClient(Flux<String> source, String searchContext, boolean appendBoothButton) {
        return normalizeForClient(source, searchContext, appendBoothButton, Collections.<Document>emptyList());
    }

    public static Flux<String> normalizeForClient(
            Flux<String> source,
            String searchContext,
            boolean appendBoothButton,
            Document primaryDocument
    ) {
        List<Document> docs = primaryDocument != null ? List.of(primaryDocument) : List.of();
        return normalizeForClient(source, searchContext, appendBoothButton, docs);
    }

    public static Flux<String> normalizeForClient(
            Flux<String> source,
            String searchContext,
            boolean appendBoothButton,
            Collection<Document> searchDocuments
    ) {
        Collection<Document> docs = searchDocuments != null ? searchDocuments : Collections.emptyList();
        return source
                .collect(StringBuilder::new, StringBuilder::append)
                .flatMapMany(sb -> toDisplayStream(sb.toString(), searchContext, appendBoothButton, docs));
    }

    public static Flux<String> normalizeForClient(Flux<String> source) {
        return normalizeForClient(source, "", false);
    }

    static Flux<String> toDisplayStream(
            String fullText,
            String searchContext,
            boolean appendBoothButton,
            Collection<Document> searchDocuments
    ) {
        CompanyResponseComposer.ComposedParts parts = CompanyResponseComposer.composeParts(
                fullText == null ? "" : fullText,
                searchDocuments,
                searchContext
        );

        List<String> chunks = new ArrayList<>();

        String body = parts.bodyText();
        if (appendBoothButton
                && parts.companyCardHtml() == null
                && (body == null || !body.toLowerCase().contains("map?booth="))) {
            body = ensureBoothButton(body == null ? "" : body, searchContext);
            if (body.isBlank()) {
                body = null;
            }
        }

        if (parts.companyCardHtml() != null && !parts.companyCardHtml().isBlank()) {
            String card = parts.companyCardHtml();
            if (body != null && !body.isBlank()) {
                card = card + "\n\n";
            }
            chunks.add(card);
        }

        if (body != null && !body.isBlank()) {
            chunks.addAll(chunk(body, STREAM_CHUNK_SIZE));
        }

        if (chunks.isEmpty()) {
            return Flux.empty();
        }
        return Flux.fromIterable(chunks);
    }

    public static String normalizeText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String normalized = text;
        normalized = replaceAll(normalized, HTML_IMG_BOOTH);
        normalized = replaceAll(normalized, MARKDOWN_IMAGE_BOOTH);
        normalized = replaceAll(normalized, MARKDOWN_LINK_BOOTH);
        normalized = replaceAll(normalized, HTML_ANCHOR_BOOTH);
        return normalized;
    }

    /**
     * AI가 부스 지도 링크를 빠뜨렸을 때, 검색 컨텍스트·답변에서 부스 번호를 찾아 버튼을 붙입니다.
     */
    static String ensureBoothButton(String text, String searchContext) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if (text.toLowerCase().contains("map?booth=")) {
            return text;
        }

        String booth = extractBoothNumber(searchContext);
        if (booth == null) {
            booth = extractBoothNumber(text);
        }
        if (booth == null || booth.isBlank()) {
            return text;
        }

        return text.trim() + "\n\n" + toAnchor(normalizeHref("/map?booth=" + booth));
    }

    private static String extractBoothNumber(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        Matcher boothMatcher = BOOTH_NUMBER_IN_TEXT.matcher(text);
        if (boothMatcher.find()) {
            return cleanBoothId(boothMatcher.group(1));
        }

        Matcher inlineMatcher = BOOTH_INLINE_MENTION.matcher(text);
        if (inlineMatcher.find()) {
            return cleanBoothId(inlineMatcher.group(1));
        }

        return null;
    }

    private static String cleanBoothId(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().replaceAll("[\\s*]+$", "").replaceAll("^[^A-Z0-9]+", "");
        cleaned = cleaned.replaceAll("[^A-Z0-9-]", "");
        return cleaned.isBlank() ? null : cleaned;
    }

    private static String replaceAll(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String href = normalizeHref(extractBoothHref(matcher.group(0), matcher.groupCount() >= 1 ? matcher.group(1) : null));
            matcher.appendReplacement(out, Matcher.quoteReplacement(toAnchor(href)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String extractBoothHref(String matched, String capturedGroup) {
        if (capturedGroup != null && capturedGroup.toLowerCase().contains("map?booth=")) {
            return capturedGroup.trim();
        }
        Matcher urlMatcher = BOOTH_URL.matcher(matched);
        return urlMatcher.find() ? urlMatcher.group() : "/map?booth=";
    }

    private static String normalizeHref(String href) {
        String value = href.trim();
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        return value;
    }

    private static String toAnchor(String href) {
        return "<a href=\"" + href + "\">" + STANDARD_BOOTH_LABEL + "</a>";
    }

    private static List<String> chunk(String text, int size) {
        List<String> chunks = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            int anchorStart = text.indexOf("<a ", index);
            if (anchorStart >= 0 && anchorStart < index + size) {
                if (anchorStart > index) {
                    chunks.add(text.substring(index, anchorStart));
                }
                int anchorEnd = text.indexOf("</a>", anchorStart);
                if (anchorEnd > 0) {
                    anchorEnd += 4;
                    chunks.add(text.substring(anchorStart, anchorEnd));
                    index = anchorEnd;
                    continue;
                }
            }

            int nextBreak = Math.min(index + size, text.length());
            int tagStart = text.indexOf('<', index);
            if (tagStart >= index && tagStart < nextBreak) {
                int tagEnd = text.indexOf('>', tagStart);
                if (tagEnd < 0 || tagEnd >= nextBreak) {
                    nextBreak = tagStart;
                }
            }

            if (nextBreak <= index) {
                nextBreak = Math.min(index + 1, text.length());
            }
            chunks.add(text.substring(index, nextBreak));
            index = nextBreak;
        }
        return chunks;
    }
}
