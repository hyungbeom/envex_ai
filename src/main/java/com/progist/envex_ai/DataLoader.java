package com.progist.envex_ai;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.List;

@Configuration
public class DataLoader {

    @Value("classpath:bdtec.txt")
    private Resource bdtec;

    @Value("classpath:duon.txt")
    private Resource duon;

    @Value("classpath:gemma.txt")
    private Resource gemma;

    @Bean
    public CommandLineRunner loadData(VectorStore vectorStore) {
        return args -> {
            System.out.println("🚀 3개 회사(bdtec, duon, gemma) 데이터 적재를 시작합니다...");

            TokenTextSplitter splitter = new TokenTextSplitter();

            // --- 1. bdtec 데이터 적재 ---
            TextReader bdtecReader = new TextReader(bdtec);
            List<Document> bdtecDocs = bdtecReader.get();
            // 🚨 꼬리표 달기: company_id = bdtec
            bdtecDocs.forEach(doc -> doc.getMetadata().put("company_id", "bdtec"));
            vectorStore.add(splitter.apply(bdtecDocs));

            // --- 2. duon 데이터 적재 ---
            TextReader duonReader = new TextReader(duon);
            List<Document> duonDocs = duonReader.get();
            // 🚨 꼬리표 달기: company_id = duon
            duonDocs.forEach(doc -> doc.getMetadata().put("company_id", "duon"));
            vectorStore.add(splitter.apply(duonDocs));

            // --- 3. gemma 데이터 적재 ---
            TextReader gemmaReader = new TextReader(gemma);
            List<Document> gemmaDocs = gemmaReader.get();
            // 🚨 꼬리표 달기: company_id = gemma
            gemmaDocs.forEach(doc -> doc.getMetadata().put("company_id", "gemma"));
            vectorStore.add(splitter.apply(gemmaDocs));

            System.out.println("✅ 3개 회사 멀티 테넌트 벡터 DB 적재 완료!");
        };
    }
}