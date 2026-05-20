package com.progist.envex_ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration // 이 어노테이션이 반드시 있어야 Spring이 설정 파일로 인식합니다.
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 백엔드의 모든 API 경로에 대해
                // ❌ .allowedOriginPatterns("*") // 모든 주소 허용 (보안 취약점 발생 가능)
                // ✅ 프론트엔드의 정확한 주소만 명시 (로컬 개발용 + 실제 배포용 도메인)
                .allowedOrigins("http://localhost:3000", "https://www.promote.co.kr")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 허용할 HTTP 메서드
                .allowedHeaders("*") // 모든 헤더 허용
                .allowCredentials(true); // 쿠키나 인증 정보 포함 허용
    }
}