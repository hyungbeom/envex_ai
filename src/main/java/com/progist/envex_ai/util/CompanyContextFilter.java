package com.progist.envex_ai.util;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 프롬프트용 RAG 컨텍스트 구성. 단일 기업 질의 시 해당 업체 문서만 전달해 타 업체 부스 등이 섞이지 않게 합니다.
 */
public final class CompanyContextFilter {

    private CompanyContextFilter() {
    }

    public static String buildPromptContext(List<Document> searchDocuments, String userMessage) {
        if (searchDocuments == null || searchDocuments.isEmpty()) {
            return "";
        }
        String fullContext = joinContents(searchDocuments);
        if (!CompanyQueryIntent.isSingleCompanyQuery(userMessage, searchDocuments)) {
            return fullContext;
        }

        CompanyFacts target = CompanyPrimaryResolver.resolve(searchDocuments, fullContext, userMessage);
        List<Document> scoped = filterByCompany(searchDocuments, target);
        if (scoped.isEmpty()) {
            return fullContext;
        }
        return joinContents(scoped);
    }

    static List<Document> filterByCompany(List<Document> searchDocuments, CompanyFacts target) {
        if (searchDocuments == null || target == null || target.companyName() == null || target.companyName().isBlank()) {
            return List.of();
        }

        String targetCompanyId = findCompanyId(searchDocuments, target);
        List<Document> matched = new ArrayList<>();
        for (Document document : searchDocuments) {
            if (matchesCompany(document, target, targetCompanyId)) {
                matched.add(document);
            }
        }
        return matched;
    }

    private static String findCompanyId(List<Document> searchDocuments, CompanyFacts target) {
        for (Document document : searchDocuments) {
            CompanyFacts facts = CompanyFacts.fromDocument(document);
            if (namesAlign(facts.companyName(), target.companyName())) {
                String id = metadataString(document.getMetadata(), "company_id");
                if (id != null && !id.isBlank()) {
                    return id.trim();
                }
            }
        }
        return null;
    }

    private static boolean matchesCompany(Document document, CompanyFacts target, String targetCompanyId) {
        Map<String, Object> metadata = document.getMetadata();
        if (targetCompanyId != null) {
            String docId = metadataString(metadata, "company_id");
            if (docId != null && targetCompanyId.equalsIgnoreCase(docId.trim())) {
                return true;
            }
        }
        CompanyFacts facts = CompanyFacts.fromDocument(document);
        return namesAlign(facts.companyName(), target.companyName());
    }

    private static boolean namesAlign(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        String a = CompanyNameMatcher.normalize(left);
        String b = CompanyNameMatcher.normalize(right);
        if (a.isBlank() || b.isBlank()) {
            return false;
        }
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    private static String metadataString(Map<String, Object> metadata, String key) {
        if (metadata == null || key == null) {
            return null;
        }
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key) && entry.getValue() != null) {
                String value = entry.getValue().toString().trim();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String joinContents(List<Document> documents) {
        return documents.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n"));
    }
}
