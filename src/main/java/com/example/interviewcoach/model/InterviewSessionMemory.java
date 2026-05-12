package com.example.interviewcoach.model;

import java.util.ArrayDeque;
import java.util.Deque;

public class InterviewSessionMemory {
    private final InterviewProfile profile = new InterviewProfile();
    private final Deque<ChatTurn> recentTurns = new ArrayDeque<>();
    private int maxTurns = 4;
    private ConversationStage stage = ConversationStage.INIT;

    public InterviewProfile getProfile() {
        return profile;
    }

    public Deque<ChatTurn> getRecentTurns() {
        return recentTurns;
    }

    public ConversationStage getStage() {
        return stage;
    }

    public void setStage(ConversationStage stage) {
        this.stage = stage;
    }

    public void mergeRequest(ChatRequest request) {
        profile.mergeFrom(request);
        // Disable state-machine transitions and keep a single-stage flow.
        this.stage = ConversationStage.INIT;
    }

    public void addTurn(String question, String answer) {
        if (question == null || question.isBlank()) {
            return;
        }
        this.stage = ConversationStage.INIT;
        recentTurns.addLast(new ChatTurn(question, answer));
        while (recentTurns.size() > maxTurns) {
            recentTurns.removeFirst();
        }
    }

    public boolean hasEnoughProfile() {
        return profile.isComplete();
    }
}
