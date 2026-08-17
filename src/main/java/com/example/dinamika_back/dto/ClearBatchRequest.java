// ClearBatchRequest.java
package com.example.dinamika_back.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ClearBatchRequest {
    private List<UUID> cellUids;
}