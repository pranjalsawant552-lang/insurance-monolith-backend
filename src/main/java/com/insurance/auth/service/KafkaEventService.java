package com.insurance.auth.service;

import com.insurance.auth.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.insurance.monolith.events.DomainEventBus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaEventService {

    private final DomainEventBus domainEventBus;

    private static final String TOPIC_USER_EVENTS = "auth.user.events";

    @Async
    public void publishUserRegistered(User user) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "USER_REGISTERED");
        event.put("userId", user.getId().toString());
        event.put("email", user.getEmail());
        event.put("mobileNumber", user.getMobileNumber());
        event.put("firstName", user.getFirstName());
        event.put("lastName", user.getLastName());
        event.put("role", user.getRole().name());
        event.put("timestamp", ZonedDateTime.now().toString());
        publish(TOPIC_USER_EVENTS, user.getId().toString(), event);
    }

    @Async
    public void publishUserActivated(User user) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "USER_ACTIVATED");
        event.put("userId", user.getId().toString());
        event.put("email", user.getEmail());
        event.put("timestamp", ZonedDateTime.now().toString());
        publish(TOPIC_USER_EVENTS, user.getId().toString(), event);
    }

    @Async
    public void publishPasswordChanged(User user) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "PASSWORD_CHANGED");
        event.put("userId", user.getId().toString());
        event.put("timestamp", ZonedDateTime.now().toString());
        publish(TOPIC_USER_EVENTS, user.getId().toString(), event);
    }

    private void publish(String topic, String key, Map<String, Object> payload) {
        domainEventBus.publish(topic, key, payload);
        log.debug("Published event to topic {} key={}", topic, key);
    }
}
