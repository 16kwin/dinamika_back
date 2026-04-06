// StationWebSocketController.java
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.StationDynamicDto;
import com.example.dinamika_back.dto.StationStaticDto;
import com.example.dinamika_back.service.StationService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StationWebSocketController {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final StationService stationService;
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleStationChange(StationChangeEvent event) {
        // Отправляем все статические данные
        List<StationStaticDto> staticData = stationService.getAllStaticStations();
        messagingTemplate.convertAndSend("/topic/stations/static", staticData);
        
        // Отправляем все динамические данные
        List<StationDynamicDto> dynamicData = stationService.getAllDynamicStations();
        messagingTemplate.convertAndSend("/topic/stations/dynamic", dynamicData);
        
        // Если нужно только по конкретной станции
        if (event.getUid() != null) {
            StationDynamicDto singleDynamic = stationService.getDynamicByUid(event.getUid());
            messagingTemplate.convertAndSend("/topic/stations/dynamic/" + event.getUid(), singleDynamic);
            
            StationStaticDto singleStatic = stationService.getStaticByUid(event.getUid());
            messagingTemplate.convertAndSend("/topic/stations/static/" + event.getUid(), singleStatic);
        }
    }
}