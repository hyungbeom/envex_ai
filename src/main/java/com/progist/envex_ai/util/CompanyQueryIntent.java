package com.progist.envex_ai.util;

import org.springframework.ai.document.Document;

import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 단일 기업 질의 vs 여러 업체 추천·목록 질의 구분.
 */
public final class CompanyQueryIntent {

    private static final Pattern HALL_RECOMMENDATION = Pattern.compile(
            "(수질|대기|폐수|환경|전시|에너지|그린).*관.{0,12}(추천|알려|소개|골라|선정|업체)"
    );
    private static final Pattern EXPLICIT_RECOMMENDATION = Pattern.compile(
            "(추천해|추천해줘|추천해주|골라줘|선정해|비교해|목록으로|리스트로)"
    );
    private static final Pattern HALL_ONLY = Pattern.compile(
            "^(수질|대기|폐수|환경|전시).{0,4}관\\s*(업체|참가|전시)?"
    );
    private static final Pattern GENERAL_EVENT = Pattern.compile(
            "행사|운영\\s*시간|개최\\s*시간|입장\\s*시간|폐막|개막|마감|몇\\s*시|티켓|입장료|코엑스|박람회\\s*일정|전시\\s*기간|개최\\s*일|입장\\s*안내"
    );
    /** 블루원은?, 천세 알려줘 등 짧은 기업명 질의 */
    private static final Pattern SHORT_COMPANY_NAME_QUERY = Pattern.compile(
            "^[가-힣A-Za-z0-9().\\s]{2,28}(?:은|는|이|가|임|냐|야)\\??$|"
                    + "^[가-힣A-Za-z0-9().\\s]{2,24}\\s+(?:알려|소개|정보|부스|어디|뭐야|뭔지)"
    );

    private CompanyQueryIntent() {
    }

    /** 행사 시간·장소·입장 등 박람회 일반 안내 (특정 업체 아님) */
    public static boolean isGeneralEventQuery(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String message = userMessage.trim();
        if (!GENERAL_EVENT.matcher(message).find()) {
            return false;
        }
        return !message.contains("업체")
                && !message.contains("회사")
                && !message.contains("기업")
                && !message.contains("부스")
                && !message.contains("참가사");
    }

    /** 특정 업체·부스·관별 추천 등 기업 카드가 필요한 질문 */
    public static boolean isCompanyRelatedQuery(String userMessage) {
        return isCompanyRelatedQuery(userMessage, null);
    }

    public static boolean isCompanyRelatedQuery(String userMessage, Collection<Document> searchDocuments) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        if (isGeneralEventQuery(userMessage)) {
            return false;
        }
        String message = userMessage.trim();
        if (message.contains("업체")
                || message.contains("회사")
                || message.contains("기업")
                || message.contains("참가사")
                || message.contains("전시사")
                || message.contains("부스")
                || message.contains("(주)")
                || message.contains("㈜")
                || HALL_RECOMMENDATION.matcher(message).find()
                || HALL_ONLY.matcher(message).find()) {
            return true;
        }
        if (SHORT_COMPANY_NAME_QUERY.matcher(message).find()) {
            return true;
        }
        return infersCompanyFromSearch(userMessage, searchDocuments);
    }

    private static boolean infersCompanyFromSearch(String userMessage, Collection<Document> searchDocuments) {
        if (searchDocuments == null || searchDocuments.isEmpty()) {
            return false;
        }
        String context = searchDocuments.stream()
                .map(doc -> doc.getContent() != null ? doc.getContent() : "")
                .collect(Collectors.joining("\n\n"));
        CompanyFacts resolved = CompanyPrimaryResolver.resolve(searchDocuments, context, userMessage);
        if (resolved.companyName() == null || resolved.companyName().isBlank()) {
            return false;
        }
        return namesAlign(userMessage, resolved.companyName());
    }

    private static boolean namesAlign(String left, String right) {
        String a = CompanyNameMatcher.normalize(left);
        String b = CompanyNameMatcher.normalize(right);
        if (a.isBlank() || b.isBlank()) {
            return false;
        }
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    /** 단일 업체 질의 — 카드 1장·컨텍스트 1사 필터 */
    public static boolean isSingleCompanyQuery(String userMessage) {
        return isSingleCompanyQuery(userMessage, null);
    }

    public static boolean isSingleCompanyQuery(String userMessage, Collection<Document> searchDocuments) {
        return isCompanyRelatedQuery(userMessage, searchDocuments) && !isMultiCompanyListQuery(userMessage);
    }

    /** 수질관 추천, 대기관 업체 알려줘 등 — 카드 여러 개 */
    public static boolean isMultiCompanyListQuery(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String message = userMessage.trim();
        if (EXPLICIT_RECOMMENDATION.matcher(message).find()) {
            return true;
        }
        if (HALL_RECOMMENDATION.matcher(message).find()) {
            return true;
        }
        if (message.contains("추천") && !message.contains("추천사항")) {
            return true;
        }
        return HALL_ONLY.matcher(message).find();
    }
}
