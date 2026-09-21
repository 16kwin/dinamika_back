package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.DashReleaseEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DashReleaseEventRepository extends JpaRepository<DashReleaseEvent, Long> {

    /** Последние события выпуска продукции */
    List<DashReleaseEvent> findAllByOrderByEventAtDesc(Pageable pageable);
}
