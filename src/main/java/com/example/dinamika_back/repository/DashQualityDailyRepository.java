package com.example.dinamika_back.repository;

import com.example.dinamika_back.dto.DashQualitySumDTO;
import com.example.dinamika_back.model.DashQualityDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface DashQualityDailyRepository extends JpaRepository<DashQualityDaily, LocalDate> {

    /** Выпуск и брак за период */
    @Query("SELECT new com.example.dinamika_back.dto.DashQualitySumDTO(SUM(q.releasedQty), SUM(q.defectQty)) "
            + "FROM DashQualityDaily q WHERE q.statDate BETWEEN :dateFrom AND :dateTo")
    DashQualitySumDTO sumForPeriod(@Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo);
}
