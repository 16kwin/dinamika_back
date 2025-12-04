package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AuthDTO {

    private String id;

    private String username;

    private String role;

    private String roleDescription;

    private String firstName;
    private String middleName;
    private String lastName;


}
