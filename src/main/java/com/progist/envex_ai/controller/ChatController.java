package com.progist.envex_ai.controller;



import com.progist.envex_ai.service.VectorStoreDocumentEnricher;
import com.progist.envex_ai.util.BoothMapLinkNormalizer;
import com.progist.envex_ai.util.ChatGuardrails;
import com.progist.envex_ai.util.CompanyFacts;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;

import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;

import org.springframework.ai.chat.memory.ChatMemory;

import org.springframework.ai.chat.memory.InMemoryChatMemory;

import org.springframework.ai.document.Document;

import org.springframework.ai.vectorstore.SearchRequest;

import org.springframework.ai.vectorstore.VectorStore;

import org.springframework.web.bind.annotation.*;

import reactor.core.publisher.Flux;



import java.util.List;

import java.util.stream.Collectors;



@RestController

public class ChatController {



    private static final Logger log = LoggerFactory.getLogger(ChatController.class);



    private final ChatClient chatClient;

    private final VectorStore vectorStore;

    private final ChatMemory chatMemory;

    private final VectorStoreDocumentEnricher documentEnricher;



    public ChatController(
            ChatClient.Builder chatClientBuilder,
            VectorStore vectorStore,
            VectorStoreDocumentEnricher documentEnricher
    ) {

        this.chatClient = chatClientBuilder.build();

        this.vectorStore = vectorStore;

        this.chatMemory = new InMemoryChatMemory();

        this.documentEnricher = documentEnricher;

    }



    @GetMapping(value = "/api/chat/{companyId}", produces = "text/event-stream;charset=UTF-8")

    public Flux<String> chat(

            @PathVariable("companyId") String companyId,

            @RequestParam(value = "message") String message,

            @RequestParam(value = "chatId", defaultValue = "default-room") String chatId) {



        log.info("[{}] chatId={} message={}", companyId, chatId, message);

        if (ChatGuardrails.shouldHardReject(message)) {
            log.warn("[{}] guardrail hard-reject triggered", companyId);
            return Flux.just(ChatGuardrails.REJECTION_MESSAGE);
        }

        final String context;
        final Document primaryDocument;
        final List<Document> searchDocuments;

        try {

            SearchRequest searchRequest = SearchRequest.query(message).withTopK(12);



            if (!"envex".equalsIgnoreCase(companyId) && !"all".equalsIgnoreCase(companyId)) {

                String filterExpression = String.format("\"company_id\" == '%s'", companyId);

                searchRequest = searchRequest.withFilterExpression(filterExpression);

            }



            searchDocuments = documentEnricher.enrich(vectorStore.similaritySearch(searchRequest));

            primaryDocument = searchDocuments.isEmpty() ? null : searchDocuments.get(0);

            if (primaryDocument != null) {
                CompanyFacts sample = CompanyFacts.fromDocument(primaryDocument);
                log.info(
                        "[{}] primary hit company={} booth={} logo={} metaKeys={}",
                        companyId,
                        sample.companyName(),
                        sample.boothNumber(),
                        sample.logoUrl() != null ? "yes" : "no",
                        primaryDocument.getMetadata() != null ? primaryDocument.getMetadata().keySet() : "null"
                );
            }

            context = searchDocuments.stream()

                    .map(Document::getContent)

                    .collect(Collectors.joining("\n\n"));



            log.info("[{}] vector search hits={} contextLength={}", companyId, searchDocuments.size(), context.length());

        } catch (Exception e) {

            log.error("[{}] vector search failed", companyId, e);

            return Flux.just(errorMessage("DB 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.", e));

        }

        String systemPrompt = buildSystemPrompt(companyId);

        Flux<String> aiStream = chatClient.prompt()

                .system(sp -> sp.text(systemPrompt).param("context", context))

                .user(message)

                .advisors(new MessageChatMemoryAdvisor(chatMemory, chatId, 10))

                .stream()

                .content();



        return BoothMapLinkNormalizer.normalizeForClient(aiStream, context, true, searchDocuments)

                .doOnSubscribe(sub -> log.debug("[{}] OpenAI stream subscribed", companyId))

                .doOnNext(chunk -> {
                    if (chunk.contains("![") && chunk.toLowerCase().contains("map?booth=")) {
                        log.warn("[{}] unconverted booth markdown image leaked: {}", companyId, chunk);
                    }
                    if (chunk.contains("<img") && chunk.toLowerCase().contains("map?booth=")) {
                        log.warn("[{}] unconverted booth img leaked: {}", companyId, chunk);
                    }
                })

                .doOnComplete(() -> log.info("[{}] OpenAI stream completed", companyId))

                .doOnError(e -> log.error("[{}] OpenAI stream error", companyId, e))

                .switchIfEmpty(Flux.defer(() -> {

                    log.warn("[{}] OpenAI stream was empty", companyId);

                    return Flux.just(errorMessage("AI 응답이 비어 있습니다. API 키·프로필(local) 설정을 확인해 주세요.", null));

                }))

                .onErrorResume(e -> {

                    log.error("[{}] returning error to client", companyId, e);

                    return Flux.just(errorMessage("AI 응답 생성 중 오류가 발생했습니다.", e));

                });

    }



