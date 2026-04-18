package io.agentbrain.dream.controller;

import io.agentbrain.memory.service.DreamCycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/dream")
@RequiredArgsConstructor
public class DreamController {

    private final DreamCycleService dreamCycleService;

    @PostMapping("/run")
    public Map<String, Object> run() {
        dreamCycleService.runDream();
        return Map.of(
                "result", dreamCycleService.getLastRunResult(),
                "ranAt", LocalDateTime.now().toString()
        );
    }

    @GetMapping("/last")
    public Map<String, Object> last() {
        return Map.of(
                "result", dreamCycleService.getLastRunResult(),
                "lastRunAt", dreamCycleService.getLastRunAt() != null
                        ? dreamCycleService.getLastRunAt().toString()
                        : "never"
        );
    }
}
