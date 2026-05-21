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
    void interleavesMarkdownBoldListWithoutDuplicate() {
        Document doc1 = new Document(
                "■ 기업명: 기산수기\n",
                Map.of("company_name", "기산수기", "booth_number", "D08")
        );
        Document doc2 = new Document(
                "■ 기업명: 한소주식회사\n",
                Map.of("company_name", "한소주식회사", "booth_number", "G01")
        );
        Document doc3 = new Document(
                "■ 기업명: 천세(주)\n",
                Map.of("company_name", "천세(주)", "booth_number", "C14")
        );

        String ai = """
                신기술 개발과 다양한 인증을 보유한 수처리 기기 전문 업체입니다.
                **기산수기** — 신기술 개발과 다양한 인증을 보유한 수처리 기기 전문 업체입니다.
                **한소주식회사** — 폐수처리 및 정수처리 설비 전문입니다.
                **천세(주)** — 정량펌프 전문 기업입니다.
                이 업체들이 수질관 관련 제품을 제공합니다.
                """;

        String result = CompanyResponseComposer.composeParts(ai, List.of(doc1, doc2, doc3), "").companyCardHtml();

        assertFalse(result.contains("**"), result);
        int d08 = result.indexOf("[[D08]]");
        int g01 = result.indexOf("[[G01]]");
        int c14 = result.indexOf("[[C14]]");
        int hansoDesc = result.indexOf("폐수처리 및 정수처리");
        assertTrue(d08 >= 0 && g01 > d08 && hansoDesc > g01 && c14 > hansoDesc, result);
        assertTrue(result.indexOf("신기술 개발") < result.lastIndexOf("신기술 개발") || result.split("신기술 개발", -1).length <= 2,
                "duplicate description for same company");
    }

    @Test
    void interleavesInlineAiFormatCardThenDescription() {
        Document doc1 = new Document(
                "■ 기업명: 기산수기\n■ 부스 번호: D08\n",
                Map.of("company_name", "기산수기", "booth_number", "D08")
        );
        Document doc2 = new Document(
                "■ 기업명: 동양수기산업주식회사\n■ 부스 번호: B10\n",
                Map.of("company_name", "동양수기산업주식회사", "booth_number", "B10")
        );
        Document doc3 = new Document(
                "■ 기업명: 천세(주)\n■ 부스 번호: C14\n",
                Map.of("company_name", "천세(주)", "booth_number", "C14")
        );

        String ai = """
                신기술 개발로 다수의 특허를 보유한 수처리 기기 전문 업체입니다. - 동양수기산업주식회사 — 순수, 상·하수 및 폐수처리 설비 제작 전문 업체입니다. - 천세(주) — 정량펌프의 국산화를 목표로 하며, 다양한 산업 현장에 필요한 정량펌프를 개발하고 있습니다.이 업체들이 수질관 관련 제품을 제공하는 업체들입니다.
                """;

        CompanyResponseComposer.ComposedParts parts = CompanyResponseComposer.composeParts(
                ai, List.of(doc1, doc2, doc3), ""
        );
        String result = parts.companyCardHtml();

        int b10Card = result.indexOf("[[B10]]");
        int dongyangDesc = result.indexOf("순수, 상·하수");
        int c14Card = result.indexOf("[[C14]]");
        int cheonseDesc = result.indexOf("정량펌프의 국산화");
        int d08Card = result.indexOf("[[D08]]");

        assertTrue(b10Card >= 0, result);
        assertTrue(dongyangDesc > b10Card && cheonseDesc > c14Card, result);
        assertTrue(c14Card > dongyangDesc && d08Card > cheonseDesc, result);
        assertTrue(result.indexOf("이 업체들이") > d08Card, result);
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
    void shortCompanyNameQueryShowsCardLikeBluewin() {
        Document bluewin = new Document(
                "■ 기업명: 주식회사 블루원\n■ 부스 번호: D02\n (로고 URL: https://envex.or.kr/bluewin.png)\n",
                Map.of("company_id", "bw", "company_name", "주식회사 블루원", "booth_number", "D02",
                        "logo_url", "https://envex.or.kr/bluewin.png")
        );

        String ai = """
                **혹시 주식회사 블루원 을 찾으시나요?**

                **주식회사 블루원**은 1987년부터 환경기술을 연구해 온 기업입니다.
                - **부스 번호**: D02
                - **연락처**: 02-912-4438
                - **홈페이지**: 블루원 공식 홈페이지

                📍 부스 지도로 이동하기
                """;

        CompanyResponseComposer.ComposedParts parts = CompanyResponseComposer.composeParts(
                ai, List.of(bluewin), "", "블루원은?"
        );

        assertTrue(parts.companyCardHtml() != null, parts.companyCardHtml());
        assertTrue(parts.companyCardHtml().contains("[[D02]]"), parts.companyCardHtml());
        assertTrue(parts.companyCardHtml().contains("블루원"), parts.companyCardHtml());
        assertTrue(parts.bodyText() != null, parts.bodyText());
        assertFalse(parts.bodyText().contains("혹시"), parts.bodyText());
        assertFalse(parts.bodyText().contains("찾으시나요"), parts.bodyText());
        assertFalse(parts.bodyText().contains("부스 번호"), parts.bodyText());
        assertFalse(parts.bodyText().contains("부스 지도로 이동"), parts.bodyText());
        assertTrue(parts.bodyText().contains("1987"), parts.bodyText());
    }

    @Test
    void generalEventQueryDoesNotTriggerSingleCompanyMode() {
        assertTrue(CompanyQueryIntent.isGeneralEventQuery("행사 오늘 몇시까지해?"));
        assertFalse(CompanyQueryIntent.isCompanyRelatedQuery("행사 오늘 몇시까지해?"));
        assertFalse(CompanyQueryIntent.isSingleCompanyQuery("행사 오늘 몇시까지해?"));

        Document doc = new Document(
                "■ 기업명: 테스트업체\n■ 부스 번호: A01\n",
                Map.of("company_name", "테스트업체", "booth_number", "A01")
        );
        CompanyResponseComposer.ComposedParts parts = CompanyResponseComposer.composeParts(
                "오늘은 18시까지 운영합니다.",
                List.of(doc),
                "",
                "행사 오늘 몇시까지해?"
        );

        assertTrue(parts.companyCardHtml() == null, parts.companyCardHtml());
        assertTrue(parts.bodyText() != null && parts.bodyText().contains("18시"), parts.bodyText());
    }

    @Test
    void mergeHandlesNullRightWithoutNpe() {
        CompanyFacts left = new CompanyFacts("A사", "https://envex.or.kr/logo.png", "B01");
        CompanyFacts merged = CompanyFacts.merge(left, null);
        assertTrue("A사".equals(merged.companyName()));
        assertTrue("B01".equals(merged.boothNumber()));
    }

    @Test
    void singleCompanyContextFilterKeepsOnlyMatchingDocuments() {
        Document dxg = new Document(
                "■ 기업명: (주)디엑스지\n■ 부스 번호: L28\n",
                Map.of("company_id", "dxg1", "company_name", "(주)디엑스지", "booth_number", "L28")
        );
        Document other = new Document(
                "■ 기업명: 다른업체\n■ 부스 번호: G02\n",
                Map.of("company_id", "other", "company_name", "다른업체", "booth_number", "G02")
        );

        String context = CompanyContextFilter.buildPromptContext(List.of(other, dxg), "(주)디엑스지 업체 정보좀");

        assertTrue(context.contains("L28"), context);
        assertTrue(context.contains("디엑스지"), context);
        assertFalse(context.contains("G02"), context);
        assertFalse(context.contains("다른업체"), context);
    }

    @Test
    void correctsWrongBoothInBodyWhenResolvedBoothKnown() {
        Document doc = new Document(
                "■ 기업명: (주)디엑스지\n■ 부스 번호: L28\n",
                Map.of("company_name", "(주)디엑스지", "booth_number", "L28")
        );
        String ai = "(주)디엑스지 정보입니다.\n- 부스 번호: G02\n";

        CompanyResponseComposer.ComposedParts parts = CompanyResponseComposer.composeParts(
                ai, List.of(doc), "", "(주)디엑스지 업체 정보좀"
        );

        assertFalse(parts.bodyText().contains("G02"), parts.bodyText());
        assertFalse(parts.bodyText().contains("부스 번호"), parts.bodyText());
    }

    @Test
    void stripsTentativePhraseWhenCompanyNameMatchesQuery() {
        Document doc = new Document(
                "■ 기업명: (주)디엑스지\n■ 부스 번호: L28\n",
                Map.of("company_name", "(주)디엑스지", "booth_number", "L28")
        );
        String ai = """
                혹시 (주)디엑스지 를 찾으시나요? 아래에 해당 업체의 정보를 안내해 드립니다.

                (주)디엑스지는 환경 분석기 전문 기업입니다.
                """;

        CompanyResponseComposer.ComposedParts parts = CompanyResponseComposer.composeParts(
                ai, List.of(doc), "", "(주)디엑스지 업체 정보좀"
        );

        assertTrue(parts.bodyText() != null, parts.bodyText());
        assertFalse(parts.bodyText().contains("혹시"), parts.bodyText());
        assertFalse(parts.bodyText().contains("찾으시나요"), parts.bodyText());
        assertTrue(parts.bodyText().contains("환경 분석기"), parts.bodyText());
    }

    @Test
    void singleCompanyQueryShowsOneCardFromMatchedHit() {
        Document dxg = new Document(
                "■ 기업명: (주)디엑스지\n■ 부스 번호: M02\nDXG 환경장비\n",
                Map.of("company_id", "dxg", "company_name", "(주)디엑스지", "booth_number", "M02")
        );
        Document other1 = new Document(
                "■ 기업명: 기산수기\n■ 부스 번호: D08\n",
                Map.of("company_id", "1", "company_name", "기산수기", "booth_number", "D08")
        );
        Document other2 = new Document(
                "■ 기업명: 한소주식회사\n■ 부스 번호: G01\n",
                Map.of("company_id", "2", "company_name", "한소주식회사", "booth_number", "G01")
        );

        CompanyResponseComposer.ComposedParts parts = CompanyResponseComposer.composeParts(
                "(주)디엑스지는 환경 모니터링 장비를 전시합니다.",
                List.of(other1, other2, dxg),
                "",
                "DXG 업체 정보좀"
        );

        assertTrue(parts.companyCardHtml() != null, parts.companyCardHtml());
        assertTrue(parts.companyCardHtml().contains("[[M02]]"), parts.companyCardHtml());
        assertTrue(parts.companyCardHtml().contains("디엑스지"), parts.companyCardHtml());
        int cardCount = parts.companyCardHtml().split("id=\"company\" class=\"envex-company-card\"").length - 1;
        assertTrue(cardCount == 1, "expected 1 card but got " + cardCount);
        assertTrue(parts.bodyText() != null, parts.bodyText());
        assertFalse(parts.bodyText().contains("envex-company-card"));
    }

    @Test
    void recommendationQueryStillShowsMultipleCards() {
        Document doc1 = new Document(
                "■ 기업명: (주)씨티에A\n■ 부스 번호: M02\n",
                Map.of("company_name", "(주)씨티에A", "booth_number", "M02")
        );
        Document doc2 = new Document(
                "■ 기업명: 수기산업주식회사\n■ 부스 번호: B10\n",
                Map.of("company_name", "수기산업주식회사", "booth_number", "B10")
        );
        Document doc3 = new Document(
                "■ 기업명: 기산수기\n■ 부스 번호: D08\n",
                Map.of("company_name", "기산수기", "booth_number", "D08")
        );

        String ai = """
                - **(주)씨티에A** — 수질 관련 솔루션
                - **수기산업주식회사** — 폐수처리 설비 전문
                - **기산수기** — 펌프 제품 제공
                """;

        CompanyResponseComposer.ComposedParts parts = CompanyResponseComposer.composeParts(
                ai, List.of(doc1, doc2, doc3), "", "수질관 업체 추천해줘"
        );
        String result = parts.companyCardHtml();
        int cardCount = result.split("id=\"company\" class=\"envex-company-card\"").length - 1;
        assertTrue(cardCount >= 3, "expected 3 cards but got " + cardCount);
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
