package com.example.dinamika_back.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {

    private Integer id;

    private String userName;

    private String firstName;

    private String middleName;

    private String lastName;

    private Long idRole;

    private String rolename;


}
