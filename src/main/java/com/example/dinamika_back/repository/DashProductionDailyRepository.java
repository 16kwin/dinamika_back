package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.DashProductionDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DashProductionDailyRepository extends JpaRepository<DashProductionDaily, LocalDate> {

    /** Точки графика расхода объема за период по возрастанию даты */
    @Query("SELECT p FROM DashProductionDaily p WHERE p.statDate BETWEEN :dateFrom AND :dateTo ORDER BY p.statDate ASC")
    List<DashProductionDaily> findByPeriod(@Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);
}
