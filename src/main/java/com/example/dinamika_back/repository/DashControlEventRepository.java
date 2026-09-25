package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.DashControlEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DashControlEventRepository extends JpaRepository<DashControlEvent, Long> {

    /** События подразделения, самые свежие первыми (меньше days_ago, позже время) */
    List<DashControlEvent> findByDepartmentOrderByDaysAgoAscTimeOfDayDescIdDesc(String department, Pageable pageable);

    /** События всех подразделений, самые свежие первыми */
    List<DashControlEvent> findAllByOrderByDaysAgoAscTimeOfDayDescIdDesc(Pageable pageable);
}
