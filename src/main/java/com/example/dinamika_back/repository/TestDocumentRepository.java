// TestDocumentRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.TestDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestDocumentRepository extends JpaRepository<TestDocument, Long> {
    
    List<TestDocument> findByUserIdAndCompletedFalseOrderByUpdatedAtDesc(Long userId);
    
    List<TestDocument> findByUserIdAndCompletedTrueOrderByUpdatedAtDesc(Long userId);
    
    List<TestDocument> findByUserIdOrderByUpdatedAtDesc(Long userId);
}