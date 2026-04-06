// StationEntityListener.java (для отслеживания изменений в БД)
package com.example.dinamika_back.listener;

import com.example.dinamika_back.controller.StationChangeEvent;
import com.example.dinamika_back.model.Station;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class StationEntityListener {
    
    private static ApplicationEventPublisher eventPublisher;
    
    @Autowired
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        eventPublisher = applicationEventPublisher;
    }
    
    @PostPersist
    @PostUpdate
    @PostRemove
    public void publishChange(Station station) {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new StationChangeEvent(station.getUid()));
        }
    }
}