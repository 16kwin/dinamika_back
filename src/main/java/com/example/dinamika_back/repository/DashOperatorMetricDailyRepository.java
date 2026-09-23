package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.DashOperatorMetricDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DashOperatorMetricDailyRepository extends JpaRepository<DashOperatorMetricDaily, LocalDate> {

    /** Показатели на самую позднюю дату */
    Optional<DashOperatorMetricDaily> findFirstByOrderByStatDateDesc();
}
