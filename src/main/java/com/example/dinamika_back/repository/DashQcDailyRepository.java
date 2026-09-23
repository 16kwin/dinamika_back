package com.example.dinamika_back.repository;

import com.example.dinamika_back.dto.DashQcSumDTO;
import com.example.dinamika_back.model.DashQcDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface DashQcDailyRepository extends JpaRepository<DashQcDaily, LocalDate> {

    /** Прошли, ожидают и не прошли контроль качества за период */
    @Query("SELECT new com.example.dinamika_back.dto.DashQcSumDTO("
            + "  SUM(q.passedQty), SUM(q.waitingQty), SUM(q.failedQty)) "
            + "FROM DashQcDaily q WHERE q.statDate BETWEEN :dateFrom AND :dateTo")
    DashQcSumDTO sumForPeriod(@Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);
}
