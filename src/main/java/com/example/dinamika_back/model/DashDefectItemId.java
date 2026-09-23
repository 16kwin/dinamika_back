package com.example.dinamika_back.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Составной ключ справочника элементов раскрытия: вид графика + ключ элемента. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DashDefectItemId implements Serializable {
    private String viewKind;
    private String itemKey;
}
