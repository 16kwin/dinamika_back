// AWMS — service/TkpService.java — ПОЛНЫЙ ФАЙЛ
package com.example.dinamika_back.service;

import com.example.dinamika_back.model.*;
import com.example.dinamika_back.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TkpService {

    private final TkpListRepository tkpListRepository;
    private final TkpFullRepository tkpFullRepository;
    private final TkpStatusRepository tkpStatusRepository;
    private final OrdersListRepository ordersListRepository;
    private final OrderStatusRepository orderStatusRepository;
    private final OrderTrackingRepository orderTrackingRepository;
    private final RestTemplate restTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Value("${sync.service.url}")
    private String syncServiceUrl;

    @Value("${api.key.awms}")
    private String apiKey;

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);
        return headers;
    }

    public List<Map<String, Object>> getActiveTkp() {
        return tkpListRepository.findByStatus("active")
                .stream().map(this::toTkpListMap).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getClosedTkp() {
        return tkpListRepository.findByStatus("closed")
                .stream().map(this::toTkpListMap).collect(Collectors.toList());
    }

    public Map<String, Object> getTkp(String tkpUid) {
        Optional<TkpFull> fullOpt = tkpFullRepository.findByTkpUid(tkpUid);
        if (fullOpt.isPresent()) {
            try {
                Map<String, Object> tkp = objectMapper.readValue(fullOpt.get().getTkpJson(), Map.class);
                tkp.put("status", getLatestTkpStatus(tkpUid));
                tkp.put("statusinvoice", getLatestStatusInvoice(tkpUid));
                return tkp;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new LinkedHashMap<>();
    }

    @Transactional
    public void saveTkpLocally(String tkpUid, Map<String, Object> tkpData) {
        TkpList tkpList = TkpList.builder()
                .tkpUid(tkpUid)
                .orderUid((String) tkpData.get("order_uid"))
                .customerId((String) tkpData.get("customer"))
                .orderNumber((String) tkpData.get("tkp_number"))
                .orderDatetime(parseDateTime((String) tkpData.get("tkp_data")))
                .totalCost(tkpData.get("total_cost") != null ? new BigDecimal(tkpData.get("total_cost").toString()) : null)
                .deliveryDate(tkpData.get("delivery_date") != null ? LocalDate.parse(tkpData.get("delivery_date").toString()) : null)
                .status((String) tkpData.getOrDefault("status", "active"))
                .statusinvoice((String) tkpData.get("statusinvoice"))
                .syncedAt(ZonedDateTime.now())
                .build();
        tkpListRepository.save(tkpList);

        try {
            String json = objectMapper.writeValueAsString(tkpData);
            TkpFull tkpFull = TkpFull.builder()
                    .tkpUid(tkpUid)
                    .orderUid((String) tkpData.get("order_uid"))
                    .tkpJson(json)
                    .build();
            tkpFullRepository.save(tkpFull);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Transactional
    public Map<String, Object> receiveTkp(String tkpUid, Map<String, Object> request) {
        saveTkpLocally(tkpUid, request);

        TkpStatus tkpStatus = TkpStatus.builder()
                .tkpUid(tkpUid)
                .orderUid((String) request.get("order_uid"))
                .datetime(ZonedDateTime.now())
                .build();
        tkpStatusRepository.save(tkpStatus);

        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("tkp_uid", tkpUid);
        notification.put("order_uid", request.get("order_uid"));
        notification.put("type", "tkp_new");
        messagingTemplate.convertAndSend("/topic/tkp/new", notification);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tkp_uid", tkpUid);
        response.put("status", "received");
        response.put("message", "ТКП получен");
        return response;
    }

    @Transactional
    public void receiveTkpStatusUpdate(String tkpUid, String statusinvoice) {
        TkpStatus tkpStatus = TkpStatus.builder()
                .tkpUid(tkpUid)
                .subStatus(statusinvoice)
                .datetime(ZonedDateTime.now())
                .build();
        tkpStatusRepository.save(tkpStatus);

        // При inrealise автоматически ставим трек notinwork
        if ("inrealise".equals(statusinvoice)) {
            tkpListRepository.findById(tkpUid).ifPresent(tkp -> {
                String orderUid = tkp.getOrderUid();
                if (orderUid != null) {
                    OrderTracking tracking = OrderTracking.builder()
                            .orderUid(orderUid)
                            .trackingStatus("notinwork")
                            .datetime(ZonedDateTime.now())
                            .build();
                    orderTrackingRepository.save(tracking);
                }
            });
        }

        tkpListRepository.findById(tkpUid).ifPresent(tkp -> {
            tkp.setStatusinvoice(statusinvoice);
            String orderUid = tkp.getOrderUid();
            
            if ("paid".equals(statusinvoice) || "unpaid".equals(statusinvoice) || 
                "cancelcustomer".equals(statusinvoice) || "cancelprovider".equals(statusinvoice)) {
                tkp.setStatus("closed");
                
                if (orderUid != null) {
                    String orderSubStatus = "paid".equals(statusinvoice) ? "done" : statusinvoice;
                    OrderStatus orderStatus = OrderStatus.builder()
                            .orderUid(orderUid)
                            .status("closed")
                            .subStatus(orderSubStatus)
                            .datetime(ZonedDateTime.now())
                            .build();
                    orderStatusRepository.save(orderStatus);
                    
                    ordersListRepository.findById(orderUid).ifPresent(order -> {
                        order.setStatus("closed");
                        order.setStatusreason(orderSubStatus);
                        ordersListRepository.save(order);
                    });
                }
            }
            tkpListRepository.save(tkp);
        });

        messagingTemplate.convertAndSend("/topic/tkp/status", Map.of("tkp_uid", tkpUid, "status", statusinvoice));
        messagingTemplate.convertAndSend("/topic/orders/refresh", Map.of("type", "refresh"));
    }

    @Transactional
    public Map<String, Object> confirmTkp(String tkpUid, Map<String, Object> request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-Key", apiKey);
            Map<String, Object> body = Map.of("statusinvoice", "accept");
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.exchange(syncServiceUrl + "/v1/tkp/" + tkpUid + "/statusinvoice", HttpMethod.POST, entity, String.class);

            receiveTkpStatusUpdate(tkpUid, "accept");
        } catch (Exception e) {
            e.printStackTrace();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tkp_uid", tkpUid);
        response.put("status", "confirmed");
        response.put("message", "ТКП подтверждён");
        return response;
    }

    @Transactional
    public Map<String, Object> cancelTkp(String tkpUid) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-Key", apiKey);
            Map<String, Object> body = Map.of("statusinvoice", "cancelcustomer");
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.exchange(syncServiceUrl + "/v1/tkp/" + tkpUid + "/statusinvoice", HttpMethod.POST, entity, String.class);

            receiveTkpStatusUpdate(tkpUid, "cancelcustomer");
        } catch (Exception e) {
            e.printStackTrace();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tkp_uid", tkpUid);
        response.put("status", "cancelled");
        response.put("message", "ТКП отклонён");
        return response;
    }

    private String getLatestTkpStatus(String tkpUid) {
        List<TkpStatus> statuses = tkpStatusRepository.findByTkpUidOrderByDatetimeDesc(tkpUid);
        String subStatus = statuses.stream().filter(s -> s.getSubStatus() != null).findFirst().map(TkpStatus::getSubStatus).orElse(null);
        if (subStatus == null) return "active";
        if ("paid".equals(subStatus) || "unpaid".equals(subStatus) || "cancelcustomer".equals(subStatus) || "cancelprovider".equals(subStatus)) return "closed";
        return "active";
    }

    private String getLatestStatusInvoice(String tkpUid) {
        List<TkpStatus> statuses = tkpStatusRepository.findByTkpUidOrderByDatetimeDesc(tkpUid);
        return statuses.stream().filter(s -> s.getSubStatus() != null).findFirst().map(TkpStatus::getSubStatus).orElse(null);
    }

    private Map<String, Object> toTkpListMap(TkpList tkp) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tkp_uid", tkp.getTkpUid());
        map.put("order_uid", tkp.getOrderUid());
        map.put("customer_id", tkp.getCustomerId());
        map.put("tkp_number", tkp.getOrderNumber());
        map.put("tkp_datetime", tkp.getOrderDatetime() != null ? tkp.getOrderDatetime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null);
        map.put("delivery_date", tkp.getDeliveryDate() != null ? tkp.getDeliveryDate().toString() : null);
        map.put("total_cost", tkp.getTotalCost());
        map.put("status", tkp.getStatus());
        map.put("statusinvoice", tkp.getStatusinvoice());
        return map;
    }

    private ZonedDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null) return null;
        try {
            return ZonedDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }
}