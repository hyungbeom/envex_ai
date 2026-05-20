package com.progist.envex_ai.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VectorStoreDocumentEnricherTest {

    @Test
    void enrichesDocumentWithLogoFromDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(any(String.class), eq(String.class), eq("B10")))
                .thenReturn(List.of("https://envex.or.kr/board/upload_file/ENVEX_form2/logo.png"));

        VectorStoreDocumentEnricher enricher = new VectorStoreDocumentEnricher(jdbc, "vector_store");

        Document sparse = new Document(
                "요약",
                Map.of("company_name", "수기산업", "booth_number", "B10")
        );

        Document enriched = enricher.enrich(List.of(sparse)).get(0);

        assertTrue(enriched.getMetadata().get("logo_url").toString().contains("logo.png"));
    }
}
