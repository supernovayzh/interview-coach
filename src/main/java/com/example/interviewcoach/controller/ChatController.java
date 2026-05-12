package com.example.interviewcoach.controller;

import com.example.interviewcoach.model.ChatRequest;
import com.example.interviewcoach.model.ChatResponse;
import com.example.interviewcoach.model.ChatAnswer;
import com.example.interviewcoach.model.ConversationMessage;
import com.example.interviewcoach.model.ConversationStage;
import com.example.interviewcoach.model.InterviewSessionMemory;
import com.example.interviewcoach.service.ChatService;
import com.example.interviewcoach.service.InterviewMemoryService;
import com.example.interviewcoach.service.ConversationPersistenceService;
import com.example.interviewcoach.service.LlmSummarizerService;
import com.example.interviewcoach.service.StreamingChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;
    private final InterviewMemoryService memoryService;
    private final StreamingChatService streamingChatService;
    private final ConversationPersistenceService conversationPersistenceService;
    private final LlmSummarizerService llmSummarizerService;
    private final ObjectMapper objectMapper;

    public ChatController(ChatService chatService,
                          InterviewMemoryService memoryService,
                          StreamingChatService streamingChatService,
                          ConversationPersistenceService conversationPersistenceService,
                          LlmSummarizerService llmSummarizerService,
                          ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.memoryService = memoryService;
        this.streamingChatService = streamingChatService;
        this.conversationPersistenceService = conversationPersistenceService;
        this.llmSummarizerService = llmSummarizerService;
        this.objectMapper = objectMapper;
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
            resp.setNextAction(answerObj.getNextAction());
            resp.setFollowUpQuestion(answerObj.getFollowUpQuestion());
        }
        return ResponseEntity.ok(resp);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamByGet(@ModelAttribute ChatRequest req) {
        if (req == null || req.getQuestion() == null || req.getQuestion().isBlank()) {
            SseEmitter emitter = new SseEmitter(0L);
            try {
                emitter.send(SseEmitter.event().name("error").data("question required"));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
            return emitter;
        }

        String sessionId = req.getEffectiveSessionId();
        InterviewSessionMemory memory = memoryService.getOrCreate(sessionId);
        ConversationStage stage = memory.getStage();
        SseEmitter emitter = new SseEmitter(0L);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("sessionId", sessionId);
        meta.put("stage", stage.name());

        try {
            emitter.send(SseEmitter.event().name("meta").data(objectMapper.writeValueAsString(meta)));
        } catch (Exception ex) {
            emitter.complete();
            return emitter;
        }

        Disposable[] disposableRef = new Disposable[1];
        disposableRef[0] = streamingChatService.stream(req).subscribe(
                chunk -> {
                    try {
                        if (chunk == null || chunk.isEmpty()) {
                            return;
                        }
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (Exception ex) {
                        Disposable disposable = disposableRef[0];
                        if (disposable != null && !disposable.isDisposed()) {
                            disposable.dispose();
                        }
                        emitter.complete();
                    }
                },
                error -> {
                    try {
                        emitter.send(SseEmitter.event().name("error").data(error.getMessage() == null ? "stream error" : error.getMessage()));
                    } catch (Exception ignored) {
                    }
                    emitter.complete();
                },
                () -> {
                    try {
                        emitter.send(SseEmitter.event().name("done").data(objectMapper.writeValueAsString(meta)));
                        emitter.complete();
                    } catch (Exception ex) {
                        emitter.completeWithError(ex);
                    }
                }
        );

        emitter.onCompletion(() -> {
            Disposable disposable = disposableRef[0];
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
            }
        });
        emitter.onTimeout(() -> {
            Disposable disposable = disposableRef[0];
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
            }
        });

        return emitter;
    }

    @GetMapping("/history")
    public ResponseEntity<List<ConversationMessage>> history(@RequestParam String sessionId,
                                                             @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(conversationPersistenceService.listMessages(sessionId, limit));
    }

    @GetMapping("/evaluations")
    public ResponseEntity<List<ConversationPersistenceService.EvaluationRecord>> evaluations(@RequestParam String sessionId,
                                                                                              @RequestParam(defaultValue = "50") int limit) {
        List<ConversationPersistenceService.EvaluationRecord> evals = conversationPersistenceService.listEvaluations(sessionId, limit);
        return ResponseEntity.ok(evals);
    }

    @GetMapping("/session-title")
    public ResponseEntity<?> sessionTitle(@RequestParam String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest().body("sessionId required");
        }
        List<ConversationMessage> messages = conversationPersistenceService.listMessages(sessionId, 12);
        String title = llmSummarizerService.generateSessionTitle(messages);
        return ResponseEntity.ok(Map.of("sessionId", sessionId, "title", title == null ? "" : title));
    }

    @DeleteMapping("/session")
    public ResponseEntity<?> deleteSession(@RequestParam String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest().body("sessionId required");
        }
        conversationPersistenceService.deleteSession(sessionId);
        return ResponseEntity.ok(Map.of("sessionId", sessionId, "deleted", true));
    }
}
