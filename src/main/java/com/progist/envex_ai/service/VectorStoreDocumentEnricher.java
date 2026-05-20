package com.progist.envex_ai.service;

import com.progist.envex_ai.util.CompanyFacts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * similaritySearch 결과에 metadata/logo가 비어 있을 때 vector_store 테이블에서 직접 보강합니다.
 */
@Service
public class VectorStoreDocumentEnricher {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreDocumentEnricher.class);

    private final JdbcTemplate jdbcTemplate;
    private final String vectorTableName;

    public VectorStoreDocumentEnricher(
            JdbcTemplate jdbcTemplate,
            @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}") String vectorTableName
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.vectorTableName = vectorTableName;
    }

    public List<Document> enrich(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return documents;
        }
        List<Document> enriched = new ArrayList<>(documents.size());
        for (Document document : documents) {
            enriched.add(enrichOne(document));
        }
        return enriched;
    }

    private Document enrichOne(Document document) {
        if (document == null) {
            return null;
        }
        String existingLogo = CompanyFacts.extractLogoUrl(document.getContent());
        if (existingLogo == null) {
            existingLogo = metadataLogo(document.getMetadata());
        }
        if (existingLogo != null && !existingLogo.isBlank()) {
            return document;
        }

        String booth = metadataField(document.getMetadata(), "booth_number", "boothNumber", "booth");
        String companyName = metadataField(document.getMetadata(), "company_name", "companyName");
        if ((booth == null || booth.isBlank()) && document.getContent() != null) {
            CompanyFacts parsed = CompanyFacts.fromDocument(document);
            booth = parsed.boothNumber();
            companyName = firstNonBlank(companyName, parsed.companyName());
        }

        String logoFromDb = lookupLogoUrl(booth, companyName);
        if (logoFromDb == null || logoFromDb.isBlank()) {
            log.debug("logo not found in DB for booth={} company={}", booth, companyName);
            return document;
        }

        Map<String, Object> metadata = new HashMap<>();
        if (document.getMetadata() != null) {
            metadata.putAll(document.getMetadata());
        }
        metadata.put("logo_url", CompanyFacts.sanitizeLogoUrl(logoFromDb));

        log.debug("logo enriched from DB for booth={} company={} url={}", booth, companyName, logoFromDb);
        return new Document(document.getContent(), metadata);
    }

    private String lookupLogoUrl(String booth, String companyName) {
        if (booth != null && !booth.isBlank()) {
            String byBooth = queryLogo("booth_number", booth.trim());
            if (byBooth != null) {
                return byBooth;
            }
        }
        if (companyName != null && !companyName.isBlank()) {
            String byName = queryLogo("company_name", companyName.trim());
            if (byName != null) {
                return byName;
            }
            String byNameLike = queryLogoLike(companyName.trim());
            if (byNameLike != null) {
                return byNameLike;
            }
        }
        return null;
    }

    private String queryLogo(String metadataField, String value) {
        String sql = """
                SELECT metadata->>'logo_url'
                FROM %s
                WHERE metadata->>'%s' = ?
                  AND COALESCE(metadata->>'logo_url', '') <> ''
                LIMIT 1
                """.formatted(vectorTableName, metadataField);
        List<String> rows = jdbcTemplate.queryForList(sql, String.class, value);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String queryLogoLike(String companyName) {
        String sql = """
                SELECT metadata->>'logo_url'
                FROM %s
                WHERE metadata->>'company_name' ILIKE ?
                  AND COALESCE(metadata->>'logo_url', '') <> ''
                LIMIT 1
                """.formatted(vectorTableName);
        List<String> rows = jdbcTemplate.queryForList(sql, String.class, "%" + companyName + "%");
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static String metadataLogo(Map<String, Object> metadata) {
        return metadataField(metadata, "logo_url", "logoUrl", "logo");
    }

    private static String metadataField(Map<String, Object> metadata, String... keys) {
        if (metadata == null) {
            return null;
        }
        for (String key : keys) {
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                    String value = stringVal(entry.getValue());
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private static String stringVal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return string;
        }
        return value.toString();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
