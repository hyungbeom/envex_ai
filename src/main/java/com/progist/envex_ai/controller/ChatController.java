package com.progist.envex_ai.controller;

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

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    // 🌟 1. AI의 뇌(해마) 역할을 할 메모리 저장소 변수 선언
    private final ChatMemory chatMemory;

    public ChatController(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        // 🌟 2. 컨트롤러가 생성될 때 메모리 공간(서버 RAM)을 하나 할당해 줍니다.
        this.chatMemory = new InMemoryChatMemory();
    }

    @GetMapping(value = "/api/chat/{companyId}", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> chat(
            @PathVariable("companyId") String companyId,
            @RequestParam(value = "message") String message,
            // 🌟 3. 누구와의 대화인지 구분하기 위해 프론트엔드에서 chatId(대화방 번호)를 받습니다.
            @RequestParam(value = "chatId", defaultValue = "default-room") String chatId) {

        System.out.println("🚀 [" + companyId + "] 방 번호 [" + chatId + "] 에 질문이 들어왔습니다: " + message);

        // 🌟 4. [수정됨] 검색 기본 세팅 (전체 검색을 고려해 TopK를 5개로 여유 있게 설정)
        SearchRequest searchRequest = SearchRequest.query(message).withTopK(30);

        // 🌟 5. [수정됨] companyId가 'envex' 또는 'all'이 아닐 때만 특정 기업 필터를 적용!
        if (!"envex".equalsIgnoreCase(companyId) && !"all".equalsIgnoreCase(companyId)) {
            String filterExpression = String.format("\"company_id\" == '%s'", companyId);
            searchRequest = searchRequest.withFilterExpression(filterExpression);
        }

        // 🌟 6. 조건이 적용된 searchRequest로 Vector DB 검색
        List<Document> similarDocuments = vectorStore.similaritySearch(searchRequest);

        String context = similarDocuments.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n"));

        System.out.println("💡 DB에서 찾아온 회사 정보: \n" + context);
        System.out.println("--------------------------------------------------");

        // 🌟 7. [수정됨] 검색 대상(박람회 전체 vs 특정 기업)에 따라 프롬프트 역할 분리
        String systemPrompt;

        if ("envex".equalsIgnoreCase(companyId) || "all".equalsIgnoreCase(companyId)) {
            // [모드 A] 박람회 전체 안내데스크 모드
            systemPrompt = """
                    당신은 ENVEX 환경 박람회의 총괄 안내 데스크 AI 가이드입니다.
                    
                    [박람회 기본 정보]
                    - 이번 ENVEX 박람회의 총 참가기업 수는 278개사입니다.
                    - 개최 장소는 코엑스(COEX)입니다.
                    
                    [🌟 중요 지침: 유연한 기업명 검색 및 센스 있는 답변]
                    관람객이 질문한 회사 이름의 일부(예: '천세')만 입력하더라도, 가장 비슷하거나 포함되는 이름(예: '천세(주)')이 있다면 그 기업으로 간주하고 "혹시 OOO를 찾으시나요?"라며 친절하게 안내해 주세요.
                    
                 [🌟 출력 포맷 지침 (매우 중요)]
                    당신은 답변을 작성할 때 반드시 아래의 마크다운(Markdown) 규칙을 철저히 따라야 합니다.
                    1. 이미지 출력: [검색된 참가기업 정보]에 '로고파일명'이 있다면 반드시 답변 최상단에 마크다운 이미지 태그를 넣으세요.
                       - ⚠️ 주의: 절대 '/images/' 경로를 지어내서 사용하지 마세요!
                       - 형식: ![기업명 로고](https://envex.or.kr/board/upload_file/ENVEX_form2/여기에실제로고파일명입력)
                       - 예시: ![이앤켐솔루션 로고](https://envex.or.kr/board/upload_file/ENVEX_form2/이앤켐솔루션로고.png)
                    2. 이동 버튼: 특정 기업을 안내한 후에는 답변 맨 마지막에 관람객이 부스 지도로 이동할 수 있는 일반 마크다운 링크를 반드시 추가하세요.
                       - ⚠️ 매우 중요: 버튼 링크 맨 앞에 절대로 느낌표(!)를 붙이지 마세요! 이미지가 아닌 링크여야 합니다.
                       - ⭕ 올바른 형식: [📍 부스 지도로 이동하기](/map?booth=해당기업부스번호)
                       - ❌ 틀린 형식: ![📍 부스 지도로 이동하기](/map?booth=해당기업부스번호)
                       
                    [검색된 참가기업 정보]
                    {context}
                    """;
        } else {
            // [모드 B] 특정 기업 전담 영업사원 모드
            systemPrompt = """
                    당신은 고객을 응대하는 친절하고 전문적인 AI 영업사원입니다.
                    답변을 작성할 때는 아래 제공된 [검색된 회사 정보]와, 당신이 기억하고 있는 [이전 대화 내역]을 모두 종합해서 답변하세요.
                    고객이 "좀 더 자세히 설명해줘", "그건 얼마야?" 처럼 대화의 맥락이 이어지는 질문을 하면, 이전 대화 내용을 파악하여 자연스럽게 이어서 설명해 주세요.
                    단, 검색된 정보와 이전 대화 내역 어디에도 없는 완전히 새로운 질문이라면 절대 지어내지 말고 "해당 내용은 제가 정확히 알 수 없습니다. 담당자에게 문의해 주세요."라고 답변하세요.

                    [검색된 회사 정보]
                    {context}
                    """;
        }

        return chatClient.prompt()
                .system(sp -> sp.text(systemPrompt).param("context", context))
                .user(message)
                // 🌟 8. 핵심! AI에게 답변을 지시하기 전에 '메모리 조언자(Advisor)'를 붙여줍니다.
                .advisors(new MessageChatMemoryAdvisor(chatMemory, chatId, 10))
                .stream()
                .content();
    }
}