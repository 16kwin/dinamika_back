// LocationService.java
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.Enterprise;
import com.example.dinamika_back.model.Holding;
import com.example.dinamika_back.model.Section;
import com.example.dinamika_back.model.Workshop;
import com.example.dinamika_back.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final HoldingRepository holdingRepository;

    public HierarchyDTO getFullHierarchy() {
        Set<Holding> holdings = holdingRepository.findAllWithHierarchy();

        List<HoldingDTO> holdingDTOs = holdings.stream()
                .map(this::convertToHoldingDTO)
                .collect(Collectors.toList());

        return new HierarchyDTO(holdingDTOs);
    }

    private HoldingDTO convertToHoldingDTO(Holding holding) {
        HoldingDTO dto = new HoldingDTO();
        dto.setId(holding.getId());
        dto.setName(holding.getName());
        dto.setDescription(holding.getDescription());

        List<EnterpriseDTO> enterpriseDTOs = holding.getEnterprises().stream()
                .map(this::convertToEnterpriseDTO)
                .collect(Collectors.toList());
        dto.setEnterprises(enterpriseDTOs);

        return dto;
    }

    private EnterpriseDTO convertToEnterpriseDTO(Enterprise enterprise) {
        EnterpriseDTO dto = new EnterpriseDTO();
        dto.setId(enterprise.getId());
        dto.setName(enterprise.getName());
        dto.setHoldingId(enterprise.getHolding() != null ? enterprise.getHolding().getId() : null);
        dto.setHoldingName(enterprise.getHolding() != null ? enterprise.getHolding().getName() : null);

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
        dto.setHoldingId(workshop.getEnterprise().getHolding() != null
                ? workshop.getEnterprise().getHolding().getId() : null);
        dto.setHoldingName(workshop.getEnterprise().getHolding() != null
                ? workshop.getEnterprise().getHolding().getName() : null);

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
        dto.setEnterpriseId(section.getWorkshop().getEnterprise() != null
                ? section.getWorkshop().getEnterprise().getId() : null);
        dto.setHoldingId(section.getWorkshop().getEnterprise() != null
                && section.getWorkshop().getEnterprise().getHolding() != null
                ? section.getWorkshop().getEnterprise().getHolding().getId() : null);
        dto.setHoldingName(section.getWorkshop().getEnterprise() != null
                && section.getWorkshop().getEnterprise().getHolding() != null
                ? section.getWorkshop().getEnterprise().getHolding().getName() : null);
        return dto;
    }
}