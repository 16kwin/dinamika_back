package com.example.dinamika_back.service;

import com.example.dinamika_back.model.TestDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class SyncService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${sync.service.url}")
    private String syncServiceUrl;

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
            
            System.out.println("Документ отправлен во вторую базу: " + document.getId());
            
        } catch (Exception e) {
            System.err.println("Ошибка синхронизации: " + e.getMessage());
        }
    }
}