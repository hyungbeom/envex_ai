package com.progist.envex_ai.util;

import org.springframework.ai.document.Document;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        return composeParts(aiText, searchDocuments, searchContext, null);
    }

    public static ComposedParts composeParts(
            String aiText,
            Collection<Document> searchDocuments,
            String searchContext,
            String userMessage
    ) {
        if (userMessage != null && !CompanyQueryIntent.isCompanyRelatedQuery(userMessage, searchDocuments)) {
            return new ComposedParts(null, formatBody(aiText));
        }

        if (CompanyQueryIntent.isSingleCompanyQuery(userMessage, searchDocuments)) {
            return composeSingleCompany(aiText, searchDocuments, searchContext, userMessage);
        }

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

    private static ComposedParts composeSingleCompany(
            String aiText,
            Collection<Document> searchDocuments,
            String searchContext,
            String userMessage
    ) {
        CompanyFacts fact = CompanyPrimaryResolver.resolve(searchDocuments, searchContext, userMessage);
        fact = withAiLogo(fact, aiText);

        String cardHtml = null;
        if (fact.hasCardData()) {
            cardHtml = CompanyCardBuilder.build(fact);
        }
        if (cardHtml != null && cardHtml.isBlank()) {
            cardHtml = null;
        }

        String body = formatBody(aiText, userMessage, fact);
        return new ComposedParts(cardHtml, body);
    }

    /** 카드 → 설명 (AI 언급 순서 우선, 중복·마크다운 잔여 제거) */
    private static String composeInterleaved(
            List<CompanyFacts> facts,
            String aiText,
            Collection<Document> searchDocuments,
            String searchContext
    ) {
        List<CompanyFacts> enrichedFacts = CompanyFacts.enrichFromCatalog(facts, searchDocuments, searchContext);
        String cleaned = stripDuplicateCompanyChrome(aiText);
        cleaned = BoothMapLinkNormalizer.normalizeText(cleaned);

        Map<String, CompanyFacts> factsByKey = new LinkedHashMap<>();
        for (CompanyFacts fact : enrichedFacts) {
            factsByKey.put(CompanyFacts.factKey(fact), fact);
        }

        List<CompanyDescriptionExtractor.OrderedDescription> ordered =
                CompanyDescriptionExtractor.extractOrdered(cleaned, enrichedFacts);
        Map<String, String> descriptions = CompanyDescriptionExtractor.descriptionsByFactKey(cleaned, enrichedFacts);
        String leadingOrphan = descriptions.remove(CompanyDescriptionExtractor.LEADING_ORPHAN_KEY);
        String outro = CompanyDescriptionExtractor.extractOutro(cleaned, enrichedFacts);

        StringBuilder out = new StringBuilder();
        Set<String> emitted = new HashSet<>();

        for (CompanyDescriptionExtractor.OrderedDescription block : ordered) {
            CompanyFacts fact = factsByKey.get(block.factKey());
            if (fact == null || emitted.contains(block.factKey())) {
                continue;
            }
            appendCardAndDescription(out, withAiLogo(fact, aiText), block.description());
            emitted.add(block.factKey());
        }

        boolean leadingUsed = !ordered.isEmpty();
        for (CompanyFacts fact : enrichedFacts) {
            String key = CompanyFacts.factKey(fact);
            if (emitted.contains(key)) {
                continue;
            }
            String desc = descriptions.get(key);
            if ((desc == null || desc.isBlank()) && !leadingUsed && leadingOrphan != null && !leadingOrphan.isBlank()) {
                desc = leadingOrphan;
                leadingUsed = true;
            }
            appendCardAndDescription(out, withAiLogo(fact, aiText), desc);
            emitted.add(key);
        }

        if (outro != null && !outro.isBlank()) {
            if (out.length() > 0) {
                out.append("\n\n");
            }
            out.append(ChatResponseFormatter.formatChunk(outro));
        }

        return out.toString().trim();
    }

    private static void appendCardAndDescription(StringBuilder out, CompanyFacts fact, String description) {
        if (out.length() > 0) {
            out.append("\n\n");
        }
        out.append(CompanyCardBuilder.build(fact));
        if (description != null && !description.isBlank()) {
            out.append("\n\n").append(ChatResponseFormatter.formatChunk(description));
        }
    }

    private static String formatBody(String aiText) {
        return formatBody(aiText, null, null);
    }

    private static String formatBody(String aiText, String userMessage, CompanyFacts resolvedCompany) {
        String body = stripDuplicateCompanyChrome(aiText);
        body = TentativeMatchPhraseStripper.stripWhenNamesAlign(body, userMessage, resolvedCompany);
        if (resolvedCompany != null) {
            body = BoothNumberCorrector.alignWithResolvedBooth(body, resolvedCompany);
            body = CompanyCardBodyStripper.stripRedundantFields(body);
        }
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
