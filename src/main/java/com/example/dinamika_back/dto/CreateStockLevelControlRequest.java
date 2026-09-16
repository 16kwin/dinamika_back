package com.example.dinamika_back.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateStockLevelControlRequest {
    private UUID uid;
    private LocalDate docDate;
    private String stationUid;
    private List<BindingRequest> bindings;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BindingRequest {
        private UUID uid;
        private UUID materialUid;
        private LocalDate bindingDate;
        private Integer minStock;
        private Integer criticalStock;
    }
}