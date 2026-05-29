// dto/BatchOperationRequest.java
package com.example.dinamika_back.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
public class BatchOperationRequest {
    private List<UUID> groupUids;
    private List<UUID> materialUids;
    private UUID targetParentUid;
}