package com.progist.envex_ai.util;

import org.springframework.ai.document.Document;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * AI 본문 + 기업 카드(#company) HTML을 조합합니다.
 */
public final class CompanyResponseComposer {

    private static final int MAX_RECOMMENDATION_CARDS = 6;

    private static final Pattern MARKDOWN_LOGO = Pattern.compile(
            "!\\[[^\\]]*]\\(https?://[^)]*envex\\.or\\.kr[^)]*\\)\\s*",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EXISTING_COMPANY_CARD = Pattern.compile(
            "<div\\s+(?:id=[\"']company[\"']\\s+)?class=[\"']envex-company-card[\"'][^>]*>[\\s\\S]*?</div>\\s*",
            Pattern.CASE_INSENSITIVE
    );

    private CompanyResponseComposer() {
    }

    public record ComposedParts(String companyCardHtml, String bodyText) {
    }

    public static ComposedParts composeParts(String aiText, Document primaryDocument, String searchContext) {
        List<Document> docs = primaryDocument != null ? List.of(primaryDocument) : List.of();
        return composeParts(aiText, docs, searchContext);
    }

    public static ComposedParts composeParts(
            String aiText,
            Collection<Document> searchDocuments,
            String searchContext
    ) {
        List<CompanyFacts> distinctFacts = CompanyFacts.resolveAll(
                searchDocuments,
                searchContext,
                MAX_RECOMMENDATION_CARDS
        );

        if (distinctFacts.size() > 1) {
            String interleaved = composeInterleaved(distinctFacts, aiText, searchDocuments, searchContext);
            if (interleaved != null && !interleaved.isBlank()) {
                return new ComposedParts(interleaved, null);
            }
        }

        String cardHtml = null;
        if (distinctFacts.size() == 1) {
            cardHtml = CompanyCardBuilder.build(withAiLogo(distinctFacts.get(0), aiText));
        } else {
            CompanyFacts merged = CompanyFacts.resolve(searchDocuments, searchContext);
            List<CompanyFacts> enriched = CompanyFacts.enrichFromCatalog(
                    List.of(merged),
                    searchDocuments,
                    searchContext
            );
            if (!enriched.isEmpty()) {
                merged = enriched.get(0);
            }
            merged = withAiLogo(merged, aiText);
            if (merged.hasCardData()) {
                cardHtml = CompanyCardBuilder.build(merged);
            }
        }

        if (cardHtml != null && cardHtml.isBlank()) {
            cardHtml = null;
        }

        String body = formatBody(aiText);

        if (cardHtml == null) {
            return new ComposedParts(null, body);
        }

        return new ComposedParts(cardHtml, body);
    }

    /** 카드 → 설명 → 카드 → 설명 (업체별 interleave) */
    private static String composeInterleaved(
            List<CompanyFacts> facts,
            String aiText,
            Collection<Document> searchDocuments,
            String searchContext
    ) {
        List<CompanyFacts> enrichedFacts = CompanyFacts.enrichFromCatalog(facts, searchDocuments, searchContext);
        String cleaned = stripDuplicateCompanyChrome(aiText);
        cleaned = BoothMapLinkNormalizer.normalizeText(cleaned);
        CompanyBulletParser.ParsedAi parsed = CompanyBulletParser.parse(cleaned);

        StringBuilder out = new StringBuilder();
        Set<String> usedKeys = new HashSet<>();

        if (parsed.intro() != null && !parsed.intro().isBlank()) {
            out.append(ChatResponseFormatter.formatChunk(parsed.intro())).append("\n\n");
        }

        for (CompanyBulletParser.CompanyBullet bullet : parsed.bullets()) {
            CompanyFacts matched = CompanyNameMatcher.match(enrichedFacts, bullet.companyName(), usedKeys);
            if (matched != null) {
                matched = withAiLogo(matched, aiText);
                out.append(CompanyCardBuilder.build(matched)).append("\n\n");
            }
            String description = formatBody(bullet.description());
            if (description != null && !description.isBlank()) {
                out.append(description).append("\n\n");
            }
        }

        for (CompanyFacts fact : enrichedFacts) {
            if (!usedKeys.contains(CompanyFacts.factKey(fact))) {
                out.append(CompanyCardBuilder.build(fact)).append("\n\n");
            }
        }

        if (parsed.outro() != null && !parsed.outro().isBlank()) {
            out.append(ChatResponseFormatter.formatChunk(parsed.outro()));
        }

        return out.toString().trim();
    }

    private static String formatBody(String aiText) {
        String body = stripDuplicateCompanyChrome(aiText);
        body = BoothMapLinkNormalizer.normalizeText(body);
        body = ChatResponseFormatter.formatChunk(body);
        if (body != null) {
            body = body.trim();
        }
        if (body != null && body.isEmpty()) {
            body = null;
        }
        return body;
    }

    private static CompanyFacts withAiLogo(CompanyFacts facts, String aiText) {
        if (facts == null) {
            return CompanyFacts.fromContextText("");
        }
        String logoFromAi = CompanyFacts.extractLogoUrl(aiText);
        if ((facts.logoUrl() == null || facts.logoUrl().isBlank()) && logoFromAi != null) {
            return new CompanyFacts(facts.companyName(), logoFromAi, facts.boothNumber());
        }
        return facts;
    }

    public static String compose(String aiText, Document primaryDocument, String searchContext) {
        ComposedParts parts = composeParts(aiText, primaryDocument, searchContext);
        if (parts.companyCardHtml() == null) {
            return parts.bodyText() == null ? "" : parts.bodyText();
        }
        if (parts.bodyText() == null) {
            return parts.companyCardHtml();
        }
        return parts.companyCardHtml() + "\n\n" + parts.bodyText();
    }

    private static String stripDuplicateCompanyChrome(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String stripped = EXISTING_COMPANY_CARD.matcher(text).replaceAll("");
        stripped = MARKDOWN_LOGO.matcher(stripped).replaceAll("");
        stripped = stripped.replaceAll("(?i)<a\\b[^>]*href=[\"'][^\"']*map\\?booth=[^\"']*[\"'][^>]*>[\\s\\S]*?</a>\\s*", "");
        stripped = stripped.replaceAll("!\\[[^\\]]*]\\([^)]*map\\?booth=[^)]+\\)\\s*", "");
        return stripped.trim();
    }
}
