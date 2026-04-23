// LocationService.java
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.Enterprise;
import com.example.dinamika_back.model.Section;
import com.example.dinamika_back.model.Workshop;
import com.example.dinamika_back.repository.EnterpriseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LocationService {

    private final EnterpriseRepository enterpriseRepository;

    @Autowired
    public LocationService(EnterpriseRepository enterpriseRepository) {
        this.enterpriseRepository = enterpriseRepository;
    }

    public HierarchyDTO getFullHierarchy() {
        Set<Enterprise> enterprises = enterpriseRepository.findAllWithHierarchy();
        
        List<EnterpriseDTO> enterpriseDTOs = enterprises.stream()
            .map(this::convertToEnterpriseDTO)
            .collect(Collectors.toList());
        
        return new HierarchyDTO(enterpriseDTOs);
    }

    private EnterpriseDTO convertToEnterpriseDTO(Enterprise enterprise) {
        EnterpriseDTO dto = new EnterpriseDTO();
        dto.setId(enterprise.getId());
        dto.setName(enterprise.getName());
        
        List<WorkshopDTO> workshopDTOs = enterprise.getWorkshops().stream()
            .map(this::convertToWorkshopDTO)
            .collect(Collectors.toList());
        dto.setWorkshops(workshopDTOs);
        
        return dto;
    }

    private WorkshopDTO convertToWorkshopDTO(Workshop workshop) {
        WorkshopDTO dto = new WorkshopDTO();
        dto.setId(workshop.getId());
        dto.setName(workshop.getName());
        dto.setEnterpriseId(workshop.getEnterprise().getId());
        
        List<SectionDTO> sectionDTOs = workshop.getSections().stream()
            .map(this::convertToSectionDTO)
            .collect(Collectors.toList());
        dto.setSections(sectionDTOs);
        
        return dto;
    }

    private SectionDTO convertToSectionDTO(Section section) {
        SectionDTO dto = new SectionDTO();
        dto.setId(section.getId());
        dto.setName(section.getName());
        dto.setWorkshopId(section.getWorkshop().getId());
        return dto;
    }
}