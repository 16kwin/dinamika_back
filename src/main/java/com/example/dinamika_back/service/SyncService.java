package com.example.dinamika_back.service;

import com.example.dinamika_back.features.AccessAudit;
import com.example.dinamika_back.features.ConditionalOnFeature;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.features.FeatureService;
import com.example.dinamika_back.features.net.OutboundGuard;
import com.example.dinamika_back.model.TestDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Отправка тестовых документов во вторую базу (функция {@code testsync}, выключена во всех пресетах): без функции
 * в потолке бина нет. Свой клиент, а не общий бин заказов, но собранный так же: таймауты, без редиректов,
 * сторож {@link OutboundGuard} функции testsync (адрес — только из {@code awms.network.outbound.allowed}).
 */
@Service
@ConditionalOnFeature(Feature.TESTSYNC)
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final RestTemplate restTemplate;

    @Value("${sync.service.url}")
    private String syncServiceUrl;

    public SyncService(RestTemplateBuilder builder, FeatureService features, AccessAudit audit) {
        this.restTemplate = builder
                .requestFactory(OutboundGuard::requestFactory)
                .setConnectTimeout(OutboundGuard.CONNECT_TIMEOUT)
                .setReadTimeout(OutboundGuard.READ_TIMEOUT)
                .additionalInterceptors(new OutboundGuard(Feature.TESTSYNC, features, audit))
                .build();
    }

    public void sendToSecondDatabase(TestDocument document) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<TestDocument> request = new HttpEntity<>(document, headers);

            restTemplate.postForEntity(
                    syncServiceUrl + "/api/sync/test-document",
                    request,
                    String.class
            );

            log.info("Документ отправлен во вторую базу: {}", document.getId());

        } catch (Exception e) {
            log.warn("Ошибка синхронизации: {}", e.getMessage());
        }
    }
}
