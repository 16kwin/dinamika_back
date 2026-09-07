package com.example.dinamika_back.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Getter
@Setter
public class SprMaterialDTO {
    private UUID uid;
    private Integer code;
    private String name;
    private String article;
    private String description;
    private Boolean usage;
    private Boolean wasteMaterial;
    private Boolean recycleMaterial;
    
    private UUID groupUid;
    private String groupName;
    
    private UUID typeMainUid;
    private String typeMainName;
    
    private UUID typePurposeUid;
    private String typePurposeName;
    
    private UUID typeProductUid;
    private String typeProductName;
    
    private UUID releaseUid;
    private String releaseName;
    
    private UUID measureUid;
    private String measureName;
    
    private UUID manufacturerUid;
    private String manufacturerName;
    
    private UUID brandUid;
    private String brandName;
    
    private UUID modelOfBrandUid;
    private String modelOfBrandName;
    
    private UUID countryUid;
    private String countryName;
}