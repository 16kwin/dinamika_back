package com.example.dinamika_back.service;

import com.example.dinamika_back.model.TestDocument;
import com.example.dinamika_back.repository.TestDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TestDocumentService {

    private final TestDocumentRepository testDocumentRepository;
    private final SyncService syncService;

    @Transactional
    public TestDocument create(TestDocument document) {
        document.setId(null);
        document.setCompleted(false);
        TestDocument saved = testDocumentRepository.save(document);
        
        // Отправляем во вторую базу
        syncService.sendToSecondDatabase(saved);
        
        return saved;
    }

    @Transactional
    public TestDocument createWithoutSync(TestDocument document) {
        document.setId(null);
        if (document.getCompleted() == null) {
            document.setCompleted(false);
        }
        return testDocumentRepository.save(document);
    }

    @Transactional
    public TestDocument update(Long id, TestDocument document) {
        TestDocument existing = testDocumentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Документ не найден"));
        
        if (document.getTitle() != null) {
            existing.setTitle(document.getTitle());
        }
        if (document.getField2() != null) {
            existing.setField2(document.getField2());
        }
        if (document.getField3() != null) {
            existing.setField3(document.getField3());
        }
        if (document.getCompleted() != null) {
            existing.setCompleted(document.getCompleted());
        }
        
        TestDocument updated = testDocumentRepository.save(existing);
        
        // Отправляем во вторую базу
        syncService.sendToSecondDatabase(updated);
        
        return updated;
    }

    public TestDocument findById(Long id) {
        return testDocumentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Документ не найден"));
    }

    public List<TestDocument> findDraftsByUserId(Long userId) {
        return testDocumentRepository.findByUserIdAndCompletedFalseOrderByUpdatedAtDesc(userId);
    }

    @Transactional
    public void delete(Long id) {
        testDocumentRepository.deleteById(id);
    }
}