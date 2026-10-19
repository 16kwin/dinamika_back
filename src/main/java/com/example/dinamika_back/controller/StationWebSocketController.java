// StationWebSocketController.java
package com.example.dinamika_back.controller;

import com.example.dinamika_back.dto.StationDynamicDto;
import com.example.dinamika_back.dto.StationStaticDto;
import com.example.dinamika_back.features.ConditionalOnFeature;
import com.example.dinamika_back.features.Feature;
import com.example.dinamika_back.service.LiveUpdates;
import com.example.dinamika_back.service.StationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Рассылка состояний станций после коммита (функция {@code websocket}). Без функции в потолке бина нет: события
 * изменения станции никто не слушает и станции на каждое изменение из БД не перечитываются.
 */
@Component
@ConditionalOnFeature(Feature.WEBSOCKET)
@RequiredArgsConstructor
public class StationWebSocketController {

    private final LiveUpdates liveUpdates;
    private final StationService stationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleStationChange(StationChangeEvent event) {
        // Отправляем все статические данные
        List<StationStaticDto> staticData = stationService.getAllStaticStations();
        liveUpdates.publish("/topic/stations/static", staticData);

        // Отправляем все динамические данные
        List<StationDynamicDto> dynamicData = stationService.getAllDynamicStations();
        liveUpdates.publish("/topic/stations/dynamic", dynamicData);

        // Если нужно только по конкретной станции
        if (event.getUid() != null) {
            StationDynamicDto singleDynamic = stationService.getDynamicByUid(event.getUid());
            liveUpdates.publish("/topic/stations/dynamic/" + event.getUid(), singleDynamic);

            StationStaticDto singleStatic = stationService.getStaticByUid(event.getUid());
            liveUpdates.publish("/topic/stations/static/" + event.getUid(), singleStatic);
        }
    }
}
