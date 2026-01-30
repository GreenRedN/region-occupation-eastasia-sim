package com.green.regionoccupation.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 웹 설정:
 * - "/" 및 "/map" 를 Thymeleaf 템플릿(map.html)로 연결
 * - 동일 출처 기준이지만, 실습 중 프론트/백 분리 테스트를 대비해 CORS도 넉넉히 허용(로컬용)
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("map");
        registry.addViewController("/map").setViewName("map");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedOrigins("*");
    }
}

