package com.example.dinamika_back.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Getter
@Setter
public class NomenclatureSaveRequest {
    private UUID uid;
    private Integer code;
    private String name;
    private String article;
    private String description;
    private UUID groupUid;
    private UUID typeMainUid;
    private UUID typePurposeUid;
    private UUID typeProductUid;
    private Boolean usage;
    private Boolean wasteMaterial;
    private Boolean recycleMaterial;
    private UUID measureUid;
    private UUID manufacturerUid;
    private UUID brandUid;
    private UUID modelOfBrandUid;
    private UUID countryUid;
    private String barcode;
}