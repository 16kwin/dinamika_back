package com.example.dinamika_back.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDocRefillRequest {
    private LocalDateTime docDatetime;
    private String stationUid;
    private String status;
    private List<CreateDocRefillRequest.RefillCellRequest> cells;
}