package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Состояние модуля: найден ли FFmpeg, куда пишется архив и какие записи идут прямо сейчас. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoStatusDTO {
    private boolean ffmpegAvailable;
    private String ffmpegVersion;
    private String storageDir;
    private List<VideoRecordDTO> activeRecords;
}
