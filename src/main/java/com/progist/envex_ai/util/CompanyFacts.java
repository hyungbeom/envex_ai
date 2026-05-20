package com.progist.envex_ai.util;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 벡터 DB 문서에서 기업명·로고·부스 번호를 추출합니다.
 */
public record CompanyFacts(String companyName, String logoUrl, String boothNumber) {

    private static final Pattern COMPANY_NAME = Pattern.compile("■\\s*기업명:\\s*([^\\n(]+)");
    private static final Pattern LOGO_URL = Pattern.compile(
            "(?i)로고\\s*URL\\s*[:：]\\s*(https?://[^\\s)\\]]+)"
    );
    private static final Pattern ENVEX_LOGO_URL = Pattern.compile(
            "https?://[^\\s)\"']*envex\\.or\\.kr[^\\s)\"']*",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MARKDOWN_ENVEX_LOGO = Pattern.compile(
            "!\\[[^\\]]*]\\((https?://[^)]*envex\\.or\\.kr[^)]+)\\)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BOOTH_NUMBER = Pattern.compile(
            "(?i)(?:■\\s*)?부스\\s*(?:번호|위치|No\\.?)?\\s*[:：]\\s*([A-Z0-9][A-Z0-9\\-]*[A-Z0-9])"
    );

    public boolean hasCardData() {
        return companyName != null && !companyName.isBlank()
                && ((logoUrl != null && !logoUrl.isBlank()) || (boothNumber != null && !boothNumber.isBlank()));
    }

    public static CompanyFacts resolve(Collection<Document> documents, String searchContext) {
        CompanyFacts merged = empty();
        if (documents != null) {
            for (Document document : documents) {
                merged = merge(merged, fromDocument(document));
            }
        }
        return merge(merged, fromContextText(searchContext));
    }

    /** 검색 hit마다 업체 1개씩 (중복 제거, 추천·목록 질의용) */
    public static List<CompanyFacts> resolveAll(Collection<Document> documents, int maxCompanies) {
        return resolveAll(documents, null, maxCompanies);
    }

    public static List<CompanyFacts> resolveAll(
            Collection<Document> documents,
            String searchContext,
            int maxCompanies
    ) {
        LinkedHashMap<String, CompanyFacts> unique = new LinkedHashMap<>();
        if (documents == null || maxCompanies <= 0) {
            return List.of();
        }
        for (Document document : documents) {
            CompanyFacts facts = fromDocument(document);
            if (!facts.hasCardData()) {
                continue;
            }
            String key = dedupeKey(document, facts);
            unique.merge(key, facts, CompanyFacts::merge);
            if (unique.size() >= maxCompanies) {
                break;
            }
        }
        return enrichFromCatalog(List.copyOf(unique.values()), documents, searchContext);
    }

    /** 검색 문서·RAG 컨텍스트 전체에서 로고 URL 보강 */
    public static List<CompanyFacts> enrichFromCatalog(
            List<CompanyFacts> targets,
            Collection<Document> documents,
            String searchContext
    ) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }
        Catalog catalog = Catalog.build(documents, searchContext);
        List<CompanyFacts> enriched = new ArrayList<>(targets.size());
        for (CompanyFacts target : targets) {
            enriched.add(catalog.enrich(target));
        }
        return enriched;
    }

    private record Catalog(Map<String, CompanyFacts> byBooth, Map<String, CompanyFacts> byName) {

        static Catalog build(Collection<Document> documents, String searchContext) {
            Map<String, CompanyFacts> byBooth = new HashMap<>();
            Map<String, CompanyFacts> byName = new HashMap<>();
            registerAll(byBooth, byName, documents);
            if (searchContext != null && !searchContext.isBlank()) {
                for (String block : searchContext.split("\\n\\n+")) {
                    registerFact(byBooth, byName, fromContextText(block));
                }
            }
            return new Catalog(byBooth, byName);
        }

        private static void registerAll(
                Map<String, CompanyFacts> byBooth,
                Map<String, CompanyFacts> byName,
                Collection<Document> documents
        ) {
            if (documents == null) {
                return;
            }
            for (Document document : documents) {
                registerFact(byBooth, byName, fromDocument(document));
            }
        }

        private static void registerFact(
                Map<String, CompanyFacts> byBooth,
                Map<String, CompanyFacts> byName,
                CompanyFacts facts
        ) {
            if (facts == null || !facts.hasCardData()) {
                return;
            }
            if (facts.boothNumber() != null && !facts.boothNumber().isBlank()) {
                byBooth.merge(facts.boothNumber(), facts, CompanyFacts::merge);
            }
            if (facts.companyName() != null && !facts.companyName().isBlank()) {
                byName.merge(CompanyNameMatcher.normalize(facts.companyName()), facts, CompanyFacts::merge);
            }
        }

        CompanyFacts enrich(CompanyFacts target) {
            if (target == null) {
                return empty();
            }
            if (hasLogo(target)) {
                return target;
            }
            CompanyFacts merged = target;
            if (target.boothNumber() != null) {
                merged = merge(merged, byBooth.get(target.boothNumber()));
            }
            if (target.companyName() != null) {
                merged = merge(merged, byName.get(CompanyNameMatcher.normalize(target.companyName())));
            }
            return merged;
        }

        private static boolean hasLogo(CompanyFacts facts) {
            return facts.logoUrl() != null && !facts.logoUrl().isBlank();
        }
    }

    public static String factKey(CompanyFacts facts) {
        if (facts == null) {
            return "";
        }
        if (facts.boothNumber() != null && !facts.boothNumber().isBlank()) {
            return "booth:" + facts.boothNumber();
        }
        if (facts.companyName() != null && !facts.companyName().isBlank()) {
            return "name:" + facts.companyName().trim().toLowerCase(Locale.ROOT);
        }
        return "facts:" + System.identityHashCode(facts);
    }

    private static String dedupeKey(Document document, CompanyFacts facts) {
        String companyId = metadataString(document.getMetadata(), "company_id");
        if (companyId != null && !companyId.isBlank()) {
            return "id:" + companyId.trim();
        }
        if (facts.companyName() != null && !facts.companyName().isBlank()) {
            return "name:" + facts.companyName().trim().toLowerCase();
        }
        if (facts.boothNumber() != null && !facts.boothNumber().isBlank()) {
            return "booth:" + facts.boothNumber();
        }
        return "doc:" + System.identityHashCode(document);
    }

    public static CompanyFacts fromDocument(Document document) {
        if (document == null) {
            return empty();
        }

        String content = document.getContent() != null ? document.getContent() : "";
        Map<String, Object> metadata = document.getMetadata();

        String name = metadataString(metadata, "company_name");
        String logo = metadataString(metadata, "logo_url", "logoUrl", "logo");
        String booth = metadataString(metadata, "booth_number", "boothNumber", "booth");

        if (name == null || name.isBlank()) {
            name = extract(COMPANY_NAME, content, 1);
        }
        if (logo == null || logo.isBlank()) {
            logo = extractLogoUrl(content);
        }
        if (booth == null || booth.isBlank()) {
            booth = extractBooth(content);
        }

        return new CompanyFacts(trim(name), sanitizeLogoUrl(logo), cleanBooth(booth));
    }

    public static CompanyFacts fromContextText(String context) {
        if (context == null || context.isBlank()) {
            return empty();
        }
        String name = extract(COMPANY_NAME, context, 1);
        String logo = extractLogoUrl(context);
        String booth = extractBooth(context);
        return new CompanyFacts(trim(name), sanitizeLogoUrl(logo), cleanBooth(booth));
    }

    /** AI 본문·마크다운에서 envex 로고 URL 추출 (DB에 없을 때 카드용) */
    public static String extractLogoUrl(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String labeled = extract(LOGO_URL, text, 1);
        if (labeled != null && !labeled.isBlank()) {
            return sanitizeLogoUrl(labeled);
        }
        Matcher markdown = MARKDOWN_ENVEX_LOGO.matcher(text);
        if (markdown.find()) {
            return sanitizeLogoUrl(markdown.group(1));
        }
        Matcher envex = ENVEX_LOGO_URL.matcher(text);
        if (envex.find()) {
            return sanitizeLogoUrl(envex.group());
        }
        return null;
    }

    /** (로고 URL: https://...png) 처럼 뒤에 붙은 괄호·구두점 제거 */
    public static String sanitizeLogoUrl(String url) {
        if (url == null) {
            return null;
        }
        String cleaned = url.trim();
        while (!cleaned.isEmpty()) {
            char last = cleaned.charAt(cleaned.length() - 1);
            if (last == ')' || last == ']' || last == ',' || last == ';' || last == '"' || last == '\'') {
                cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
                continue;
            }
            break;
        }
        return cleaned.isBlank() ? null : cleaned;
    }

    private static CompanyFacts empty() {
        return new CompanyFacts(null, null, null);
    }

    private static CompanyFacts merge(CompanyFacts left, CompanyFacts right) {
        return new CompanyFacts(
                firstNonBlank(left.companyName(), right.companyName()),
                firstNonBlank(left.logoUrl(), right.logoUrl()),
                firstNonBlank(left.boothNumber(), right.boothNumber())
        );
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private static String metadataString(Map<String, Object> metadata, String... keys) {
        if (metadata == null) {
            return null;
        }
        for (String key : keys) {
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                if (entry.getKey() == null || !entry.getKey().equalsIgnoreCase(key)) {
                    continue;
                }
                String value = stringVal(entry.getValue());
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String extract(Pattern pattern, String text, int group) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(group) : null;
    }

    private static String extractBooth(String text) {
        Matcher matcher = BOOTH_NUMBER.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String cleanBooth(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().replaceAll("[\\s*]+$", "").replaceAll("[^A-Z0-9-]", "");
        return cleaned.isBlank() ? null : cleaned;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String stringVal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return value.toString();
    }
}
