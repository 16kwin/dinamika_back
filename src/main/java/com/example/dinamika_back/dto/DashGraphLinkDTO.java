package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Связь между поставщиками графа закупок: a, b — ключи поставщиков. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashGraphLinkDTO {
    private String a;
    private String b;
    /** Вид связи: пока только "affiliated" — аффилированность */
    private String kind;
    /** Основание связи («Общий учредитель» и т.п.) */
    private String reason;
}
