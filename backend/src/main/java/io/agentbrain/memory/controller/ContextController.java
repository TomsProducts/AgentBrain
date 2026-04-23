package io.agentbrain.memory.controller;

import io.agentbrain.memory.service.ContextBudgetService;
import io.agentbrain.memory.service.ContextBudgetService.ContextBudget;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/context")
@RequiredArgsConstructor
public class ContextController {

    private final ContextBudgetService contextBudgetService;

    @GetMapping
    public ContextBudget getContext(@RequestParam(required = false) String q) {
        return contextBudgetService.buildContext(q);
    }
}
