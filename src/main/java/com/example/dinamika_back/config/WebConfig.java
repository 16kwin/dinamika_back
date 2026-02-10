package com.example.dinamika_back.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Разрешаем доступ к файлам через URL /uploads/**
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir + "/")
                .setCachePeriod(3600); // Кэширование на 1 час
        
        // Для отладки - показываем где ищем файлы
        System.out.println("=== WEB CONFIG ===");
        System.out.println("Upload directory: " + uploadDir);
        System.out.println("Static resources mapped: /uploads/** -> file:" + uploadDir + "/");
        System.out.println("=== END WEB CONFIG ===");
    }
}