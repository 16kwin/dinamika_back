package com.example.dinamika_back.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_code_defaults")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCodeDefault {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "code_kind", nullable = false, length = 20)
    private String codeKind;

    @Column(name = "code_type", nullable = false, length = 20)
    private String codeType;
}