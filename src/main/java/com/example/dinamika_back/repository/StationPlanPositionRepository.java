package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.StationPlanPosition;
import com.example.dinamika_back.model.StationPlanPositionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface StationPlanPositionRepository extends JpaRepository<StationPlanPosition, StationPlanPositionId> {

    /** Позиция с uid станции — для ответа API */
    interface PositionRow {
        String getStationUid();

        Double getX();

        Double getY();
    }

    /** Станция для проверки расстановки: id и цех, в котором она сейчас числится */
    interface StationRef {
        Long getId();

        String getUid();

        Long getWorkshopId();
    }

    /**
     * Позиции станций, которые сейчас числятся в цехе плана. Позиции станций, переведённых
     * в другой цех, остаются в таблице, но не показываются и удаляются при следующем сохранении.
     */
    @Query(value = "SELECT s.uid AS stationUid, p.x AS x, p.y AS y "
            + "FROM station_plan_positions p JOIN stations s ON s.id = p.station_id "
            + "WHERE p.plan_id = :planId AND s.workshop_id = :workshopId "
            + "ORDER BY s.uid", nativeQuery = true)
    List<PositionRow> findRows(@Param("planId") Long planId, @Param("workshopId") Long workshopId);

    @Query(value = "SELECT s.id AS id, s.uid AS uid, s.workshop_id AS workshopId "
            + "FROM stations s WHERE s.uid IN (:uids)", nativeQuery = true)
    List<StationRef> findStations(@Param("uids") Collection<String> uids);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM StationPlanPosition p WHERE p.planId = :planId")
    int deleteByPlan(@Param("planId") Long planId);
}
