package com.example.dinamika_back.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Составной ключ справочника субъектов графика «Уровень брака»: вид графика + ключ субъекта. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DashDefectSubjectId implements Serializable {
    private String viewKind;
    private String subjectKey;
}
