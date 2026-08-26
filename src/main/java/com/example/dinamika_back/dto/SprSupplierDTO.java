package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SprSupplierDTO {
    private UUID uid;
    private Integer code;
    private String name;
    private UUID countryUid;
    private String countryName;
    private String address;
    private UUID shortDescriptionUid;
    private String shortDescriptionName;
    private String description;
    private String email;
    private String website;
    private String phone;
    // brandUid и brandName удалены
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}