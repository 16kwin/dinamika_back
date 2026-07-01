package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.CreateWorkshopRequest;
import com.example.dinamika_back.dto.UpdateWorkshopRequest;
import com.example.dinamika_back.dto.WorkshopFlatDto;
import com.example.dinamika_back.model.Enterprise;
import com.example.dinamika_back.model.Workshop;
import com.example.dinamika_back.repository.EnterpriseRepository;
import com.example.dinamika_back.repository.WorkshopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkshopService {

    private final WorkshopRepository workshopRepository;
    private final EnterpriseRepository enterpriseRepository;

    public List<WorkshopFlatDto> getAll() {
        return workshopRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<WorkshopFlatDto> getByEnterpriseId(Long enterpriseId) {
        return workshopRepository.findByEnterpriseId(enterpriseId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public WorkshopFlatDto getById(Long id) {
        Workshop workshop = workshopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Цех не найден: " + id));
        return toDTO(workshop);
    }

    @Transactional
    public WorkshopFlatDto create(CreateWorkshopRequest request) {
        Enterprise enterprise = enterpriseRepository.findById(request.getEnterpriseId())
                .orElseThrow(() -> new RuntimeException("Предприятие не найдено: " + request.getEnterpriseId()));
        if (workshopRepository.existsByNameAndEnterpriseId(request.getName(), request.getEnterpriseId())) {
            throw new RuntimeException("Цех с таким именем уже существует на этом предприятии");
        }
        Workshop workshop = new Workshop();
        workshop.setName(request.getName());
        workshop.setEnterprise(enterprise);
        workshop = workshopRepository.save(workshop);
        return toDTO(workshop);
    }

    @Transactional
    public WorkshopFlatDto update(Long id, UpdateWorkshopRequest request) {
        Workshop workshop = workshopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Цех не найден: " + id));
        Enterprise enterprise = enterpriseRepository.findById(request.getEnterpriseId())
                .orElseThrow(() -> new RuntimeException("Предприятие не найдено: " + request.getEnterpriseId()));
        if (!workshop.getName().equals(request.getName())
                || !workshop.getEnterprise().getId().equals(request.getEnterpriseId())) {
            if (workshopRepository.existsByNameAndEnterpriseId(request.getName(), request.getEnterpriseId())) {
                throw new RuntimeException("Цех с таким именем уже существует на этом предприятии");
            }
        }
        workshop.setName(request.getName());
        workshop.setEnterprise(enterprise);
        workshop = workshopRepository.save(workshop);
        return toDTO(workshop);
    }

    @Transactional
    public void delete(Long id) {
        Workshop workshop = workshopRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Цех не найден: " + id));
        workshopRepository.delete(workshop);
    }

    private WorkshopFlatDto toDTO(Workshop workshop) {
        String enterpriseName = workshop.getEnterprise() != null ? workshop.getEnterprise().getName() : null;
        return new WorkshopFlatDto(workshop.getId(), workshop.getName(), workshop.getEnterprise().getId(), enterpriseName);
    }
}