package com.example.dinamika_back.features;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Регистрация ворот функций сервлет-фильтром раньше springSecurityFilterChain (порядок -110 против -100):
 * закрытая функция отвечает 404 до проверки входа. Только для исходных запросов (REQUEST): страница ошибки
 * /error после sendError(404) ворота не проходит.
 *
 * <p>Настройки CORS ворота берут из того же бина {@code corsConfigurationSource}, что и Spring Security
 * (SecurityConfig, владелец — B5), по имени: {@code mvcHandlerMappingIntrospector} тоже реализует
 * {@link CorsConfigurationSource}. Бин запрашивается лениво, при первом отказе: нет бина — ответ без CORS.
 */
@Configuration
public class FeatureWebConfig {

    static final String CORS_SOURCE_BEAN = "corsConfigurationSource";

    @Bean
    public FilterRegistrationBean<FeatureGateFilter> featureGateFilter(FeatureService features,
            @Qualifier(CORS_SOURCE_BEAN) ObjectProvider<CorsConfigurationSource> cors) {
        FilterRegistrationBean<FeatureGateFilter> registration =
                new FilterRegistrationBean<>(new FeatureGateFilter(features, cors::getIfAvailable));
        registration.setName("featureGateFilter");
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 10);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
