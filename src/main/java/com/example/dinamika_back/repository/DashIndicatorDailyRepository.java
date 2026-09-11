package com.example.dinamika_back.repository;

import com.example.dinamika_back.dto.DashIndicatorsDTO;
import com.example.dinamika_back.model.DashIndicatorDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface DashIndicatorDailyRepository extends JpaRepository<DashIndicatorDaily, LocalDate> {

    /** Суммы закупок и выдачи за период (поля null, если строк нет) */
    @Query("SELECT new com.example.dinamika_back.dto.DashIndicatorsDTO(SUM(i.purchasesAmount), SUM(i.issueAmount)) " +
           "FROM DashIndicatorDaily i WHERE i.costDate BETWEEN :dateFrom AND :dateTo")
    DashIndicatorsDTO sumForPeriod(@Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);
}
