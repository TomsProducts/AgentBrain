# WebSocket Events

AgentBrain uses STOMP over WebSocket to push real-time events to the browser dashboard.

---

## Connection

### Endpoint

```
WS /ws
```

Proxied through nginx:

```nginx
location /ws {
    proxy_pass http://backend:8080/ws;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
}
```

### STOMP Subscription

```typescript
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: `ws://${window.location.host}/ws`,
  onConnect: () => {
    client.subscribe('/topic/activity', (message) => {
      const event = JSON.parse(message.body);
      console.log(event);
    });
  },
});

client.activate();
```

---

## Event Payload

All events share the same structure:

```typescript
interface AgentEvent {
  type: string;        // event type identifier
  message: string;     // human-readable description
  timestamp: string;   // ISO-8601 datetime
  data?: any;          // optional extra payload (event-specific)
}
```

---

## Event Types

### `DREAM_COMPLETE`

Published when the Dream Cycle finishes (scheduled or manual).

```json
{
  "type": "DREAM_COMPLETE",
  "message": "Staged 3 candidates from 8 episodes across 4 clusters",
  "timestamp": "2026-04-18T03:00:01",
  "data": {
    "stagedCount": 3,
    "processedEpisodes": 8,
    "clustersFound": 4
  }
}
```

**Color in UI:** Blue

---

### `LESSON_GRADUATED`

Published when a lesson is graduated to `ACCEPTED`.

```json
{
  "type": "LESSON_GRADUATED",
  "message": "Lesson graduated: Sync agent clear() must be called before fullSync()",
  "timestamp": "2026-04-18T14:05:23"
}
```

**Color in UI:** Green

---

### `LESSON_REJECTED`

Published when a lesson is rejected.

```json
{
  "type": "LESSON_REJECTED",
  "message": "Lesson rejected: Testing AgentBrain deployment. First memory entry",
  "timestamp": "2026-04-18T14:06:10"
}
```

**Color in UI:** Red

---

### `LESSON_REOPENED`

Published when a rejected lesson is reopened for re-evaluation.

```json
{
  "type": "LESSON_REOPENED",
  "message": "Lesson reopened: Testing AgentBrain deployment. First memory entry",
  "timestamp": "2026-04-18T14:07:00"
}
```

**Color in UI:** Yellow

---

### `MEMORY_WRITE`

Published when a working or episodic memory is created.

```json
{
  "type": "MEMORY_WRITE",
  "message": "Episodic memory written: Discovered CORS blocks 192.168.x.x origins",
  "timestamp": "2026-04-18T14:08:30"
}
```

**Color in UI:** Gray

---

### `ERROR`

Published when the backend encounters an unexpected error.

```json
{
  "type": "ERROR",
  "message": "Dream cycle failed: NullPointerException in ClusteringService",
  "timestamp": "2026-04-18T03:00:15"
}
```

**Color in UI:** Red

---

## Activity Log Page

The `/activity` route in the frontend shows a full-page real-time event feed with:
- Color-coded rows by event type
- Filter dropdown by type
- Auto-scroll to newest events
- Maximum 1000 events in memory (oldest are dropped)

---

## STOMP Configuration

```java
// WebSocketConfig.java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");   // in-memory broker
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();   // SockJS fallback for older browsers
    }
}
```

### Publishing Events

```java
// AgentEventPublisher.java
@Component
@RequiredArgsConstructor
public class AgentEventPublisher {

    private final SimpMessagingTemplate template;

    public void publish(String type, String message) {
        AgentEvent event = AgentEvent.builder()
            .type(type)
            .message(message)
            .timestamp(LocalDateTime.now().toString())
            .build();
        template.convertAndSend("/topic/activity", event);
    }
}
```
