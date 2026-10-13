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
    private final StockMovementRepository stockMovementRepository;
    private final DocTransferRepository docTransferRepository;
    private final DocRefillRepository docRefillRepository;

    // ==================== ИНИЦИАЛИЗАЦИЯ ====================

    /** Возвращает последнее состояние остатка по паре (material, stock) — или создаёт новое. */
    @Transactional
    public RegStockBalance ensureBalance(UUID materialUid, String stockUid) {
        String effectiveStock = (stockUid == null || stockUid.isBlank()) ? MAIN_STOCK : stockUid;

        Optional<RegStockBalance> last =
                balanceRepository.findTopByMaterialUidAndStockUidOrderByCreatedAtDesc(materialUid, effectiveStock);
        if (last.isPresent()) return last.get();

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

        RegStockBalance saved = balanceRepository.saveAndFlush(balance);

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

    /** Вставка новой записи состояния по паре (material, stock) с новыми clean/reserved. */
    private RegStockBalance pushNewBalance(RegStockBalance previous,
                                           int newClean,
                                           int newReserved,
                                           UUID materialUid,
                                           String stockUid) {
        SprMaterial material = previous != null
                ? previous.getMaterial()
                : materialRepository.findById(materialUid)
                        .orElseThrow(() -> new RuntimeException("Номенклатура не найдена: " + materialUid));

        Integer minStock = previous != null ? previous.getMinStock() : DEFAULT_MIN_STOCK;
        Integer criticalStock = previous != null ? previous.getCriticalStock() : DEFAULT_CRITICAL_STOCK;

        RegStockBalance next = RegStockBalance.builder()
                .material(material)
                .stockUid(stockUid)
                .cleanQuantity(newClean)
                .reservedQuantity(Math.max(0, newReserved))
                .minStock(minStock)
                .criticalStock(criticalStock)
                .build();

        return balanceRepository.save(next);
    }

    private RegStockBalance getLastBalance(UUID materialUid, String stockUid) {
        return balanceRepository.findTopByMaterialUidAndStockUidOrderByCreatedAtDesc(materialUid, stockUid)
                .orElse(null);
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
            reserveOne(docRefillUid, entry.getKey(), entry.getValue(), effectiveStock, author);
        }
    }

    private void reserveOne(UUID docRefillUid, UUID materialUid, int qty, String stockUid, String author) {
        if (qty <= 0) return;

        RegStockBalance previous = ensureBalance(materialUid, stockUid);

        int cleanBefore = previous.getCleanQuantity() != null ? previous.getCleanQuantity() : 0;
        int reservedBefore = previous.getReservedQuantity() != null ? previous.getReservedQuantity() : 0;
        int cleanAfter = cleanBefore - qty;
        int reservedAfter = reservedBefore + qty;

        // 1) детальная запись брони
        RegStockReservation reservation = RegStockReservation.builder()
                .docRefill(DocRefill.builder().uid(docRefillUid).build())
                .material(previous.getMaterial())
                .stockUid(stockUid)
                .reservedQuantity(qty)
                .blocked(false)
                .build();
        RegStockReservation savedRes = reservationRepository.save(reservation);

        // 2) НОВАЯ строка состояния (не update)
        RegStockBalance next = pushNewBalance(previous, cleanAfter, reservedAfter, materialUid, stockUid);

        // 3) НОВАЯ запись движения (DRAFT) — REFILL, списание с MAIN
        createRefillMovementDraft(docRefillUid, materialUid, stockUid, qty, author);

        logStockEvent(
                next.getUid(), savedRes.getUid(), materialUid, docRefillUid,
                "RESERVE",
                "Забронировано " + qty + " ед. под документ пополнения",
                "cleanQuantity", String.valueOf(cleanBefore), String.valueOf(cleanAfter),
                author
        );
    }

    // ==================== СНЯТИЕ БРОНИ ====================

    @Transactional
    public void releaseByDoc(UUID docRefillUid, String author) {
        if (docRefillUid == null) return;

        List<RegStockReservation> reservations = reservationRepository.findByDocRefillUid(docRefillUid);
        if (reservations.isEmpty()) return;

        for (RegStockReservation r : reservations) {
            if (Boolean.TRUE.equals(r.getBlocked())) continue;
            releaseOne(r, author);
        }

        reservationRepository.deleteActiveByDocRefillUid(docRefillUid);

        // Удаляем DRAFT-движения документа
        stockMovementRepository.deleteDraftByDocUid(docRefillUid);
    }

    private void releaseOne(RegStockReservation r, String author) {
        if (r == null || Boolean.TRUE.equals(r.getBlocked())) return;
        String stockUid = r.getStockUid() != null ? r.getStockUid() : MAIN_STOCK;
        UUID materialUid = r.getMaterial().getUid();
        int qty = r.getReservedQuantity() != null ? r.getReservedQuantity() : 0;

        RegStockBalance previous = getLastBalance(materialUid, stockUid);
        if (previous == null) return;

        int cleanBefore = previous.getCleanQuantity() != null ? previous.getCleanQuantity() : 0;
        int reservedBefore = previous.getReservedQuantity() != null ? previous.getReservedQuantity() : 0;
        int cleanAfter = cleanBefore + qty;
        int reservedAfter = Math.max(0, reservedBefore - qty);

        RegStockBalance next = pushNewBalance(previous, cleanAfter, reservedAfter, materialUid, stockUid);

        logStockEvent(
                next.getUid(), r.getUid(), materialUid,
                r.getDocRefill() != null ? r.getDocRefill().getUid() : null,
                "RELEASE",
                "Снята бронь " + qty + " ед.",
                "cleanQuantity", String.valueOf(cleanBefore), String.valueOf(cleanAfter),
                author
        );
    }

    // ==================== ПРАВКА ДОКУМЕНТА (diff) ====================

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

    private void adjustOne(RegStockReservation r, int delta, String author) {
        if (r == null || delta == 0 || Boolean.TRUE.equals(r.getBlocked())) return;
        String stockUid = r.getStockUid() != null ? r.getStockUid() : MAIN_STOCK;
        UUID materialUid = r.getMaterial().getUid();
        int currentQty = r.getReservedQuantity() != null ? r.getReservedQuantity() : 0;
        int newQty = Math.max(0, currentQty + delta);

        r.setReservedQuantity(newQty);
        reservationRepository.save(r);

        RegStockBalance previous = getLastBalance(materialUid, stockUid);
        if (previous == null) return;

        int cleanBefore = previous.getCleanQuantity() != null ? previous.getCleanQuantity() : 0;
        int reservedBefore = previous.getReservedQuantity() != null ? previous.getReservedQuantity() : 0;
        int cleanAfter = cleanBefore - delta;
        int reservedAfter = Math.max(0, reservedBefore + delta);

        RegStockBalance next = pushNewBalance(previous, cleanAfter, reservedAfter, materialUid, stockUid);

        logStockEvent(
                next.getUid(), r.getUid(), materialUid,
                r.getDocRefill() != null ? r.getDocRefill().getUid() : null,
                "ADJUST",
                "Изменение брони: " + currentQty + " → " + newQty,
                "reservedQuantity", String.valueOf(currentQty), String.valueOf(newQty),
                author
        );
    }

    // ==================== ПРОВЕДЕНИЕ ====================

    @Transactional
    public void commitByDoc(UUID docRefillUid, String author) {
        if (docRefillUid == null) return;

        List<RegStockReservation> reservations = reservationRepository.findByDocRefillUidAndBlockedFalse(docRefillUid);
        if (reservations.isEmpty()) return;

        // Группируем по номенклатуре (склад = MAIN, потому что все брони пополнения — на MAIN)
        Map<UUID, Integer> byMaterial = new LinkedHashMap<>();
        for (RegStockReservation r : reservations) {
            UUID materialUid = r.getMaterial().getUid();
            int qty = r.getReservedQuantity() != null ? r.getReservedQuantity() : 0;
            if (qty <= 0) continue;
            byMaterial.merge(materialUid, qty, Integer::sum);
        }
        if (byMaterial.isEmpty()) {
            reservationRepository.blockByDocRefillUid(docRefillUid);
            return;
        }

        // Получаем документ пополнения (для кода и для станции)
        DocRefill doc = docRefillRepository.findById(docRefillUid).orElse(null);
        String stationUid = doc != null ? doc.getStationUid() : null;
        Integer docCode = doc != null ? doc.getCode() : null;

        // === ОДИН документ перемещения на всё пополнение ===
        DocTransfer transfer = null;
        if (stationUid != null) {
            transfer = DocTransfer.builder()
                    .code(nextTransferCode())
                    .docDatetime(LocalDateTime.now())
                    .docBaseUid(docRefillUid)
                    .docBaseType("REFILL")
                    .stockFromUid(MAIN_STOCK)
                    .stockToUid(stationUid)
                    .status("POSTED")
                    .build();
            transfer = docTransferRepository.save(transfer);
        }

        for (Map.Entry<UUID, Integer> e : byMaterial.entrySet()) {
            UUID materialUid = e.getKey();
            int qty = e.getValue();

            // 1) MAIN: фиксируем состояние (clean уже уменьшен при брони, тут только reserved −qty)
            RegStockBalance previousMain = getLastBalance(materialUid, MAIN_STOCK);
            if (previousMain != null) {
                int cleanBefore = previousMain.getCleanQuantity() != null ? previousMain.getCleanQuantity() : 0;
                int reservedBefore = previousMain.getReservedQuantity() != null ? previousMain.getReservedQuantity() : 0;
                int reservedAfter = Math.max(0, reservedBefore - qty);

                RegStockBalance nextMain = pushNewBalance(previousMain, cleanBefore, reservedAfter, materialUid, MAIN_STOCK);

                logStockEvent(
                        nextMain.getUid(), null, materialUid, docRefillUid,
                        "COMMIT",
                        "Проведение документа: списана бронь " + qty + " ед. с основного склада",
                        "reservedQuantity", String.valueOf(reservedBefore), String.valueOf(reservedAfter),
                        author
                );
            }

            // 2) REFILL-движение на MAIN: DRAFT → POSTED (оставляем как историю пополнения)
            markRefillMovementPosted(docRefillUid, materialUid);

            // 3) Если есть станция — TRANSFER: два движения под одним doc_transfer
            if (stationUid != null && transfer != null) {
                // 3.1) списание с MAIN
                createMovementPosted(
                        transfer.getUid(), "TRANSFER", transfer.getCode(),
                        docRefillUid, docCode,
                        materialUid, MAIN_STOCK, -qty, author);

                // 3.2) приход на станцию
                createMovementPosted(
                        transfer.getUid(), "TRANSFER", transfer.getCode(),
                        docRefillUid, docCode,
                        materialUid, stationUid, +qty, author);

                // 3.3) новая запись состояния на станцию (+qty к clean)
                RegStockBalance prevStation = ensureBalance(materialUid, stationUid);
                int cleanStationBefore = prevStation.getCleanQuantity() != null ? prevStation.getCleanQuantity() : 0;
                int reservedStationBefore = prevStation.getReservedQuantity() != null ? prevStation.getReservedQuantity() : 0;
                int cleanStationAfter = cleanStationBefore + qty;

                RegStockBalance nextStation = pushNewBalance(
                        prevStation, cleanStationAfter, reservedStationBefore, materialUid, stationUid);

                logStockEvent(
                        nextStation.getUid(), null, materialUid, docRefillUid,
                        "TRANSFER_IN",
                        "Приход на станцию " + stationUid + " по перемещению " + transfer.getCode() + ": " + qty + " ед.",
                        "cleanQuantity", String.valueOf(cleanStationBefore), String.valueOf(cleanStationAfter),
                        author
                );
            }
        }

        // 4) закрываем все брони документа
        reservationRepository.blockByDocRefillUid(docRefillUid);
    }

    private Integer nextTransferCode() {
        Integer max = docTransferRepository.findMaxCode();
        return max != null ? max + 1 : 1;
    }

    // ==================== ДВИЖЕНИЯ (stock_movement) ====================

    /** DRAFT-движение по документу пополнения: списание с MAIN. */
    private void createRefillMovementDraft(UUID docRefillUid, UUID materialUid, String stockUid,
                                           int qty, String author) {
        SprMaterial material = materialRepository.findById(materialUid).orElse(null);
        if (material == null) return;

        DocRefill doc = docRefillRepository.findById(docRefillUid).orElse(null);

        StockMovement m = StockMovement.builder()
                .docUid(docRefillUid)
                .docType("REFILL")
                .docCode(doc != null ? doc.getCode() : null)
                .docBaseUid(null)
                .docBaseType(null)
                .stockUid(stockUid)
                .material(material)
                .quantity(-qty)
                .period(LocalDateTime.now())
                .isReusable(null)
                .isResharpen(null)
                .isScrap(null)
                .status("DRAFT")
                .build();

        stockMovementRepository.save(m);
    }

    /** Меняем статус DRAFT-движения пополнения на POSTED. */
    private void markRefillMovementPosted(UUID docRefillUid, UUID materialUid) {
        List<StockMovement> movements = stockMovementRepository.findByDocUidAndStatus(docRefillUid, "DRAFT");
        for (StockMovement m : movements) {
            if (m.getMaterial() != null && m.getMaterial().getUid().equals(materialUid)) {
                m.setStatus("POSTED");
                m.setPeriod(LocalDateTime.now());
                stockMovementRepository.save(m);
            }
        }
    }

    /** POSTED-движение по перемещению (одно из двух: MAIN −qty или станция +qty). */
    private void createMovementPosted(UUID transferUid, String docType, Integer docCode,
                                      UUID docRefillUid, Integer docRefillCode,
                                      UUID materialUid, String stockUid, int qty,
                                      String author) {
        SprMaterial material = materialRepository.findById(materialUid).orElse(null);
        if (material == null) return;

        StockMovement m = StockMovement.builder()
                .docUid(transferUid)
                .docType(docType)
                .docCode(docCode)
                .docBaseUid(docRefillUid)
                .docBaseType("REFILL")
                .stockUid(stockUid)
                .material(material)
                .quantity(qty)
                .period(LocalDateTime.now())
                .isReusable(null)
                .isResharpen(null)
                .isScrap(null)
                .status("POSTED")
                .build();

        stockMovementRepository.save(m);
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

    @Transactional(readOnly = true)
    public StockBalanceDto getBalanceByMaterial(UUID materialUid, String stockUid) {
        if (materialUid == null) return null;
        String effectiveStock = (stockUid == null || stockUid.isBlank()) ? MAIN_STOCK : stockUid;
        return balanceRepository
                .findTopByMaterialUidAndStockUidOrderByCreatedAtDesc(materialUid, effectiveStock)
                .map(this::toBalanceDto)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<UUID, StockBalanceDto> getBalancesMapByMaterials(List<UUID> materialUids, String stockUid) {
        if (materialUids == null || materialUids.isEmpty()) return Collections.emptyMap();
        String effectiveStock = (stockUid == null || stockUid.isBlank()) ? MAIN_STOCK : stockUid;
        Map<UUID, StockBalanceDto> result = new LinkedHashMap<>();
        for (UUID materialUid : materialUids) {
            balanceRepository.findTopByMaterialUidAndStockUidOrderByCreatedAtDesc(materialUid, effectiveStock)
                    .ifPresent(b -> result.put(materialUid, toBalanceDto(b)));
        }
        return result;
    }

    public Integer getAvailableQuantity(UUID materialUid, String stockUid) {
        String effectiveStock = (stockUid == null || stockUid.isBlank()) ? MAIN_STOCK : stockUid;
        return balanceRepository.findTopByMaterialUidAndStockUidOrderByCreatedAtDesc(materialUid, effectiveStock)
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