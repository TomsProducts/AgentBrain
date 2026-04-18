package io.agentbrain.memory.controller;

import io.agentbrain.memory.domain.EpisodicMemory;
import io.agentbrain.memory.domain.WorkingMemory;
import io.agentbrain.memory.service.*;
import io.agentbrain.memory.service.ContextBudgetService.ContextBudget;
import io.agentbrain.memory.service.MemorySearchService.SearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final WorkingMemoryService workingMemoryService;
    private final EpisodicMemoryService episodicMemoryService;
    private final MemorySearchService memorySearchService;
    private final ContextBudgetService contextBudgetService;

    // --- Working Memory ---

    @GetMapping("/working")
    public List<WorkingMemory> getWorking() {
        return workingMemoryService.findAll();
    }

    @PostMapping("/working")
    public WorkingMemory addWorking(@RequestBody Map<String, String> body) {
        return workingMemoryService.create(body.get("content"), body.get("tags"));
    }

    @DeleteMapping("/working/{id}")
    public ResponseEntity<Void> deleteWorking(@PathVariable Long id) {
        workingMemoryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- Episodic Memory ---

    @GetMapping("/episodic")
    public Page<EpisodicMemory> getEpisodic(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return episodicMemoryService.findAll(page, size);
    }

    @PostMapping("/episodic")
    public EpisodicMemory addEpisodic(@RequestBody Map<String, String> body) {
        return episodicMemoryService.create(body.get("content"), body.get("tags"));
    }

    // --- Search ---

    @GetMapping("/search")
    public SearchResult search(@RequestParam String q) {
        return memorySearchService.search(q);
    }
}
