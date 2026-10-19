// MoveOrderFlatDto.java
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
public class MoveOrderFlatDto {
    private UUID uid;
    private Integer code;
    private LocalDateTime docDatetime;
    private String stockToUid;
    private String stockToName;
    private LocalDateTime deadline;
    private String comment;
    private String status;
    private List<MoveOrderLineDto> lines;
}