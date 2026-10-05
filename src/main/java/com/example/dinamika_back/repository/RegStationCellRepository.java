package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.RegStationCell;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegStationCellRepository extends JpaRepository<RegStationCell, UUID> {

    @Query("SELECT c FROM RegStationCell c " +
           "LEFT JOIN FETCH c.cellAssignment " +
           "LEFT JOIN FETCH c.material " +
           "WHERE c.station.uid = :stationUid " +
           "ORDER BY c.drumNumber, c.columnNumber, c.numberCell")
    List<RegStationCell> findByStationUid(@Param("stationUid") String stationUid);

    @Query("SELECT c FROM RegStationCell c " +
           "WHERE c.station.uid = :stationUid " +
           "AND c.drumNumber = :drum " +
           "AND c.columnNumber = :column " +
           "AND c.numberCell = :number")
    Optional<RegStationCell> findByAddress(@Param("stationUid") String stationUid,
                                           @Param("drum") Integer drum,
                                           @Param("column") Integer column,
                                           @Param("number") Integer number);

    default Optional<RegStationCell> findByStationUidAndAddress(String stationUid,
                                                                Integer drumNumber,
                                                                Integer columnNumber,
                                                                Integer numberCell) {
        return findByAddress(stationUid, drumNumber, columnNumber, numberCell);
    }

    @Query("SELECT c FROM RegStationCell c " +
           "WHERE c.station.uid = :stationUid " +
           "AND c.material.uid = :materialUid")
    List<RegStationCell> findByStationAndMaterial(@Param("stationUid") String stationUid,
                                                   @Param("materialUid") UUID materialUid);

    void deleteByStationUid(String stationUid);
}