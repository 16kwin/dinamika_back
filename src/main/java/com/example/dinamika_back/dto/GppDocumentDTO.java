package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Документ «График подготовки производства»: GET /api/gpp и ответ PUT /api/gpp.
 * Даты — 'YYYY-MM-DD'; rows — в порядке обхода дерева (родитель, затем его дети по position).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GppDocumentDTO {
    private String uid;
    private String title;
    /** GENERAL — «Общий по предприятию», SEQUENTIAL — «Последовательный» */
    private String gppType;
    private boolean autoCalc;
    /** Начало «Периода выборки» */
    private String periodFrom;
    private String periodTo;
    private Long version;
    /** null — документ ещё не сохраняли */
    private LocalDateTime updatedAt;
    /** ФИО (или логин) последнего сохранившего */
    private String updatedBy;
    /** Текущий пользователь может сохранять документ (администратор или директор) */
    private boolean canEdit;
    private List<GppRowDTO> rows;
}
