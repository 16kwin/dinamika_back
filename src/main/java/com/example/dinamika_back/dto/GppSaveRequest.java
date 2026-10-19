package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Тело PUT /api/gpp — полная замена строк документа. version — версия, с которой начиналась правка;
 * если документ успели изменить, ответ 409.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GppSaveRequest {
    private Long version;
    private String gppType;
    private Boolean autoCalc;
    private String periodFrom;
    private String periodTo;
    private List<GppRowDTO> rows;
}
