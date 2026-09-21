package com.example.dinamika_back.repository;

import com.example.dinamika_back.dto.DashDefectAggDTO;
import com.example.dinamika_back.model.DashDefectDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DashDefectDailyRepository extends JpaRepository<DashDefectDaily, Long> {

    /** Выпуск и брак за период по парам субъект + элемент для одного вида графика */
    @Query("SELECT new com.example.dinamika_back.dto.DashDefectAggDTO("
            + "  d.subjectKey, d.itemKey, SUM(d.releasedQty), SUM(d.defectQty)) "
            + "FROM DashDefectDaily d "
            + "WHERE d.viewKind = :viewKind AND d.statDate BETWEEN :dateFrom AND :dateTo "
            + "GROUP BY d.subjectKey, d.itemKey")
    List<DashDefectAggDTO> sumBySubjectAndItem(@Param("viewKind") String viewKind,
                                               @Param("dateFrom") LocalDate dateFrom,
                                               @Param("dateTo") LocalDate dateTo);
}
