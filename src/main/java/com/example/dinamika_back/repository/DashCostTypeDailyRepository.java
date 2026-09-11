package com.example.dinamika_back.repository;

import com.example.dinamika_back.dto.DashTypeAmountDTO;
import com.example.dinamika_back.model.DashCostTypeDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DashCostTypeDailyRepository extends JpaRepository<DashCostTypeDaily, Long> {

    /** Суммы затрат по видам номенклатуры за период (только виды, у которых есть строки) */
    @Query("SELECT new com.example.dinamika_back.dto.DashTypeAmountDTO(c.typeKey, SUM(c.amount)) " +
           "FROM DashCostTypeDaily c WHERE c.costDate BETWEEN :dateFrom AND :dateTo GROUP BY c.typeKey")
    List<DashTypeAmountDTO> sumByTypeForPeriod(@Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);
}
