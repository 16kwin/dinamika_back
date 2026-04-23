// SectionRepository.java
package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SectionRepository extends JpaRepository<Section, Long> {
    
    List<Section> findByWorkshopId(Long workshopId);
    
    List<Section> findByWorkshopIdOrderByNameAsc(Long workshopId);
    
    Optional<Section> findByNameAndWorkshopId(String name, Long workshopId);
    
    List<Section> findByWorkshopIdIn(List<Long> workshopIds);
}