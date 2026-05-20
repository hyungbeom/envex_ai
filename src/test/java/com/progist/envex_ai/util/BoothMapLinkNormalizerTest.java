package com.progist.envex_ai.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoothMapLinkNormalizerTest {

    @Test
    void convertsExactUserHtml() {
        String html = "<img alt=\"📍부스지도로이동하기\" src=\"/map?booth=Z13\" "
                + "style=\"width: 100px; display: block; border-radius: 2px; margin-right: 5px; vertical-align: middle;\">";

        String result = BoothMapLinkNormalizer.normalizeText(html);

        assertFalse(result.contains("<img"), result);
        assertTrue(result.contains("<a href=\"/map?booth=Z13\">"), result);
        assertTrue(result.contains("부스 지도로 이동하기"), result);
    }

    @Test
    void appendsBoothButtonWhenAiForgot() {
        String aiResponse = "천세(주)는 1980년 설립된 기업입니다. 부스는 C14입니다.";
        String context = "■ 기업명: 천세(주)\n■ 부스 번호: C14\n";

        String result = BoothMapLinkNormalizer.ensureBoothButton(aiResponse, context);

        assertTrue(result.contains("<a href=\"/map?booth=C14\">"), result);
        assertTrue(result.contains("부스 지도로 이동하기"), result);
    }
}
