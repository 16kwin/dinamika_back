package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * График «Затраты на приобретение»: точки по дням диапазона.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashCostsDTO {
    private List<DashCostPointDTO> points;
}
