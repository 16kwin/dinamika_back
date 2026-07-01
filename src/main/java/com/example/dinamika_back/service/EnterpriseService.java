package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.CreateEnterpriseRequest;
import com.example.dinamika_back.dto.EnterpriseFlatDto;
import com.example.dinamika_back.dto.UpdateEnterpriseRequest;
import com.example.dinamika_back.model.Enterprise;
import com.example.dinamika_back.repository.EnterpriseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnterpriseService {

    private final EnterpriseRepository enterpriseRepository;

    public List<EnterpriseFlatDto> getAll() {
        return enterpriseRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public EnterpriseFlatDto getById(Long id) {
        Enterprise enterprise = enterpriseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Предприятие не найдено: " + id));
        return toDTO(enterprise);
    }

    @Transactional
    public EnterpriseFlatDto create(CreateEnterpriseRequest request) {
        if (enterpriseRepository.existsByName(request.getName())) {
            throw new RuntimeException("Предприятие с таким именем уже существует: " + request.getName());
        }
        Enterprise enterprise = new Enterprise();
        enterprise.setName(request.getName());
        enterprise = enterpriseRepository.save(enterprise);
        return toDTO(enterprise);
    }

    @Transactional
    public EnterpriseFlatDto update(Long id, UpdateEnterpriseRequest request) {
        Enterprise enterprise = enterpriseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Предприятие не найдено: " + id));
        if (!enterprise.getName().equals(request.getName())
                && enterpriseRepository.existsByName(request.getName())) {
            throw new RuntimeException("Предприятие с таким именем уже существует: " + request.getName());
        }
        enterprise.setName(request.getName());
        enterprise = enterpriseRepository.save(enterprise);
        return toDTO(enterprise);
    }

    @Transactional
    public void delete(Long id) {
        Enterprise enterprise = enterpriseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Предприятие не найдено: " + id));
        enterpriseRepository.delete(enterprise);
    }

    private EnterpriseFlatDto toDTO(Enterprise enterprise) {
        return new EnterpriseFlatDto(enterprise.getId(), enterprise.getName());
    }
}