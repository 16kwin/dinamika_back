// EnterpriseService.java
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.CreateEnterpriseRequest;
import com.example.dinamika_back.dto.EnterpriseFlatDto;
import com.example.dinamika_back.dto.UpdateEnterpriseRequest;
import com.example.dinamika_back.model.Enterprise;
import com.example.dinamika_back.model.Holding;
import com.example.dinamika_back.repository.EnterpriseRepository;
import com.example.dinamika_back.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnterpriseService {

    private final EnterpriseRepository enterpriseRepository;
    private final HoldingRepository holdingRepository;

    public List<EnterpriseFlatDto> getAll() {
        return enterpriseRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<EnterpriseFlatDto> getByHoldingId(Long holdingId) {
        return enterpriseRepository.findByHoldingIdOrderByNameAsc(holdingId).stream()
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
        if (request.getHoldingId() != null) {
            Holding holding = holdingRepository.findById(request.getHoldingId())
                    .orElseThrow(() -> new RuntimeException("Холдинг не найден: " + request.getHoldingId()));
            enterprise.setHolding(holding);
        }
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
        if (request.getHoldingId() != null) {
            Holding holding = holdingRepository.findById(request.getHoldingId())
                    .orElseThrow(() -> new RuntimeException("Холдинг не найден: " + request.getHoldingId()));
            enterprise.setHolding(holding);
        } else {
            enterprise.setHolding(null);
        }
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
        Long holdingId = enterprise.getHolding() != null ? enterprise.getHolding().getId() : null;
        String holdingName = enterprise.getHolding() != null ? enterprise.getHolding().getName() : null;
        return new EnterpriseFlatDto(enterprise.getId(), enterprise.getName(), holdingId, holdingName);
    }
}