package io.agentbrain.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public void publish(AgentEvent event) {
        try {
            messagingTemplate.convertAndSend("/topic/activity", event);
            log.debug("Published event: {} - {}", event.getType(), event.getMessage());
        } catch (Exception e) {
            log.warn("Failed to publish event: {}", e.getMessage());
        }
    }

    public void publish(String type, String message) {
        publish(AgentEvent.of(type, message));
    }
}
