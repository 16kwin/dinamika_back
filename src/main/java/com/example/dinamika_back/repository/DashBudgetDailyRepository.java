package com.example.dinamika_back.repository;

import com.example.dinamika_back.dto.DashBudgetDTO;
import com.example.dinamika_back.model.DashBudgetDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface DashBudgetDailyRepository extends JpaRepository<DashBudgetDaily, LocalDate> {

    /** Суммы плана и факта бюджета за период (поля null, если строк нет; процент считает сервис) */
    @Query("SELECT new com.example.dinamika_back.dto.DashBudgetDTO(SUM(b.planAmount), SUM(b.factAmount)) " +
           "FROM DashBudgetDaily b WHERE b.costDate BETWEEN :dateFrom AND :dateTo")
    DashBudgetDTO sumForPeriod(@Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);
}
