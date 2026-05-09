package com.example.interviewcoach.tool;

import com.example.interviewcoach.model.ChatRequest;
import com.example.interviewcoach.model.InterviewSessionMemory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class InterviewToolContext {
    private final ChatRequest request;
    private final InterviewSessionMemory memory;
    private final String sessionId;
    private final String question;
    private final String answer;
    private final String ragContext;
    private final Map<String, Object> attributes;

    public InterviewToolContext(ChatRequest request,
                                InterviewSessionMemory memory,
                                String sessionId,
                                String question,
                                String answer,
                                String ragContext) {
        this(request, memory, sessionId, question, answer, ragContext, new HashMap<>());
    }

    public InterviewToolContext(ChatRequest request,
                                InterviewSessionMemory memory,
                                String sessionId,
                                String question,
                                String answer,
                                String ragContext,
                                Map<String, Object> attributes) {
        this.request = request;
        this.memory = memory;
        this.sessionId = sessionId;
        this.question = question;
        this.answer = answer;
        this.ragContext = ragContext;
        this.attributes = new HashMap<>(attributes == null ? Collections.emptyMap() : attributes);
    }

    public ChatRequest getRequest() {
        return request;
    }

    public InterviewSessionMemory getMemory() {
        return memory;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public String getRagContext() {
        return ragContext;
    }

    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public InterviewToolContext withAttribute(String key, Object value) {
        Map<String, Object> next = new HashMap<>(this.attributes);
        next.put(key, value);
        return new InterviewToolContext(request, memory, sessionId, question, answer, ragContext, next);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("Attribute '" + key + "' is not of type " + type.getSimpleName());
        }
        return (T) value;
    }
}
