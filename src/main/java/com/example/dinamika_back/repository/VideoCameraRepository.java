package com.example.dinamika_back.repository;

import com.example.dinamika_back.model.VideoCamera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoCameraRepository extends JpaRepository<VideoCamera, Long> {

    /** Все камеры в порядке вывода на странице */
    List<VideoCamera> findAllByOrderBySortOrderAscIdAsc();

    /** Камеры одной станции — их и включает событие станции */
    List<VideoCamera> findByStationIdOrderBySortOrderAscIdAsc(Long stationId);
}
