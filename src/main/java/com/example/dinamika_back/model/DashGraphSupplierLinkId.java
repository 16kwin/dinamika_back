package com.example.dinamika_back.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Составной ключ связи поставщиков графа закупок: пара ключей поставщиков. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DashGraphSupplierLinkId implements Serializable {
    private String supplierA;
    private String supplierB;
}
