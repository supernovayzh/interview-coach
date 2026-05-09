package com.example.interviewcoach.service;

import com.example.interviewcoach.model.ChatRequest;
import com.example.interviewcoach.model.ConversationStage;
import com.example.interviewcoach.model.InterviewSessionMemory;

public interface InterviewMemoryService {
    InterviewSessionMemory getOrCreate(String sessionId);

    InterviewSessionMemory upsertProfile(String sessionId, ChatRequest request);

    void addTurn(String sessionId, String question, String answer);

    String buildContext(String sessionId);

    ConversationStage getStage(String sessionId);

    String buildMissingFields(String sessionId);
}
