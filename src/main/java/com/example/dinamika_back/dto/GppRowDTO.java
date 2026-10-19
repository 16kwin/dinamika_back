package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Строка ГПП в ответе и в запросе сохранения. Идентификаторы и даты — строками: так ошибки формата
 * получают понятное сообщение из проверки, а не общее «некорректное тело запроса».
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GppRowDTO {
    /** UUID; новые строки фронтенд генерирует сам */
    private String uid;
    private String parentUid;
    /** Порядок среди братьев, с 0 */
    private Integer position;
    /** «000», «001»… */
    private String opNumber;
    /** «Р03.200.000.12» — на экране выводится перед наименованием через пробел */
    private String code;
    private String name;
    /** PRODUCT | PART | MATERIAL */
    private String kind;
    private BigDecimal quantity;
    private String unit;
    /** 'YYYY-MM-DD'; у материала всегда null */
    private String start;
    private String finish;
    /** Календарные дни 1..3650; у материала null */
    private Integer durationDays;
    /** '#RRGGBB' */
    private String color;
    /** uid предшественников «окончание → начало» */
    private List<String> dependsOn;
    /** Узел свёрнут при открытии */
    private Boolean collapsed;
}
