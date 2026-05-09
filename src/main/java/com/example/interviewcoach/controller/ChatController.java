package com.example.interviewcoach.controller;

import com.example.interviewcoach.model.ChatRequest;
import com.example.interviewcoach.model.ChatResponse;
import com.example.interviewcoach.model.ChatAnswer;
import com.example.interviewcoach.model.ConversationStage;
import com.example.interviewcoach.model.InterviewSessionMemory;
import com.example.interviewcoach.service.ChatService;
import com.example.interviewcoach.service.InterviewMemoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;
    private final InterviewMemoryService memoryService;

    public ChatController(ChatService chatService,
                          InterviewMemoryService memoryService) {
        this.chatService = chatService;
        this.memoryService = memoryService;
    }

    @PostMapping("/ask")
    public ResponseEntity<?> ask(@RequestBody ChatRequest req) {
        if (req == null || req.getQuestion() == null || req.getQuestion().isBlank()) {
            return ResponseEntity.badRequest().body("question required");
        }
        String sessionId = req.getEffectiveSessionId();
        ChatAnswer answerObj = chatService.ask(req);
        String answer = answerObj == null ? null : answerObj.getAnswer();
        InterviewSessionMemory memory = memoryService.getOrCreate(sessionId);
        ConversationStage stage = memory.getStage();
        String missingFields = memory.hasEnoughProfile() ? null : memory.getProfile().missingFields();
        ChatResponse resp = new ChatResponse(answer, sessionId, stage, missingFields);
        if (answerObj != null) {
            resp.setScore(answerObj.getScore());
            resp.setScoreFeedback(answerObj.getScoreFeedback());
        }
        return ResponseEntity.ok(resp);
    }
}
