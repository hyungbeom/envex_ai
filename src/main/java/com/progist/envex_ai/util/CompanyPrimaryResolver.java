package com.progist.envex_ai.util;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 단일 기업 질의 시 검색 hit 중 사용자가 물은 업체 1건 선택 */
final class CompanyPrimaryResolver {

    private static final Pattern LATIN_TOKEN = Pattern.compile("[A-Za-z]{2,}");

    private CompanyPrimaryResolver() {
    }

    static CompanyFacts resolve(
            Collection<Document> searchDocuments,
            String searchContext,
            String userMessage
    ) {
        if (searchDocuments == null || searchDocuments.isEmpty()) {
            return CompanyFacts.fromContextText(searchContext);
        }

        CompanyFacts best = null;
        int bestScore = 0;
        for (Document document : searchDocuments) {
            CompanyFacts facts = CompanyFacts.fromDocument(document);
            if (!facts.hasCardData()) {
                continue;
            }
            String content = document.getContent() != null ? document.getContent() : "";
            int score = scoreCandidate(userMessage, content, facts);
            if (score > bestScore) {
                bestScore = score;
                best = facts;
            }
        }

        if (best == null || bestScore < 5) {
            best = CompanyFacts.fromDocument(searchDocuments.iterator().next());
        }

        List<CompanyFacts> enriched = CompanyFacts.enrichFromCatalog(
                List.of(best),
                searchDocuments,
                searchContext
        );
        return enriched.isEmpty() ? best : enriched.get(0);
    }

    private static int scoreCandidate(String userMessage, String documentContent, CompanyFacts candidate) {
        if (userMessage == null || userMessage.isBlank()) {
            return 0;
        }

        String messageLower = userMessage.toLowerCase(Locale.ROOT);
        String messageNorm = CompanyNameMatcher.normalize(userMessage);
        List<String> latinTokens = extractLatinTokens(userMessage);
        String contentLower = documentContent == null ? "" : documentContent.toLowerCase(Locale.ROOT);
        String nameNorm = CompanyNameMatcher.normalize(candidate.companyName());
        if (nameNorm.isBlank()) {
            return 0;
        }

        int score = 0;
        if (messageNorm.contains(nameNorm) || nameNorm.contains(messageNorm)) {
            score = 80 + nameNorm.length();
        }

        for (String token : latinTokens) {
            String tokenLower = token.toLowerCase(Locale.ROOT);
            if (!messageLower.contains(tokenLower)) {
                continue;
            }
            if (nameNorm.contains(tokenLower)) {
                score = Math.max(score, 90 + token.length());
            }
            if (candidate.companyName() != null
                    && candidate.companyName().toLowerCase(Locale.ROOT).contains(tokenLower)) {
                score = Math.max(score, 85 + token.length());
            }
            if (contentLower.contains(tokenLower)) {
                score = Math.max(score, 88 + token.length());
            }
        }

        return score;
    }

    private static List<String> extractLatinTokens(String message) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = LATIN_TOKEN.matcher(message);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

}
