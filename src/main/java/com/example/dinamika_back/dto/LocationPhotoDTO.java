package com.example.dinamika_back.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class LocationPhotoDTO {
    private String photoFileName;
    private String photoFilePath;
    private String photoFileExtension;
    
    // Для фронтенда - полный URL
    private String photoUrl;
    
    // Новое поле - станции на этой локации
    private List<StationPositionDTO> stations;
}