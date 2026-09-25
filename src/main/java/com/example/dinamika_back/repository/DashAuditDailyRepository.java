package com.example.dinamika_back.repository;

import com.example.dinamika_back.dto.DashAuditSumDTO;
import com.example.dinamika_back.model.DashAuditDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface DashAuditDailyRepository extends JpaRepository<DashAuditDaily, LocalDate> {

    /** Операции, инциденты и выдачи сверх нормы за период */
    @Query("SELECT new com.example.dinamika_back.dto.DashAuditSumDTO("
            + "  SUM(a.operations), SUM(a.incidents), SUM(a.overNorm)) "
            + "FROM DashAuditDaily a WHERE a.statDate BETWEEN :dateFrom AND :dateTo")
    DashAuditSumDTO sumForPeriod(@Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);
}
