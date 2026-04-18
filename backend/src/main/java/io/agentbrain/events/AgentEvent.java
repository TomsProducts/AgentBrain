package io.agentbrain.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentEvent {
    private String type;
    private String message;
    private String timestamp;

    public static AgentEvent of(String type, String message) {
        return new AgentEvent(type, message, Instant.now().toString());
    }
}
