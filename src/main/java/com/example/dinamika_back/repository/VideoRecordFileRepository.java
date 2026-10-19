package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.VideoRecordFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface VideoRecordFileRepository extends JpaRepository<VideoRecordFile, Long> {

    List<VideoRecordFile> findByRecordIdOrderByIdAsc(Long recordId);

    List<VideoRecordFile> findByRecordIdIn(Collection<Long> recordIds);

    List<VideoRecordFile> findByStatus(String status);
}
