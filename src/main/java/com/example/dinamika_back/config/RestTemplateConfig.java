package com.example.dinamika_back.config;

import com.example.dinamika_back.features.AccessAudit;
import com.example.dinamika_back.features.ConditionalOnFeature;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.FeatureService;
import com.example.dinamika_back.features.net.OutboundGuard;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP-клиент обмена заказами и ТКП с внешней системой (функция {@code orders}). Без функции в потолке клиента
 * нет. Запросы идут только через сторож {@link OutboundGuard}: адрес должен быть в
 * {@code awms.network.outbound.allowed}; таймауты 5 с / 30 с, переходов по редиректам нет.
 */
@Configuration
@ConditionalOnFeature(Feature.ORDERS)
public class RestTemplateConfig {

    /** Построитель контекста (конвертеры Jackson приложения) + фабрика соединений без редиректов + сторож orders */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, FeatureService features, AccessAudit audit) {
        return builder
                .requestFactory(OutboundGuard::requestFactory)
                .setConnectTimeout(OutboundGuard.CONNECT_TIMEOUT)
                .setReadTimeout(OutboundGuard.READ_TIMEOUT)
                .additionalInterceptors(new OutboundGuard(Feature.ORDERS, features, audit))
                .build();
    }
}
