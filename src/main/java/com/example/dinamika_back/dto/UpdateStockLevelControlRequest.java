package com.example.dinamika_back.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStockLevelControlRequest {
    private LocalDate docDate;
    private String stationUid;
    private List<CreateStockLevelControlRequest.BindingRequest> bindings;
}