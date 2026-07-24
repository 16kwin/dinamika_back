// AWMS — service/OrderService.java — ПОЛНЫЙ ФАЙЛ (трек read-only, нельзя менять)
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrdersListRepository ordersListRepository;
    private final OrdersFullRepository ordersFullRepository;
    private final OrderStatusRepository orderStatusRepository;
    private final OrderTrackingRepository orderTrackingRepository;
    private final SprMaterialRepository materialRepository;
    private final SprMaterialImageRepository imageRepository;
    private final SprMaterialBlueprintRepository blueprintRepository;
    private final SprMaterialCodeRepository codeRepository;
    private final RegAttributesRepository regAttributesRepository;
    private final RegAnalogRepository regAnalogRepository;
    private final RestTemplate restTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Value("${sync.service.url}")
    private String syncServiceUrl;

    @Value("${api.key.awms}")
    private String apiKey;

    private static final String UPLOAD_DIR = "uploads/nomenclature/";

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // Читаем из локальной базы
    public List<Map<String, Object>> getActiveOrders() {
        return ordersListRepository.findByStatusIn(Arrays.asList("active", "processed"))
                .stream().map(this::toOrderListMap).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getClosedOrders() {
        return ordersListRepository.findByStatus("closed")
                .stream().map(this::toOrderListMap).collect(Collectors.toList());
    }

    public Map<String, Object> getOrder(String orderUid) {
        Optional<OrdersFull> fullOpt = ordersFullRepository.findByOrderUid(orderUid);
        if (fullOpt.isPresent()) {
            try {
                Map<String, Object> order = objectMapper.readValue(fullOpt.get().getOrderJson(), Map.class);
                order.put("statustrack", getLatestTrackingStatus(orderUid));
                order.put("status", getLatestStatus(orderUid));
                order.put("statusreason", getLatestStatusReason(orderUid));
                return order;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> createOrder(String orderUid, Map<String, Object> request) {
        List<Map<String, Object>> productsInput = (List<Map<String, Object>>) request.get("products");
        List<Map<String, Object>> fullProducts = new ArrayList<>();
        
        for (Map<String, Object> productInput : productsInput) {
            String productUid = (String) productInput.get("productUid");
            Integer quantity = productInput.get("quantity") != null 
                    ? Integer.parseInt(productInput.get("quantity").toString()) : 0;
            
            Optional<SprMaterial> materialOpt = materialRepository.findById(UUID.fromString(productUid));
            if (materialOpt.isEmpty()) continue;
            
            SprMaterial material = materialOpt.get();
            fullProducts.add(buildProductData(material, quantity));
        }
        
        Map<String, Object> orderData = new LinkedHashMap<>();
        orderData.put("order_uid", orderUid);
        orderData.put("ordernumber", request.getOrDefault("orderNumber", "ORD-" + System.currentTimeMillis()));
        orderData.put("orderdata", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        orderData.put("customer", request.getOrDefault("customer", "AWMS ДИНАМИКА"));
        orderData.put("ogrn", request.getOrDefault("ogrn", "1234567890123"));
        orderData.put("inn", request.getOrDefault("inn", "7701234567"));
        orderData.put("kpp", request.getOrDefault("kpp", "770101001"));
        orderData.put("legaladdress", request.getOrDefault("legalAddress", "г. Пермь"));
        orderData.put("deliveryaddress", request.getOrDefault("deliveryAddress", "г. Пермь"));
        orderData.put("contactperson", request.getOrDefault("contactPerson", "Иванов Иван Иванович"));
        orderData.put("contact", request.getOrDefault("contact", "+7 (999) 123-45-67"));
        orderData.put("products", fullProducts);
        
        try {
            // Отправляем в SAAS
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(orderData, createHeaders());
            restTemplate.exchange(syncServiceUrl + "/v1/orders/" + orderUid, HttpMethod.POST, entity, Map.class);

            // Сохраняем локально
            saveOrderLocally(orderUid, orderData, "active", "inprocessing");

            Map<String, Object> notification = new LinkedHashMap<>();
            notification.put("order_uid", orderUid);
            notification.put("type", "order_created");
            messagingTemplate.convertAndSend("/topic/orders/refresh", notification);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("order_uid", orderUid);
            response.put("message", "Заказ создан");
            return response;
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("error", "internal_error");
            errorResponse.put("message", "Ошибка при отправке заказа: " + e.getMessage());
            return errorResponse;
        }
    }

    @Transactional
    public void saveOrderLocally(String orderUid, Map<String, Object> orderData, String status, String statusreason) {
        OrdersList orderList = OrdersList.builder()
                .orderUid(orderUid)
                .customerId((String) orderData.get("customer"))
                .orderNumber((String) orderData.get("ordernumber"))
                .orderDatetime(parseDateTime((String) orderData.get("orderdata")))
                .status(status)
                .statusreason(statusreason)
                .syncedAt(ZonedDateTime.now())
                .build();
        ordersListRepository.save(orderList);

        try {
            String json = objectMapper.writeValueAsString(orderData);
            OrdersFull orderFull = OrdersFull.builder()
                    .orderUid(orderUid)
                    .orderJson(json)
                    .build();
            ordersFullRepository.save(orderFull);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Transactional
    public void receiveStatusUpdate(String orderUid, String newStatus) {
        OrderStatus orderStatus = OrderStatus.builder()
                .orderUid(orderUid)
                .status(newStatus)
                .datetime(ZonedDateTime.now())
                .build();
        orderStatusRepository.save(orderStatus);

        ordersListRepository.findById(orderUid).ifPresent(order -> {
            order.setStatus(newStatus);
            ordersListRepository.save(order);
        });
    }

    @Transactional
    public void receiveStatusReasonUpdate(String orderUid, String statusreason) {
        OrderStatus orderStatus = OrderStatus.builder()
                .orderUid(orderUid)
                .subStatus(statusreason)
                .datetime(ZonedDateTime.now())
                .build();
        orderStatusRepository.save(orderStatus);

        ordersListRepository.findById(orderUid).ifPresent(order -> {
            order.setStatusreason(statusreason);
            ordersListRepository.save(order);
        });
    }

    @Transactional
    public void cancelOrder(String orderUid) {
        try {
            Map<String, Object> reasonBody = Map.of("statusreason", "cancelcustomer");
            HttpEntity<Map<String, Object>> reasonEntity = new HttpEntity<>(reasonBody, createHeaders());
            restTemplate.exchange(syncServiceUrl + "/v1/orders/" + orderUid + "/statusreason", HttpMethod.POST, reasonEntity, String.class);

            Map<String, Object> statusBody = Map.of("status", "closed");
            HttpEntity<Map<String, Object>> statusEntity = new HttpEntity<>(statusBody, createHeaders());
            restTemplate.exchange(syncServiceUrl + "/v1/orders/" + orderUid + "/status", HttpMethod.POST, statusEntity, String.class);

            receiveStatusReasonUpdate(orderUid, "cancelcustomer");
            receiveStatusUpdate(orderUid, "closed");

            Map<String, Object> notification = new LinkedHashMap<>();
            notification.put("order_uid", orderUid);
            notification.put("type", "order_cancelled");
            messagingTemplate.convertAndSend("/topic/orders/refresh", notification);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Только сохраняет трек локально (при проксировании из SAAS), не отправляет в SAAS
    @Transactional
    public void saveTrackLocally(String orderUid, String statustrack) {
        OrderTracking tracking = OrderTracking.builder()
                .orderUid(orderUid)
                .trackingStatus(statustrack)
                .datetime(ZonedDateTime.now())
                .build();
        orderTrackingRepository.save(tracking);
    }

    private String getLatestStatus(String orderUid) {
        List<OrderStatus> statuses = orderStatusRepository.findByOrderUidOrderByDatetimeDesc(orderUid);
        return statuses.stream().filter(s -> s.getStatus() != null).findFirst().map(OrderStatus::getStatus).orElse("active");
    }

    private String getLatestStatusReason(String orderUid) {
        List<OrderStatus> statuses = orderStatusRepository.findByOrderUidOrderByDatetimeDesc(orderUid);
        return statuses.stream().filter(s -> s.getSubStatus() != null).findFirst().map(OrderStatus::getSubStatus).orElse("inprocessing");
    }

    private String getLatestTrackingStatus(String orderUid) {
        List<OrderTracking> tracks = orderTrackingRepository.findByOrderUidOrderByDatetimeDesc(orderUid);
        return tracks.stream().filter(t -> t.getTrackingStatus() != null).findFirst().map(OrderTracking::getTrackingStatus).orElse(null);
    }

    private Map<String, Object> toOrderListMap(OrdersList order) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("order_uid", order.getOrderUid());
        map.put("customer_id", order.getCustomerId());
        map.put("order_number", order.getOrderNumber());
        map.put("order_datetime", order.getOrderDatetime() != null
                ? order.getOrderDatetime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null);
        map.put("status", order.getStatus());
        map.put("statusreason", order.getStatusreason());
        map.put("statustrack", getLatestTrackingStatus(order.getOrderUid()));
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

    private Map<String, Object> buildProductData(SprMaterial material, Integer quantity) {
        UUID materialUid = material.getUid();
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("product_uid", materialUid.toString());
        product.put("article", material.getArticle() != null ? material.getArticle() : "");
        product.put("product", material.getNameMaterial() != null ? material.getNameMaterial() : "");
        product.put("quantity", quantity);
        product.put("group", material.getTypeMain() != null ? material.getTypeMain().getTypeName() : "");
        product.put("type", material.getTypeProduct() != null ? material.getTypeProduct().getTypeName() : "");
        product.put("description", material.getDescription() != null ? material.getDescription() : "");
        product.put("manufacturer", material.getManufacturer() != null ? material.getManufacturer().getName() : "");
        product.put("country", material.getCountry() != null ? material.getCountry().getName() : "");
        product.put("brand", material.getBrand() != null ? material.getBrand().getName() : "");
        product.put("model", material.getModelOfBrand() != null ? material.getModelOfBrand().getName() : "");
        
        List<String> images = imageRepository.findByMaterialUidOrderBySortOrderAsc(materialUid)
                .stream().map(img -> {
                    try {
                        Path filePath = Paths.get(UPLOAD_DIR, materialUid.toString(), img.getFilePath());
                        return Base64.getEncoder().encodeToString(Files.readAllBytes(filePath));
                    } catch (IOException e) { return ""; }
                }).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        product.put("images", images);
        
        List<String> draws = blueprintRepository.findByMaterialUid(materialUid)
                .stream().map(bp -> {
                    try {
                        Path filePath = Paths.get(UPLOAD_DIR, materialUid.toString(), bp.getFilePath());
                        return Base64.getEncoder().encodeToString(Files.readAllBytes(filePath));
                    } catch (IOException e) { return ""; }
                }).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        product.put("draws", draws);
        
        List<SprMaterialCode> barcodes = codeRepository.findByMaterialUidAndCodeKindOrderByCreatedAtDesc(materialUid, "BARCODE");
        if (!barcodes.isEmpty()) {
            SprMaterialCode barcode = barcodes.get(0);
            Map<String, Object> barcodeData = new LinkedHashMap<>();
            barcodeData.put("code", barcode.getCodeValue() != null ? barcode.getCodeValue() : "");
            if (barcode.getFilePath() != null) {
                try {
                    Path filePath = Paths.get(UPLOAD_DIR, materialUid.toString(), barcode.getFilePath());
                    barcodeData.put("codeimage", Base64.getEncoder().encodeToString(Files.readAllBytes(filePath)));
                } catch (IOException e) { barcodeData.put("codeimage", ""); }
            } else { barcodeData.put("codeimage", ""); }
            product.put("barcode", barcodeData);
        }
        
        List<SprMaterialCode> skus = codeRepository.findByMaterialUidAndCodeKindOrderByCreatedAtDesc(materialUid, "SKU");
        if (!skus.isEmpty()) {
            SprMaterialCode sku = skus.get(0);
            Map<String, Object> skuData = new LinkedHashMap<>();
            skuData.put("code", sku.getCodeValue() != null ? sku.getCodeValue() : "");
            if (sku.getFilePath() != null) {
                try {
                    Path filePath = Paths.get(UPLOAD_DIR, materialUid.toString(), sku.getFilePath());
                    skuData.put("image", Base64.getEncoder().encodeToString(Files.readAllBytes(filePath)));
                } catch (IOException e) { skuData.put("image", ""); }
            } else { skuData.put("image", ""); }
            product.put("sku", skuData);
        }
        
        List<RegAttributes> attributes = regAttributesRepository.findByMaterialUid(materialUid);
        List<Map<String, Object>> specifications = attributes.stream()
                .filter(a -> a.getAttributeType() != null && a.getMeaning() != null && !a.getMeaning().isEmpty())
                .map(a -> {
                    Map<String, Object> spec = new LinkedHashMap<>();
                    spec.put("characteristic", a.getAttributeType().getName());
                    spec.put("unit", a.getMeasure() != null ? a.getMeasure().getName() : "");
                    spec.put("value", a.getMeaning());
                    return spec;
                }).collect(Collectors.toList());
        product.put("specifications", specifications);
        
        List<RegAnalog> analogs = regAnalogRepository.findByMaterialUid(materialUid);
        List<Map<String, Object>> analogues = analogs.stream().map(a -> {
            Map<String, Object> analog = new LinkedHashMap<>();
            analog.put("uid", a.getAnalogMaterial().getUid().toString());
            analog.put("name", a.getAnalogMaterial().getNameMaterial());
            analog.put("model", a.getAnalogMaterial().getModelOfBrand() != null 
                    ? a.getAnalogMaterial().getModelOfBrand().getName() : "");
            return analog;
        }).collect(Collectors.toList());
        product.put("analogues", analogues);
        
        return product;
    }
}