package com.example.interviewcoach.controller;

import com.example.interviewcoach.model.RagChunk;
import com.example.interviewcoach.service.RagKnowledgeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rag")
public class RagController {

    private final RagKnowledgeService ragKnowledgeService;

    public RagController(RagKnowledgeService ragKnowledgeService) {
        this.ragKnowledgeService = ragKnowledgeService;
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String query,
                                    @RequestParam(defaultValue = "3") int topK) {
        List<RagChunk> results = ragKnowledgeService.search(query, topK);
        return ResponseEntity.ok(Map.of(
                "query", query,
                "topK", topK,
                "results", results,
                "context", ragKnowledgeService.buildContext(query)
        ));
    }

    @PostMapping("/reload")
    public ResponseEntity<?> reload() {
        ragKnowledgeService.reload();
        return ResponseEntity.ok(Map.of("status", "reloaded"));
    }
}
