package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.StationPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StationPlanRepository extends JpaRepository<StationPlan, Long> {

    Optional<StationPlan> findByWorkshopId(Long workshopId);

    /** План с блокировкой строки до конца транзакции — сохранения одного цеха идут по очереди */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM StationPlan p WHERE p.workshopId = :workshopId")
    Optional<StationPlan> lockByWorkshopId(@Param("workshopId") Long workshopId);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM workshops WHERE id = :workshopId)", nativeQuery = true)
    boolean workshopExists(@Param("workshopId") Long workshopId);

    /** Файлы картинок всех планов — остальные файлы в каталоге планов осиротели */
    @Query("SELECT p.imagePath FROM StationPlan p WHERE p.imagePath IS NOT NULL")
    List<String> findImagePaths();
}
