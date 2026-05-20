package com.progist.envex_ai.util;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyCardBuilderTest {

    @Test
    void extractLogoUrlStripsTrailingParenFromEmbeddedText() {
        String text = "■ 기업명: 세일분석기술 (로고 URL: https://envex.or.kr/board/upload_file/ENVEX_form2/logo_세일분석기술-.png)\n";

        String logo = CompanyFacts.extractLogoUrl(text);

        assertTrue(logo != null && logo.endsWith(".png"), logo);
        assertFalse(logo.endsWith(")"), logo);
    }

    @Test
    void buildsFrontendCompanyCardHtml() {
        CompanyFacts facts = new CompanyFacts("천세(주)", "https://envex.or.kr/logo.png", "Z13");

        String html = CompanyCardBuilder.build(facts);

        assertTrue(html.contains("id=\"company\" class=\"envex-company-card\""), html);
        assertTrue(html.contains("class=\"envex-company-card__logo\""), html);
        assertTrue(html.contains("class=\"envex-company-card__info\""), html);
        assertTrue(html.contains("class=\"envex-company-card__booth\">Booth No. : [[Z13]]"), html);
        assertTrue(html.contains("class=\"envex-company-card__name\">천세(주)</p>"), html);
        assertTrue(html.indexOf("envex-company-card__booth") < html.indexOf("envex-company-card__name"), html);
        assertTrue(html.indexOf("envex-company-card__name") < html.indexOf("envex-company-card__btn"), html);
        assertTrue(html.contains("href=\"/map?booth=Z13\" class=\"envex-company-card__btn\">"), html);
        assertTrue(html.contains("<img src=\"https://envex.or.kr/logo.png\""), html);
    }

    @Test
    void composePrependsCardAndRemovesDuplicateLogo() {
        Document doc = new Document(
                "■ 기업명: 천세(주) (로고 URL: https://envex.or.kr/logo.png)\n■ 부스 번호: A17\n",
                Map.of("company_name", "천세(주)", "logo_url", "https://envex.or.kr/logo.png", "booth_number", "A17")
        );

        String ai = "![천세 로고](https://envex.or.kr/logo.png)\n\n천세(주)는 환경 기업입니다.";

        String result = CompanyResponseComposer.compose(ai, doc, "");

        assertTrue(result.startsWith("<div id=\"company\" class=\"envex-company-card\">"), result);
        assertFalse(result.contains("![천세"), result);
        assertTrue(result.contains("환경 기업"), result);
    }

    @Test
    void resolvesLogoFromAnySearchHit() {
        Document primary = new Document(
                "요약 텍스트",
                Map.of("company_name", "천세(주)", "booth_number", "Z13")
        );
        Document withLogoInBody = new Document(
                "■ 기업명: 천세(주) (로고 URL: https://envex.or.kr/board/upload_file/ENVEX_form2/cheonse.png)\n",
                Map.of()
        );

        CompanyFacts facts = CompanyFacts.resolve(List.of(primary, withLogoInBody), "");

        assertTrue(facts.logoUrl() != null && facts.logoUrl().contains("cheonse.png"), facts.logoUrl());
    }

    @Test
    void composeUsesLogoFromAiMarkdownWhenDbHasNoLogo() {
        Document doc = new Document(
                "■ 기업명: 천세(주)\n■ 부스 번호: Z13\n",
                Map.of("company_name", "천세(주)", "booth_number", "Z13")
        );
        String ai = "![로고](https://envex.or.kr/board/upload_file/ENVEX_form2/from-ai.png)\n\n설명";

        String result = CompanyResponseComposer.compose(ai, doc, "");

        assertTrue(result.contains("<img src=\"https://envex.or.kr/board/upload_file/ENVEX_form2/from-ai.png\""), result);
    }

    @Test
    void composeBuildsMultipleCompanyCardsForRecommendations() {
        Document doc1 = new Document(
                "■ 기업명: (주)씨티에A\n■ 부스 번호: M02\n",
                Map.of("company_id", "1", "company_name", "(주)씨티에A", "booth_number", "M02")
        );
        Document doc2 = new Document(
                "■ 기업명: 수기산업주식회사\n■ 부스 번호: B10\n",
                Map.of("company_id", "2", "company_name", "수기산업주식회사", "booth_number", "B10")
        );
        Document doc3 = new Document(
                "■ 기업명: 기산수기\n■ 부스 번호: D08\n",
                Map.of("company_id", "3", "company_name", "기산수기", "booth_number", "D08")
        );

        String ai = """
                - **(주)씨티에A** — 수질 관련 솔루션
                - **수기산업주식회사** — 폐수처리 설비 전문
                - **기산수기** — 펌프 제품 제공
                """;

        CompanyResponseComposer.ComposedParts parts = CompanyResponseComposer.composeParts(
                ai, List.of(doc1, doc2, doc3), ""
        );
        String result = parts.companyCardHtml();

        assertTrue(result.contains("<div id=\"company\" class=\"envex-company-card\">"), result);
        int cardCount = result.split("id=\"company\" class=\"envex-company-card\"").length - 1;
        assertTrue(cardCount >= 3, "expected 3 cards but got " + cardCount);

        int m02 = result.indexOf("[[M02]]");
        int ctaDesc = result.indexOf("수질 관련 솔루션");
        int b10Card = result.indexOf("[[B10]]");
        int b10Desc = result.indexOf("폐수처리 설비 전문");
        int d08Card = result.indexOf("[[D08]]");
        int d08Desc = result.indexOf("펌프 제품 제공");

        assertTrue(m02 >= 0 && ctaDesc > m02, result);
        assertTrue(b10Card > ctaDesc && b10Desc > b10Card, result);
        assertTrue(d08Card > b10Desc && d08Desc > d08Card, result);
        assertTrue(parts.bodyText() == null, "interleaved mode should send one block");
    }

    @Test
    void enrichFromCatalogFillsLogoByBooth() {
        Document sparse = new Document(
                "요약",
                Map.of("company_name", "수기산업주식회사", "booth_number", "B10")
        );
        Document full = new Document(
                "■ 기업명: 수기산업주식회사 (로고 URL: https://envex.or.kr/board/upload_file/ENVEX_form2/sugi.png)\n■ 부스 번호: B10\n",
                Map.of("company_name", "수기산업주식회사", "booth_number", "B10", "logo_url", "https://envex.or.kr/board/upload_file/ENVEX_form2/sugi.png")
        );

        List<CompanyFacts> enriched = CompanyFacts.enrichFromCatalog(
                CompanyFacts.resolveAll(List.of(sparse), 6),
                List.of(sparse, full),
                ""
        );

        assertTrue(enriched.get(0).logoUrl() != null && enriched.get(0).logoUrl().contains("sugi.png"));
        String html = CompanyCardBuilder.build(enriched.get(0));
        assertTrue(html.contains("<img src=\"https://envex.or.kr/board/upload_file/ENVEX_form2/sugi.png\""), html);
    }

    @Test
    void sendsCompanyCardAsSingleStreamChunk() {
        Document doc = new Document(
                "■ 기업명: 천세(주)\n■ 부스 번호: Z13\n",
                Map.of("company_name", "천세(주)", "booth_number", "Z13")
        );

        List<String> chunks = BoothMapLinkNormalizer.toDisplayStream(
                "안내 문구입니다.",
                "",
                true,
                List.of(doc)
        ).collectList().block();

        assertTrue(chunks != null && chunks.size() >= 2);
        assertTrue(chunks.get(0).startsWith("<div id=\"company\" class=\"envex-company-card\">"));
        assertTrue(chunks.get(0).contains("envex-company-card__btn"));
        assertTrue(chunks.get(0).endsWith("</div>\n\n"));
    }
}
