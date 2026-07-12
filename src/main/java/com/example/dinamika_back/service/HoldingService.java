// HoldingService.java
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.CreateHoldingRequest;
import com.example.dinamika_back.dto.HoldingFlatDto;
import com.example.dinamika_back.dto.UpdateHoldingRequest;
import com.example.dinamika_back.model.Holding;
import com.example.dinamika_back.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HoldingService {

    private final HoldingRepository holdingRepository;

    public List<HoldingFlatDto> getAll() {
        return holdingRepository.findAllByOrderByNameAsc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public HoldingFlatDto getById(Long id) {
        Holding holding = holdingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Холдинг не найден: " + id));
        return toDTO(holding);
    }

    @Transactional
    public HoldingFlatDto create(CreateHoldingRequest request) {
        if (holdingRepository.existsByName(request.getName())) {
            throw new RuntimeException("Холдинг с таким именем уже существует: " + request.getName());
        }
        Holding holding = new Holding();
        holding.setName(request.getName());
        holding.setDescription(request.getDescription());
        holding = holdingRepository.save(holding);
        return toDTO(holding);
    }

    @Transactional
    public HoldingFlatDto update(Long id, UpdateHoldingRequest request) {
        Holding holding = holdingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Холдинг не найден: " + id));
        if (!holding.getName().equals(request.getName())
                && holdingRepository.existsByName(request.getName())) {
            throw new RuntimeException("Холдинг с таким именем уже существует: " + request.getName());
        }
        holding.setName(request.getName());
        holding.setDescription(request.getDescription());
        holding = holdingRepository.save(holding);
        return toDTO(holding);
    }

    @Transactional
    public void delete(Long id) {
        Holding holding = holdingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Холдинг не найден: " + id));
        holdingRepository.delete(holding);
    }

    private HoldingFlatDto toDTO(Holding holding) {
        return new HoldingFlatDto(holding.getId(), holding.getName(), holding.getDescription());
    }
}