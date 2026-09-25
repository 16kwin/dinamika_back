package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.DashDayEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DashDayEventRepository extends JpaRepository<DashDayEvent, Long> {

    /** События источника за день, самые поздние первыми; при равном времени — по sort_order */
    List<DashDayEvent> findBySourceOrderByTimeOfDayDescSortOrderAsc(String source);
}
