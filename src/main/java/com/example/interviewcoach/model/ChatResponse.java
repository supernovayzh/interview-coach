package com.example.interviewcoach.model;

public class ChatResponse {
    private String answer;
    private String sessionId;
    private ConversationStage stage;
    private String missingFields;

    public ChatResponse() {}

    public ChatResponse(String answer) {
        this.answer = answer;
    }

    public ChatResponse(String answer, String sessionId, ConversationStage stage, String missingFields) {
        this.answer = answer;
        this.sessionId = sessionId;
        this.stage = stage;
        this.missingFields = missingFields;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public ConversationStage getStage() {
        return stage;
    }

    public void setStage(ConversationStage stage) {
        this.stage = stage;
    }

    public String getMissingFields() {
        return missingFields;
    }

    public void setMissingFields(String missingFields) {
        this.missingFields = missingFields;
    }
}
