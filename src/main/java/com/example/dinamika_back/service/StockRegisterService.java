// StockRegisterService.java
package com.example.dinamika_back.service;

import com.example.dinamika_back.dto.*;
import com.example.dinamika_back.model.*;
import com.example.dinamika_back.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockRegisterService {

    public static final String MAIN_STOCK = "MAIN";

    private static final int DEFAULT_INITIAL_STOCK = 50;
    private static final Integer DEFAULT_MIN_STOCK = 20;
    private static final Integer DEFAULT_CRITICAL_STOCK = 10;

    private final RegStockBalanceRepository balanceRepository;
    private final RegStockReservationRepository reservationRepository;
    private final StockEventLogRepository eventLogRepository;
    private final SprMaterialRepository materialRepository;
    private final StationRepository stationRepository;

    // ==================== ИНИЦИАЛИЗАЦИЯ ====================

    @Transactional
    public RegStockBalance ensureBalance(UUID materialUid, String stockUid) {
        String effectiveStock = (stockUid == null || stockUid.isBlank()) ? MAIN_STOCK : stockUid;

        Optional<RegStockBalance> existing =
                balanceRepository.findByMaterialUidAndStockUid(materialUid, effectiveStock);
        if (existing.isPresent()) return existing.get();

        SprMaterial material = materialRepository.findById(materialUid)
                .orElseThrow(() -> new RuntimeException("Номенклатура не найдена: " + materialUid));

        RegStockBalance balance = RegStockBalance.builder()
                .material(material)
                .stockUid(effectiveStock)
                .cleanQuantity(DEFAULT_INITIAL_STOCK)
                .reservedQuantity(0)
                .minStock(DEFAULT_MIN_STOCK)
                .criticalStock(DEFAULT_CRITICAL_STOCK)
                .build();

        RegStockBalance saved;
        try {
            saved = balanceRepository.saveAndFlush(balance);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            saved = balanceRepository.findByMaterialUidAndStockUid(materialUid, effectiveStock)
                    .orElseThrow(() -> e);
            return saved;
        }

        logStockEvent(
                saved.getUid(), null, materialUid, null,
                "INIT",
                "Инициализация остатка номенклатуры: " + DEFAULT_INITIAL_STOCK + " ед. (мин. "
                        + DEFAULT_MIN_STOCK + ", крит. " + DEFAULT_CRITICAL_STOCK + ")",
                "cleanQuantity", "0", String.valueOf(DEFAULT_INITIAL_STOCK),
                "Система"
        );

        return saved;
    }

    // ==================== БРОНИРОВАНИЕ ====================

    @Transactional
    public void reserve(UUID docRefillUid, String stockUid, List<StockReserveItem> items, String author) {
        if (docRefillUid == null || items == null || items.isEmpty()) return;

        String effectiveStock = (stockUid == null || stockUid.isBlank()) ? MAIN_STOCK : stockUid;

        Map<UUID, Integer> grouped = new LinkedHashMap<>();
        for (StockReserveItem item : items) {
            if (item == null || item.getMaterialUid() == null) continue;
            int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            if (qty <= 0) continue;
            grouped.merge(item.getMaterialUid(), qty, Integer::sum);
        }

        if (grouped.isEmpty()) return;

        for (Map.Entry<UUID, Integer> entry : grouped.entrySet()) {
            UUID materialUid = entry.getKey();
            int qty = entry.getValue();

            RegStockBalance balance = ensureBalance(materialUid, effectiveStock);

            RegStockReservation reservation = RegStockReservation.builder()
                    .docRefill(DocRefill.builder().uid(docRefillUid).build())
                    .material(balance.getMaterial())
                    .stockUid(effectiveStock)
                    .reservedQuantity(qty)
                    .blocked(false)
                    .build();
            RegStockReservation savedRes = reservationRepository.save(reservation);

            int cleanBefore = balance.getCleanQuantity() != null ? balance.getCleanQuantity() : 0;
            int reservedBefore = balance.getReservedQuantity() != null ? balance.getReservedQuantity() : 0;

            balance.setCleanQuantity(cleanBefore - qty);
            balance.setReservedQuantity(reservedBefore + qty);
            balanceRepository.save(balance);

            logStockEvent(
                    balance.getUid(), savedRes.getUid(), materialUid, docRefillUid,
                    "RESERVE",
                    "Забронировано " + qty + " ед. под документ пополнения",
                    "cleanQuantity", String.valueOf(cleanBefore), String.valueOf(cleanBefore - qty),
                    author
            );
        }
    }

    // ==================== СНЯТИЕ БРОНИ ====================

    @Transactional
    public void releaseByDoc(UUID docRefillUid, String author) {
        if (docRefillUid == null) return;

        List<RegStockReservation> reservations = reservationRepository.findByDocRefillUid(docRefillUid);
        if (reservations.isEmpty()) return;

        for (RegStockReservation r : reservations) {
            if (Boolean.TRUE.equals(r.getBlocked())) continue;

            String stockUid = r.getStockUid() != null ? r.getStockUid() : MAIN_STOCK;
            UUID materialUid = r.getMaterial().getUid();
            int qty = r.getReservedQuantity() != null ? r.getReservedQuantity() : 0;

            balanceRepository.findByMaterialUidAndStockUid(materialUid, stockUid).ifPresent(balance -> {
                int cleanBefore = balance.getCleanQuantity() != null ? balance.getCleanQuantity() : 0;
                int reservedBefore = balance.getReservedQuantity() != null ? balance.getReservedQuantity() : 0;

                balance.setCleanQuantity(cleanBefore + qty);
                balance.setReservedQuantity(Math.max(0, reservedBefore - qty));
                balanceRepository.save(balance);

                logStockEvent(
                        balance.getUid(), r.getUid(), materialUid, docRefillUid,
                        "RELEASE",
                        "Снята бронь " + qty + " ед.",
                        "cleanQuantity", String.valueOf(cleanBefore), String.valueOf(cleanBefore + qty),
                        author
                );
            });
        }

        reservationRepository.deleteActiveByDocRefillUid(docRefillUid);
    }

    // ==================== ПРАВКА ДОКУМЕНТА (только diff) ====================

    @Transactional
    public void rebuildForDoc(UUID docRefillUid, String stockUid, List<StockReserveItem> items, String author) {
        if (docRefillUid == null) return;

        String effectiveStock = (stockUid == null || stockUid.isBlank()) ? MAIN_STOCK : stockUid;

        Map<UUID, Integer> desired = new LinkedHashMap<>();
        if (items != null) {
            for (StockReserveItem item : items) {
                if (item == null || item.getMaterialUid() == null) continue;
                int qty = item.getQuantity() != null ? item.getQuantity() : 0;
                if (qty <= 0) continue;
                desired.merge(item.getMaterialUid(), qty, Integer::sum);
            }
        }

        List<RegStockReservation> current = reservationRepository.findByDocRefillUidAndBlockedFalse(docRefillUid);
        Map<UUID, RegStockReservation> currentByMaterial = new LinkedHashMap<>();
        for (RegStockReservation r : current) {
            UUID mUid = r.getMaterial().getUid();
            currentByMaterial.merge(mUid, r, (a, b) -> {
                a.setReservedQuantity((a.getReservedQuantity() == null ? 0 : a.getReservedQuantity())
                        + (b.getReservedQuantity() == null ? 0 : b.getReservedQuantity()));
                return a;
            });
        }

        for (Map.Entry<UUID, RegStockReservation> e : currentByMaterial.entrySet()) {
            UUID mUid = e.getKey();
            if (!desired.containsKey(mUid)) {
                RegStockReservation r = e.getValue();
                releaseOne(r, author);
                reservationRepository.delete(r);
            }
        }

        for (Map.Entry<UUID, Integer> e : desired.entrySet()) {
            UUID mUid = e.getKey();
            int targetQty = e.getValue();
            RegStockReservation existing = currentByMaterial.get(mUid);

            if (existing == null) {
                reserveOne(docRefillUid, mUid, targetQty, effectiveStock, author);
            } else {
                int currentQty = existing.getReservedQuantity() != null ? existing.getReservedQuantity() : 0;
                if (currentQty != targetQty) {
                    adjustOne(existing, targetQty - currentQty, author);
                }
            }
        }
    }

    private void reserveOne(UUID docRefillUid, UUID materialUid, int qty, String stockUid, String author) {
        if (qty <= 0) return;
        RegStockBalance balance = ensureBalance(materialUid, stockUid);

        RegStockReservation reservation = RegStockReservation.builder()
                .docRefill(DocRefill.builder().uid(docRefillUid).build())
                .material(balance.getMaterial())
                .stockUid(stockUid)
                .reservedQuantity(qty)
                .blocked(false)
                .build();
        RegStockReservation savedRes = reservationRepository.save(reservation);

        int cleanBefore = balance.getCleanQuantity() != null ? balance.getCleanQuantity() : 0;
        int reservedBefore = balance.getReservedQuantity() != null ? balance.getReservedQuantity() : 0;

        balance.setCleanQuantity(cleanBefore - qty);
        balance.setReservedQuantity(reservedBefore + qty);
        balanceRepository.save(balance);

        logStockEvent(
                balance.getUid(), savedRes.getUid(), materialUid, docRefillUid,
                "RESERVE",
                "Забронировано " + qty + " ед. под документ пополнения",
                "cleanQuantity", String.valueOf(cleanBefore), String.valueOf(cleanBefore - qty),
                author
        );
    }

    private void releaseOne(RegStockReservation r, String author) {
        if (r == null || Boolean.TRUE.equals(r.getBlocked())) return;
        String stockUid = r.getStockUid() != null ? r.getStockUid() : MAIN_STOCK;
        UUID materialUid = r.getMaterial().getUid();
        int qty = r.getReservedQuantity() != null ? r.getReservedQuantity() : 0;

        balanceRepository.findByMaterialUidAndStockUid(materialUid, stockUid).ifPresent(balance -> {
            int cleanBefore = balance.getCleanQuantity() != null ? balance.getCleanQuantity() : 0;
            int reservedBefore = balance.getReservedQuantity() != null ? balance.getReservedQuantity() : 0;

            balance.setCleanQuantity(cleanBefore + qty);
            balance.setReservedQuantity(Math.max(0, reservedBefore - qty));
            balanceRepository.save(balance);

            logStockEvent(
                    balance.getUid(), r.getUid(), materialUid, r.getDocRefill() != null ? r.getDocRefill().getUid() : null,
                    "RELEASE",
                    "Снята бронь " + qty + " ед.",
                    "cleanQuantity", String.valueOf(cleanBefore), String.valueOf(cleanBefore + qty),
                    author
            );
        });
    }

    private void adjustOne(RegStockReservation r, int delta, String author) {
        if (r == null || delta == 0 || Boolean.TRUE.equals(r.getBlocked())) return;
        String stockUid = r.getStockUid() != null ? r.getStockUid() : MAIN_STOCK;
        UUID materialUid = r.getMaterial().getUid();
        int currentQty = r.getReservedQuantity() != null ? r.getReservedQuantity() : 0;
        int newQty = Math.max(0, currentQty + delta);

        r.setReservedQuantity(newQty);
        reservationRepository.save(r);

        balanceRepository.findByMaterialUidAndStockUid(materialUid, stockUid).ifPresent(balance -> {
            int cleanBefore = balance.getCleanQuantity() != null ? balance.getCleanQuantity() : 0;
            int reservedBefore = balance.getReservedQuantity() != null ? balance.getReservedQuantity() : 0;

            balance.setCleanQuantity(cleanBefore - delta);
            balance.setReservedQuantity(Math.max(0, reservedBefore + delta));
            balanceRepository.save(balance);

            logStockEvent(
                    balance.getUid(), r.getUid(), materialUid, r.getDocRefill() != null ? r.getDocRefill().getUid() : null,
                    "ADJUST",
                    "Изменение брони: " + currentQty + " → " + newQty,
                    "reservedQuantity", String.valueOf(currentQty), String.valueOf(newQty),
                    author
            );
        });
    }

    // ==================== ПРОВЕДЕНИЕ ====================

    @Transactional
    public void commitByDoc(UUID docRefillUid, String author) {
        if (docRefillUid == null) return;

        List<RegStockReservation> reservations = reservationRepository.findByDocRefillUidAndBlockedFalse(docRefillUid);
        if (reservations.isEmpty()) return;

        Map<String, Map<UUID, Integer>> byStockMaterial = new LinkedHashMap<>();
        for (RegStockReservation r : reservations) {
            String stockUid = r.getStockUid() != null ? r.getStockUid() : MAIN_STOCK;
            UUID materialUid = r.getMaterial().getUid();
            int qty = r.getReservedQuantity() != null ? r.getReservedQuantity() : 0;
            if (qty <= 0) continue;
            byStockMaterial
                    .computeIfAbsent(stockUid, k -> new LinkedHashMap<>())
                    .merge(materialUid, qty, Integer::sum);
        }

        for (Map.Entry<String, Map<UUID, Integer>> stockEntry : byStockMaterial.entrySet()) {
            String stockUid = stockEntry.getKey();
            for (Map.Entry<UUID, Integer> matEntry : stockEntry.getValue().entrySet()) {
                UUID materialUid = matEntry.getKey();
                int qty = matEntry.getValue();

                balanceRepository.findByMaterialUidAndStockUid(materialUid, stockUid).ifPresent(balance -> {
                    int reservedBefore = balance.getReservedQuantity() != null ? balance.getReservedQuantity() : 0;
                    balance.setReservedQuantity(Math.max(0, reservedBefore - qty));
                    balanceRepository.save(balance);

                    logStockEvent(
                            balance.getUid(), null, materialUid, docRefillUid,
                            "COMMIT",
                            "Проведение документа: списана бронь " + qty + " ед.",
                            "reservedQuantity", String.valueOf(reservedBefore),
                            String.valueOf(Math.max(0, reservedBefore - qty)),
                            author
                    );
                });
            }
        }

        reservationRepository.blockByDocRefillUid(docRefillUid);
    }

    // ==================== ЧТЕНИЕ ====================

    public List<StockBalanceDto> getBalances(String stockUid) {
        String effectiveStock = (stockUid == null || stockUid.isBlank()) ? MAIN_STOCK : stockUid;
        return balanceRepository.findByStockUidWithMaterial(effectiveStock).stream()
                .map(this::toBalanceDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StockReservationDto> getReservationsByDoc(UUID docRefillUid) {
        return reservationRepository.findByDocRefillUidWithMaterial(docRefillUid).stream()
                .map(this::toReservationDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StockReservationDto> getActiveReservationsByMaterial(UUID materialUid, String stockUid) {
        if (materialUid == null) return Collections.emptyList();
        String effectiveStock = (stockUid == null || stockUid.isBlank()) ? MAIN_STOCK : stockUid;
        return reservationRepository
                .findActiveByMaterialWithRelations(materialUid, effectiveStock)
                .stream()
                .map(this::toReservationDto)
                .collect(Collectors.toList());
    }

    public List<StockEventLogDto> getEventsByDoc(UUID docRefillUid) {
        return eventLogRepository.findByDocRefillUidOrderByCreatedAtDesc(docRefillUid).stream()
                .map(this::toEventDto)
                .collect(Collectors.toList());
    }

    /** Полная информация по складу и номенклатуре: остаток + пороги. */
    @Transactional(readOnly = true)
    public StockBalanceDto getBalanceByMaterial(UUID materialUid, String stockUid) {
        if (materialUid == null) return null;
        String effectiveStock = (stockUid == null || stockUid.isBlank()) ? MAIN_STOCK : stockUid;
        return balanceRepository.findByMaterialUidAndStockUid(materialUid, effectiveStock)
                .map(this::toBalanceDto)
                .orElse(null);
    }

    /** Карта полных данных (остаток + пороги) по списку номенклатур. */
    @Transactional(readOnly = true)
    public Map<UUID, StockBalanceDto> getBalancesMapByMaterials(List<UUID> materialUids, String stockUid) {
        if (materialUids == null || materialUids.isEmpty()) return Collections.emptyMap();
        String effectiveStock = (stockUid == null || stockUid.isBlank()) ? MAIN_STOCK : stockUid;
        Map<UUID, StockBalanceDto> result = new LinkedHashMap<>();
        for (UUID materialUid : materialUids) {
            balanceRepository.findByMaterialUidAndStockUid(materialUid, effectiveStock)
                    .ifPresent(b -> result.put(materialUid, toBalanceDto(b)));
        }
        return result;
    }

    public Integer getAvailableQuantity(UUID materialUid, String stockUid) {
        String effectiveStock = (stockUid == null || stockUid.isBlank()) ? MAIN_STOCK : stockUid;
        return balanceRepository.findByMaterialUidAndStockUid(materialUid, effectiveStock)
                .map(RegStockBalance::getCleanQuantity)
                .orElse(DEFAULT_INITIAL_STOCK);
    }

    public Integer getAvailableQuantitySafe(UUID materialUid, String stockUid) {
        if (materialUid == null) return null;
        return getAvailableQuantity(materialUid, stockUid);
    }

    public Map<UUID, Integer> getAvailableMap(List<UUID> materialUids, String stockUid) {
        if (materialUids == null || materialUids.isEmpty()) return Collections.emptyMap();
        String effectiveStock = (stockUid == null || stockUid.isBlank()) ? MAIN_STOCK : stockUid;
        Map<UUID, Integer> result = new LinkedHashMap<>();
        for (UUID materialUid : materialUids) {
            result.put(materialUid, getAvailableQuantity(materialUid, effectiveStock));
        }
        return result;
    }

    // ==================== ПРИВАТНОЕ ====================

    private void logStockEvent(UUID balanceUid, UUID reservationUid, UUID materialUid, UUID docRefillUid,
                               String eventType, String description,
                               String fieldName, String oldValue, String newValue, String author) {
        StockEventLog logEntry = StockEventLog.builder()
                .balanceUid(balanceUid)
                .reservationUid(reservationUid)
                .materialUid(materialUid)
                .docRefillUid(docRefillUid)
                .eventType(eventType)
                .eventDescription(description)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .author(author)
                .source("Через карточку")
                .createdAt(LocalDateTime.now())
                .build();
        eventLogRepository.save(logEntry);
    }

    private String buildUserName(User u) {
        if (u == null) return null;
        StringBuilder sb = new StringBuilder();
        if (u.getLastName() != null) sb.append(u.getLastName()).append(" ");
        if (u.getFirstName() != null) sb.append(u.getFirstName()).append(" ");
        if (u.getMiddleName() != null) sb.append(u.getMiddleName());
        String s = sb.toString().trim();
        return s.isEmpty() ? u.getUsername() : s;
    }

    private StockBalanceDto toBalanceDto(RegStockBalance b) {
        SprMaterial m = b.getMaterial();
        return StockBalanceDto.builder()
                .uid(b.getUid())
                .materialUid(m != null ? m.getUid() : null)
                .materialName(m != null ? m.getNameMaterial() : null)
                .materialArticle(m != null ? m.getArticle() : null)
                .materialCode(m != null ? m.getCodeMaterial() : null)
                .stockUid(b.getStockUid())
                .cleanQuantity(b.getCleanQuantity())
                .reservedQuantity(b.getReservedQuantity())
                .periodTotal(b.getPeriodTotal())
                .minStock(b.getMinStock())
                .criticalStock(b.getCriticalStock())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }

    private StockReservationDto toReservationDto(RegStockReservation r) {
        SprMaterial m = r.getMaterial();
        DocRefill doc = r.getDocRefill();

        String author = null;
        String authorFullName = null;
        String docStationUid = null;
        String docStationName = null;
        String docSectionName = null;

        if (doc != null) {
            User createdBy = doc.getCreatedBy();
            if (createdBy != null) {
                author = createdBy.getUsername();
                authorFullName = buildUserName(createdBy);
            }

            docStationUid = doc.getStationUid();
            Station station = doc.getStation();
            if (station == null && docStationUid != null) {
                station = stationRepository.findByUid(docStationUid).orElse(null);
            }
            if (station != null) {
                docStationName = station.getName();
                if (station.getSection() != null) {
                    docSectionName = station.getSection().getName();
                }
            }
        }

        return StockReservationDto.builder()
                .uid(r.getUid())
                .docRefillUid(doc != null ? doc.getUid() : null)
                .docRefillCode(doc != null ? doc.getCode() : null)
                .materialUid(m != null ? m.getUid() : null)
                .materialName(m != null ? m.getNameMaterial() : null)
                .materialArticle(m != null ? m.getArticle() : null)
                .materialCode(m != null ? m.getCodeMaterial() : null)
                .stockUid(r.getStockUid())
                .reservedQuantity(r.getReservedQuantity())
                .blocked(r.getBlocked())
                .author(author)
                .authorFullName(authorFullName)
                .docStationUid(docStationUid)
                .docStationName(docStationName)
                .docSectionName(docSectionName)
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    private StockEventLogDto toEventDto(StockEventLog e) {
        return StockEventLogDto.builder()
                .uid(e.getUid())
                .balanceUid(e.getBalanceUid())
                .reservationUid(e.getReservationUid())
                .materialUid(e.getMaterialUid())
                .docRefillUid(e.getDocRefillUid())
                .eventType(e.getEventType())
                .eventDescription(e.getEventDescription())
                .fieldName(e.getFieldName())
                .oldValue(e.getOldValue())
                .newValue(e.getNewValue())
                .author(e.getAuthor())
                .source(e.getSource())
                .createdAt(e.getCreatedAt())
                .build();
    }
}