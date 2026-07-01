package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.CreateSectionRequest;
import com.example.dinamika_back.dto.SectionFlatDto;
import com.example.dinamika_back.dto.UpdateSectionRequest;
import com.example.dinamika_back.model.Section;
import com.example.dinamika_back.model.Workshop;
import com.example.dinamika_back.repository.SectionRepository;
import com.example.dinamika_back.repository.WorkshopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SectionService {

    private final SectionRepository sectionRepository;
    private final WorkshopRepository workshopRepository;

    public List<SectionFlatDto> getAll() {
        return sectionRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<SectionFlatDto> getByWorkshopId(Long workshopId) {
        return sectionRepository.findByWorkshopId(workshopId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public SectionFlatDto getById(Long id) {
        Section section = sectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Участок не найден: " + id));
        return toDTO(section);
    }

    @Transactional
    public SectionFlatDto create(CreateSectionRequest request) {
        Workshop workshop = workshopRepository.findById(request.getWorkshopId())
                .orElseThrow(() -> new RuntimeException("Цех не найден: " + request.getWorkshopId()));
        if (sectionRepository.existsByNameAndWorkshopId(request.getName(), request.getWorkshopId())) {
            throw new RuntimeException("Участок с таким именем уже существует в этом цехе");
        }
        Section section = new Section();
        section.setName(request.getName());
        section.setWorkshop(workshop);
        section = sectionRepository.save(section);
        return toDTO(section);
    }

    @Transactional
    public SectionFlatDto update(Long id, UpdateSectionRequest request) {
        Section section = sectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Участок не найден: " + id));
        Workshop workshop = workshopRepository.findById(request.getWorkshopId())
                .orElseThrow(() -> new RuntimeException("Цех не найден: " + request.getWorkshopId()));
        if (!section.getName().equals(request.getName())
                || !section.getWorkshop().getId().equals(request.getWorkshopId())) {
            if (sectionRepository.existsByNameAndWorkshopId(request.getName(), request.getWorkshopId())) {
                throw new RuntimeException("Участок с таким именем уже существует в этом цехе");
            }
        }
        section.setName(request.getName());
        section.setWorkshop(workshop);
        section = sectionRepository.save(section);
        return toDTO(section);
    }

    @Transactional
    public void delete(Long id) {
        Section section = sectionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Участок не найден: " + id));
        sectionRepository.delete(section);
    }

    private SectionFlatDto toDTO(Section section) {
        String workshopName = section.getWorkshop() != null ? section.getWorkshop().getName() : null;
        Long enterpriseId = section.getWorkshop() != null && section.getWorkshop().getEnterprise() != null
                ? section.getWorkshop().getEnterprise().getId() : null;
        String enterpriseName = section.getWorkshop() != null && section.getWorkshop().getEnterprise() != null
                ? section.getWorkshop().getEnterprise().getName() : null;
        return new SectionFlatDto(section.getId(), section.getName(), section.getWorkshop().getId(),
                workshopName, enterpriseId, enterpriseName);
    }
}