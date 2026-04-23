// UserFilterDTO.java
package com.example.dinamika_back.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserFilterDTO {
    
    // Поиск
    private String searchQuery;
    
    // Сортировка
    private SortOption sortOption;
    
    // Размещение
    private List<Long> selectedEnterprises;
    private List<Long> selectedWorkshops;
    private List<Long> selectedSections;
    
    // Статусы
    private List<String> selectedStatuses;
    
    // Типы станций
    private List<String> selectedTypes;
    
    // Сверхнормы
    private Boolean overissue;
    
    // Ошибка
    private Boolean hasError;
    
    // ТМЦ
    private Boolean isTmc;
    
    // СГД
    private Boolean isSgd;
    
    // Остатки
    private Boolean minOstatok;
    private Boolean criticalOstatok;
    
    // Вид отображения
    private String viewMode;
    
    public enum SortOption {
        NAME_ASC,
        NAME_DESC,
        PLACEMENT,
        STATUS,
        TYPE_PRIORITY
    }
}