package com.example.interviewcoach.model;

public class ConversationMessage {
    private String sessionId;
    private String traceId;
    private String role;
    private String content;
    private String createdAt;

    public ConversationMessage() {
    }

    public ConversationMessage(String sessionId, String traceId, String role, String content, String createdAt) {
        this.sessionId = sessionId;
        this.traceId = traceId;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
