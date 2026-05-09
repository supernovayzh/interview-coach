package com.example.interviewcoach.controller;

import com.example.interviewcoach.model.ChatRequest;
import com.example.interviewcoach.service.InterviewMemoryService;
import com.example.interviewcoach.service.PdfResumeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/profile")
public class PdfResumeController {

    private final PdfResumeService pdfResumeService;
    private final InterviewMemoryService memoryService;

    public PdfResumeController(PdfResumeService pdfResumeService, InterviewMemoryService memoryService) {
        this.pdfResumeService = pdfResumeService;
        this.memoryService = memoryService;
    }

    @PostMapping(value = "/uploadResume")
    public ResponseEntity<?> uploadResume(@RequestParam("sessionId") String sessionId,
                                          @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "file required"));
        }

        try {
            String text = pdfResumeService.extractText(file);
            ChatRequest req = new ChatRequest();
            req.setSessionId(sessionId);
            req.setResumeSummary(text);
            // upsert profile using resume text so memory picks it up
            memoryService.upsertProfile(sessionId, req);
            String missing = memoryService.buildMissingFields(sessionId);
            return ResponseEntity.ok(Map.of("resumeSummary", text, "missingFields", missing));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "failed to parse pdf", "detail", e.getMessage()));
        }
    }
}