    private static String buildSystemPrompt(String companyId) {

        if ("envex".equalsIgnoreCase(companyId) || "all".equalsIgnoreCase(companyId)) {

            return ChatGuardrails.guardrailsPrompt() + """

                    당신은 ENVEX 환경 박람회의 총괄 안내 데스크 AI 가이드입니다.

                    

                    [박람회 기본 정보]

                    - 이번 ENVEX 박람회의 총 참가기업 수는 278개사입니다.

                    - 개최 장소는 코엑스(COEX)입니다.

                    

                    [🌟 중요 지침: 유연한 기업명 검색 및 센스 있는 답변]

                    관람객이 질문한 회사 이름의 일부(예: '천세')만 입력하더라도, 가장 비슷하거나 포함되는 이름(예: '천세(주)')이 있다면 그 기업으로 간주하고 "혹시 OOO를 찾으시나요?"라며 친절하게 안내해 주세요.

                    

                 [🌟 출력 포맷 지침 (매우 중요)]
                    - 문단과 항목 사이에는 빈 줄(줄바꿈 2번)을 넣어 가독성을 확보하세요.
                    - 목록은 반드시 `- ` 로 시작하는 불릿 목록을 사용하세요.
                    - ⚠️ 회사 로고·부스 지도 버튼 HTML은 시스템이 자동으로 상단에 넣습니다. 당신은 텍스트 설명만 작성하세요.
                    - 로고 마크다운 ![...], 부스 지도 링크/버튼(<a>, ![...](/map?booth=...)) 을 직접 넣지 마세요.

                    [🌟 여러 업체 추천·목록 안내 시]
                    - 시스템이 각 업체 카드(로고·부스·부스 이동 버튼)를 회사별 설명 바로 위에 자동 배치합니다.
                    - 업체당 불릿 1개만, 형식: `- **회사명** — 한두 문장 소개`
                    - 부스 번호, 연락처, 로고, 지도 링크를 불릿에 반복 나열하지 마세요.
                    - 선택 안내 문장(인트로·마무리)은 불릿 밖 일반 문단으로 작성 가능합니다.

                    [검색된 참가기업 정보]

                    {context}

                    """;

        }



        return ChatGuardrails.guardrailsPrompt() + """
                당신은 고객을 응대하는 친절하고 전문적인 AI 영업사원입니다.
                답변은 문단·목록마다 줄바꿈을 넣어 읽기 쉽게 작성하세요. 목록은 `- ` 불릿을 사용하세요.

                답변을 작성할 때는 아래 제공된 [검색된 회사 정보]와, 당신이 기억하고 있는 [이전 대화 내역]을 모두 종합해서 답변하세요.

                고객이 "좀 더 자세히 설명해줘", "그건 얼마야?" 처럼 대화의 맥락이 이어지는 질문을 하면, 이전 대화 내용을 파악하여 자연스럽게 이어서 설명해 주세요.

                단, 검색된 정보와 이전 대화 내역 어디에도 없는 완전히 새로운 질문이라면 절대 지어내지 말고 "해당 내용은 제가 정확히 알 수 없습니다. 담당자에게 문의해 주세요."라고 답변하세요.



                [검색된 회사 정보]

                {context}

                """;

    }



    private static String errorMessage(String headline, Throwable cause) {

        if (cause == null || cause.getMessage() == null || cause.getMessage().isBlank()) {

            return headline;

        }

        return headline + " (" + cause.getMessage() + ")";

    }

}

