package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.DashCostDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DashCostDailyRepository extends JpaRepository<DashCostDaily, LocalDate> {

    /** Точки графика за период по возрастанию даты */
    @Query("SELECT c FROM DashCostDaily c WHERE c.costDate BETWEEN :dateFrom AND :dateTo ORDER BY c.costDate ASC")
    List<DashCostDaily> findByPeriod(@Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);
}
