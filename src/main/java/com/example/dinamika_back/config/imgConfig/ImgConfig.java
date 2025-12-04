package com.example.dinamika_back.config.imgConfig;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ImgConfig implements WebMvcConfigurer {
    //При добавлении мультимедиа в уже собранное приложение
    // в этой же директории создаются папки images, docs, где они и хранятся
    // этот функционал нужен для того, чтобы Spring Boot мог сканировать эту директорию
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**", "/proc_docs/**", "/other_docs/**", "/form_docs/**",
                        "/example_docs/**", "/instr_docs/**", "/posesh_rules_docs/**", "/curator_rules_docs/**", "/profiles/**")
                .addResourceLocations("file:images/")
                .addResourceLocations("file:proc_docs/")
                .addResourceLocations("file:other_docs/")
                .addResourceLocations("file:form_docs/")
                .addResourceLocations("file:example_docs/")
                .addResourceLocations("file:instr_docs/")
                .addResourceLocations("file:posesh_rules_docs/")
                .addResourceLocations("file:curator_rules_docs/")
                .addResourceLocations("file:profiles/");
    }
}
