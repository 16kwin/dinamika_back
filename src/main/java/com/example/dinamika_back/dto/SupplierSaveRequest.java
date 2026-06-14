// SupplierSaveRequest.java
package com.example.dinamika_back.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class SupplierSaveRequest {
    private UUID uid;
    private Integer code;
    private String name;
    private UUID countryUid;
    private String address;
    private UUID shortDescriptionUid;
    private String description;
    private String email;
    private String website;
    private String phone;
    private UUID brandUid;
    private String inn;
    private String ogrn;
    private String kpp;
    private String contactPerson;
    private String contactPosition;
    private String contactPhone;
    private String director;
    private String directorPosition;
    private String bankName;
    private String bik;
    private String correspondentAccount;
    private String settlementAccount;
    private String author;
}