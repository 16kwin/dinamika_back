package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.DashOperatorEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DashOperatorEventRepository extends JpaRepository<DashOperatorEvent, Long> {

    /** Лента одного вида, свежие сверху */
    List<DashOperatorEvent> findByKindOrderByEventAtDesc(String kind);
}
