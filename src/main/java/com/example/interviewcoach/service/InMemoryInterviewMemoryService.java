package com.example.interviewcoach.service;

import com.example.interviewcoach.model.ChatRequest;
import com.example.interviewcoach.model.ChatTurn;
import com.example.interviewcoach.model.ConversationStage;
import com.example.interviewcoach.model.InterviewProfile;
import com.example.interviewcoach.model.InterviewSessionMemory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryInterviewMemoryService implements InterviewMemoryService {

    private static final int MAX_CONTEXT_FIELD_CHARS = 120;
    private static final int MAX_CONTEXT_TURN_CHARS = 180;

    private final Map<String, InterviewSessionMemory> store = new ConcurrentHashMap<>();
    private final ConversationPersistenceService conversationPersistenceService;

    public InMemoryInterviewMemoryService(ObjectProvider<ConversationPersistenceService> conversationPersistenceServiceProvider) {
        this.conversationPersistenceService = conversationPersistenceServiceProvider.getIfAvailable();
    }

    @Override
    public InterviewSessionMemory getOrCreate(String sessionId) {
        return store.computeIfAbsent(normalize(sessionId), key -> new InterviewSessionMemory());
    }

    @Override
    public InterviewSessionMemory upsertProfile(String sessionId, ChatRequest request) {
        InterviewSessionMemory memory = getOrCreate(sessionId);
        memory.mergeRequest(request);
        return memory;
    }

    @Override
    public void addTurn(String sessionId, String question, String answer) {
        String normalizedSessionId = normalize(sessionId);
        getOrCreate(normalizedSessionId).addTurn(question, answer);
        if (conversationPersistenceService != null) {
            conversationPersistenceService.saveConversationTurn(normalizedSessionId, question, answer, MDC.get("traceId"));
        }
    }

    @Override
    public void saveEvaluation(String sessionId, String question, Double score, String feedback) {
        String normalizedSessionId = normalize(sessionId);
        if (conversationPersistenceService != null) {
            conversationPersistenceService.saveEvaluation(normalizedSessionId, question, score, feedback, MDC.get("traceId"));
        }
    }

    @Override
    public String buildContext(String sessionId) {
        InterviewSessionMemory memory = getOrCreate(sessionId);
        InterviewProfile profile = memory.getProfile();
        StringBuilder sb = new StringBuilder();
        appendIfNotBlank(sb, "目标公司", clip(profile.getTargetCompany(), MAX_CONTEXT_FIELD_CHARS));
        appendIfNotBlank(sb, "公司类型/规模", clip(profile.getCompanyTier(), MAX_CONTEXT_FIELD_CHARS));
        appendIfNotBlank(sb, "目标岗位", clip(profile.getTargetRole(), MAX_CONTEXT_FIELD_CHARS));
        appendIfNotBlank(sb, "重点考察方向", clip(profile.getFocusAreas(), MAX_CONTEXT_FIELD_CHARS));
        appendIfNotBlank(sb, "简历摘要", clip(profile.getResumeSummary(), MAX_CONTEXT_FIELD_CHARS));
        appendIfNotBlank(sb, "本次目标", clip(profile.getInterviewGoal(), MAX_CONTEXT_FIELD_CHARS));
        if (!memory.getRecentTurns().isEmpty()) {
            sb.append("最近对话：\n");
            for (ChatTurn turn : memory.getRecentTurns()) {
                sb.append("Q: ").append(clip(turn.getQuestion(), MAX_CONTEXT_TURN_CHARS)).append('\n');
                if (turn.getAnswer() != null && !turn.getAnswer().isBlank()) {
                    sb.append("A: ").append(clip(turn.getAnswer(), MAX_CONTEXT_TURN_CHARS)).append('\n');
                }
            }
        }
        return sb.toString();
    }

    @Override
    public ConversationStage getStage(String sessionId) {
        return getOrCreate(sessionId).getStage();
    }

    @Override
    public String buildMissingFields(String sessionId) {
        InterviewProfile profile = getOrCreate(sessionId).getProfile();
        return profile.missingFields();
    }

    private String normalize(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? "default" : sessionId;
    }

    private void appendIfNotBlank(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append("：").append(value).append('\n');
        }
    }

    private String clip(String value, int maxChars) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "…";
    }
}
