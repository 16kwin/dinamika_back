package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.VideoCameraDTO;
import com.example.dinamika_back.dto.VideoRecordDTO;
import com.example.dinamika_back.dto.VideoRecordFileDTO;
import com.example.dinamika_back.features.ConditionalOnFeature;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.model.Station;
import com.example.dinamika_back.model.VideoCamera;
import com.example.dinamika_back.model.VideoRecord;
import com.example.dinamika_back.model.VideoRecordFile;
import com.example.dinamika_back.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Сущности видеоархива → DTO для страницы. Названия станций подтягиваются одним запросом. Бина нет без видео. */
@Component
@ConditionalOnFeature(Feature.VIDEO)
@RequiredArgsConstructor
public class VideoMapper {

    private final StationRepository stationRepository;

    /** Названия станций по id; отсутствующие id просто не попадают в карту */
    public Map<Long, String> stationNames(Collection<Long> stationIds) {
        Map<Long, String> names = new HashMap<>();
        List<Long> ids = stationIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return names;
        for (Station station : stationRepository.findAllById(ids)) {
            names.put(station.getId(), station.getName());
        }
        return names;
    }

    /** Станции по id одним запросом — для списка камер */
    public Map<Long, Station> stations(Collection<Long> stationIds) {
        Map<Long, Station> stations = new HashMap<>();
        List<Long> ids = stationIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return stations;
        for (Station station : stationRepository.findAllById(ids)) {
            stations.put(station.getId(), station);
        }
        return stations;
    }

    public VideoCameraDTO toDto(VideoCamera camera, Map<Long, Station> stations, Set<Long> recordingCameraIds) {
        Station station = stations.get(camera.getStationId());
        return new VideoCameraDTO(
                camera.getId(),
                camera.getUid(),
                camera.getName(),
                camera.getIp(),
                camera.getMac(),
                camera.getCameraType(),
                cameraTypeTitle(camera.getCameraType()),
                VideoSource.maskPassword(camera.getRtspUrl()),
                camera.getStationId(),
                station == null ? null : station.getUid(),
                station == null ? null : station.getName(),
                recordingCameraIds.contains(camera.getId()));
    }

    public VideoRecordDTO toDto(VideoRecord record, List<VideoRecordFile> files, Map<Long, String> stationNames) {
        List<VideoRecordFileDTO> fileDtos = files.stream().map(this::toDto).toList();
        return new VideoRecordDTO(
                record.getId(),
                record.getId(),
                record.getUid(),
                record.getEventType(),
                VideoEventType.titleOf(record.getEventType()),
                record.getStationId(),
                stationNames.get(record.getStationId()),
                record.getStatus(),
                record.getStartedAt(),
                record.getFinishedAt(),
                record.getDurationSeconds(),
                record.getErrorMessage(),
                fileDtos);
    }

    public VideoRecordFileDTO toDto(VideoRecordFile file) {
        String base = "/api/video/files/" + file.getId();
        return new VideoRecordFileDTO(
                file.getId(),
                file.getCameraId(),
                file.getCameraName(),
                file.getCameraIp(),
                file.getCameraType(),
                cameraTitle(file.getCameraType()),
                Paths.get(file.getFilePath()).getFileName().toString(),
                file.getFileSize(),
                file.getDurationSeconds(),
                file.getStatus(),
                file.getErrorMessage(),
                base + "/content",
                base + "/download");
    }

    /** Значение поля «Тип камеры» */
    public static String cameraTypeTitle(String type) {
        return VideoCamera.TYPE_OVERVIEW.equals(type) ? "Обзорная" : "Станция";
    }

    /** Подпись над видео в плеере */
    public static String cameraTitle(String type) {
        return VideoCamera.TYPE_OVERVIEW.equals(type) ? "Обзорная камера" : "Камера станции";
    }
}
